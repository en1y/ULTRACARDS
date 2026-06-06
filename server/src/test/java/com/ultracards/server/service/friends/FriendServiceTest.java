package com.ultracards.server.service.friends;

import com.ultracards.gateway.dto.friends.UserPresenceStatusDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.friends.FriendBlockEntity;
import com.ultracards.server.entity.friends.FriendRequestEntity;
import com.ultracards.server.entity.friends.FriendRelationEntity;
import com.ultracards.server.entity.games.gamestats.BriskulaMatchupStats;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import com.ultracards.server.enums.friends.FriendRequestStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.friends.FriendBlockRepository;
import com.ultracards.server.repositories.friends.FriendRequestRepository;
import com.ultracards.server.repositories.friends.FriendRelationRepository;
import com.ultracards.server.repositories.games.UserBriskulaStatsRepository;
import com.ultracards.server.entity.chat.ChatEntity;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.lobby.LobbyService;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.presence.UserPresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendRelationRepository friendRelationRepository;

    @Mock
    private FriendBlockRepository friendBlockRepository;

    @Mock
    private UserBriskulaStatsRepository userBriskulaStatsRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private LobbyService lobbyService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private FriendService friendService;

    @Test
    void sendsFriendRequestAndCreatesNotification() {
        var requester = user(1L, "Requester");
        var recipient = user(2L, "Recipient");
        var requestId = UUID.randomUUID();

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(friendRelationRepository.findByNormalizedPair(1L, 2L)).thenReturn(Optional.empty());
        when(friendRequestRepository.findBetweenUsersWithStatus(1L, 2L, FriendRequestStatus.PENDING))
                .thenReturn(Optional.empty());
        when(friendRequestRepository.save(any(FriendRequestEntity.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, FriendRequestEntity.class);
            saved.setId(requestId);
            return saved;
        });

        var result = friendService.sendFriendRequest(requester, 2L);

        assertThat(result.getId()).isEqualTo(requestId);
        assertThat(result.getStatus().name()).isEqualTo(FriendRequestStatus.PENDING.name());
        verify(notificationService).createFriendInviteNotification(requester, recipient, requestId);
    }

    @Test
    void rejectsFriendRequestWhenRecipientBlockedRequester() {
        var requester = user(1L, "Requester");
        var recipient = user(2L, "Recipient");

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(friendBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> friendService.sendFriendRequest(requester, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(friendRequestRepository, notificationService);
    }

    @Test
    void acceptsFriendRequestAndCreatesFriendRelation() {
        var requester = user(1L, "Requester");
        var recipient = user(2L, "Recipient");
        var requestId = UUID.randomUUID();
        var request = friendRequest(requestId, requester, recipient);

        when(friendRequestRepository.findByIdAndRecipientId(requestId, 2L)).thenReturn(Optional.of(request));
        when(friendRelationRepository.findByNormalizedPair(1L, 2L)).thenReturn(Optional.empty());
        when(friendRelationRepository.save(any(FriendRelationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(friendRequestRepository.save(request)).thenReturn(request);

        var result = friendService.acceptRequest(recipient, requestId);

        var friendRelationCaptor = ArgumentCaptor.forClass(FriendRelationEntity.class);
        verify(friendRelationRepository).save(friendRelationCaptor.capture());
        assertThat(friendRelationCaptor.getValue().getUserOne()).isEqualTo(requester);
        assertThat(friendRelationCaptor.getValue().getUserTwo()).isEqualTo(recipient);
        assertThat(result.getStatus().name()).isEqualTo(FriendRequestStatus.ACCEPTED.name());
        verify(chatService).openFriendChat(friendRelationCaptor.getValue());
    }

    @Test
    void blocksFriendRequestAndStoresOneWayBlock() {
        var requester = user(1L, "Requester");
        var recipient = user(2L, "Recipient");
        var requestId = UUID.randomUUID();
        var request = friendRequest(requestId, requester, recipient);

        when(friendRequestRepository.findByIdAndRecipientId(requestId, 2L)).thenReturn(Optional.of(request));
        when(friendBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);
        when(friendRelationRepository.findByNormalizedPair(1L, 2L)).thenReturn(Optional.empty());
        when(friendRequestRepository.save(request)).thenReturn(request);

        var result = friendService.blockRequest(recipient, requestId);

        var blockCaptor = ArgumentCaptor.forClass(FriendBlockEntity.class);
        verify(friendBlockRepository).save(blockCaptor.capture());
        assertThat(blockCaptor.getValue().getBlocker()).isEqualTo(recipient);
        assertThat(blockCaptor.getValue().getBlocked()).isEqualTo(requester);
        assertThat(result.getStatus().name()).isEqualTo(FriendRequestStatus.BLOCKED.name());
    }

    @Test
    void removesFriendRelationForBothUsers() {
        var user = user(1L, "User");
        var friend = user(2L, "Friend");
        var friendRelation = new FriendRelationEntity(user, friend);

        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(friendRelationRepository.findByNormalizedPair(1L, 2L)).thenReturn(Optional.of(friendRelation));

        friendService.removeFriend(user, 2L);

        verify(chatService).detachFriendChat(friendRelation);
        verify(friendRelationRepository).delete(friendRelation);
    }

    @Test
    void ordersFriendsByPlayedTogetherCount() {
        var user = user(1L, "User");
        var lesserFriend = user(2L, "Lesser");
        var greaterFriend = user(3L, "Greater");
        var lesserFriendRelation = friendRelation(UUID.randomUUID(), user, lesserFriend);
        var greaterFriendRelation = friendRelation(UUID.randomUUID(), user, greaterFriend);
        var stats = new UserBriskulaStats(user);
        stats.getWinsAgainstUser().add(new BriskulaMatchupStats("TWO_PLAYER", 2L, 1, 0));
        stats.getWinsAgainstUser().add(new BriskulaMatchupStats("TWO_PLAYER", 3L, 4, 0));

        when(friendRelationRepository.findByUserId(1L))
                .thenReturn(List.of(lesserFriendRelation, greaterFriendRelation));
        when(userBriskulaStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        when(userPresenceService.getStatus(lesserFriend)).thenReturn(UserPresenceStatusDTO.ONLINE);
        when(userPresenceService.getStatus(greaterFriend)).thenReturn(UserPresenceStatusDTO.IN_GAME);
        when(chatService.createFriendChat(lesserFriendRelation)).thenReturn(chat(UUID.randomUUID(), lesserFriendRelation));
        when(chatService.createFriendChat(greaterFriendRelation)).thenReturn(chat(UUID.randomUUID(), greaterFriendRelation));

        var friends = friendService.getFriends(user);

        assertThat(friends).extracting(friend -> friend.getUser().getId())
                .containsExactly(3L, 2L);
        assertThat(friends.getFirst().getTotalPlayedTogether()).isEqualTo(4);
        assertThat(friends.getFirst().getChatId()).isNotNull();
        assertThat(friends.getFirst().getPresenceStatus()).isEqualTo(UserPresenceStatusDTO.IN_GAME);
    }

    @Test
    void returnsBlockedUsersFromBlockRows() {
        var user = user(1L, "User");
        var blocked = user(2L, "Blocked");
        var block = new FriendBlockEntity(user, blocked);
        block.setId(UUID.randomUUID());
        block.setCreatedAt(Instant.parse("2026-06-01T10:00:00Z"));

        when(friendBlockRepository.findByBlockerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(block));

        var blockedUsers = friendService.getBlockedUsers(user);

        assertThat(blockedUsers).hasSize(1);
        assertThat(blockedUsers.getFirst().getUser().getId()).isEqualTo(2L);
        assertThat(blockedUsers.getFirst().getFriendRelationId()).isNull();
        assertThat(blockedUsers.getFirst().getChatId()).isNull();
        verify(friendBlockRepository).findByBlockerIdOrderByCreatedAtDesc(1L);
        verifyNoInteractions(userPresenceService, chatService);
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity(username + "@example.com", username);
        user.setId(id);
        return user;
    }

    private FriendRequestEntity friendRequest(UUID id, UserEntity requester, UserEntity recipient) {
        var request = new FriendRequestEntity(requester, recipient);
        request.setId(id);
        return request;
    }

    private FriendRelationEntity friendRelation(UUID id, UserEntity user, UserEntity friend) {
        var friendRelation = new FriendRelationEntity(user, friend);
        friendRelation.setId(id);
        return friendRelation;
    }

    private ChatEntity chat(UUID id, FriendRelationEntity friendRelation) {
        var chat = new ChatEntity(friendRelation);
        chat.setId(id);
        return chat;
    }
}

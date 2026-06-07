package com.ultracards.server.service.chat;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.chat.ChatEntity;
import com.ultracards.server.entity.chat.ChatMessageEntity;
import com.ultracards.server.entity.friends.FriendRelationEntity;
import com.ultracards.server.repositories.chat.ChatMessageRepository;
import com.ultracards.server.repositories.chat.ChatReadStateRepository;
import com.ultracards.server.repositories.chat.ChatRepository;
import com.ultracards.server.repositories.friends.FriendRelationRepository;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock
    private ChatManager chatManager;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatReadStateRepository chatReadStateRepository;

    @Mock
    private FriendRelationRepository friendRelationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ChatEventPublisher eventPublisher;

    @Mock
    private UltrakillLevelService ultrakillLevelService;

    @InjectMocks
    private ChatService chatService;

    @Test
    void sanitizesLobbyMessagesBeforeSending() {
        var lobbyId = UUID.randomUUID();
        var sender = user(1L, "Sender");
        var chat = new ChatEntity(lobbyId);

        when(chatManager.getChat(lobbyId)).thenReturn(chat);
        when(ultrakillLevelService.findLevelNumbers("Hello friend")).thenReturn(new String[0]);

        chatService.sendMessage(lobbyId, sender, "<script>alert(1)</script>Hello <b>friend</b>");

        assertThat(chat.getMessages()).hasSize(1);
        assertThat(chat.getMessages().getFirst().getMessage()).isEqualTo("Hello friend");
    }

    @Test
    void keepsPlainTextPunctuationAfterSanitizing() {
        var lobbyId = UUID.randomUUID();
        var sender = user(1L, "Sender");
        var chat = new ChatEntity(lobbyId);
        var message = "I'm \"ready\" & waiting <b>now</b>";

        when(chatManager.getChat(lobbyId)).thenReturn(chat);
        when(ultrakillLevelService.findLevelNumbers("I'm \"ready\" & waiting now")).thenReturn(new String[0]);

        chatService.sendMessage(lobbyId, sender, message);

        assertThat(chat.getMessages()).hasSize(1);
        assertThat(chat.getMessages().getFirst().getMessage()).isEqualTo("I'm \"ready\" & waiting now");
    }

    @Test
    void sanitizesFriendMessagesBeforePersisting() {
        var user = user(1L, "User");
        var friend = user(2L, "Friend");
        var relation = new FriendRelationEntity(user, friend);
        relation.setId(UUID.randomUUID());

        when(friendRelationRepository.findByNormalizedPair(1L, 2L)).thenReturn(Optional.of(relation));
        when(chatRepository.findByFriendRelationId(relation.getId())).thenReturn(Optional.empty());
        when(chatRepository.save(any(ChatEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatService.sendFriendMessage(user, 2L, "<img src=x onerror=alert(1)>Safe text");

        var chatCaptor = ArgumentCaptor.forClass(ChatEntity.class);
        verify(chatRepository, atLeastOnce()).save(chatCaptor.capture());
        var savedChat = chatCaptor.getAllValues().getLast();
        assertThat(savedChat.getMessages()).hasSize(1);
        assertThat(savedChat.getMessages().getFirst().getMessage()).isEqualTo("Safe text");
        verify(notificationService).createTextNotification(user, 2L, "Safe text");
    }

    @Test
    void marksFriendMessageNotificationsReadWhenReadingFriendChat() {
        var user = user(1L, "User");
        var friend = user(2L, "Friend");
        var relation = new FriendRelationEntity(user, friend);
        relation.setId(UUID.randomUUID());
        var chat = new ChatEntity(relation);
        var message = new ChatMessageEntity(chat, friend, "hello");

        when(friendRelationRepository.findByNormalizedPair(1L, 2L)).thenReturn(Optional.of(relation));
        when(chatRepository.findByFriendRelationId(relation.getId())).thenReturn(Optional.of(chat));
        when(chatMessageRepository.findFirstByChatIdOrderByCreatedAtDesc(chat.getId())).thenReturn(Optional.of(message));

        chatService.readAllFriendMessages(user, 2L);

        verify(chatReadStateRepository).save(any());
        verify(notificationService).markUnreadTextNotificationsFromSenderRead(user, 2L);
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity(username + "@example.com", username);
        user.setId(id);
        return user;
    }
}

package com.ultracards.server.service.friends;

import com.ultracards.gateway.dto.friends.FriendDTO;
import com.ultracards.gateway.dto.friends.FriendPlayCountDTO;
import com.ultracards.gateway.dto.friends.FriendRequestDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.friends.FriendBlockEntity;
import com.ultracards.server.entity.friends.FriendRequestEntity;
import com.ultracards.server.entity.friends.FriendRelationEntity;
import com.ultracards.server.entity.games.gamestats.BriskulaMatchupStats;
import com.ultracards.server.enums.friends.FriendRelationStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.friends.FriendBlockRepository;
import com.ultracards.server.repositories.friends.FriendRequestRepository;
import com.ultracards.server.repositories.friends.FriendRelationRepository;
import com.ultracards.server.repositories.games.UserBriskulaStatsRepository;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.presence.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ultracards.server.enums.friends.FriendRequestStatus.PENDING;
import static com.ultracards.server.enums.friends.FriendRelationStatus.BLOCKED;
import static com.ultracards.server.enums.friends.FriendRelationStatus.FRIENDS;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final FriendBlockRepository friendBlockRepository;
    private final UserBriskulaStatsRepository userBriskulaStatsRepository;
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;

    @Transactional(readOnly = true)
    public List<FriendDTO> getFriends(UserEntity user) {
        var friends = getFriendDtos(user, FRIENDS);
        sortFriends(friends, false);
        return friends;
    }

    @Transactional(readOnly = true)
    public List<FriendDTO> getBlockedUsers(UserEntity user) {
        var friends = getFriendDtos(user, BLOCKED);
        sortFriends(friends, true);
        return friends;
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getIncomingRequests(UserEntity user) {
        return toRequestDtos(friendRequestRepository.findByRecipientIdAndStatusOrderByCreatedAtDesc(user.getId(), PENDING));
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getOutgoingRequests(UserEntity user) {
        return toRequestDtos(friendRequestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(user.getId(), PENDING));
    }

    @Transactional
    public FriendRequestDTO sendFriendRequest(UserEntity requester, Long recipientUserId) {
        var recipient = findUser(recipientUserId, "Recipient user not found");

        if (requester.equals(recipient))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot send a friend request to yourself");

        if (isBlocked(recipient, requester))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This user is not accepting friend requests from you");

        if (isBlocked(requester, recipient))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unblock this user before sending a friend request");

        if (findFriendRelation(requester, recipient, FRIENDS).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Users are already friends");

        if (friendRequestRepository.findBetweenUsersWithStatus(requester.getId(), recipient.getId(), PENDING).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending friend request already exists");

        var friendRequest = friendRequestRepository.save(new FriendRequestEntity(requester, recipient));
        notificationService.createFriendInviteNotification(requester, recipient, friendRequest.getId());
        return friendRequest.toDto();
    }

    @Transactional
    public FriendRequestDTO acceptRequest(UserEntity recipient, UUID requestId) {
        var request = getPendingOwnedRequest(recipient, requestId);
        var requester = request.getRequester();

        if (isBlocked(recipient, requester))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Unblock this user before accepting the request");

        var friendRelation = findFriendRelation(recipient, requester)
                .orElseGet(() -> new FriendRelationEntity(recipient, requester));
        friendRelation.activate();
        friendRelationRepository.save(friendRelation);

        request.accept();
        return friendRequestRepository.save(request).toDto();
    }

    @Transactional
    public FriendRequestDTO declineRequest(UserEntity recipient, UUID requestId) {
        var request = getPendingOwnedRequest(recipient, requestId);
        request.decline();
        return friendRequestRepository.save(request).toDto();
    }

    @Transactional
    public FriendRequestDTO blockRequest(UserEntity recipient, UUID requestId) {
        var request = getPendingOwnedRequest(recipient, requestId);
        var requester = request.getRequester();

        if (!isBlocked(recipient, requester))
            friendBlockRepository.save(new FriendBlockEntity(recipient, requester));

        findFriendRelation(recipient, requester, FRIENDS)
                .ifPresent(friendRelation -> friendRelation.remove(recipient));

        request.block();
        return friendRequestRepository.save(request).toDto();
    }

    @Transactional
    public void removeFriend(UserEntity user, Long friendUserId) {
        var friendRelation = getActiveFriendRelation(user, friendUserId);
        friendRelation.remove(user);
        friendRelationRepository.save(friendRelation);
    }

    @Transactional
    public void unblockUser(UserEntity user, Long blockedUserId) {
        friendBlockRepository.deleteByBlockerIdAndBlockedId(user.getId(), blockedUserId);
    }

    private FriendRequestEntity getPendingOwnedRequest(UserEntity recipient, UUID requestId) {
        var request = friendRequestRepository.findByIdAndRecipientId(requestId, recipient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found"));
        if (request.getStatus() != PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Friend request has already been handled");
        return request;
    }

    private UserEntity findUser(Long userId, String message) {
        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }

    @Transactional(readOnly = true)
    public UserEntity getActiveFriend(UserEntity user, Long friendUserId) {
        return getActiveFriendRelation(user, friendUserId).getOtherUser(user);
    }

    private FriendRelationEntity getActiveFriendRelation(UserEntity user, Long friendUserId) {
        var relation = findFriendRelation(user, findUser(friendUserId, "Friend user not found"), FRIENDS)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active friend relation not found"));
        if (!relation.contains(user))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active friend relation not found");
        return relation;
    }

    private Optional<FriendRelationEntity> findFriendRelation(UserEntity user, UserEntity friend) {
        var userOneId = Math.min(user.getId(), friend.getId());
        var userTwoId = Math.max(user.getId(), friend.getId());
        return friendRelationRepository.findByNormalizedPair(userOneId, userTwoId);
    }

    private Optional<FriendRelationEntity> findFriendRelation(UserEntity user, UserEntity friend, FriendRelationStatus status) {
        return findFriendRelation(user, friend)
                .filter(friendRelation -> friendRelation.getStatus() == status);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UserEntity blocker, UserEntity blocked) {
        return friendBlockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blocked.getId());
    }

    private List<FriendDTO> getFriendDtos(UserEntity user, FriendRelationStatus status) {
        var friendRelations = friendRelationRepository.findByUserIdAndStatus(user.getId(), status);
        return toFriendDtos(user, friendRelations, loadPlayCounts(user));
    }

    private List<FriendDTO> toFriendDtos(UserEntity user, List<FriendRelationEntity> friendRelations, Map<Long, Map<GameTypeDTO, Integer>> playCountsByUser) {
        var dtos = new ArrayList<FriendDTO>();
        for (var friendRelation : friendRelations) {
            var friend = friendRelation.getOtherUser(user);
            var playCounts = playCountsByUser.getOrDefault(friend.getId(), Map.of());
            dtos.add(toFriendDto(friendRelation, friend, playCounts));
        }
        return dtos;
    }

    private void sortFriends(List<FriendDTO> friends, boolean blockedFriends) {
        friends.sort(blockedFriends ? this::compareBlockedFriends : this::compareFriends);
    }

    private int compareFriends(FriendDTO left, FriendDTO right) {
        var byPlayed = Integer.compare(right.getTotalPlayedTogether(), left.getTotalPlayedTogether());
        if (byPlayed != 0) return byPlayed;
        return left.getUser().getName().compareToIgnoreCase(right.getUser().getName());
    }

    private int compareBlockedFriends(FriendDTO left, FriendDTO right) {
        if (left.getRemovedAt() == null && right.getRemovedAt() == null) return 0;
        if (left.getRemovedAt() == null) return 1;
        if (right.getRemovedAt() == null) return -1;
        return right.getRemovedAt().compareTo(left.getRemovedAt());
    }

    private FriendDTO toFriendDto(FriendRelationEntity friendRelation, UserEntity friend, Map<GameTypeDTO, Integer> playCounts) {
        var counts = new ArrayList<FriendPlayCountDTO>();
        var total = 0;
        for (var entry : playCounts.entrySet()) {
            counts.add(new FriendPlayCountDTO(entry.getKey(), entry.getValue()));
            total += entry.getValue();
        }

        return new FriendDTO(
                friendRelation.getId(),
                new GamePlayerDTO(friend.getUsername(), friend.getId()),
                friendRelation.getStatus().toDto(),
                userPresenceService.getStatus(friend),
                total,
                counts,
                friendRelation.getCreatedAt(),
                friendRelation.getRemovedAt()
        );
    }

    private Map<Long, Map<GameTypeDTO, Integer>> loadPlayCounts(UserEntity user) {
        var countsByUser = new HashMap<Long, Map<GameTypeDTO, Integer>>();
        var briskulaStats = userBriskulaStatsRepository.findByUser(user).orElse(null);
        if (briskulaStats == null)
            return countsByUser;

        addPlayCounts(countsByUser, briskulaStats.getWinsAgainstUser());
        addPlayCounts(countsByUser, briskulaStats.getWinsWithTeammate());
        return countsByUser;
    }

    private void addPlayCounts(Map<Long, Map<GameTypeDTO, Integer>> countsByUser, Collection<BriskulaMatchupStats> stats) {
        for (var stat : stats)
            addPlayCount(countsByUser, stat.getRelatedUserId(), GameTypeDTO.Briskula, stat.getPlayed());
    }

    private void addPlayCount(Map<Long, Map<GameTypeDTO, Integer>> countsByUser, Long userId, GameTypeDTO gameType, int played) {
        var countsByType = countsByUser.computeIfAbsent(userId, ignored -> new EnumMap<>(GameTypeDTO.class));
        countsByType.merge(gameType, played, Integer::sum);
    }

    private List<FriendRequestDTO> toRequestDtos(List<FriendRequestEntity> requests) {
        var dtos = new ArrayList<FriendRequestDTO>();
        for (var request : requests) {
            dtos.add(request.toDto());
        }
        return dtos;
    }
}

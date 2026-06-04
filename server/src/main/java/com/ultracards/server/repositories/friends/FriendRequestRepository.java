package com.ultracards.server.repositories.friends;

import com.ultracards.server.entity.friends.FriendRequestEntity;
import com.ultracards.server.enums.friends.FriendRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, UUID> {

    @EntityGraph(attributePaths = {"requester", "recipient"})
    List<FriendRequestEntity> findByRecipientIdAndStatusOrderByCreatedAtDesc(Long recipientId, FriendRequestStatus status);

    @EntityGraph(attributePaths = {"requester", "recipient"})
    List<FriendRequestEntity> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, FriendRequestStatus status);

    @EntityGraph(attributePaths = {"requester", "recipient"})
    Optional<FriendRequestEntity> findByIdAndRecipientId(UUID id, Long recipientId);

    @EntityGraph(attributePaths = {"requester", "recipient"})
    @Query("""
            select request
            from FriendRequestEntity request
            where request.status = :status
                and (
                    (request.requester.id = :firstUserId and request.recipient.id = :secondUserId)
                    or (request.requester.id = :secondUserId and request.recipient.id = :firstUserId)
                )
            """)
    Optional<FriendRequestEntity> findBetweenUsersWithStatus(
            @Param("firstUserId") Long firstUserId,
            @Param("secondUserId") Long secondUserId,
            @Param("status") FriendRequestStatus status
    );
}

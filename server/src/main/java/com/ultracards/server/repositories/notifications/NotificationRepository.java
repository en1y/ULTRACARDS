package com.ultracards.server.repositories.notifications;

import com.ultracards.server.entity.notifications.NotificationEntity;
import com.ultracards.server.enums.NotificationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    @EntityGraph(attributePaths = {"sender", "recipient"})
    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    @EntityGraph(attributePaths = {"sender", "recipient"})
    List<NotificationEntity> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

    @EntityGraph(attributePaths = {"sender", "recipient"})
    Page<NotificationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "recipient"})
    Page<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "recipient"})
    @Query("""
            select n from NotificationEntity n
            where (:recipientId is null or n.recipient.id = :recipientId)
              and (:type is null or n.type = :type)
              and (:read is null or n.read = :read)
              and (:query is null or lower(coalesce(n.message, '')) like :query)
            order by n.createdAt desc
            """)
    Page<NotificationEntity> findAdminReport(@Param("recipientId") Long recipientId,
                                             @Param("type") NotificationType type,
                                             @Param("read") Boolean read,
                                             @Param("query") String query,
                                             Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "recipient"})
    Optional<NotificationEntity> findByIdAndRecipientId(UUID id, Long recipientId);

    @EntityGraph(attributePaths = {"sender", "recipient"})
    List<NotificationEntity> findByRecipientIdAndSenderIdAndTypeAndReadFalse(
            Long recipientId,
            Long senderId,
            NotificationType type
    );

    boolean existsByRecipientIdAndTypeAndLobbyId(Long recipientId, NotificationType type, UUID lobbyId);

    void deleteByTypeAndLobbyId(NotificationType type, UUID lobbyId);
}

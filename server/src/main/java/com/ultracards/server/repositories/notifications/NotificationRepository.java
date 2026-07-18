package com.ultracards.server.repositories.notifications;

import com.ultracards.server.entity.notifications.NotificationEntity;
import com.ultracards.server.enums.NotificationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

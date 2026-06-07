package com.ultracards.server.repositories.chat;

import com.ultracards.server.entity.chat.ChatReadStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatReadStateRepository extends JpaRepository<ChatReadStateEntity, UUID> {
    Optional<ChatReadStateEntity> findByChatIdAndUserId(UUID chatId, Long userId);
}

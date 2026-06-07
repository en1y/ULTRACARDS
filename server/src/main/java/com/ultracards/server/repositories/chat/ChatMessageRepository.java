package com.ultracards.server.repositories.chat;

import com.ultracards.server.entity.chat.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    Optional<ChatMessageEntity> findFirstByChatIdOrderByCreatedAtDesc(UUID chatId);
}

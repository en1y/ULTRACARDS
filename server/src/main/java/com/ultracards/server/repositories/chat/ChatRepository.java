package com.ultracards.server.repositories.chat;

import com.ultracards.server.entity.chat.ChatEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {
    @EntityGraph(attributePaths = {"messages", "messages.sender", "friendRelation", "userOne", "userTwo"})
    Optional<ChatEntity> findByFriendRelationId(UUID friendRelationId);

    @EntityGraph(attributePaths = {"messages", "messages.sender", "friendRelation", "userOne", "userTwo"})
    Optional<ChatEntity> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);
}

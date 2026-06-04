package com.ultracards.server.repositories.friends;

import com.ultracards.server.entity.friends.FriendBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FriendBlockRepository extends JpaRepository<FriendBlockEntity, UUID> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}

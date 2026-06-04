package com.ultracards.server.enums.friends;

import com.ultracards.gateway.dto.friends.FriendRelationStatusDTO;

public enum FriendRelationStatus {
    FRIENDS,
    BLOCKED;

    public FriendRelationStatusDTO toDto() {
        return FriendRelationStatusDTO.valueOf(name());
    }
}

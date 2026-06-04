package com.ultracards.server.enums.friends;

import com.ultracards.gateway.dto.friends.FriendRequestStatusDTO;

public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    BLOCKED;

    public FriendRequestStatusDTO toDto() {
        return FriendRequestStatusDTO.valueOf(name());
    }
}

package com.ultracards.server.entity.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.templates.game.interfaces.HandInterface;
import com.ultracards.templates.game.interfaces.PlayerInterface;

public interface PlayerEntity {
    UserEntity getUser();
}

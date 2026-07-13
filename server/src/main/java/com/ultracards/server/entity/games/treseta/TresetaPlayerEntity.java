package com.ultracards.server.entity.games.treseta;

import com.ultracards.games.treseta.TresetaPlayer;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.PlayerEntity;
import lombok.Getter;

public class TresetaPlayerEntity extends TresetaPlayer implements PlayerEntity {
    @Getter private final UserEntity user;

    public TresetaPlayerEntity(String name, UserEntity user) {
        super(name);
        this.user = user;
    }

    public GamePlayerDTO getGamePlayerDTO() {
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }
}

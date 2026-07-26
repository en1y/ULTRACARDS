package com.ultracards.server.entity.games.durak;

import com.ultracards.games.durak.DurakPlayer;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.PlayerEntity;
import lombok.Getter;

public class DurakPlayerEntity extends DurakPlayer implements PlayerEntity {
    @Getter private final UserEntity user;

    public DurakPlayerEntity(UserEntity user, int seat) {
        super(user.getUsername(), seat);
        this.user = user;
    }

    public GamePlayerDTO getGamePlayerDTO() {
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }
}

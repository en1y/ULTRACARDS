package com.ultracards.server.entity.games.briskula;

import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.gateway.dto.updated.games.GamePlayerDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.PlayerEntity;
import jakarta.persistence.*;
import lombok.Getter;

public class BriskulaPlayerEntity extends BriskulaPlayer implements PlayerEntity {
    @Getter
    private final UserEntity user;

    public BriskulaPlayerEntity(String name, UserEntity user) {
        super(name);
        this.user = user;
    }

    public GamePlayerDTO getGamePlayerDTO() {
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }
}

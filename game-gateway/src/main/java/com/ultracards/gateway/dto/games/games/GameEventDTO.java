package com.ultracards.gateway.dto.games.games;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GameEventDTO {
    private GameEntityDTO gameEntity;
    private GameEventTypeDTO gameEvent;
    private GameResultDTO result;

    public GameEventDTO(GameEntityDTO gameEntity, GameEventTypeDTO gameEvent) {
        this.gameEntity = gameEntity;
        this.gameEvent = gameEvent;
    }

    public enum GameEventTypeDTO {
        STARTED, UPDATED, RESULTED, CLOSED
    }
}

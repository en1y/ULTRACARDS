package com.ultracards.gateway.dto.games.games;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GameEventDTO<T extends GameEntityDTO> {
    private T gameEntity;
    private GameEventTypeDTO gameEvent;
    private GameResultDTO result;

    public GameEventDTO(T gameEntity, GameEventTypeDTO gameEvent) {
        this.gameEntity = gameEntity;
        this.gameEvent = gameEvent;
    }

    public enum GameEventTypeDTO {
        STARTED, UPDATED, RESULTED, CLOSED
    }
}

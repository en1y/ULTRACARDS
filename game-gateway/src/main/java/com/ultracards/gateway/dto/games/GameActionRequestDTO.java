package com.ultracards.gateway.dto.games;

import java.util.Map;

/**
 * DTO for performing actions on a game.
 */
public class GameActionRequestDTO {
    private Long playerId;
    private String actionType;

    public GameActionRequestDTO() {
    }

    public GameActionRequestDTO(Long playerId, GameAction actionType) {
        this.playerId = playerId;
        this.actionType = actionType.name();
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
}
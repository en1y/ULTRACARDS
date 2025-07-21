package com.ultracards.server.dto.games;

import java.util.Map;

/**
 * DTO for performing actions on a game.
 */
public class GameActionRequestDTO {
    private Long playerId;
    private String actionType;
    private Map<String, Object> actionParams;

    public GameActionRequestDTO() {
    }

    public GameActionRequestDTO(Long playerId, String actionType, Map<String, Object> actionParams) {
        this.playerId = playerId;
        this.actionType = actionType;
        this.actionParams = actionParams;
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

    public Map<String, Object> getActionParams() {
        return actionParams;
    }

    public void setActionParams(Map<String, Object> actionParams) {
        this.actionParams = actionParams;
    }
}
package com.ultracards.server.dto.games;

import java.util.Map;

/**
 * DTO for representing a player within a game.
 */
public class GamePlayerDTO {
    private Long playerId;
    private String username;
    private Map<String, Object> playerState;

    public GamePlayerDTO() {
    }

    public GamePlayerDTO(Long playerId, String username, Map<String, Object> playerState) {
        this.playerId = playerId;
        this.username = username;
        this.playerState = playerState;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Map<String, Object> getPlayerState() {
        return playerState;
    }

    public void setPlayerState(Map<String, Object> playerState) {
        this.playerState = playerState;
    }
}
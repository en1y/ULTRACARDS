package com.ultracards.server.dto.games;

import java.util.List;
import java.util.Map;

/**
 * DTO for creating a new game.
 */
public class GameCreationRequestDTO {
    private String gameType;
    private List<Long> playerIds;
    private Map<String, Object> gameOptions;

    public GameCreationRequestDTO() {
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public List<Long> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<Long> playerIds) {
        this.playerIds = playerIds;
    }

    public Map<String, Object> getGameOptions() {
        return gameOptions;
    }

    public void setGameOptions(Map<String, Object> gameOptions) {
        this.gameOptions = gameOptions;
    }
}
package com.ultracards.gateway.dto.games;

import java.util.List;
import java.util.Map;

/**
 * DTO for creating a new game.
 */
public class GameCreationRequestDTO {
    private String gameType;
    private List<Long> playerIds;
    private Long creatorId;
    private String gameName;
    private Map<String, Object> gameOptions;

    public GameCreationRequestDTO() {
    }

    public GameCreationRequestDTO(String gameName, String gameType, Long creatorId, List<Long> playerIds, Map<String, Object> gameOptions) {
        this.gameType = gameType;
        this.creatorId = creatorId;
        this.playerIds = playerIds;
        this.gameName = gameName;
        this.gameOptions = gameOptions;
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

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public Map<String, Object> getGameOptions() {
        return gameOptions;
    }

    public void setGameOptions(Map<String, Object> gameOptions) {
        this.gameOptions = gameOptions;
    }
}
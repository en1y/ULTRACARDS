package com.ultracards.gateway.dto.games;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for representing a summary of a game for listing purposes.
 */
public class GameSummaryDTO {
    private String gameId;
    private String gameType;
    private String status;
    private int playerCount;
    private Long creator;
    private List<String> playerNames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String gameName;

    // Todo: implement setting the gameName field // sigma

    public GameSummaryDTO() {}

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public Long getCreator() {
        return creator;
    }

    public void setCreator(Long creator) {
        this.creator = creator;
    }

    public List<String> getPlayerNames() {
        return playerNames;
    }

    public void setPlayerNames(List<String> playerNames) {
        this.playerNames = playerNames;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    @Override
    public String toString() {
        return String.format("%s game \"%s\"",
                gameType.substring(0, 1).toUpperCase() + gameType.substring(1).toLowerCase(),
                gameName);
    }
}
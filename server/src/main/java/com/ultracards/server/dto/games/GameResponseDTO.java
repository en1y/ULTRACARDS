package com.ultracards.server.dto.games;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for returning game state to clients.
 */
public class GameResponseDTO {
    private String gameId;
    private String gameType;
    private String status; // CREATED, IN_PROGRESS, FINISHED
    private List<GamePlayerDTO> players;
    private Map<String, Object> gameState;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GameResponseDTO() {
    }

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

    public List<GamePlayerDTO> getPlayers() {
        return players;
    }

    public void setPlayers(List<GamePlayerDTO> players) {
        this.players = players;
    }

    public Map<String, Object> getGameState() {
        return gameState;
    }

    public void setGameState(Map<String, Object> gameState) {
        this.gameState = gameState;
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
}
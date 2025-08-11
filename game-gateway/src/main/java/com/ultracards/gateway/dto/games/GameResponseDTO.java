package com.ultracards.gateway.dto.games;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for returning game state to clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameResponseDTO {
    private String gameId;
    private String gameType;
    private String status; // CREATED, IN_PROGRESS, FINISHED
    private List<GamePlayerDTO> players;
    private Long creatorId;
    private String gameName;
    /*@JsonDeserialize(using = GameStateDeserializer.class)*/
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

/*class GameStateDeserializer extends JsonDeserializer<Map<String, Object>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); // Reuse ObjectMapper

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        // Parse the JSON into a Map
        try {
            return new HashMap<String, Object>(OBJECT_MAPPER.readValue(p, Map.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}*/

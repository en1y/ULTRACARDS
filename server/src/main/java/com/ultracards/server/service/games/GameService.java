package com.ultracards.server.service.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.server.dto.games.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing games.
 */
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GameService(GameRepository gameRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new game.
     *
     * @param request the game creation request
     * @return the created game
     */
    @Transactional
    public GameResponseDTO createGame(GameCreationRequestDTO request) {
        List<UserEntity> players = request.getPlayerIds().stream()
                .map(id -> userRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id)))
                .collect(Collectors.toList());

        GameEntity gameEntity = new GameEntity();
        gameEntity.setGameType(request.getGameType());
        gameEntity.setStatus("CREATED");
        gameEntity.setPlayers(players);

        try {
            // Store game options as part of the game state
            Map<String, Object> gameState = new HashMap<>();
            gameState.put("options", request.getGameOptions());
            gameEntity.setGameStateJson(objectMapper.writeValueAsString(gameState));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing game state", e);
        }

        gameEntity = gameRepository.save(gameEntity);
        return convertToResponseDTO(gameEntity);
    }

    /**
     * Get a game by ID.
     *
     * @param gameId the game ID
     * @return the game
     */
    public GameResponseDTO getGame(String gameId) {
        GameEntity gameEntity = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with id: " + gameId));
        return convertToResponseDTO(gameEntity);
    }

    /**
     * Update a game with an action.
     *
     * @param gameId the game ID
     * @param request the game action request
     * @return the updated game
     */
    @Transactional
    public GameResponseDTO updateGame(String gameId, GameActionRequestDTO request) {
        GameEntity gameEntity = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with id: " + gameId));

        // Verify player is part of the game
        UserEntity player = userRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getPlayerId()));
        
        if (!gameEntity.getPlayers().contains(player)) {
            throw new IllegalArgumentException("Player is not part of this game");
        }

        try {
            // Update game state based on action
            Map<String, Object> gameState = objectMapper.readValue(gameEntity.getGameStateJson(), Map.class);
            
            // Store action in game state history
            List<Map<String, Object>> actions = (List<Map<String, Object>>) gameState.getOrDefault("actions", new ArrayList<>());
            Map<String, Object> action = new HashMap<>();
            action.put("playerId", request.getPlayerId());
            action.put("actionType", request.getActionType());
            action.put("actionParams", request.getActionParams());
            action.put("timestamp", LocalDateTime.now().toString());
            actions.add(action);
            gameState.put("actions", actions);
            
            // In a real implementation, you would process the action based on game type and update the game state accordingly
            // For now, we just store the action in the history
            
            gameEntity.setGameStateJson(objectMapper.writeValueAsString(gameState));
            gameEntity.setUpdatedAt(LocalDateTime.now());
            
            gameEntity = gameRepository.save(gameEntity);
            return convertToResponseDTO(gameEntity);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing game state", e);
        }
    }

    /**
     * List all games.
     *
     * @return a list of all games
     */
    public List<GameSummaryDTO> listGames() {
        return gameRepository.findAll().stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * List games by status.
     *
     * @param status the status to filter by
     * @return a list of games with the specified status
     */
    public List<GameSummaryDTO> listGamesByStatus(String status) {
        return gameRepository.findByStatus(status).stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * List games by game type.
     *
     * @param gameType the game type to filter by
     * @return a list of games with the specified game type
     */
    public List<GameSummaryDTO> listGamesByType(String gameType) {
        return gameRepository.findByGameType(gameType).stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * List games by player.
     *
     * @param playerId the player ID to filter by
     * @return a list of games that include the specified player
     */
    public List<GameSummaryDTO> listGamesByPlayer(Long playerId) {
        UserEntity player = userRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + playerId));
        
        return gameRepository.findByPlayer(player).stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert a GameEntity to a GameResponseDTO.
     *
     * @param entity the entity to convert
     * @return the converted DTO
     */
    private GameResponseDTO convertToResponseDTO(GameEntity entity) {
        GameResponseDTO dto = new GameResponseDTO();
        dto.setGameId(entity.getId());
        dto.setGameType(entity.getGameType());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        // Convert players to DTOs
        List<GamePlayerDTO> playerDTOs = entity.getPlayers().stream()
                .map(player -> {
                    GamePlayerDTO playerDTO = new GamePlayerDTO();
                    playerDTO.setPlayerId(player.getId());
                    playerDTO.setUsername(player.getUsername());
                    // In a real implementation, you would extract player-specific state from the game state
                    playerDTO.setPlayerState(new HashMap<>());
                    return playerDTO;
                })
                .collect(Collectors.toList());
        dto.setPlayers(playerDTOs);
        
        try {
            // Parse game state from JSON
            dto.setGameState(objectMapper.readValue(entity.getGameStateJson(), Map.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error deserializing game state", e);
        }
        
        return dto;
    }

    /**
     * Convert a GameEntity to a GameSummaryDTO.
     *
     * @param entity the entity to convert
     * @return the converted DTO
     */
    private GameSummaryDTO convertToSummaryDTO(GameEntity entity) {
        GameSummaryDTO dto = new GameSummaryDTO();
        dto.setGameId(entity.getId());
        dto.setGameType(entity.getGameType());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setPlayerCount(entity.getPlayers().size());
        
        List<String> playerNames = entity.getPlayers().stream()
                .map(UserEntity::getUsername)
                .collect(Collectors.toList());
        dto.setPlayerNames(playerNames);
        
        return dto;
    }
}
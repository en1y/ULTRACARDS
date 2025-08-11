package com.ultracards.server.service.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.dto.games.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        var players = request.getPlayerIds().stream()
                .map(id -> userRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id)))
                .collect(Collectors.toList());

        var gameEntity = new GameEntity();
        gameEntity.setGameType(request.getGameType());
        gameEntity.setCreator(userRepository.findById(request.getCreatorId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getCreatorId())));
        gameEntity.setStatus("CREATED");
        gameEntity.setPlayers(players);
        gameEntity.setGameName(request.getGameName());

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
        GameEntity gameEntity = gameRepository.findByIdAndActiveTrue(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with id: " + gameId));
        return convertToResponseDTO(gameEntity);
    }

    /**
     * Update a game with an action.
     *
     * @param gameId  the game ID
     * @param request the game action request
     * @return the updated game
     */
    @Transactional
    public GameResponseDTO updateGame(String gameId, GameActionRequestDTO request) {
        var game = gameRepository.findByIdAndActiveTrue(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with id: " + gameId));

        var player = userRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getPlayerId()));

        var actionType = GameAction.valueOf(request.getActionType());

        if (GameAction.JOIN_GAME.equals(actionType)) {

            game.addPlayer(player);
            gameRepository.save(game);

        } else if (GameAction.LEAVE_GAME.equals(actionType)) {

            game.removePlayer(player);
            if (game.getCreator().equals(player)) {
                game.setActive(false);
            }
            if (game.getPlayers().isEmpty()) {
                game.setActive(false);
            }
            gameRepository.save(game);

        } else if (GameAction.START_GAME.equals(actionType)) {
            // TODO: Implement starting game
        } else {
            throw new IllegalArgumentException("Unsupported action type: " + actionType);
        }
        return convertToResponseDTO(game);
    }

    /**
     * List all games.
     *
     * @return a list of all games
     */
    public List<GameSummaryDTO> listGames() {
        return gameRepository.findAllByActiveTrue().stream()
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
        return gameRepository.findByStatusAndActiveTrue(status).stream()
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
        return gameRepository.findByGameTypeAndActiveTrue(gameType).stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    public void deactivateGamesByPlayer(Long playerId) {
        var player = userRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + playerId));

        gameRepository.findByPlayerAndActiveTrue(player)
                .forEach(game -> {
                    game.setActive(false);
                    gameRepository.save(game);
                });
    }

    /**
     * List games by player.
     *
     * @param playerId the player ID to filter by
     * @return a list of games that include the specified player
     */
    public List<GameSummaryDTO> listGamesByPlayer(Long playerId) {
        var player = userRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + playerId));

        return gameRepository.findByPlayerAndActiveTrue(player).stream()
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
        dto.setGameName(entity.getGameName());

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
        dto.setPlayerCount(entity.getPlayers().size());
        dto.setCreator(entity.getCreator().getId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setGameName(entity.getGameName());

        List<String> playerNames = entity.getPlayers().stream()
                .map(UserEntity::getUsername)
                .collect(Collectors.toList());
        dto.setPlayerNames(playerNames);

        return dto;
    }
}
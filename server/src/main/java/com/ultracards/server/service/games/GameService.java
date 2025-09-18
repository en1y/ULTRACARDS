package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameDTO;
import com.ultracards.gateway.dto.games.GameResultDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.GameLobby;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.GameEntityRepository;
import com.ultracards.server.service.games.briskula.BriskulaRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameEntityRepository gameRepository;
    private final UserGamesStatsService statsService;
    private final GameEventPublisher eventPublisher;
    private final LobbyEventPublisher lobbyEventPublisher;
    private final BriskulaRuntimeService briskulaRuntimeService;

    @Transactional
    public GameEntity startGame(GameLobby lobby, String configJson) {
        var game = new GameEntity();
        game.setType(lobby.getGameType());
        game.setGameType(lobby.getGameType().name());
        game.setGameName(lobby.getGameType().name() + " Lobby " + lobby.getId());
        game.setCreator(lobby.getOwner());
        game.setPlayers(lobby.getPlayers());
        game.setGameStateJson(configJson != null ? configJson : "{}");
        game.setActive(true);
        game.setCreatedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());

        // Persist
        game = gameRepository.save(game);

        if (game.getType() == GameType.BRISKULA) {
            var initialState = briskulaRuntimeService.initialize(game, lobby, configJson);
            game.setGameStateJson(initialState);
        }

        game.setUpdatedAt(LocalDateTime.now());
        game = gameRepository.save(game);

        // Increment games played for each player
        for (UserEntity p : lobby.getPlayers()) {
            var ugs = statsService.getByUser(p);
            if (ugs != null) statsService.addGamePlayed(ugs, lobby.getGameType());
        }

        // Notify start
        eventPublisher.publish(game.getId(), "STARTED", toJson(game.getGameStateJson()));
        lobbyEventPublisher.publish("STARTED", lobby, game.getId());
        if (game.getType() == GameType.BRISKULA) {
            eventPublisher.publish(game.getId(), "STATE", toJson(game.getGameStateJson()));
        }

        return game;
    }

    public GameEntity getGame(UUID gameId) {
        return gameRepository.findById(gameId).orElse(null);
    }

    @Transactional
    public void finishGame(GameResultDTO result, GameType type) {
        var game = gameRepository.findById(result.getGameId()).orElse(null);
        if (game == null) return;
        game.setActive(false);
        if (result.getFinalStateJson() != null) game.setGameStateJson(result.getFinalStateJson());
        game.setUpdatedAt(LocalDateTime.now());
        gameRepository.save(game);

        // Update winners
        if (result.getWinnerUserIds() != null) {
            for (var user : game.getPlayers()) {
                if (result.getWinnerUserIds().contains(user.getId())) {
                    var ugs = statsService.getByUser(user);
                    if (ugs != null) statsService.addGameWon(ugs, type);
                }
            }
        }

        eventPublisher.publish(game.getId(), "FINISHED", toJson(game.getGameStateJson()));
    }

    public GameDTO toDto(GameEntity game) {
        return new GameDTO(
                game.getId(),
                game.getGameName(),
                game.getType() != null ? game.getType().name() : null,
                game.getPlayers().stream().map(UserEntity::getId).toList(),
                game.getGameStateJson(),
                game.isActive(),
                game.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                game.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
    }

    private String toJson(String state) {
        // state is already JSON string; return as-is
        return state == null ? "{}" : state;
    }
}

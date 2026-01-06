package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.updated.games.GameTypeDTO;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameManager {
    private final Map<UUID, GameEntity<?>> gamesById = new ConcurrentHashMap<>();
    private final Map<Long, GameEntity<?>> gamesByUser = new ConcurrentHashMap<>();
    private final Map<UUID, GameEntity<?>> gamesByLobby = new ConcurrentHashMap<>();
    private final Map<GameTypeDTO, List<GameEntity<?>>> gamesByGameType = new ConcurrentHashMap<>();
    @Getter
    private final List<GameEntity<?>> games = Collections.synchronizedList(new ArrayList<>());
    private final GameEventPublisher gameEventPublisher;

    public GameManager(GameEventPublisher gameEventPublisher) {
        this.gameEventPublisher = gameEventPublisher;
        for (var gt: GameTypeDTO.values()) {
            gamesByGameType.put(gt, new ArrayList<>());
        }
    }

    public GameEntity<?> getGame(UUID id) {
        return gamesById.get(id);
    }
    public GameEntity<?> getGame(Long userId) {
        return gamesByUser.get(userId);
    }
    public GameEntity<?> getGameByLobbyId(UUID lobbyId) {
        return gamesByLobby.get(lobbyId);
    }
    public List<GameEntity<?>> getGames(GameTypeDTO gameTypeDTO) {
        return gamesByGameType.get(gameTypeDTO);
    }

    public GameEntity<?> createGame(GameEntity<?> gameEntity) {
        put(gameEntity);
        return gameEntity;
    }
    public Boolean deleteGame(GameEntity<?> game) {
        return remove(game);
    }

    private Boolean remove(GameEntity<?> game) {
        var g = getGame(game.getId());
        if (g != null) {
            gamesById.remove(g.getId());
            for (var p: g.getPlayers()) {
                gamesByUser.remove(p.getId());
            }
            gamesByLobby.put(game.getLobbyId(), game);
            gamesByGameType.get(g.getGameType()).remove(g);
            games.remove(game);
        }
        return g != null;
    }

    private void put(GameEntity<?> game) {
        remove(game);

        gamesById.put(game.getId(), game);
        for(var p: game.getPlayers()) {
            gamesByUser.put(p.getId(), game);
        }
        gamesByLobby.put(game.getLobbyId(), game);
        gamesByGameType.get(game.getGameType()).add(game);
        games.add(game);
    }
}

package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.entity.games.GameEntity;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameManager {
    private final Map<UUID, GameEntity<?>> gamesById = new ConcurrentHashMap<>();
    private final Map<Long, GameEntity<?>> gamesByUser = new ConcurrentHashMap<>();
    private final Map<UUID, GameEntity<?>> gamesByLobby = new ConcurrentHashMap<>();
    private final Map<UUID, GameEntity<?>>  lobbyByGameId = new ConcurrentHashMap<>();
    private final Map<GameTypeDTO, List<GameEntity<?>>> gamesByGameType = new ConcurrentHashMap<>();
    @Getter
    private final List<GameEntity<?>> games = Collections.synchronizedList(new ArrayList<>());

    public GameManager() {
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

    public GameEntity<?> getByLobby(UUID lobbyId) {
        return lobbyByGameId.get(lobbyId);
    }

    public GameEntity<?> createGame(GameEntity<?> gameEntity) {
        put(gameEntity);
        lobbyByGameId.put(gameEntity.getLobbyId(), gameEntity);
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
            lobbyByGameId.remove(game.getId());
            gamesByLobby.remove(game.getLobbyId());
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

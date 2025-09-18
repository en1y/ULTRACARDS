package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.LobbyCreateRequest;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameLobby;
import com.ultracards.server.enums.games.GameType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class LobbyService {

    private final Map<UUID, GameLobby> lobbies = new ConcurrentHashMap<>();
    // last heartbeat per lobby per userId
    private final Map<UUID, Map<Long, Long>> lobbyHeartbeats = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${app.ws.heartbeat-interval-ms:10000}")
    private long heartbeatIntervalMs;
    @org.springframework.beans.factory.annotation.Value("${app.ultracards.ws-socket-timeout-times:5}")
    private int allowedMisses;

    private final LobbyEventPublisher lobbyEventPublisher;

    public LobbyService(LobbyEventPublisher lobbyEventPublisher) {
        this.lobbyEventPublisher = lobbyEventPublisher;
    }

    public GameLobby createLobby(UserEntity owner, LobbyCreateRequest req) {
        var lobby = new GameLobby();
        lobby.setId(UUID.randomUUID());
        lobby.setOwner(owner);
        try {
            lobby.setGameType(req.getGameType() != null ? com.ultracards.server.enums.games.GameType.valueOf(req.getGameType()) : null);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown game type: " + req.getGameType());
        }
        lobby.setCreatedAt(Instant.now());
        lobby.setPlayers(new ArrayList<>(List.of(owner)));
        lobby.setLobbyName(req.getLobbyName());
        // default max = 4 unless config enforces another value
        int max = req.getMaxPlayers() != null ? req.getMaxPlayers() : 4;
        if (req.getConfigJson() != null && lobby.getGameType() == com.ultracards.server.enums.games.GameType.BRISKULA) {
            try {
                var cfg = com.ultracards.games.briskula.BriskulaGameConfig.valueOf(req.getConfigJson());
                max = cfg.getNumberOfPlayers();
            } catch (IllegalArgumentException ignore) {}
        }
        lobby.setMaxPlayers(max);
        lobby.setConfigJson(req.getConfigJson());
        lobbies.put(lobby.getId(), lobby);
        var hb = new ConcurrentHashMap<Long, Long>();
        if (owner != null && owner.getId() != null) {
            hb.put(owner.getId(), System.currentTimeMillis());
        }
        lobbyHeartbeats.put(lobby.getId(), hb);
        lobbyEventPublisher.publish("CREATED", lobby);
        return lobby;
    }

    public List<GameLobby> listLobbies() {
        return new ArrayList<>(lobbies.values());
    }

    public Optional<GameLobby> getLobby(UUID id) {
        return Optional.ofNullable(lobbies.get(id));
    }

    public GameLobby joinLobby(UserEntity user, UUID lobbyId) {
        var lobby = getLobbyOrThrow(lobbyId);
        var players = lobby.getPlayers();
        if (players.stream().anyMatch(u -> u.getId().equals(user.getId()))) return lobby;
        if (lobby.getMaxPlayers() != null && players.size() >= lobby.getMaxPlayers()) {
            throw new IllegalStateException("Lobby is full");
        }
        players.add(user);
        lobbyEventPublisher.publish("UPDATED", lobby);
        return lobby;
    }

    public GameLobby updateSettings(UserEntity owner, UUID lobbyId, String lobbyName, Integer maxPlayers, String configJson) {
        var lobby = getLobbyOrThrow(lobbyId);
        if (lobby.getOwner() == null || !Objects.equals(lobby.getOwner().getId(), owner.getId()))
            throw new SecurityException("Only owner can update lobby");

        if (lobbyName != null) lobby.setLobbyName(lobbyName);
        if (configJson != null) {
            lobby.setConfigJson(configJson);
            // If Briskula, align maxPlayers to selected config
            if (lobby.getGameType() == com.ultracards.server.enums.games.GameType.BRISKULA) {
                try {
                    var cfg = com.ultracards.games.briskula.BriskulaGameConfig.valueOf(configJson);
                    maxPlayers = cfg.getNumberOfPlayers();
                } catch (IllegalArgumentException ignore) {}
            }
        }
        if (maxPlayers != null) {
            int target = Math.max(1, maxPlayers);
            var players = lobby.getPlayers();
            if (players != null && players.size() > target) {
                throw new IllegalStateException("Cannot reduce max players below current player count");
            }
            lobby.setMaxPlayers(target);
        }
        lobbyEventPublisher.publish("UPDATED", lobby);
        return lobby;
    }

    public GameLobby kickPlayer(UserEntity owner, UUID lobbyId, Long playerId) {
        var lobby = getLobbyOrThrow(lobbyId);
        if (lobby.getOwner() == null || !Objects.equals(lobby.getOwner().getId(), owner.getId()))
            throw new SecurityException("Only owner can kick players");
        if (Objects.equals(owner.getId(), playerId)) {
            throw new IllegalArgumentException("Owner cannot kick themselves");
        }
        var players = lobby.getPlayers();
        boolean removed = players.removeIf(p -> Objects.equals(p.getId(), playerId));
        if (!removed) {
            throw new NoSuchElementException("Player not found in lobby");
        }
        var hb = lobbyHeartbeats.get(lobbyId);
        if (hb != null) hb.remove(playerId);
        lobbyEventPublisher.publish("UPDATED", lobby);
        return lobby;
    }

    public void disbandLobby(UserEntity requester, UUID lobbyId) {
        var lobby = getLobbyOrThrow(lobbyId);
        if (lobby.getOwner() == null || !Objects.equals(lobby.getOwner().getId(), requester.getId())) {
            throw new SecurityException("Only owner can disband lobby");
        }
        disbandLobby(lobbyId);
    }

    public void leaveLobby(UserEntity user, UUID lobbyId) {
        var lobby = getLobbyOrThrow(lobbyId);
        lobby.getPlayers().removeIf(u -> Objects.equals(u.getId(), user.getId()));
        if (lobby.getOwner() != null && Objects.equals(lobby.getOwner().getId(), user.getId())) {
            // owner left -> disband lobby
            lobbies.remove(lobbyId);
            lobbyEventPublisher.publish("DELETED", lobby);
        } else {
            lobbyEventPublisher.publish("UPDATED", lobby);
        }
    }

    public void disbandLobby(UUID lobbyId) {
        var lobby = lobbies.remove(lobbyId);
        lobbyHeartbeats.remove(lobbyId);
        if (lobby != null) lobbyEventPublisher.publish("DELETED", lobby);
    }

    private GameLobby getLobbyOrThrow(UUID lobbyId) throws NoSuchElementException {
        var lobby = lobbies.get(lobbyId);
        if (lobby == null) throw new NoSuchElementException("Lobby not found");
        return lobby;
    }

    public void recordHeartbeat(UUID lobbyId, Long userId) {
        if (userId == null) return;
        var hb = lobbyHeartbeats.computeIfAbsent(lobbyId, id -> new ConcurrentHashMap<>());
        hb.put(userId, System.currentTimeMillis());
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.ws.heartbeat-interval-ms:10000}")
    public void cleanupDeadLobbies() {
        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(1, allowedMisses) * Math.max(1000, heartbeatIntervalMs);
        for (var entry : lobbies.entrySet()) {
            var lobbyId = entry.getKey();
            var lobby = entry.getValue();
            var owner = lobby.getOwner();
            if (owner == null) continue;
            var hb = lobbyHeartbeats.get(lobbyId);
            Long last = hb != null ? hb.get(owner.getId()) : null;
            if (last == null) {
                continue;
            }
            if ((now - last) > timeoutMs) {
                // Owner offline too long -> disband lobby
                disbandLobby(lobbyId);
            }
        }
    }
}

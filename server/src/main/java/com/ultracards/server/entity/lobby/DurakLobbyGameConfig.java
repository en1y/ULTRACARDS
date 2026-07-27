package com.ultracards.server.entity.lobby;

import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.games.durak.DurakRuleException;
import com.ultracards.games.durak.DurakThrowInPolicy;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bridges the Durak lobby DTO and the validated {@link DurakGameConfig}, keeping an explicit
 * clockwise seat order that survives joins, leaves and mode changes.
 */
public class DurakLobbyGameConfig implements GameConfig {
    @Getter private final DurakGameConfig gameConfig;
    @Getter private final List<UserEntity> orderedUsers;

    public DurakLobbyGameConfig(DurakGameConfigDTO dto, List<UserEntity> users) {
        this(toConfig(dto), new ArrayList<>(users));
    }

    private DurakLobbyGameConfig(DurakGameConfig config, List<UserEntity> users) {
        gameConfig = config;
        orderedUsers = users;
    }

    @Override
    public DurakGameConfigDTO toDto() {
        var players = orderedUsers.stream().map(u -> new GamePlayerDTO(u.getUsername(), u.getId())).toList();
        return new DurakGameConfigDTO(gameConfig.numberOfPlayers(), DurakGameConfig.CARDS_IN_HAND,
                gameConfig.deckSize(), gameConfig.jokersEnabled(), toDto(gameConfig.throwInPolicy()),
                gameConfig.passingEnabled(), players, gameConfig.modeKey());
    }

    @Override
    public GameEntity<?, ?> createGame(UUID lobbyId, String name, UserEntity owner, List<UserEntity> users) {
        if (users.size() != gameConfig.numberOfPlayers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + gameConfig.modeKey() + " game needs exactly " + gameConfig.numberOfPlayers() + " players");
        }
        return new DurakGameEntity(lobbyId, name, owner, this, users);
    }

    /**
     * Rebuilds the seat order from a client request: duplicates collapse to their first
     * occurrence, unknown users are ignored, the owner is always present, and remaining seats are
     * filled from the lobby in join order.
     */
    public static DurakLobbyGameConfig fromDto(DurakGameConfigDTO dto, List<UserEntity> users, UserEntity owner) {
        var config = toConfig(dto);
        var byId = users.stream().collect(Collectors.toMap(UserEntity::getId, user -> user, (a, b) -> a));
        var ordered = new ArrayList<UserEntity>();
        if (dto.getOrderedUsers() != null) {
            for (var player : dto.getOrderedUsers()) {
                var user = byId.get(player.getId());
                if (user != null && !ordered.contains(user)) ordered.add(user);
            }
        }
        for (var user : users) if (!ordered.contains(user)) ordered.add(user);
        if (!ordered.contains(owner)) ordered.addFirst(owner);
        if (ordered.size() > config.numberOfPlayers()) {
            // Never evict an occupant implicitly; the owner must kick first.
            if (users.size() > config.numberOfPlayers()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Kick a player before reducing the game mode player count");
            }
            while (ordered.size() > config.numberOfPlayers()) ordered.removeLast();
        }
        return new DurakLobbyGameConfig(config, ordered);
    }

    /** Validates the DTO through the canonical record constructor and maps rule errors to 400. */
    public static DurakGameConfig toConfig(DurakGameConfigDTO dto) {
        if (dto == null || dto.getNumberOfPlayers() == null || dto.getDeckSize() == null
                || dto.getJokersEnabled() == null || dto.getThrowInPolicy() == null
                || dto.getPassingEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Durak requires numberOfPlayers, deckSize, jokersEnabled, throwInPolicy and passingEnabled");
        }
        if (dto.getCardsInHandNum() != null && dto.getCardsInHandNum() != DurakGameConfig.CARDS_IN_HAND) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Durak always deals six cards");
        }
        try {
            return new DurakGameConfig(dto.getNumberOfPlayers(), dto.getDeckSize(), dto.getJokersEnabled(),
                    toPolicy(dto.getThrowInPolicy()), dto.getPassingEnabled());
        } catch (DurakRuleException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public static DurakGameConfigDTO toDto(DurakGameConfig config, List<GamePlayerDTO> players) {
        return new DurakGameConfigDTO(config.numberOfPlayers(), DurakGameConfig.CARDS_IN_HAND, config.deckSize(),
                config.jokersEnabled(), toDto(config.throwInPolicy()), config.passingEnabled(), players,
                config.modeKey());
    }

    public static DurakThrowInPolicy toPolicy(DurakThrowInPolicyDTO dto) {
        return dto == DurakThrowInPolicyDTO.EVERYONE
                ? DurakThrowInPolicy.EVERYONE : DurakThrowInPolicy.NEIGHBORS_ONLY;
    }

    public static DurakThrowInPolicyDTO toDto(DurakThrowInPolicy policy) {
        return policy == DurakThrowInPolicy.EVERYONE
                ? DurakThrowInPolicyDTO.EVERYONE : DurakThrowInPolicyDTO.NEIGHBORS_ONLY;
    }
}

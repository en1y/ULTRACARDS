package com.ultracards.server.entity.lobby;

import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TresetaLobbyGameConfig implements GameConfig {
    @Getter private final TresetaGameConfig gameConfig;
    @Getter private final List<UserEntity> orderedUsers;

    public TresetaLobbyGameConfig(TresetaGameConfigDTO dto, List<UserEntity> users) {
        this(toConfig(dto), new ArrayList<>(users));
    }

    private TresetaLobbyGameConfig(TresetaGameConfig config, List<UserEntity> users) {
        gameConfig = config;
        orderedUsers = users;
    }

    @Override
    public TresetaGameConfigDTO toDto() {
        var players = orderedUsers.stream().map(u -> new GamePlayerDTO(u.getUsername(), u.getId())).toList();
        return new TresetaGameConfigDTO(gameConfig.getNumberOfPlayers(), gameConfig.getCardsInHandNum(),
                gameConfig.areTeamsEnabled(), players);
    }

    @Override
    public GameEntity<?, ?> createGame(UUID lobbyId, String name, UserEntity owner, List<UserEntity> users) {
        var gameUsers = users;
        if (gameConfig.areTeamsEnabled())
            gameUsers = new ArrayList<>(List.of(users.get(0), users.get(2), users.get(1), users.get(3)));
        return new TresetaGameEntity(lobbyId, name, owner, this, gameUsers);
    }

    public static TresetaLobbyGameConfig fromDto(TresetaGameConfigDTO dto, List<UserEntity> users, UserEntity owner) {
        var byId = users.stream().collect(Collectors.toMap(UserEntity::getId, user -> user));
        var ordered = new ArrayList<UserEntity>();
        if (dto.getOrderedUsers() != null)
            for (var player : dto.getOrderedUsers()) {
                var user = byId.get(player.getId());
                if (user != null && !ordered.contains(user)) ordered.add(user);
            }
        for (var user : users) if (!ordered.contains(user)) ordered.add(user);
        if (!ordered.contains(owner)) ordered.addFirst(owner);
        var config = toConfig(dto);
        while (ordered.size() > config.getNumberOfPlayers()) ordered.removeLast();
        return new TresetaLobbyGameConfig(config, ordered);
    }

    private static TresetaGameConfig toConfig(TresetaGameConfigDTO dto) {
        for (var config : TresetaGameConfig.values())
            if (config.getNumberOfPlayers() == dto.getNumberOfPlayers()
                    && config.getCardsInHandNum() == dto.getCardsInHandNum()
                    && config.areTeamsEnabled() == dto.getTeamsEnabled()) return config;
        throw new IllegalArgumentException("Invalid TresetaGameConfigDTO.");
    }
}

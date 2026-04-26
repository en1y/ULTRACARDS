package com.ultracards.server.entity.lobby;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BriskulaLobbyGameConfig implements GameConfig {
    @Getter private final BriskulaGameConfig gameConfig;
    @Getter private final List<UserEntity> orderedUsers;

    public BriskulaLobbyGameConfig(BriskulaGameConfigDTO gameConfigDTO, List<UserEntity> users) {
        this(toBriskulaGameConfig(gameConfigDTO), users);
    }

    private BriskulaLobbyGameConfig(BriskulaGameConfig gameConfig, List<UserEntity> users) {
        this.gameConfig = gameConfig;
        this.orderedUsers = users;
    }

    @Override
    public BriskulaGameConfigDTO toDto() {
        var users = orderedUsers.stream().map(BriskulaLobbyGameConfig::userToPlayerDto).toList();
        return new BriskulaGameConfigDTO(
                gameConfig.getNumberOfPlayers(),
                gameConfig.getCardsInHandNum(),
                gameConfig.areTeamsEnabled(),
                users
        );
    }

    @Override
    public GameEntity<?, ?> createGame(UUID lobbyId, String name, UserEntity owner, List<UserEntity> users) {
        if (gameConfig.areTeamsEnabled())
            return new BriskulaGameEntity(lobbyId, name, owner, this,
                    new ArrayList<>(List.of(users.get(0), users.get(2), users.get(1), users.get(3))));

        return new BriskulaGameEntity(lobbyId, name, owner, this, users);
    }

    public static BriskulaLobbyGameConfig fromDto(BriskulaGameConfigDTO gameConfigDTO, List<UserEntity> users, UserEntity owner) {
        var orderedUsers = new ArrayList<UserEntity>();
        var idUserMap = users.stream().collect(Collectors.toMap(UserEntity::getId, u -> u));
        var briskulaGameConfig = toBriskulaGameConfig(gameConfigDTO);

        orderedUsers.add(owner);
        for (var player: gameConfigDTO.getOrderedUsers()) // FIXME: lobby owner can be autokicked

            if ((idUserMap.containsKey(player.getId()) && !player.getId().equals(owner.getId()) && orderedUsers.size() < briskulaGameConfig.getNumberOfPlayers()))
                orderedUsers.add(idUserMap.get(player.getId()));

        return new BriskulaLobbyGameConfig(briskulaGameConfig, orderedUsers);
    }

    private static GamePlayerDTO userToPlayerDto(UserEntity user) {
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }

    private static BriskulaGameConfig toBriskulaGameConfig(BriskulaGameConfigDTO gameConfigDTO) {
        if (gameConfigDTO.getNumberOfPlayers() == 3) {
            return BriskulaGameConfig.THREE_PLAYERS;
        }
        if (gameConfigDTO.getNumberOfPlayers() == 2) {
            if (gameConfigDTO.getCardsInHandNum() == 3) {
                return BriskulaGameConfig.TWO_PLAYERS;
            }
            if (gameConfigDTO.getCardsInHandNum() == 4) {
                return BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH;
            }
        }
        if (gameConfigDTO.getNumberOfPlayers() == 4) {
            if (gameConfigDTO.getTeamsEnabled()) {
                return BriskulaGameConfig.FOUR_PLAYERS_WITH_TEAMS;
            }
            return BriskulaGameConfig.FOUR_PLAYERS_NO_TEAMS;
        }
        throw new IllegalArgumentException("Invalid BriskulaGameConfigDTO.");
    }
}

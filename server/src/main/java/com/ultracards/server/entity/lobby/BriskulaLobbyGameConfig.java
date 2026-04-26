package com.ultracards.server.entity.lobby;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.games.GameConfigDTO;
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
    @Getter private final List<UserEntity> team1;
    @Getter private final List<UserEntity> team2;

    public BriskulaLobbyGameConfig(BriskulaGameConfigDTO gameConfigDTO) {
        this(
                toBriskulaGameConfig(gameConfigDTO),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    private BriskulaLobbyGameConfig(BriskulaGameConfig gameConfig, List<UserEntity> team1, List<UserEntity> team2) {
        this.gameConfig = gameConfig;
        this.team1 = team1;
        this.team2 = team2;
    }

    @Override
    public BriskulaGameConfigDTO toDto() {
        var normalizedTeams = normalizeTeams();
        return new BriskulaGameConfigDTO(
                gameConfig.getNumberOfPlayers(),
                gameConfig.getCardsInHandNum(),
                gameConfig.areTeamsEnabled(),
                normalizedTeams.team1(),
                normalizedTeams.team2()
        );
    }

    @Override
    public GameEntity<?, ?> createGame(UUID lobbyId, String name, UserEntity owner, List<UserEntity> users) {
        return new BriskulaGameEntity(lobbyId, name, owner, this, orderPlayers(users));
    }

    private List<UserEntity> orderPlayers(List<UserEntity> users) {
        if (!gameConfig.areTeamsEnabled()) {
            return new ArrayList<>(users);
        }

        return List.of(team1.getFirst(), team2.getFirst(), team1.get(1), team2.get(1));
    }

    private Teams normalizeTeams() {
        if (!gameConfig.areTeamsEnabled()) {
            return new Teams(new ArrayList<>(), new ArrayList<>());
        }

        return new Teams(toPlayerDtos(team1), toPlayerDtos(team2));
    }

    public static BriskulaLobbyGameConfig fromDto(BriskulaGameConfigDTO gameConfigDTO, List<UserEntity> users) {
        var gameConfig = toBriskulaGameConfig(gameConfigDTO);
        if (!gameConfig.areTeamsEnabled()) {
            return new BriskulaLobbyGameConfig(gameConfig, new ArrayList<>(), new ArrayList<>());
        }

        var team1 = resolveTeam(gameConfigDTO.getTeam1(), users);
        var team2 = resolveTeam(gameConfigDTO.getTeam2(), users);
        var team1PlayersNum = Math.min(users.size(), 2);
        var team2PlayersNum = Math.max(users.size() - 2, 0);

        if (team1.size() == team1PlayersNum && team2.size() == team2PlayersNum) {
            return new BriskulaLobbyGameConfig(gameConfig, team1, team2);
        }

        return new BriskulaLobbyGameConfig(gameConfig,
                new ArrayList<>(users.subList(0, team1PlayersNum)),
                new ArrayList<>(users.subList(team1PlayersNum, users.size())));
    }

    private static List<UserEntity> resolveTeam(List<GamePlayerDTO> team, List<UserEntity> users) {
        if (team == null || team.isEmpty()) {
            return new ArrayList<>();
        }

        var res = new ArrayList<UserEntity>(team.size());
        var map = users.stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        for (var player: team) {
            if (map.containsKey(player.getId()))
                res.add(map.get(player.getId()));
        }

        return res;
    }

    private static List<GamePlayerDTO> toPlayerDtos(List<UserEntity> users) {
        var dtos = new ArrayList<GamePlayerDTO>(users.size());
        for (var user : users) {
            dtos.add(userToPlayerDto(user));
        }
        return dtos;
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

    private record Teams(List<GamePlayerDTO> team1, List<GamePlayerDTO> team2) {}
}

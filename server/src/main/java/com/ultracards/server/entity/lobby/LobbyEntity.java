package com.ultracards.server.entity.lobby;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import lombok.Data;

import java.time.Instant;
import java.util.*;

@Data
public class LobbyEntity {
    private UUID id;
    private String name;
    private GameTypeDTO gameType;
    private Instant createdAt;
    private Set<UserEntity> users = new HashSet<>();
    private UserEntity owner;
    private int minPlayers;
    private int maxPlayers;
    private GameConfigDTO gameConfig;
    private LobbyState lobbyState;

    public LobbyEntity(String name, GameTypeDTO gameType, UserEntity owner, int minPlayers, int maxPlayers, GameConfigDTO gameConfig) {
        id = UUID.randomUUID();
        this.name = name;
        this.gameType = gameType;
        this.owner = owner;
        users.add(owner);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.gameConfig = gameConfig;
        this.lobbyState = LobbyState.OPEN;
    }

    public boolean containsUser(UserEntity user) {
        return users.contains(user);
    }

    public boolean addUser(UserEntity user) {
        return users.contains(user) || ( users.size() < maxPlayers && users.add(user) );
    }

    public boolean removeUser(UserEntity user) {
        return !owner.equals(user) && users.remove(user);
    }

    public GameEntity<?> createGame() {
        if (gameType.equals(GameTypeDTO.Briskula)) {
            var res = new BriskulaGameEntity(getId(), getName(), getOwner(), BriskulaDTOtoConfig((BriskulaGameConfigDTO) gameConfig), new ArrayList<>(getUsers()));
            lobbyState = LobbyState.CLOSED;
            return res;
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public GameLobbyDTO createLobbyDTO() {
        var users = new HashSet<GamePlayerDTO>();

        for (var u: getUsers()) {
            users.add(new GamePlayerDTO(u.getUsername(), u.getId()));
        }

        return new GameLobbyDTO(
                getId(),
                getName(),
                getMinPlayers(),
                getMaxPlayers(),
                users,
                new GamePlayerDTO(getOwner().getUsername(), getOwner().getId()),
                getGameType(),
                getGameConfig()
        );
    }

    private BriskulaGameConfig BriskulaDTOtoConfig(BriskulaGameConfigDTO briskulaGameConfigDTO) {
        if (briskulaGameConfigDTO.getNumberOfPlayers() == 3) {
            return BriskulaGameConfig.THREE_PLAYERS;
        } else if (briskulaGameConfigDTO.getNumberOfPlayers() == 2) {
            if (briskulaGameConfigDTO.getCardsInHandNum() == 3) {
                return  BriskulaGameConfig.TWO_PLAYERS;
            } else if (briskulaGameConfigDTO.getCardsInHandNum() == 4) {
                return BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH;
            }
        } else if (briskulaGameConfigDTO.getNumberOfPlayers() == 4) {
            if (briskulaGameConfigDTO.getTeamsEnabled()) {
                return BriskulaGameConfig.FOUR_PLAYERS_WITH_TEAMS;
            } else return BriskulaGameConfig.FOUR_PLAYERS_WITH_TEAMS;
        }
        throw new IllegalArgumentException("Invalid briskulaGameConfigDTO");
    }
}

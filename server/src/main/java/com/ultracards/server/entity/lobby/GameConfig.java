package com.ultracards.server.entity.lobby;

import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;

import java.util.List;
import java.util.UUID;

public interface GameConfig {
    static GameConfig from(GameTypeDTO gameType, GameConfigDTO gameConfigDTO, List<UserEntity> users) {
        return switch (gameType) {
            case Briskula -> new BriskulaLobbyGameConfig((BriskulaGameConfigDTO) gameConfigDTO, users);
            case Treseta -> new TresetaLobbyGameConfig((TresetaGameConfigDTO) gameConfigDTO, users);
            default -> throw new UnsupportedOperationException("Game config is not supported for " + gameType + ".");
        };
    }

    GameConfigDTO toDto();

    GameEntity<?, ?> createGame(UUID lobbyId, String name, UserEntity owner, List<UserEntity> users);
}

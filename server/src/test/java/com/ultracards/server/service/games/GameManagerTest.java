package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameManagerTest {
    @Test
    void removesEveryLookupWhenGameIsDeleted() {
        var manager = new GameManager();
        var game = mock(GameEntity.class);
        var player = mock(UserEntity.class);
        var gameId = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        when(game.getId()).thenReturn(gameId);
        when(game.getLobbyId()).thenReturn(lobbyId);
        when(game.getGameType()).thenReturn(GameTypeDTO.Briskula);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(player.getId()).thenReturn(42L);

        manager.createGame(game);
        assertThat(manager.getGameByLobbyId(lobbyId)).isSameAs(game);

        assertThat(manager.deleteGame(game)).isTrue();
        assertThat(manager.getGame(gameId)).isNull();
        assertThat(manager.getGame(42L)).isNull();
        assertThat(manager.getGameByLobbyId(lobbyId)).isNull();
        assertThat(manager.getGames(GameTypeDTO.Briskula)).doesNotContain(game);
        assertThat(manager.getGames()).doesNotContain(game);
    }
}

package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.lobby.WagerConfigDTO;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.briskula.BriskulaGameService;
import com.ultracards.server.service.games.durak.DurakGameService;
import com.ultracards.server.service.games.treseta.TresetaGameService;
import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServiceTest {
    @Test
    void failedStartupRefundsEscrowAndRemovesLiveState() {
        var manager = new GameManager();
        var briskula = mock(BriskulaGameService.class);
        var treseta = mock(TresetaGameService.class);
        var durak = mock(DurakGameService.class);
        var recording = mock(GameRecordingService.class);
        var points = mock(PointsService.class);
        var service = new GameService(manager, briskula, treseta, durak, recording, points);
        var lobby = mock(LobbyEntity.class);
        var game = mock(BriskulaGameEntity.class);
        var gameId = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var wager = new WagerConfigDTO(true, 100);
        doReturn(game).when(lobby).createGame();
        when(lobby.getId()).thenReturn(lobbyId);
        when(game.getId()).thenReturn(gameId);
        when(game.getLobbyId()).thenReturn(lobbyId);
        when(game.getGameType()).thenReturn(GameTypeDTO.Briskula);
        when(game.getPlayers()).thenReturn(List.of());
        when(game.getWager()).thenReturn(wager);
        doThrow(new IllegalStateException("startup failed")).when(briskula).onGameStarted(game);

        assertThatThrownBy(() -> service.startGame(lobby))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("startup failed");

        verify(points).reserveWager(gameId, lobbyId, wager, List.of());
        verify(points).cancelWager(gameId);
        verify(recording).release(game);
        verify(lobby).setStarted(false);
        assertThat(manager.getGame(gameId)).isNull();
        assertThat(manager.getGameByLobbyId(lobbyId)).isNull();
    }
}

package com.ultracards.server.service.games.briskula;

import com.ultracards.games.briskula.BriskulaGame;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameEventPublisherTest {

    @Test
    void marksAnAllPlayerTieAsADrawWithoutPayouts() {
        var deltas = Map.of(1L, 0L, 2L, 0L);
        var fixture = fixture(2, 2, Map.of(), deltas);

        fixture.publisher().publish(fixture.entity(), GameEventDTO.GameEventTypeDTO.RESULTED);

        var result = publishedResult(fixture);
        assertThat(result.isDraw()).isTrue();
        assertThat(result.getGameWinners()).hasSize(2);
        assertThat(result.getWagerPayouts()).isEmpty();
        assertThat(result.getWagerDeltas()).containsExactlyInAnyOrderEntriesOf(deltas);
    }

    @Test
    void keepsSeveralWinnersWhenAtLeastOnePlayerLost() {
        var payouts = Map.of(1L, 148L, 2L, 148L);
        var deltas = Map.of(1L, 48L, 2L, 48L, 3L, -100L);
        var fixture = fixture(3, 2, payouts, deltas);

        fixture.publisher().publish(fixture.entity(), GameEventDTO.GameEventTypeDTO.RESULTED);

        var result = publishedResult(fixture);
        assertThat(result.isDraw()).isFalse();
        assertThat(result.getGameWinners()).hasSize(2);
        assertThat(result.getWagerPayouts()).containsExactlyInAnyOrderEntriesOf(payouts);
        assertThat(result.getWagerDeltas()).containsExactlyInAnyOrderEntriesOf(deltas);
    }

    private Fixture fixture(int playerCount, int winnerCount, Map<Long, Long> payouts,
                            Map<Long, Long> deltas) {
        var messaging = mock(SimpMessagingTemplate.class);
        var points = mock(PointsService.class);
        var entity = mock(BriskulaGameEntity.class);
        var game = mock(BriskulaGame.class);
        var gameId = UUID.randomUUID();
        var players = new java.util.ArrayList<BriskulaPlayerEntity>();
        for (int index = 0; index < playerCount; index++) {
            var player = mock(BriskulaPlayerEntity.class);
            when(player.getGamePlayerDTO()).thenReturn(new GamePlayerDTO("Player " + index, (long) index + 1));
            when(player.getPoints()).thenReturn(60);
            players.add(player);
        }

        when(entity.getGameType()).thenReturn(GameTypeDTO.Briskula);
        when(entity.getId()).thenReturn(gameId);
        when(entity.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.copyOf(players));
        when(game.determineGameWinners()).thenReturn(List.copyOf(players.subList(0, winnerCount)));
        when(points.wagerPayouts(gameId)).thenReturn(payouts);
        when(points.wagerDeltas(gameId)).thenReturn(deltas);
        return new Fixture(new GameEventPublisher(messaging, points), entity, messaging, gameId);
    }

    private com.ultracards.gateway.dto.games.games.GameResultDTO publishedResult(Fixture fixture) {
        var event = ArgumentCaptor.forClass(Object.class);
        verify(fixture.messaging()).convertAndSend(eq("/topic/game/" + fixture.gameId()), event.capture());
        return ((GameEventDTO<?>) event.getValue()).getResult();
    }

    private record Fixture(GameEventPublisher publisher, BriskulaGameEntity entity,
                           SimpMessagingTemplate messaging, UUID gameId) {
    }
}

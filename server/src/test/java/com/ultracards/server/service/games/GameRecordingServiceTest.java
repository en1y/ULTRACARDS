package com.ultracards.server.service.games;

import com.ultracards.games.briskula.BriskulaGame;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO;
import com.ultracards.games.treseta.TresetaGame;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.games.treseta.TresetaPlayer;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import com.ultracards.server.entity.lobby.DurakLobbyGameConfig;
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import com.ultracards.server.repositories.games.DurakGameRepository;
import com.ultracards.server.repositories.games.TresetaGameRepository;
import com.ultracards.templates.game.model.AbstractGame;
import com.ultracards.templates.game.model.AbstractPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameRecordingServiceTest {

    @Test
    void findsCardDiscardedFromThreePlayerGames() {
        assertDiscardedCard(new BriskulaGame(BriskulaGameConfig.THREE_PLAYERS, List.of(
                new BriskulaPlayer("One"), new BriskulaPlayer("Two"), new BriskulaPlayer("Three"))));
        assertDiscardedCard(new TresetaGame(List.of(
                new TresetaPlayer("One"), new TresetaPlayer("Two"), new TresetaPlayer("Three")),
                TresetaGameConfig.THREE_PLAYERS));
    }

    @Test
    void aFailedDurakSaveKeepsTheRecorderAvailableForRetry() {
        var repository = mock(DurakGameRepository.class);
        var service = new GameRecordingService(
                mock(BriskulaGameRepository.class), mock(TresetaGameRepository.class), repository);
        var one = user(1L, "one");
        var two = user(2L, "two");
        var config = new DurakGameConfigDTO(
                2, 24, false, DurakThrowInPolicyDTO.NEIGHBORS_ONLY, false, null);
        var game = new DurakGameEntity(UUID.randomUUID(), "game", one,
                new DurakLobbyGameConfig(config, List.of(one, two)), List.of(one, two));
        service.start(game);
        when(repository.saveAndFlush(any()))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalStateException.class, () -> service.finish(game));
        assertDoesNotThrow(() -> service.finish(game));
        service.release(game);
        assertThrows(IllegalStateException.class, () -> service.finish(game));
    }

    private static UserEntity user(long id, String name) {
        var user = new UserEntity(name + "@example.com", name);
        user.setId(id);
        return user;
    }

    private void assertDiscardedCard(AbstractGame<?, ?, ?, ?, ?, ?, ?> game) {
        game.start();
        var discarded = GameRecordingService.findDiscardedCard(game);

        assertNotNull(discarded);
        var card = discarded.toCard();
        assertFalse(game.getDeck().getCards().contains(card));
        for (var rawPlayer : game.getPlayers()) {
            var player = (AbstractPlayer<?, ?, ?, ?, ?>) rawPlayer;
            assertFalse(player.getHand().getCards().contains(card));
        }
    }
}

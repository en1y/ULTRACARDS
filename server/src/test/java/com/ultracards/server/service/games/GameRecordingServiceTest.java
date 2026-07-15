package com.ultracards.server.service.games;

import com.ultracards.games.briskula.BriskulaGame;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.games.treseta.TresetaGame;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.games.treseta.TresetaPlayer;
import com.ultracards.templates.game.model.AbstractGame;
import com.ultracards.templates.game.model.AbstractPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameRecordingServiceTest {

    @Test
    void findsCardDiscardedFromThreePlayerGames() {
        assertDiscardedCard(new BriskulaGame(BriskulaGameConfig.THREE_PLAYERS, List.of(
                new BriskulaPlayer("One"), new BriskulaPlayer("Two"), new BriskulaPlayer("Three"))));
        assertDiscardedCard(new TresetaGame(List.of(
                new TresetaPlayer("One"), new TresetaPlayer("Two"), new TresetaPlayer("Three")),
                TresetaGameConfig.THREE_PLAYERS));
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

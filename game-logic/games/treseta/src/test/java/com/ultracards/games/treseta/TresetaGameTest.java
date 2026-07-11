package com.ultracards.games.treseta;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TresetaGameTest {

    @Test
    void acceptsEachSupportedConfiguration() {
        for (var config : TresetaGameConfig.values()) {
            assertDoesNotThrow(() -> new TresetaGame(players(config), config));
        }
    }

    @Test
    void rejectsConfigurationWithWrongPlayerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new TresetaGame(players(TresetaGameConfig.TWO_PLAYERS, 3), TresetaGameConfig.TWO_PLAYERS));
    }

    private ArrayList<TresetaPlayer> players(TresetaGameConfig config) {
        return players(config, config.getNumberOfPlayers());
    }

    private ArrayList<TresetaPlayer> players(TresetaGameConfig config, int playerCount) {
        var players = new ArrayList<TresetaPlayer>();
        for (int i = 0; i < playerCount; i++) {
            players.add(new TresetaPlayer("Player " + (i + 1)));
        }
        return players;
    }
}

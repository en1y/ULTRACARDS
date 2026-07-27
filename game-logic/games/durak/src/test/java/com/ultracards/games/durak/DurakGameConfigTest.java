package com.ultracards.games.durak;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DurakGameConfigTest {

    private static DurakGameConfig config(int players, int deck, boolean jokers) {
        return new DurakGameConfig(players, deck, jokers, DurakThrowInPolicy.NEIGHBORS_ONLY, false);
    }

    @Test
    void rejectsPlayerCountOutsideTwoToSix() {
        assertThrows(DurakRuleException.class, () -> config(1, 36, false));
        assertThrows(DurakRuleException.class, () -> config(7, 36, false));
    }

    @Test
    void rejectsFiveOrSixPlayersOnTheShortPack() {
        assertThrows(DurakRuleException.class, () -> config(5, 24, false));
        assertThrows(DurakRuleException.class, () -> config(6, 24, false));
        assertDoesNotThrow(() -> config(4, 24, false));
    }

    @Test
    void rejectsUnknownDeckSizes() {
        assertThrows(DurakRuleException.class, () -> config(2, 52, false));
        assertThrows(DurakRuleException.class, () -> config(2, 32, false));
    }

    @Test
    void jokersOnlyExistOnTheFullPack() {
        assertThrows(DurakRuleException.class, () -> config(2, 24, true));
        assertThrows(DurakRuleException.class, () -> config(2, 36, true));
        assertDoesNotThrow(() -> config(2, 54, true));
        assertDoesNotThrow(() -> config(2, 54, false));
    }

    @Test
    void rejectsMissingThrowInPolicy() {
        assertThrows(DurakRuleException.class, () -> new DurakGameConfig(2, 36, false, null, false));
    }

    @Test
    void effectiveCardCountDropsTheJokersWhenDisabled() {
        assertEquals(52, config(2, 54, false).effectiveCardCount());
        assertEquals(54, config(2, 54, true).effectiveCardCount());
        assertEquals(36, config(2, 36, false).effectiveCardCount());
        assertEquals(24, config(2, 24, false).effectiveCardCount());
    }

    @Test
    void modeKeyRoundTrips() {
        for (var config : DurakGameConfig.validConfigs()) {
            assertEquals(config, DurakGameConfig.fromModeKey(config.modeKey()));
        }
    }

    @Test
    void modeKeyUsesTheCanonicalFormat() {
        assertEquals("P4_D36_NO_JOKERS_EVERYONE_PASS",
                new DurakGameConfig(4, 36, false, DurakThrowInPolicy.EVERYONE, true).modeKey());
        assertEquals("P6_D54_JOKERS_EVERYONE_NO_PASS",
                new DurakGameConfig(6, 54, true, DurakThrowInPolicy.EVERYONE, false).modeKey());
    }

    @Test
    void unknownModeKeyIsRejected() {
        assertThrows(DurakRuleException.class, () -> DurakGameConfig.fromModeKey("p4_d36_no_jokers_everyone_pass"));
        assertThrows(DurakRuleException.class, () -> DurakGameConfig.fromModeKey("P4_D36"));
    }

    @Test
    void validConfigsAreUniqueAndOrdered() {
        var configs = DurakGameConfig.validConfigs();
        assertEquals(configs.size(), configs.stream().map(DurakGameConfig::modeKey).distinct().count());
        // 2-4 players: 24, 36, 54-no-jokers, 54-jokers => 4 packs x 4 toggles = 16 each
        // 5-6 players: 3 packs x 4 toggles = 12 each
        assertEquals(3 * 16 + 2 * 12, configs.size());
        assertEquals("P2_D24_NO_JOKERS_NEIGHBORS_NO_PASS", configs.getFirst().modeKey());
        assertEquals("P6_D54_JOKERS_EVERYONE_PASS", configs.getLast().modeKey());
    }

    @Test
    void everyValidConfigCanDealSixCardsToEveryPlayer() {
        for (var config : DurakGameConfig.validConfigs()) {
            assertTrue(config.effectiveCardCount() >= config.numberOfPlayers() * DurakGameConfig.CARDS_IN_HAND,
                    config.modeKey());
        }
    }
}

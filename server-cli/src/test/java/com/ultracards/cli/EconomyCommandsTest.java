package com.ultracards.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyCommandsTest {
    @Test
    void parsesTheFullRepeatableAchievementFormat() {
        var goal = EconomyCommands.SaveEvent.parseAchievement("Durak set|750|8|3|2|1|Complete the set");

        assertEquals("Durak set", goal.name());
        assertEquals(750, goal.rewardPoints());
        assertEquals(8, goal.gamesRequired());
        assertEquals(3, goal.winsRequired());
        assertEquals(2, goal.lossesRequired());
        assertEquals(1, goal.drawsRequired());
        assertEquals("Complete the set", goal.description());
    }

    @Test
    void rejectsMalformedAchievementSpecs() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> EconomyCommands.SaveEvent.parseAchievement("win three"));
        assertTrue(error.getMessage().contains("NAME|REWARD"));
    }
}

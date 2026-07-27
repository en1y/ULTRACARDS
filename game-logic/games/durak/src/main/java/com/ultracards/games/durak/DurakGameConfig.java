package com.ultracards.games.durak;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The complete, validated Durak lobby configuration.
 *
 * <p>The compact constructor is the canonical validator: DTOs, lobby code, availability,
 * history and statistics must build (or rebuild) this record instead of duplicating the rules.
 */
public record DurakGameConfig(
        int numberOfPlayers,
        int deckSize,
        boolean jokersEnabled,
        DurakThrowInPolicy throwInPolicy,
        boolean passingEnabled
) {
    public static final int CARDS_IN_HAND = 6;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    public static final Set<Integer> PACK_SIZES = Set.of(24, 36, 54);

    public DurakGameConfig {
        if (numberOfPlayers < MIN_PLAYERS || numberOfPlayers > MAX_PLAYERS) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_PLAYER_COUNT,
                    "Durak requires 2 to 6 players, got %d.", numberOfPlayers);
        }
        if (!PACK_SIZES.contains(deckSize)) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_DECK_SIZE,
                    "Durak deck size must be 24, 36, or 54, got %d.", deckSize);
        }
        if (deckSize == 24 && numberOfPlayers > 4) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_PLAYER_COUNT,
                    "A 24-card Durak game supports at most 4 players.");
        }
        if (jokersEnabled && deckSize != 54) {
            throw new DurakRuleException(DurakErrorCode.DURAK_JOKERS_UNAVAILABLE,
                    "Jokers are available only with the 54-card pack.");
        }
        if (throwInPolicy == null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_DECK_SIZE, "throwInPolicy is required.");
        }
    }

    /**
     * How many cards actually enter play. The 54-card pack is physically 52 suited cards plus
     * two Jokers, so it contributes 52 when the Joker toggle is off.
     */
    public int effectiveCardCount() {
        return deckSize == 54 && !jokersEnabled ? 52 : deckSize;
    }

    /** The lowest suited rank contained in this pack (2, 6 or 9 as a poker card number). */
    public int lowestRank() {
        return switch (deckSize) {
            case 24 -> 9;
            case 36 -> 6;
            default -> 2;
        };
    }

    /** Canonical identifier persisted in history, statistics, leaderboards and admin filters. */
    public String modeKey() {
        return "P" + numberOfPlayers
                + "_D" + deckSize
                + (jokersEnabled ? "_JOKERS" : "_NO_JOKERS")
                + (throwInPolicy == DurakThrowInPolicy.EVERYONE ? "_EVERYONE" : "_NEIGHBORS")
                + (passingEnabled ? "_PASS" : "_NO_PASS");
    }

    /** Parses a canonical {@link #modeKey()}. Only the exact canonical format is accepted. */
    public static DurakGameConfig fromModeKey(String modeKey) {
        Objects.requireNonNull(modeKey, "modeKey");
        for (var config : validConfigs()) {
            if (config.modeKey().equals(modeKey)) {
                return config;
            }
        }
        throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_DECK_SIZE,
                "Unknown Durak mode key: %s", modeKey);
    }

    /**
     * Every valid configuration in a stable order: players ascending, deck size ascending,
     * no-Jokers before Jokers, NEIGHBORS_ONLY before EVERYONE, no-pass before pass.
     * Used by the legacy numeric {@code gameSettingId} lobby filter.
     */
    public static List<DurakGameConfig> validConfigs() {
        var res = new ArrayList<DurakGameConfig>();
        for (int players = MIN_PLAYERS; players <= MAX_PLAYERS; players++) {
            for (var deckSize : List.of(24, 36, 54)) {
                for (var jokers : List.of(false, true)) {
                    for (var policy : List.of(DurakThrowInPolicy.NEIGHBORS_ONLY, DurakThrowInPolicy.EVERYONE)) {
                        for (var passing : List.of(false, true)) {
                            if (deckSize == 24 && players > 4) continue;
                            if (jokers && deckSize != 54) continue;
                            res.add(new DurakGameConfig(players, deckSize, jokers, policy, passing));
                        }
                    }
                }
            }
        }
        return List.copyOf(res);
    }
}

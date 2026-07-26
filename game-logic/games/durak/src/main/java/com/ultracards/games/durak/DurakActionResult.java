package com.ultracards.games.durak;

/**
 * What an accepted action did, so the server can decide what to publish and persist.
 *
 * @param type          the action that was applied
 * @param resolvedBout  the outcome when this action ended the bout, otherwise {@code null}
 * @param gameFinished  whether this action ended the whole game
 */
public record DurakActionResult(DurakActionType type, DurakBoutOutcome resolvedBout, boolean gameFinished) {
}

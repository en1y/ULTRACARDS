package com.ultracards.games.durak;

/**
 * Who is allowed to throw extra cards into a bout.
 */
public enum DurakThrowInPolicy {
    /** Only the active players immediately clockwise and counter-clockwise from the defender. */
    NEIGHBORS_ONLY,
    /** Every active player except the defender. */
    EVERYONE
}

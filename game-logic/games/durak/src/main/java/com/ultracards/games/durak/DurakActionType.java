package com.ultracards.games.durak;

public enum DurakActionType {
    ATTACK,
    DEFEND,
    THROW_IN,
    PASS,
    TAKE,
    DONE;

    public boolean requiresCard() {
        return this == ATTACK || this == DEFEND || this == THROW_IN || this == PASS;
    }
}

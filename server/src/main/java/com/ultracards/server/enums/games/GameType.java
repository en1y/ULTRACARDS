package com.ultracards.server.enums.games;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GameType {
    BRISKULA("BRISKULA"),
    POKER("POKER"),
    TRESETA("TREŠETA"),
    DURAK("DURAK");

    private final String name;

    @Override
    public String toString() {
        return name;
    }
}

package com.ultracards.server.enums.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
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

    public static GameType fromDTO(GameTypeDTO gameTypeDTO) {
        return switch (gameTypeDTO) {
            case Briskula -> BRISKULA;
            case Poker -> POKER;
            case Treseta -> TRESETA;
            case Durak -> DURAK;
        };
    }
}

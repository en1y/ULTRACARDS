package com.ultracards.server.service.games;

import com.ultracards.server.enums.games.GameType;
import org.springframework.stereotype.Service;

@Service
public class GameRulesService {

    public int defaultRequiredPlayers(GameType type) {
        if (type == null) return 2;
        return switch (type) {
            case BRISKULA -> 2; // commonly 2; owner can choose a different max in lobby
            case POKER -> 2;
            case TRESETA -> 2;
            case DURAK -> 2;
        };
    }
}


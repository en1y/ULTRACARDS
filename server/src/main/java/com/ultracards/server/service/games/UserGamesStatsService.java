package com.ultracards.server.service.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.UserGamesStats;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserGamesStatsService {
    private final UserGamesStatsRepository userGamesStatsRepository;

    public UserGamesStats getByUser(UserEntity user) {
        return userGamesStatsRepository.findByUser(user).orElse(null);
    }

    public void addGameWon(UserGamesStats ugs, GameType gameType) {
        ugs.addGameWon(gameType);
        save(ugs);
    }
    public void addGamePlayed(UserGamesStats ugs, GameType gameType) {
        ugs.addGamePlayed(gameType);
        save(ugs);
    }

    public void save(UserGamesStats ugs) {
        userGamesStatsRepository.save(ugs);
    }
}

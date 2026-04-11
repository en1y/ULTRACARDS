package com.ultracards.server.service.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.UserGamesStats;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserGamesStatsService {
    private final UserGamesStatsRepository userGamesStatsRepository;

    public void createEmptyStats(UserEntity user) {
        var ugs = new UserGamesStats(user);
        userGamesStatsRepository.save(ugs);
    }

    public UserGamesStats getByUser(UserEntity user) {
        return userGamesStatsRepository.findByUser(user).orElse(null);
    }

    @Transactional
    public void addGameWon(UserEntity user, GameType gameType) {
        var stats = userGamesStatsRepository.findByUser(user).orElse(null);
        if (stats == null) {
            return;
        }
        stats.addGameWon(gameType);
    }

    @Transactional
    public void addGamePlayed(UserEntity user, GameType gameType) {
        var stats = userGamesStatsRepository.findByUser(user).orElse(null);
        if (stats == null) {
            return;
        }
        stats.addGamePlayed(gameType);
    }

    public void save(UserGamesStats ugs) {
        userGamesStatsRepository.save(ugs);
    }
}

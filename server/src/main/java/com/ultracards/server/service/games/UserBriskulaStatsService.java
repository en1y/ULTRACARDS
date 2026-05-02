package com.ultracards.server.service.games;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import com.ultracards.server.repositories.games.UserBriskulaStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBriskulaStatsService {
    private final UserBriskulaStatsRepository userBriskulaStatsRepository;

    public void createEmptyStats(UserEntity user) {
        var stats = new UserBriskulaStats(user);
        userBriskulaStatsRepository.save(stats);
    }

    public UserBriskulaStats getByUser(UserEntity user) {
        return userBriskulaStatsRepository.findByUser(user).orElse(null);
    }

    @Transactional
    public void addBriskulaGame(UserEntity user, BriskulaGameConfig gameConfig, boolean won) {
        var stats = userBriskulaStatsRepository.findByUser(user).orElse(null);
        if (stats == null) {
            return;
        }
        stats.addGame(gameConfig, won);
    }

    @Transactional
    public void addBriskulaGameAgainstUser(UserEntity user, BriskulaGameConfig gameConfig, UserEntity otherUser, boolean won) {
        var stats = userBriskulaStatsRepository.findByUser(user).orElse(null);
        if (stats == null) {
            return;
        }
        stats.addGameAgainstUser(gameConfig, otherUser, won);
    }

    @Transactional
    public void addBriskulaGameWithTeammate(UserEntity user, BriskulaGameConfig gameConfig, UserEntity teammate, boolean won) {
        var stats = userBriskulaStatsRepository.findByUser(user).orElse(null);
        if (stats == null) {
            return;
        }
        stats.addGameWithTeammate(gameConfig, teammate, won);
    }
}

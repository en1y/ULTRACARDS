package com.ultracards.server.service.games.briskula;

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

    @Transactional
    public UserBriskulaStats getByUser(UserEntity user) {
        return userBriskulaStatsRepository.findByUser(user)
                .orElseGet(() -> userBriskulaStatsRepository.save(new UserBriskulaStats(user)));
    }

    @Transactional
    public void addBriskulaGame(UserEntity user, BriskulaGameConfig gameConfig, boolean won) {
        getByUser(user).addGame(gameConfig, won);
    }

    @Transactional
    public void addBriskulaGameAgainstUser(UserEntity user, BriskulaGameConfig gameConfig, UserEntity otherUser, boolean won) {
        getByUser(user).addGameAgainstUser(gameConfig, otherUser, won);
    }

    @Transactional
    public void addBriskulaGameWithTeammate(UserEntity user, BriskulaGameConfig gameConfig, UserEntity teammate, boolean won) {
        getByUser(user).addGameWithTeammate(gameConfig, teammate, won);
    }
}

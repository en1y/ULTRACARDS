package com.ultracards.server.service.games.treseta;

import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserTresetaStats;
import com.ultracards.server.repositories.games.UserTresetaStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserTresetaStatsService {
    private final UserTresetaStatsRepository userTresetaStatsRepository;

    public void createEmptyStats(UserEntity user) {
        var stats = new UserTresetaStats(user);
        userTresetaStatsRepository.save(stats);
    }

    @Transactional
    public UserTresetaStats getByUser(UserEntity user) {
        return userTresetaStatsRepository.findByUser(user)
                .orElseGet(() -> userTresetaStatsRepository.save(new UserTresetaStats(user)));
    }

    @Transactional
    public void addTresetaGame(UserEntity user, TresetaGameConfig gameConfig, boolean won) {
        getByUser(user).addGame(gameConfig, won);
    }

    @Transactional
    public void addTresetaGameAgainstUser(UserEntity user, TresetaGameConfig gameConfig, UserEntity otherUser, boolean won) {
        getByUser(user).addGameAgainstUser(gameConfig, otherUser, won);
    }

    @Transactional
    public void addTresetaGameWithTeammate(UserEntity user, TresetaGameConfig gameConfig, UserEntity teammate, boolean won) {
        getByUser(user).addGameWithTeammate(gameConfig, teammate, won);
    }
}

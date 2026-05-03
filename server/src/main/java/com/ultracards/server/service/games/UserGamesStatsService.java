package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.auth.BriskulaMatchupStatsDTO;
import com.ultracards.gateway.dto.auth.DetailedProfileStatsDTO;
import com.ultracards.gateway.dto.auth.GameStatsDTO;
import com.ultracards.gateway.dto.auth.UserBriskulaStatsDTO;
import com.ultracards.gateway.dto.auth.UserGamesStatsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.BriskulaMatchupStats;
import com.ultracards.server.entity.games.gamestats.GameStats;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import com.ultracards.server.entity.games.gamestats.UserGamesStats;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class UserGamesStatsService {
    private final UserBriskulaStatsService userBriskulaStatsService;
    private final UserRepository userRepository;
    private final UserGamesStatsRepository userGamesStatsRepository;

    public void createEmptyStats(UserEntity user) {
        var ugs = new UserGamesStats(user);
        userGamesStatsRepository.save(ugs);
    }

    public UserGamesStats getByUser(UserEntity user) {
        return userGamesStatsRepository.findByUser(user).orElse(null);
    }

    @Transactional
    public void addGame(UserEntity user, GameType gameType, boolean won) {
        var stats = userGamesStatsRepository.findByUser(user).orElse(null);
        if (stats == null) {
            return;
        }
        stats.addGame(gameType, won);
    }

    @Transactional(readOnly = true)
    public DetailedProfileStatsDTO getDetailedStatsByUser(UserEntity user) {
        var gameStats = userGamesStatsRepository.findByUser(user).orElse(null);
        var briskulaStats = userBriskulaStatsService.getByUser(user);

        return new DetailedProfileStatsDTO(
                toUserGamesStatsDTO(gameStats),
                toUserBriskulaStatsDTO(briskulaStats)
        );
    }

    public void save(UserGamesStats ugs) {
        userGamesStatsRepository.save(ugs);
    }

    private UserGamesStatsDTO toUserGamesStatsDTO(UserGamesStats stats) {
        if (stats == null) {
            return null;
        }

        var gameStats = new LinkedHashMap<String, GameStatsDTO>();
        for (var gameType : GameType.values()) {
            gameStats.put(
                    gameType.name(),
                    new GameStatsDTO(
                            stats.getGamesPlayed(gameType),
                            stats.getGamesWon(gameType),
                            stats.getLastPlayedAt(gameType)
                    )
            );
        }

        return new UserGamesStatsDTO(
                stats.getId(),
                stats.getUser() != null ? stats.getUser().getId() : null,
                gameStats
        );
    }

    private UserBriskulaStatsDTO toUserBriskulaStatsDTO(UserBriskulaStats stats) {
        if (stats == null) {
            return null;
        }

        var configStats = new java.util.LinkedHashMap<String, GameStatsDTO>();
        for (var entry : stats.getConfigStats().entrySet()) {
            configStats.put(entry.getKey().name(), toGameStatsDTO(entry.getValue()));
        }

        var winsAgainstUser = stats.getWinsAgainstUser().stream()
                .map(this::toBriskulaMatchupStatsDTO)
                .toList();
        var winsWithTeammate = stats.getWinsWithTeammate().stream()
                .map(this::toBriskulaMatchupStatsDTO)
                .toList();

        return new UserBriskulaStatsDTO(
                stats.getId(),
                stats.getUser() != null ? stats.getUser().getId() : null,
                configStats,
                winsAgainstUser,
                winsWithTeammate
        );
    }

    private GameStatsDTO toGameStatsDTO(GameStats stats) {
        return new GameStatsDTO(stats.getPlayed(), stats.getWins(), stats.getLastPlayedAt());
    }

    private BriskulaMatchupStatsDTO toBriskulaMatchupStatsDTO(BriskulaMatchupStats stats) {
        var relatedUser = userRepository.findById(stats.getRelatedUserId()).orElse(null);
        return new BriskulaMatchupStatsDTO(
                stats.getGameConfig(),
                stats.getRelatedUserId(),
                relatedUser != null ? relatedUser.getUsername() : null,
                stats.getPlayed(),
                stats.getWins(),
                stats.getLastPlayedAt()
        );
    }
}

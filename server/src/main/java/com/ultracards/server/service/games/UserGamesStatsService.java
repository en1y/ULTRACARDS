package com.ultracards.server.service.games;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.auth.BriskulaMatchupStatsDTO;
import com.ultracards.gateway.dto.auth.DetailedProfileStatsDTO;
import com.ultracards.gateway.dto.auth.GameStatsDTO;
import com.ultracards.gateway.dto.auth.TresetaMatchupStatsDTO;
import com.ultracards.gateway.dto.auth.UserBriskulaStatsDTO;
import com.ultracards.gateway.dto.auth.UserGamesStatsDTO;
import com.ultracards.gateway.dto.auth.UserTresetaStatsDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.BriskulaMatchupStats;
import com.ultracards.server.entity.games.gamestats.GameStats;
import com.ultracards.server.entity.games.gamestats.TresetaMatchupStats;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import com.ultracards.server.entity.games.gamestats.UserGamesStats;
import com.ultracards.server.entity.games.gamestats.UserTresetaStats;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import com.ultracards.server.service.games.briskula.UserBriskulaStatsService;
import com.ultracards.server.service.games.treseta.UserTresetaStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class UserGamesStatsService {
    private final UserBriskulaStatsService userBriskulaStatsService;
    private final UserTresetaStatsService userTresetaStatsService;
    private final UserRepository userRepository;
    private final UserGamesStatsRepository userGamesStatsRepository;

    public void createEmptyStats(UserEntity user) {
        var ugs = new UserGamesStats(user);
        userGamesStatsRepository.save(ugs);
    }

    @Transactional
    public UserGamesStats getByUser(UserEntity user) {
        return userGamesStatsRepository.findByUser(user)
                .orElseGet(() -> userGamesStatsRepository.save(new UserGamesStats(user)));
    }

    @Transactional
    public void addGame(UserEntity user, GameType gameType, boolean won) {
        getByUser(user).addGame(gameType, won);
    }

    @Transactional
    public DetailedProfileStatsDTO getDetailedStatsByUser(UserEntity user) {
        return new DetailedProfileStatsDTO(
                toUserGamesStatsDTO(getByUser(user)),
                toUserBriskulaStatsDTO(userBriskulaStatsService.getByUser(user)),
                toUserTresetaStatsDTO(userTresetaStatsService.getByUser(user))
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

    private UserTresetaStatsDTO toUserTresetaStatsDTO(UserTresetaStats stats) {
        if (stats == null) {
            return null;
        }

        var configStats = new LinkedHashMap<String, GameStatsDTO>();
        for (var entry : stats.getConfigStats().entrySet()) {
            configStats.put(entry.getKey().name(), toGameStatsDTO(entry.getValue()));
        }

        var winsAgainstUser = new java.util.ArrayList<TresetaMatchupStatsDTO>();
        for (var matchup : stats.getWinsAgainstUser()) {
            winsAgainstUser.add(toTresetaMatchupStatsDTO(matchup));
        }
        var winsWithTeammate = new java.util.ArrayList<TresetaMatchupStatsDTO>();
        for (var matchup : stats.getWinsWithTeammate()) {
            winsWithTeammate.add(toTresetaMatchupStatsDTO(matchup));
        }

        return new UserTresetaStatsDTO(
                stats.getId(),
                stats.getUser() != null ? stats.getUser().getId() : null,
                configStats,
                winsAgainstUser,
                winsWithTeammate
        );
    }

    private TresetaMatchupStatsDTO toTresetaMatchupStatsDTO(TresetaMatchupStats stats) {
        var relatedUser = userRepository.findById(stats.getRelatedUserId()).orElse(null);
        return new TresetaMatchupStatsDTO(
                GameTypeDTO.Treseta,
                toTresetaGameConfigDTO(stats.getGameConfig()),
                stats.getRelatedUserId(),
                relatedUser != null ? relatedUser.getUsername() : null,
                stats.getPlayed(),
                stats.getWins(),
                stats.getLastPlayedAt()
        );
    }

    private TresetaGameConfigDTO toTresetaGameConfigDTO(String gameConfig) {
        var config = TresetaGameConfig.valueOf(gameConfig);
        return new TresetaGameConfigDTO(
                config.getNumberOfPlayers(),
                config.getCardsInHandNum(),
                config.areTeamsEnabled(),
                null
        );
    }

    private GameStatsDTO toGameStatsDTO(GameStats stats) {
        return new GameStatsDTO(stats.getPlayed(), stats.getWins(), stats.getLastPlayedAt());
    }

    private BriskulaMatchupStatsDTO toBriskulaMatchupStatsDTO(BriskulaMatchupStats stats) {
        var relatedUser = userRepository.findById(stats.getRelatedUserId()).orElse(null);
        return new BriskulaMatchupStatsDTO(
                GameTypeDTO.Briskula,
                toBriskulaGameConfigDTO(stats.getGameConfig()),
                stats.getRelatedUserId(),
                relatedUser != null ? relatedUser.getUsername() : null,
                stats.getPlayed(),
                stats.getWins(),
                stats.getLastPlayedAt()
        );
    }

    private BriskulaGameConfigDTO toBriskulaGameConfigDTO(String gameConfig) {
        var config = BriskulaGameConfig.valueOf(gameConfig);
        return new BriskulaGameConfigDTO(
                config.getNumberOfPlayers(),
                config.getCardsInHandNum(),
                config.areTeamsEnabled(),
                null
        );
    }
}

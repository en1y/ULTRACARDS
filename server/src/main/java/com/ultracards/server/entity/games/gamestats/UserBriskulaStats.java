package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.entity.UserEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_briskula_stats")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserBriskulaStats implements DetailedGameStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ElementCollection
    @CollectionTable(
            name = "user_briskula_stats_entries",
            joinColumns = @JoinColumn(name = "user_briskula_stats_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "briskula_game_config")
    private Map<BriskulaGameConfig, GameStats> configStats = new EnumMap<>(BriskulaGameConfig.class);

    @ElementCollection
    @CollectionTable(
            name = "user_briskula_stats_wins_against_user",
            joinColumns = @JoinColumn(name = "user_briskula_stats_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_briskula_stats_wins_against_user",
                    columnNames = {"user_briskula_stats_id", "game_config", "related_user_id"}
            )
    )
    @AttributeOverrides({
            @AttributeOverride(name = "gameConfig", column = @Column(name = "game_config", nullable = false)),
            @AttributeOverride(name = "relatedUserId", column = @Column(name = "related_user_id", nullable = false)),
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    private Set<BriskulaMatchupStats> winsAgainstUser = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "user_briskula_stats_wins_with_teammate",
            joinColumns = @JoinColumn(name = "user_briskula_stats_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_briskula_stats_wins_with_teammate",
                    columnNames = {"user_briskula_stats_id", "game_config", "related_user_id"}
            )
    )
    @AttributeOverrides({
            @AttributeOverride(name = "gameConfig", column = @Column(name = "game_config", nullable = false)),
            @AttributeOverride(name = "relatedUserId", column = @Column(name = "related_user_id", nullable = false)),
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    private Set<BriskulaMatchupStats> winsWithTeammate = new HashSet<>();

    public UserBriskulaStats(UserEntity user) {
        this.user = user;
    }

    public void addGame(BriskulaGameConfig gameConfig, boolean won) {
        var stats = configStats.computeIfAbsent(gameConfig, ignored -> new GameStats());
        stats.addPlayed();
        if (won) {
            stats.addWon();
        }
    }

    public void addGameAgainstUser(BriskulaGameConfig gameConfig, UserEntity otherUser, boolean won) {
        if (otherUser.getId() == null) {
            return;
        }
        var stats = findOrCreateUserMatchupStats(
                winsAgainstUser,
                BriskulaMatchupStats.configKey(gameConfig),
                otherUser.getId()
        );
        stats.addPlayed();
        if (won) {
            stats.addWin();
        }
    }

    public void addGameWithTeammate(BriskulaGameConfig gameConfig, UserEntity teammate, boolean won) {
        if (teammate.getId() == null) return;

        var stats = findOrCreateUserMatchupStats(
                winsWithTeammate,
                BriskulaMatchupStats.configKey(gameConfig),
                teammate.getId()
        );
        stats.addPlayed();
        if (won) {
            stats.addWin();
        }
    }

    private BriskulaMatchupStats findOrCreateUserMatchupStats(Set<BriskulaMatchupStats> statsSet, String gameConfig, Long relatedUserId) {
        for (var stat : statsSet) {
            if (stat.matches(gameConfig, relatedUserId)) {
                return stat;
            }
        }

        var stats = new BriskulaMatchupStats(gameConfig, relatedUserId, 0, 0);
        statsSet.add(stats);
        return stats;
    }
}

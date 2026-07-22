package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.treseta.TresetaGameConfig;
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
@Table(name = "user_treseta_stats")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserTresetaStats implements DetailedGameStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "declarations_made", nullable = false)
    private int declarationsMade = 0;

    // Points are stored multiplied by three, matching the game logic.
    @Column(name = "declaration_points", nullable = false)
    private int declarationPoints = 0;

    @ElementCollection
    @CollectionTable(
            name = "user_treseta_stats_entries",
            joinColumns = @JoinColumn(name = "user_treseta_stats_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "treseta_game_config")
    private Map<TresetaGameConfig, GameStats> configStats = new EnumMap<>(TresetaGameConfig.class);

    @ElementCollection
    @CollectionTable(
            name = "user_treseta_stats_wins_against_user",
            joinColumns = @JoinColumn(name = "user_treseta_stats_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_treseta_stats_wins_against_user",
                    columnNames = {"user_treseta_stats_id", "game_config", "related_user_id"}
            )
    )
    @AttributeOverrides({
            @AttributeOverride(name = "gameConfig", column = @Column(name = "game_config", nullable = false)),
            @AttributeOverride(name = "relatedUserId", column = @Column(name = "related_user_id", nullable = false)),
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    private Set<TresetaMatchupStats> winsAgainstUser = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "user_treseta_stats_wins_with_teammate",
            joinColumns = @JoinColumn(name = "user_treseta_stats_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_treseta_stats_wins_with_teammate",
                    columnNames = {"user_treseta_stats_id", "game_config", "related_user_id"}
            )
    )
    @AttributeOverrides({
            @AttributeOverride(name = "gameConfig", column = @Column(name = "game_config", nullable = false)),
            @AttributeOverride(name = "relatedUserId", column = @Column(name = "related_user_id", nullable = false)),
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    private Set<TresetaMatchupStats> winsWithTeammate = new HashSet<>();

    public UserTresetaStats(UserEntity user) {
        this.user = user;
    }

    // ponytail: additive counters only; the admin stats rebuild does not recompute them
    public void addDeclarations(int count, int points) {
        declarationsMade += count;
        declarationPoints += points;
    }

    public void addGame(TresetaGameConfig gameConfig, boolean won) {
        var stats = configStats.computeIfAbsent(gameConfig, ignored -> new GameStats());
        stats.addPlayed();
        if (won) {
            stats.addWon();
        }
    }

    public void addGameAgainstUser(TresetaGameConfig gameConfig, UserEntity otherUser, boolean won) {
        if (otherUser.getId() == null) {
            return;
        }
        var stats = findOrCreateUserMatchupStats(
                winsAgainstUser,
                TresetaMatchupStats.configKey(gameConfig),
                otherUser.getId()
        );
        stats.addPlayed();
        if (won) {
            stats.addWin();
        }
    }

    public void addGameWithTeammate(TresetaGameConfig gameConfig, UserEntity teammate, boolean won) {
        if (teammate.getId() == null) return;

        var stats = findOrCreateUserMatchupStats(
                winsWithTeammate,
                TresetaMatchupStats.configKey(gameConfig),
                teammate.getId()
        );
        stats.addPlayed();
        if (won) {
            stats.addWin();
        }
    }

    private TresetaMatchupStats findOrCreateUserMatchupStats(Set<TresetaMatchupStats> statsSet, String gameConfig, Long relatedUserId) {
        for (var stat : statsSet) {
            if (stat.matches(gameConfig, relatedUserId)) {
                return stat;
            }
        }

        var stats = new TresetaMatchupStats(gameConfig, relatedUserId, 0, 0);
        statsSet.add(stats);
        return stats;
    }
}

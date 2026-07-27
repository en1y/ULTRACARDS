package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.server.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Durak statistics per user. Because Durak has 168 valid configurations, per-mode counters are
 * keyed by the canonical mode key string rather than by an enum.
 */
@Entity
@Table(name = "user_durak_stats")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserDurakStats implements DetailedGameStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    /** How often this user was left holding cards as the durak. */
    @Column(name = "times_durak", nullable = false)
    private int timesDurak = 0;

    @Column(name = "draws", nullable = false)
    private int draws = 0;

    @ElementCollection
    @CollectionTable(
            name = "user_durak_stats_entries",
            joinColumns = @JoinColumn(name = "user_durak_stats_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    @MapKeyColumn(name = "mode_key")
    private Map<String, GameStats> configStats = new LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(
            name = "user_durak_stats_against_user",
            joinColumns = @JoinColumn(name = "user_durak_stats_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_durak_stats_against_user",
                    columnNames = {"user_durak_stats_id", "mode_key", "related_user_id"}
            )
    )
    @AttributeOverrides({
            @AttributeOverride(name = "modeKey", column = @Column(name = "mode_key", nullable = false)),
            @AttributeOverride(name = "relatedUserId", column = @Column(name = "related_user_id", nullable = false)),
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false)),
            @AttributeOverride(name = "lastPlayedAt", column = @Column(name = "last_played_at"))
    })
    private Set<DurakMatchupStats> winsAgainstUser = new HashSet<>();

    public UserDurakStats(UserEntity user) {
        this.user = user;
    }

    public void addGame(DurakGameConfig config, boolean won) {
        addGame(config.modeKey(), won);
    }

    public void addGame(String modeKey, boolean won) {
        var stats = configStats.computeIfAbsent(modeKey, ignored -> new GameStats());
        stats.addPlayed();
        if (won) stats.addWon();
    }

    public void addDurak() {
        timesDurak++;
    }

    public void addDraw() {
        draws++;
    }

    public void addGameAgainstUser(String modeKey, UserEntity otherUser, boolean won) {
        if (otherUser.getId() == null) return;
        var stats = findOrCreate(modeKey, otherUser.getId());
        stats.addPlayed();
        if (won) stats.addWin();
    }

    private DurakMatchupStats findOrCreate(String modeKey, Long relatedUserId) {
        for (var stat : winsAgainstUser) {
            if (stat.matches(modeKey, relatedUserId)) return stat;
        }
        var stats = new DurakMatchupStats(modeKey, relatedUserId, 0, 0);
        winsAgainstUser.add(stats);
        return stats;
    }

    /** Wipes every counter, so the admin rebuild can replay history from scratch. */
    public void reset() {
        configStats.clear();
        winsAgainstUser.clear();
        timesDurak = 0;
        draws = 0;
    }
}

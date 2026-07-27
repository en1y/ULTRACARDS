package com.ultracards.server.entity.games.gamestats;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One (mode, opponent) pair. A win means the subject was not the durak while the related player
 * was; two non-losers are never credited with beating each other.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"modeKey", "relatedUserId"})
public class DurakMatchupStats {
    private String modeKey;
    private Long relatedUserId;
    private int played;
    private int wins;
    private Instant lastPlayedAt;

    public DurakMatchupStats(String modeKey, Long relatedUserId, int played, int wins) {
        this(modeKey, relatedUserId, played, wins, null);
    }

    public boolean matches(String modeKey, Long relatedUserId) {
        return this.modeKey.equals(modeKey) && this.relatedUserId.equals(relatedUserId);
    }

    public void addPlayed() {
        played++;
        lastPlayedAt = Instant.now();
    }

    public void addWin() {
        wins++;
    }
}

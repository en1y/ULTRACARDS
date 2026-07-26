package com.ultracards.recorder;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A completed Durak game. Durak has no score, so the outcome is stored explicitly as a loser or a
 * draw; it must never be inferred from round points or round winners.
 */
@Entity
@Table(name = "recorded_durak_games")
public class RecordedDurakGame extends RecordedGame {
    @Column(name = "mode_key", nullable = false, length = 64)
    private String modeKey;
    @Column(name = "number_of_players", nullable = false)
    private int numberOfPlayers;
    @Column(name = "deck_size", nullable = false)
    private int deckSize;
    @Column(name = "jokers_enabled", nullable = false)
    private boolean jokersEnabled;
    @Column(name = "throw_in_policy", nullable = false, length = 32)
    private String throwInPolicy;
    @Column(name = "passing_enabled", nullable = false)
    private boolean passingEnabled;
    @Column(name = "trump_suit", length = 16)
    private String trumpSuit;
    @Column(name = "trump_indicator_code", length = 8)
    private String trumpIndicatorCode;
    /** Null when {@link #draw()} is true; a null loser on a non-draw game is a data-integrity error. */
    @Column(name = "loser_user_id")
    private Long loserUserId;
    @Column(name = "draw", nullable = false)
    private boolean draw;
    @ElementCollection
    @CollectionTable(name = "recorded_durak_finish_order", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "finish_position")
    @Column(name = "user_id")
    private List<Long> finishOrderUserIds = new ArrayList<>();

    protected RecordedDurakGame() {
    }

    public RecordedDurakGame(UUID id, UUID lobbyId, String name, Long ownerId, String modeKey,
                             int numberOfPlayers, int deckSize, boolean jokersEnabled,
                             String throwInPolicy, boolean passingEnabled) {
        super(id, lobbyId, name, ownerId);
        this.modeKey = modeKey;
        this.numberOfPlayers = numberOfPlayers;
        this.deckSize = deckSize;
        this.jokersEnabled = jokersEnabled;
        this.throwInPolicy = throwInPolicy;
        this.passingEnabled = passingEnabled;
    }

    public void result(String trumpSuit, String trumpIndicatorCode, Long loserUserId, boolean draw,
                       List<Long> finishOrderUserIds) {
        this.trumpSuit = trumpSuit;
        this.trumpIndicatorCode = trumpIndicatorCode;
        this.loserUserId = loserUserId;
        this.draw = draw;
        this.finishOrderUserIds.clear();
        this.finishOrderUserIds.addAll(finishOrderUserIds);
    }

    public String modeKey() { return modeKey; }
    public int numberOfPlayers() { return numberOfPlayers; }
    public int deckSize() { return deckSize; }
    public boolean jokersEnabled() { return jokersEnabled; }
    public String throwInPolicy() { return throwInPolicy; }
    public boolean passingEnabled() { return passingEnabled; }
    public String trumpSuit() { return trumpSuit; }
    public String trumpIndicatorCode() { return trumpIndicatorCode; }
    public Long loserUserId() { return loserUserId; }
    public boolean draw() { return draw; }
    public List<Long> finishOrderUserIds() { return List.copyOf(finishOrderUserIds); }

    /** Rejects incomplete or contradictory outcomes loaded from persistent storage. */
    public void requireValidResult() {
        if (draw && loserUserId != null) {
            throw new IllegalStateException("Recorded Durak game " + id()
                    + " is a draw but also names loser " + loserUserId);
        }
        if (!draw && loserUserId == null) {
            throw new IllegalStateException("Recorded Durak game " + id()
                    + " is not a draw but has no loser");
        }
        if (loserUserId != null && players().stream().noneMatch(player -> player.id().equals(loserUserId))) {
            throw new IllegalStateException("Recorded Durak game " + id()
                    + " names a loser who did not play: " + loserUserId);
        }
    }
}

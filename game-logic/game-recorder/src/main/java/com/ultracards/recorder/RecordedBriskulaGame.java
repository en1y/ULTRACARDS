package com.ultracards.recorder;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "recorded_briskula_games")
public class RecordedBriskulaGame extends RecordedGame {
    @Column(name = "game_config", nullable = false)
    private String gameConfig;
    @Column(name = "teams_enabled", nullable = false)
    private boolean teamsEnabled;
    @Column(name = "trump_suit", nullable = false)
    private String trumpSuit = "";
    @Column(name = "trump_value", nullable = false)
    private String trumpValue = "";
    @ElementCollection
    @CollectionTable(name = "recorded_briskula_team_players", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "player_order")
    @Column(name = "user_id")
    private List<Long> teamUserIds = new ArrayList<>();

    protected RecordedBriskulaGame() {
    }

    public RecordedBriskulaGame(UUID id, UUID lobbyId, String name, Long ownerId, String config, boolean teams, List<Long> teamIds) {
        super(id, lobbyId, name, ownerId);
        gameConfig = config;
        teamsEnabled = teams;
        teamUserIds.addAll(teamIds);
    }

    public String gameConfig() {
        return gameConfig;
    }

    public boolean teamsEnabled() {
        return teamsEnabled;
    }

    public String trumpSuit() {
        return trumpSuit;
    }

    public String trumpValue() {
        return trumpValue;
    }

    public List<Long> teamUserIds() {
        return List.copyOf(teamUserIds);
    }

    public void setTrump(String suit, String value) {
        trumpSuit = suit;
        trumpValue = value;
    }
}

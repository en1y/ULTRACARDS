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

@Entity
@Table(name = "recorded_treseta_games")
public class RecordedTresetaGame extends RecordedGame {
    @Column(name = "game_config", nullable = false)
    private String gameConfig;
    @Column(name = "teams_enabled", nullable = false)
    private boolean teamsEnabled;
    @ElementCollection
    @CollectionTable(name = "recorded_treseta_team_players", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "player_order")
    @Column(name = "user_id")
    private List<Long> teamUserIds = new ArrayList<>();

    protected RecordedTresetaGame() {
    }

    public RecordedTresetaGame(UUID id, UUID lobbyId, String name, Long ownerId, String config,
                               boolean teams, List<Long> teamIds) {
        super(id, lobbyId, name, ownerId);
        gameConfig = config;
        teamsEnabled = teams;
        teamUserIds.addAll(teamIds);
    }

    public String gameConfig() { return gameConfig; }
    public boolean teamsEnabled() { return teamsEnabled; }
    public List<Long> teamUserIds() { return List.copyOf(teamUserIds); }
}

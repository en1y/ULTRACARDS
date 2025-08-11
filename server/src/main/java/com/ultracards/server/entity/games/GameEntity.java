package com.ultracards.server.entity.games;

import com.ultracards.server.entity.UserEntity;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for storing game data.
 */
@Entity
@Table(name = "games")
public class GameEntity {
    @Id
    private String id;

    @Column(name = "game_type", nullable = false)
    private String gameType;

    @Column(name = "status", nullable = false)
    private String status;

    private String gameName;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private UserEntity creator;

    @ManyToMany
    @JoinTable(
        name = "game_players",
        joinColumns = @JoinColumn(name = "game_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<UserEntity> players;

    @Type(value = JsonBinaryType.class)
    @Column(name = "game_state", columnDefinition = "jsonb", nullable = false)
    private String gameStateJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public GameEntity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addPlayer(UserEntity user) {
        if (this.players.contains(user))
            throw new IllegalArgumentException("Player is already a part of this game");
        this.players.add(user);
    }

    public void removePlayer(UserEntity user) {
        if (!this.players.remove(user))
            throw new IllegalArgumentException("Player is not in this game");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public List<UserEntity> getPlayers() {
        return players;
    }

    public void setPlayers(List<UserEntity> players) {
        this.players = players;
    }

    public String getGameStateJson() {
        return gameStateJson;
    }

    public void setGameStateJson(String gameStateJson) {
        this.gameStateJson = gameStateJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public UserEntity getCreator() {
        return creator;
    }

    public void setCreator(UserEntity creator) {
        this.creator = creator;
    }
}
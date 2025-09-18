package com.ultracards.server.entity.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity for storing game data.
 */
@Entity
@Table(name = "games")
@Getter
@Setter
public class GameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "game_type", nullable = false)
    private String gameType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private GameType type;

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

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
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
}

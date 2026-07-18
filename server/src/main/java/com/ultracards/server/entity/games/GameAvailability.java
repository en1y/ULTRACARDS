package com.ultracards.server.entity.games;

import com.ultracards.server.enums.games.GameType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "game_availability", uniqueConstraints = @UniqueConstraint(columnNames = {"game_type", "mode"}))
@Getter
@NoArgsConstructor
public class GameAvailability {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 32)
    private GameType gameType;
    @Column(nullable = false, length = 128)
    private String mode;
    @Column(nullable = false)
    private boolean enabled;

    public GameAvailability(GameType gameType, String mode, boolean enabled) {
        this.gameType = gameType;
        this.mode = mode;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

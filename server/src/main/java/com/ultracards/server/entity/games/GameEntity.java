package com.ultracards.server.entity.games;

import com.ultracards.gateway.dto.updated.games.GameConfigDTO;
import com.ultracards.gateway.dto.updated.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.templates.game.model.AbstractGame;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
public class GameEntity<Game extends AbstractGame<?, ?, ?, ?, ?, ?, ?>> {
    private UUID id = UUID.randomUUID();
    private UUID lobbyId;
    private String name;
    private UserEntity owner;
    private Instant createdAt = Instant.now();
    private List<UserEntity> players = new ArrayList<>();
    private GameTypeDTO gameType;
    private Game game;

    public GameEntity(UUID lobbyId, String name, UserEntity owner, List<UserEntity> players, GameTypeDTO gameType, Game game) {
        this.lobbyId = lobbyId;
        this.name = name;
        this.owner = owner;
        this.players.addAll(players);
        this.gameType = gameType;
        this.game = game;
    }
}

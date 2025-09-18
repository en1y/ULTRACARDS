package com.ultracards.server.entity.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Getter @Setter
public class GameLobby {
    private UUID id;
    private GameType gameType;
    private Instant createdAt;
    private List<UserEntity> players;
    private UserEntity owner;
    private String lobbyName;
    private Integer maxPlayers;
    private String configJson;

}

package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.briskula.BriskulaGameService;
import com.ultracards.server.service.games.treseta.TresetaGameService;
import com.ultracards.server.service.lobby.LobbyManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameManager gameManager;
    private final LobbyManager lobbyManager;
    private final BriskulaGameService briskulaGameService;
    private final TresetaGameService tresetaGameService;
    private final GameRecordingService gameRecordingService;

    public GameEntity<?, ?> startGame(LobbyEntity lobby) {
        var game = gameManager.createGame(lobby.createGame());
        lobbyManager.putGame(lobby, game);
        if (game.getGameType().equals(GameTypeDTO.Briskula)) {
            var briskulaGame = (BriskulaGameEntity) game;
            gameRecordingService.start(briskulaGame);
            briskulaGameService.onGameStarted(briskulaGame);
        }
        if (game.getGameType().equals(GameTypeDTO.Treseta)) {
            var tresetaGame = (TresetaGameEntity) game;
            gameRecordingService.start(tresetaGame);
            tresetaGameService.onGameStarted(tresetaGame);
        }
        return game;
    }

    public Optional<GameEntity<?, ?>> getGameByUser(UserEntity user) {
        return Optional.ofNullable(gameManager.getGame(user.getId()));
    }

    public void playCard(UserEntity user, @Valid GameCardDTO card, GameEntity<?, ?> game) {
        if (game.getGameType().equals(GameTypeDTO.Briskula))
            briskulaGameService.playCard(user, card, (BriskulaGameEntity) game);
        if (game.getGameType().equals(GameTypeDTO.Treseta))
            tresetaGameService.playCard(user, card, (TresetaGameEntity) game);
    }
}

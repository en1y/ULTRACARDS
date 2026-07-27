package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaDeclarationRequestDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.gateway.dto.games.games.durak.DurakActionRequestDTO;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.briskula.BriskulaGameService;
import com.ultracards.server.service.games.durak.DurakGameService;
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
    private final DurakGameService durakGameService;
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
        if (game.getGameType().equals(GameTypeDTO.Durak)) {
            var durakGame = (DurakGameEntity) game;
            gameRecordingService.start(durakGame);
            durakGameService.onGameStarted(durakGame);
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
        // A Durak card is never "the player's whole turn", so tell the client instead of silently ignoring it.
        if (game.getGameType().equals(GameTypeDTO.Durak))
            durakGameService.rejectGenericPlay(user, (DurakGameEntity) game);
    }

    public void durakAction(UserEntity user, @Valid DurakActionRequestDTO request, GameEntity<?, ?> game) {
        if (!(game instanceof DurakGameEntity durakGame))
            throw new IllegalArgumentException("This is not a Durak game.");
        durakGameService.action(user, request, durakGame);
    }

    public void declare(UserEntity user, @Valid TresetaDeclarationRequestDTO declaration, GameEntity<?, ?> game) {
        if (!game.getGameType().equals(GameTypeDTO.Treseta))
            throw new IllegalArgumentException("Declarations are only supported in Treseta.");
        tresetaGameService.declare(user, declaration, (TresetaGameEntity) game);
    }
}

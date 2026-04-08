package com.ultracards.server.service.games;

import com.ultracards.cards.ItalianCard;
import com.ultracards.gateway.dto.games.games.GameCardDTO;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.lobby.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.*;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final LobbyManager lobbyManager;
    private final HashMap<Long, GameEntity<?>> gameCache = new HashMap<>();
    private final UserGamesStatsService userGamesStatsService;

    public GameEntity<?> startGame(LobbyEntity lobby) {
        var game = gameManager.createGame(lobby.createGame());
        lobbyManager.putGame(lobby, game);
        eventPublisher.publish(game, STARTED);
        game.getPlayers().forEach(p -> gameCache.put(p.getId(), game));
        return game;
    }

    public Optional<GameEntity<?>> getGameByUser(UserEntity user) {
        return Optional.ofNullable(gameCache.get(user.getId()));
    }

    public void playCard(UserEntity user, GameCardDTO cardDTO) {
        var game = gameManager.getGame(user.getId());
        var genericCard = cardDTO.toCard();
        if (game instanceof BriskulaGameEntity game1 && genericCard instanceof ItalianCard<?>) {
            var briskulaGame = game1.getGame();
            var playingField = briskulaGame.getPlayingField();
            var success = game1.playCard(user, genericCard);

            if (success) {
                var newPlayingField = briskulaGame.getPlayingField();
                if (newPlayingField == null || newPlayingField.getPlayedCards().isEmpty()) {
                    briskulaGame.setPlayingField(playingField);
                    eventPublisher.publish(game, UPDATED);
                    briskulaGame.setPlayingField(newPlayingField);
                }
                eventPublisher.publish(game, UPDATED);
                if (!game.isActive()) {
                    eventPublisher.publish(game, RESULTED);

                    var winners = game1.getGame().determineGameWinners();
                    game.getGame().getPlayers().forEach(p -> {
                        var player = (BriskulaPlayerEntity) p;
                        var stats = userGamesStatsService.getByUser(player.getUser());
                        userGamesStatsService.addGamePlayed(stats, GameType.fromDTO(game.getGameType()));
                        if (winners.contains(p)) {
                            userGamesStatsService.addGameWon(stats, GameType.fromDTO(game.getGameType()));
                        }
                    });

                    game.getPlayers().forEach(p -> gameCache.remove(p.getId()));
                    lobbyManager.getLobby(game.getLobbyId()).setLobbyState(LobbyState.OPEN);
                }
            }
        }
    }
}

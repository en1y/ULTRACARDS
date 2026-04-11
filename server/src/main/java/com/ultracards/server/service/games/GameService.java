package com.ultracards.server.service.games;

import com.ultracards.cards.ItalianCard;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.lobby.LobbyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.*;

@Service
public class GameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final LobbyManager lobbyManager;
    private final HashMap<Long, GameEntity<?>> gameCache = new HashMap<>();
    private final UserGamesStatsService userGamesStatsService;
    private final TaskScheduler taskScheduler;
    private final Function<LobbyEntity, Boolean> openLobby;

    @Value("${app.briskula-move.timer.duration-seconds}")
    private int briskulaTimerDuration;


    public GameService(
            GameManager gameManager,
            GameEventPublisher eventPublisher,
            LobbyManager lobbyManager,
            UserGamesStatsService userGamesStatsService,
            @Qualifier("timer")
            TaskScheduler taskScheduler,
            @Qualifier("openLobby") @Lazy Function<LobbyEntity, Boolean> openLobby) {
        this.gameManager = gameManager;
        this.eventPublisher = eventPublisher;
        this.lobbyManager = lobbyManager;
        this.userGamesStatsService = userGamesStatsService;
        this.taskScheduler = taskScheduler;
        this.openLobby = openLobby;
    }

    public GameEntity<?> startGame(LobbyEntity lobby) {
        var game = gameManager.createGame(lobby.createGame());
        lobbyManager.putGame(lobby, game);
        setTimer(game);
        eventPublisher.publish(game, STARTED);
        game.getPlayers().forEach(p -> gameCache.put(p.getId(), game));
        return game;
    }

    public void setTimer(GameEntity<?> game) {
        if (game.getGameType().equals(GameTypeDTO.Briskula)) {
            var briskulaGame = ((BriskulaGameEntity) game);
            game.setTurnEndTime(Instant.now().plusSeconds(briskulaTimerDuration));
            var prevCurrPlayer = briskulaGame.getCurrentPlayer();
            taskScheduler.schedule(() -> {
                if (briskulaGame.getCurrentPlayer().equals(prevCurrPlayer)){
                    var card = GameCardDTO.createCardDTO(
                            prevCurrPlayer.getHand().getCards().getFirst());
                    if (card != null) {
                        playCard(prevCurrPlayer.getUser(), card);

                    }
                }
            }, briskulaGame.getTurnEndTime());
        }
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
                if (game.isActive()) {
                    setTimer(game);
                    eventPublisher.publish(game, UPDATED);
                }
                else {
                    eventPublisher.publish(game, RESULTED);

                    var winners = game1.getGame().determineGameWinners();
                    game.getGame().getPlayers().forEach(p -> {
                        var player = (BriskulaPlayerEntity) p;
                        userGamesStatsService.addGamePlayed(player.getUser(), GameType.fromDTO(game.getGameType()));
                        if (winners.contains(p)) {
                            userGamesStatsService.addGameWon(player.getUser(), GameType.fromDTO(game.getGameType()));
                        }
                    });

                    game.getPlayers().forEach(p -> gameCache.remove(p.getId()));
                    openLobby.apply(lobbyManager.getLobby(game.getLobbyId()));
                }
            }
        }
    }
}

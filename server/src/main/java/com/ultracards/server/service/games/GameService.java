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
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import com.ultracards.server.service.lobby.LobbyManager;
import com.ultracards.templates.cards.AbstractCard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.*;

@Service
public class GameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final LobbyManager lobbyManager;
    private final HashMap<Long, GameEntity<?, ?>> gameCache = new HashMap<>();
    private final UserBriskulaStatsService userBriskulaStatsService;
    private final UserGamesStatsService userGamesStatsService;
    private final BriskulaGameRepository briskulaGameRepository;
    private final TaskScheduler taskScheduler;
    private final Function<LobbyEntity, Boolean> openLobby;

    @Value("${app.briskula-move.timer.duration-seconds}")
    private int briskulaTimerDuration;


    public GameService(
            GameManager gameManager,
            GameEventPublisher eventPublisher,
            LobbyManager lobbyManager,
            UserBriskulaStatsService userBriskulaStatsService,
            UserGamesStatsService userGamesStatsService,
            BriskulaGameRepository briskulaGameRepository,
            @Qualifier("timer")
            TaskScheduler taskScheduler,
            @Qualifier("openLobby") @Lazy Function<LobbyEntity, Boolean> openLobby) {
        this.gameManager = gameManager;
        this.eventPublisher = eventPublisher;
        this.lobbyManager = lobbyManager;
        this.userBriskulaStatsService = userBriskulaStatsService;
        this.userGamesStatsService = userGamesStatsService;
        this.briskulaGameRepository = briskulaGameRepository;
        this.taskScheduler = taskScheduler;
        this.openLobby = openLobby;
    }

    public GameEntity<?, ?> startGame(LobbyEntity lobby) {
        var game = gameManager.createGame(lobby.createGame());
        lobbyManager.putGame(lobby, game);
        if (game.getGameType().equals(GameTypeDTO.Briskula)) {
            game.setTurnDurationSeconds(briskulaTimerDuration);
        }
        setTimer(game);
        eventPublisher.publish(game, STARTED);
        game.getPlayers().forEach(p -> gameCache.put(p.getId(), game));
        return game;
    }

    public void setTimer(GameEntity<?, ?> game) {
        if (game.getGameType().equals(GameTypeDTO.Briskula))
            setBriskulaTimer((BriskulaGameEntity) game);
    }
    private void setBriskulaTimer (BriskulaGameEntity briskulaGame) {
        briskulaGame.setTurnEndTime(Instant.now().plusSeconds(briskulaTimerDuration));
        var prevTurnNum = briskulaGame.getTurnNumber();
        taskScheduler.schedule(() -> {
            if (briskulaGame.getTurnNumber().equals(prevTurnNum)) {
                var player = briskulaGame.getCurrentPlayer();
                var card = GameCardDTO.createCardDTO(
                        player.getHand().getCards().getFirst());
                if (card != null) {
                    playCard(player.getUser(), card);

                }
            }
        }, briskulaGame.getTurnEndTime());
    }

    public Optional<GameEntity<?, ?>> getGameByUser(UserEntity user) {
        return Optional.ofNullable(gameCache.get(user.getId()));
    }

    public void playCard(UserEntity user, GameCardDTO cardDTO) {
        var game = gameManager.getGame(user.getId());
        var genericCard = cardDTO.toCard();
        if (game instanceof BriskulaGameEntity game1 && genericCard instanceof ItalianCard<?>) {
            playCardInBriskula(game1, user, genericCard);

        }
    }

    private void playCardInBriskula(BriskulaGameEntity briskulaGameEntity,
                                    UserEntity user,
                                    AbstractCard<?, ?, ? extends AbstractCard<?, ?, ?>> genericCard) {
        var briskulaGame = briskulaGameEntity.getGame();
        var playingField = briskulaGame.getPlayingField();
        var success = briskulaGameEntity.playCard(user, genericCard);

        if (success) {
            var newPlayingField = briskulaGame.getPlayingField();
            if (newPlayingField == null || newPlayingField.getPlayedCards().isEmpty()) {
                briskulaGame.setPlayingField(playingField);
                eventPublisher.publish(briskulaGameEntity, UPDATED);
                briskulaGame.setPlayingField(newPlayingField);
            }
            if (briskulaGameEntity.isActive()) {
                setTimer(briskulaGameEntity);
                eventPublisher.publish(briskulaGameEntity, UPDATED);
            }
            else {
                eventPublisher.publish(briskulaGameEntity, RESULTED);

                var winners = briskulaGameEntity.getGame().determineGameWinners();
                var briskulaGameConfig = briskulaGameEntity.getGameConfig().getGameConfig();
                var winnerUsers = new HashSet<UserEntity>();
                winners.forEach(player -> winnerUsers.add(((BriskulaPlayerEntity) player).getUser()));
                briskulaGame.getPlayers().forEach(p -> {
                    var player = (BriskulaPlayerEntity) p;
                    var won = winners.contains(p);
                    userGamesStatsService.addGame(player.getUser(), GameType.BRISKULA, won);
                    userBriskulaStatsService.addBriskulaGame(player.getUser(), briskulaGameConfig, won);
                });
                updateBriskulaRelationshipStats(briskulaGameEntity.getPlayers(), winnerUsers, briskulaGameConfig);
                briskulaGameEntity.markEnded();
                briskulaGameRepository.save(briskulaGameEntity);

                briskulaGameEntity.getPlayers().forEach(p -> gameCache.remove(p.getId()));
                var lobby = lobbyManager.getLobby(briskulaGameEntity.getLobbyId());
                openLobby.apply(lobby);
            }
        }
    }

    private void updateBriskulaRelationshipStats(List<UserEntity> players, Set<UserEntity> winnerUsers, com.ultracards.games.briskula.BriskulaGameConfig gameConfig) {
        var loserUsers = new ArrayList<UserEntity>();
        for (var player : players) {
            if (!winnerUsers.contains(player)) {
                loserUsers.add(player);
            }
        }

        for (var winner : winnerUsers) {
            for (var loser : loserUsers) {
                if (!winner.equals(loser)) {
                    userBriskulaStatsService.addBriskulaGameAgainstUser(winner, gameConfig, loser, true);
                    userBriskulaStatsService.addBriskulaGameAgainstUser(loser, gameConfig, winner, false);
                }
            }
        }

        if (gameConfig.areTeamsEnabled() && winnerUsers.size() > 1) {
            var winnersList = new ArrayList<>(winnerUsers);
            for (int i = 0; i < winnersList.size(); i++) {
                for (int j = 0; j < winnersList.size(); j++) {
                    if (i != j) {
                        userBriskulaStatsService.addBriskulaGameWithTeammate(winnersList.get(i), gameConfig, winnersList.get(j), true);
                    }
                }
            }
        }

        if (gameConfig.areTeamsEnabled()) {
            updateBriskulaTeammatePlayedStats(players, winnerUsers, gameConfig);
        }
    }

    private void updateBriskulaTeammatePlayedStats(List<UserEntity> players, Set<UserEntity> winnerUsers,
                                                   com.ultracards.games.briskula.BriskulaGameConfig gameConfig) {
        var loserUsers = new ArrayList<UserEntity>();
        for (var player : players) {
            if (!winnerUsers.contains(player)) {
                loserUsers.add(player);
            }
        }

        if (loserUsers.size() > 1) {
            for (int i = 0; i < loserUsers.size(); i++) {
                for (int j = 0; j < loserUsers.size(); j++) {
                    if (i != j) {
                        userBriskulaStatsService.addBriskulaGameWithTeammate(loserUsers.get(i), gameConfig, loserUsers.get(j), false);
                    }
                }
            }
        }
    }
}

package com.ultracards.server.service.games.briskula;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.games.briskula.BriskulaDeck;
import com.ultracards.games.briskula.BriskulaGame;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.games.briskula.BriskulaPlayingField;
import com.ultracards.gateway.dto.games.briskula.BriskulaCardDTO;
import com.ultracards.gateway.dto.games.briskula.BriskulaGameStateDTO;
import com.ultracards.gateway.dto.games.briskula.BriskulaPlayCardRequest;
import com.ultracards.gateway.dto.games.briskula.BriskulaPlayedCardDTO;
import com.ultracards.gateway.dto.games.briskula.BriskulaPlayerViewDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.GameLobby;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.GameEntityRepository;
import com.ultracards.server.service.games.GameEventPublisher;
import com.ultracards.server.service.games.UserGamesStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BriskulaRuntimeService {

    private final GameEventPublisher eventPublisher;
    private final GameEntityRepository gameRepository;
    private final UserGamesStatsService statsService;
    private final ObjectMapper objectMapper;

    private final ConcurrentMap<UUID, BriskulaRuntime> games = new ConcurrentHashMap<>();

    public boolean supports(GameEntity game) {
        return game != null && game.getType() == GameType.BRISKULA;
    }

    public String initialize(GameEntity game, GameLobby lobby, String configValue) {
        if (!supports(game)) {
            return configValue != null ? configValue : "{}";
        }
        var config = resolveConfig(configValue, lobby.getPlayers().size());
        var runtime = new BriskulaRuntime(game.getId(), config, lobby.getPlayers());
        games.put(game.getId(), runtime);
        var stateJson = toJson(runtime.buildState());
        game.setGameStateJson(stateJson);
        game.setUpdatedAt(LocalDateTime.now());
        return stateJson;
    }

    @Transactional
    public void handlePlayCard(UUID gameId, UserEntity user, BriskulaPlayCardRequest payload) {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(payload, "payload");
        var runtime = games.get(gameId);
        if (runtime == null) {
            throw new IllegalStateException("Runtime not found for game " + gameId);
        }
        BriskulaGameStateDTO state;
        boolean finished;
        String stateJson;
        synchronized (runtime) {
            state = runtime.playCard(user.getId(), payload);
            finished = state.finished();
            stateJson = toJson(state);
        }
        persistState(gameId, state, stateJson, finished);
        publishState(gameId, stateJson, finished);
        if (finished) {
            games.remove(gameId);
        }
    }

    public Optional<BriskulaGameStateDTO> currentState(UUID gameId) {
        var runtime = games.get(gameId);
        if (runtime == null) return Optional.empty();
        synchronized (runtime) {
            return Optional.of(runtime.buildState());
        }
    }

    private void publishState(UUID gameId, String stateJson, boolean finished) {
        eventPublisher.publish(gameId, "STATE", stateJson);
        if (finished) {
            eventPublisher.publish(gameId, "FINISHED", stateJson);
        }
    }

    private void persistState(UUID gameId, BriskulaGameStateDTO state, String stateJson, boolean finished) {
        gameRepository.findById(gameId).ifPresent(game -> {
            game.setGameStateJson(stateJson);
            game.setUpdatedAt(LocalDateTime.now());
            if (finished && game.isActive()) {
                game.setActive(false);
                awardWinners(game.getPlayers(), state.winners());
            }
            gameRepository.save(game);
        });
    }

    private void awardWinners(List<UserEntity> players, List<Long> winners) {
        if (winners == null || winners.isEmpty()) return;
        Map<Long, UserEntity> playersById = players.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(UserEntity::getId, p -> p));
        for (Long winnerId : winners) {
            var user = playersById.get(winnerId);
            if (user == null) continue;
            var stats = statsService.getByUser(user);
            if (stats != null) {
                statsService.addGameWon(stats, GameType.BRISKULA);
            }
        }
    }

    private BriskulaGameConfig resolveConfig(String configValue, int lobbyPlayers) {
        if (configValue != null) {
            try {
                return BriskulaGameConfig.valueOf(configValue);
            } catch (IllegalArgumentException ignore) {
                // fallback below
            }
        }
        return switch (lobbyPlayers) {
            case 2 -> BriskulaGameConfig.TWO_PLAYERS;
            case 3 -> BriskulaGameConfig.THREE_PLAYERS;
            case 4 -> BriskulaGameConfig.FOUR_PLAYERS_NO_TEAMS;
            default -> BriskulaGameConfig.TWO_PLAYERS;
        };
    }

    private String toJson(BriskulaGameState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize game state", e);
        }
    }

    /* ================== Runtime ================== */

    static final class BriskulaRuntime {
        private final UUID gameId;
        private final BriskulaGameConfig config;
        private final BriskulaGame engine;
        private final BriskulaDeck deck;
        private final BriskulaCard trumpCard;
        private final ItalianCardType trumpSuit;
        private final BriskulaPlayingField playingField;
        private final List<PlayerState> players;
        private final List<BriskulaPlayer> enginePlayers;
        private final List<PlayedCard> trick;
        private int currentPlayerIndex;
        private boolean finished;
        private List<Long> winners;

        BriskulaRuntime(UUID gameId, BriskulaGameConfig config, List<UserEntity> lobbyPlayers) {
            this.gameId = gameId;
            this.config = config;
            this.engine = new BriskulaGame(config);
            this.deck = engine.createDeck(40);
            engine.removeNotNeededCards(deck, config.getCardsInHandNum());
            this.trumpCard = deck.getCards().get(deck.getSize() - 1);
            this.trumpSuit = engine.getGameTrumpCardType();
            this.playingField = engine.createPlayingField();
            this.players = new ArrayList<>(lobbyPlayers.size());
            this.enginePlayers = new ArrayList<>(lobbyPlayers.size());

            for (UserEntity user : lobbyPlayers) {
                var player = new BriskulaPlayer(user.getUsername());
                enginePlayers.add(player);
                players.add(new PlayerState(user, player));
            }

            engine.setPlayers(enginePlayers);
            engine.setNumberOfPlayers(enginePlayers.size());
            engine.setDeck(deck);
            engine.setPlayingField(playingField);
            engine.createPlayersHands(deck, enginePlayers);

            this.trick = new ArrayList<>();
            this.currentPlayerIndex = 0;
            this.finished = false;
            this.winners = List.of();
        }

        BriskulaGameState playCard(Long userId, PlayCardPayload payload) {
            if (finished) {
                throw new IllegalStateException("Game already finished");
            }
            var playerIndex = indexOf(userId);
            if (playerIndex != currentPlayerIndex) {
                throw new IllegalStateException("Not this player's turn");
            }
            var player = players.get(playerIndex);
            var card = player.removeCard(payload.suit(), payload.value());
            trick.add(new PlayedCard(playerIndex, player, card));
            playingField.addCard(card);
            playingField.addPlayer(player.enginePlayer);

            if (trick.size() == enginePlayers.size()) {
                resolveTrick();
            } else {
                currentPlayerIndex = nextIndex(playerIndex);
            }
            if (!finished) {
                ensureNextPlayerHasCards();
            }
            return buildState();
        }

        private void ensureNextPlayerHasCards() {
            for (int i = 0; i < players.size(); i++) {
                var idx = (currentPlayerIndex + i) % players.size();
                if (!players.get(idx).handEmpty()) {
                    currentPlayerIndex = idx;
                    return;
                }
            }
            finished = true;
            winners = determineWinners();
        }

        private void resolveTrick() {
            var winnerPlayer = engine.determineRoundWinner(playingField);
            int winnerIdx = indexOf(winnerPlayer);

            engine.postRoundWinnerDeterminedActions(winnerPlayer, playingField);
            drawNewCards(winnerIdx);

            trick.clear();
            playingField.setPlayedCards(new ArrayList<>());
            playingField.setPlayers(new ArrayList<>());

            reorderToMatchEngine();
            currentPlayerIndex = 0;
            checkFinished();
        }

        private void drawNewCards(int winnerIdx) {
            if (deck.isEmpty()) {
                return;
            }
            for (int offset = 0; offset < enginePlayers.size(); offset++) {
                var idx = (winnerIdx + offset) % enginePlayers.size();
                if (deck.isEmpty()) break;
                var card = deck.drawCard();
                players.get(idx).addCard(card);
            }
        }

        private void checkFinished() {
            boolean noCards = players.stream().allMatch(PlayerState::handEmpty);
            if (noCards && deck.isEmpty()) {
                finished = true;
                winners = determineWinners();
            }
        }

        private List<Long> determineWinners() {
            var winners = engine.determineGameWinners(enginePlayers);
            if (winners == null || winners.isEmpty()) {
                return players.stream().map(p -> p.userId).toList();
            }
            return winners.stream()
                    .map(this::stateFor)
                    .filter(java.util.Objects::nonNull)
                    .map(el -> el.userId)
                    .toList();
        }

        private int indexOf(Long userId) {
            for (int i = 0; i < players.size(); i++) {
                if (Objects.equals(players.get(i).userId, userId)) {
                    return i;
                }
            }
            throw new IllegalArgumentException("Player not found in game");
        }

        private int indexOf(BriskulaPlayer player) {
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).enginePlayer == player) {
                    return i;
                }
            }
            throw new IllegalArgumentException("Player not found in game");
        }

        private int nextIndex(int current) {
            return (current + 1) % players.size();
        }

        BriskulaGameState buildState() {
            var currentPlayer = finished ? null : players.get(currentPlayerIndex);
            return new BriskulaGameState(
                    gameId,
                    trumpSuit.name(),
                    BriskulaCardView.from(trumpCard),
                    deck.getSize(),
                    players.stream()
                            .map(player -> player.toView(player == currentPlayer))
                            .collect(Collectors.toList()),
                    trick.stream().map(PlayedCard::toView).collect(Collectors.toList()),
                    currentPlayer != null ? currentPlayer.userId : null,
                    currentPlayer != null ? currentPlayer.username : null,
                    finished,
                    winners,
                    config.areTeamsEnabled()
            );
        }

        private void reorderToMatchEngine() {
            var ordering = engine.getPlayers();
            players.sort(java.util.Comparator.comparingInt(ps -> ordering.indexOf(ps.enginePlayer)));
        }

        private PlayerState stateFor(BriskulaPlayer player) {
            return players.stream()
                    .filter(ps -> ps.enginePlayer == player)
                    .findFirst()
                    .orElse(null);
        }
    }

    static final class PlayerState {
        private final Long userId;
        private final String username;
        private final BriskulaPlayer enginePlayer;

        PlayerState(UserEntity user, BriskulaPlayer enginePlayer) {
            this.userId = user.getId();
            this.username = user.getUsername();
            this.enginePlayer = enginePlayer;
        }

        void addCard(BriskulaCard card) {
            enginePlayer.getHand().addCard(card);
        }

        BriskulaCard removeCard(String suit, String value) {
            var card = enginePlayer.getHand().getCards().stream()
                    .filter(c -> c.getType().name().equalsIgnoreCase(suit) && c.getValue().name().equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Card not in hand"));
            enginePlayer.getHand().drawCard(card);
            return card;
        }

        BriskulaPlayerView toView(boolean isCurrentTurn) {
            return new BriskulaPlayerView(
                    userId,
                    username,
                    enginePlayer.getPoints(),
                    enginePlayer.getHand() != null
                            ? enginePlayer.getHand().getCards().stream().map(BriskulaCardView::from).collect(Collectors.toList())
                            : List.of(),
                    enginePlayer.getHand() != null ? enginePlayer.getHand().getCardsNum() : 0,
                    enginePlayer.getWonCards() != null ? enginePlayer.getWonCards().size() : 0,
                    isCurrentTurn
            );
        }

        boolean handEmpty() {
            return enginePlayer.getHand() == null || enginePlayer.getHand().isEmpty();
        }
    }

    static final class PlayedCard {
        private final int playerIndex;
        private final PlayerState player;
        private final BriskulaCard card;

        PlayedCard(int playerIndex, PlayerState player, BriskulaCard card) {
            this.playerIndex = playerIndex;
            this.player = player;
            this.card = card;
        }

        int playerIndex() {
            return playerIndex;
        }

        BriskulaCard card() {
            return card;
        }

        BriskulaPlayedCardView toView() {
            return new BriskulaPlayedCardView(player.userId, player.username, BriskulaCardView.from(card));
        }
    }

    public record PlayCardPayload(String suit, String value) {
        public PlayCardPayload {
            Objects.requireNonNull(suit, "suit");
            Objects.requireNonNull(value, "value");
        }
    }

    public record BriskulaGameState(
            UUID gameId,
            String trumpSuit,
            BriskulaCardView trumpCard,
            int deckRemaining,
            List<BriskulaPlayerView> players,
            List<BriskulaPlayedCardView> trick,
            Long currentTurnUserId,
            String currentTurnUsername,
            boolean finished,
            List<Long> winners,
            boolean teamsEnabled
    ) {}

    public record BriskulaPlayerView(
            Long userId,
            String username,
            int points,
            List<BriskulaCardView> hand,
            int handSize,
            int capturedCards,
            boolean currentTurn
    ) {}

    public record BriskulaPlayedCardView(
            Long userId,
            String username,
            BriskulaCardView card
    ) {}

    public record BriskulaCardView(
            String suit,
            String value,
            int number,
            int points,
            String code
    ) {
        static BriskulaCardView from(BriskulaCard card) {
            var suit = card.getType();
            var value = card.getValue();
            int number = value.getNumber();
            int points = card.getPoints();
            String code = number + suit.name().substring(0, 1);
            return new BriskulaCardView(suit.name(), value.name(), number, points, code);
        }
    }
}

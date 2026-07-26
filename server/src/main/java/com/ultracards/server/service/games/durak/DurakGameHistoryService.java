package com.ultracards.server.service.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.games.durak.DurakCard;
import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameHistoryDTO;
import com.ultracards.recorder.RecordedCard;
import com.ultracards.recorder.RecordedDurakGame;
import com.ultracards.recorder.RecordedPlayer;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.DurakLobbyGameConfig;
import com.ultracards.server.repositories.games.DurakGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconstructs Durak history from the shared recorder tables. Durak has no score, so the outcome
 * comes from the recorded loser/draw flags and never from round points or round winners.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DurakGameHistoryService {
    private final DurakGameRepository repository;

    @Transactional(readOnly = true)
    public List<ShortGameHistoryDTO> getPastGames(UserEntity user, int offset, String result, String sort) {
        return getPastGames(user, result, sort).stream().skip(Math.max(0, offset)).limit(20).toList();
    }

    @Transactional(readOnly = true)
    public List<ShortGameHistoryDTO> getPastGames(UserEntity user, String result, String sort) {
        var games = "oldest".equalsIgnoreCase(sort) || "asc".equalsIgnoreCase(sort)
                ? repository.findPastGamesByUserIdOldest(user.getId())
                : repository.findPastGamesByUserIdLatest(user.getId());
        var output = new ArrayList<ShortGameHistoryDTO>();
        for (var game : games) {
            var dto = shortHistory(game);
            // A draw is neither a win nor a loss under the current filters.
            var win = !game.draw() && !user.getId().equals(game.loserUserId());
            var loss = !game.draw() && user.getId().equals(game.loserUserId());
            if (isWinFilter(result)) {
                if (win) output.add(dto);
            } else if (isLossFilter(result)) {
                if (loss) output.add(dto);
            } else {
                output.add(dto);
            }
        }
        return output;
    }

    private static boolean isWinFilter(String result) {
        return "wins".equalsIgnoreCase(result) || "win".equalsIgnoreCase(result);
    }

    private static boolean isLossFilter(String result) {
        return "loss".equalsIgnoreCase(result) || "losses".equalsIgnoreCase(result)
                || "lose".equalsIgnoreCase(result);
    }

    @Transactional(readOnly = true)
    public DurakGameHistoryDTO getGameHistory(UUID id) {
        var game = repository.findById(id).orElse(null);
        if (game == null) return null;
        game.requireValidResult();

        var bouts = new ArrayList<DurakGameHistoryDTO.DurakBoutHistoryDTO>();
        for (var round : game.rounds()) {
            var plays = new ArrayList<DurakGameHistoryDTO.DurakPlayHistoryDTO>();
            var playOrders = new java.util.HashSet<Integer>();
            for (var play : round.plays()) playOrders.add(play.order());
            for (var play : round.plays()) {
                var target = play.targetPlayOrder();
                if (target != null && !playOrders.contains(target)) {
                    // A malformed defense reference must not break the rest of this user's history.
                    log.warn("Durak game {} bout {} play {} references missing play {}",
                            game.id(), round.order(), play.order(), target);
                    target = null;
                }
                plays.add(new DurakGameHistoryDTO.DurakPlayHistoryDTO(play.order(), play.actionType(),
                        player(play.player()), card(play.card()), target));
            }
            var hands = new LinkedHashMap<GamePlayerDTO, List<GameCardDTO>>();
            for (var hand : round.startingHands()) {
                var cards = new ArrayList<GameCardDTO>();
                for (var recordedCard : hand.cards()) cards.add(card(recordedCard));
                hands.put(player(hand.player()), cards);
            }
            var attributes = round.attributes();
            bouts.add(new DurakGameHistoryDTO.DurakBoutHistoryDTO(
                    Integer.parseInt(attributes.getOrDefault("bout", Integer.toString(round.order() + 1))),
                    hands,
                    byName(game, attributes.get("initialAttacker")),
                    byName(game, attributes.get("finalDefender")),
                    Integer.parseInt(attributes.getOrDefault("maxAttackCards", "0")),
                    plays,
                    passChain(game, attributes.get("passChain")),
                    attributes.getOrDefault("outcome", "UNRESOLVED")));
        }

        return new DurakGameHistoryDTO(game.id(), game.lobbyId(), game.name(), owner(game), game.createdAt(),
                game.endedAt(), config(game), players(game), game.trumpSuit(),
                trumpIndicator(game), bouts, finishOrder(game), loser(game), winners(game), game.draw());
    }

    private ShortGameHistoryDTO shortHistory(RecordedDurakGame game) {
        game.requireValidResult();
        // Durak has no score, so every player scores zero.
        var points = new LinkedHashMap<GamePlayerDTO, Integer>();
        for (var player : game.players()) points.put(player(player), 0);
        return new ShortGameHistoryDTO(game.id(), game.lobbyId(), game.name(), GameTypeDTO.Durak, game.createdAt(),
                game.endedAt(), config(game), players(game), points, winners(game));
    }

    /** Every non-loser wins; a draw has no winners. */
    private List<GamePlayerDTO> winners(RecordedDurakGame game) {
        if (game.draw()) return List.of();
        var winners = new ArrayList<GamePlayerDTO>();
        for (var player : game.players()) {
            if (!player.id().equals(game.loserUserId())) winners.add(player(player));
        }
        return winners;
    }

    private GamePlayerDTO loser(RecordedDurakGame game) {
        if (game.draw() || game.loserUserId() == null) return null;
        for (var player : game.players()) {
            if (player.id().equals(game.loserUserId())) return player(player);
        }
        return null;
    }

    private List<GamePlayerDTO> finishOrder(RecordedDurakGame game) {
        var byId = new HashMap<Long, GamePlayerDTO>();
        for (var player : game.players()) byId.put(player.id(), player(player));
        var order = new ArrayList<GamePlayerDTO>();
        for (var userId : game.finishOrderUserIds()) {
            var player = byId.get(userId);
            if (player != null) order.add(player);
        }
        return order;
    }

    /**
     * Rebuilds the persisted configuration through the canonical validator. A stored mode key that
     * no longer parses is a data-integrity error, not a reason to fall back to another mode.
     */
    public DurakGameConfigDTO config(RecordedDurakGame game) {
        var config = DurakGameConfig.fromModeKey(game.modeKey());
        return DurakLobbyGameConfig.toDto(config, players(game));
    }

    private List<GamePlayerDTO> players(RecordedDurakGame game) {
        var players = new ArrayList<GamePlayerDTO>();
        for (var player : game.players()) players.add(player(player));
        return players;
    }

    private GamePlayerDTO owner(RecordedDurakGame game) {
        for (var player : game.players()) {
            if (player.id().equals(game.ownerUserId())) return player(player);
        }
        return null;
    }

    private GamePlayerDTO byName(RecordedDurakGame game, String name) {
        if (name == null) return null;
        for (var player : game.players()) {
            if (player.name().equals(name)) return player(player);
        }
        return null;
    }

    private List<GamePlayerDTO> passChain(RecordedDurakGame game, String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        var chain = new ArrayList<GamePlayerDTO>();
        for (var name : encoded.split(",")) {
            var player = byName(game, name);
            if (player != null) chain.add(player);
        }
        return chain;
    }

    private GameCardDTO trumpIndicator(RecordedDurakGame game) {
        if (game.trumpIndicatorCode() == null) return null;
        return new GameCardDTO(com.ultracards.gateway.dto.games.games.GameCardTypeDTO.POKER,
                game.trumpIndicatorCode());
    }

    private GamePlayerDTO player(RecordedPlayer player) {
        return player == null ? null : new GamePlayerDTO(player.name(), player.id());
    }

    private GameCardDTO card(RecordedCard card) {
        var suit = PokerCardSuit.valueOf(card.suit());
        var value = PokerCardValue.JOKER;
        if (!suit.isJokerSuit()) {
            for (var candidate : PokerCardValue.values()) {
                if (candidate != PokerCardValue.JOKER && candidate.getNumber() == card.number()) value = candidate;
            }
        }
        return GameCardDTO.createCardDTO(new DurakCard(suit, value));
    }

    /** Used by the leaderboard and admin rebuild paths, which must not depend on DTO shapes. */
    public Map<String, Integer> summary(RecordedDurakGame game) {
        return Map.of("players", game.numberOfPlayers(), "deckSize", game.deckSize());
    }
}

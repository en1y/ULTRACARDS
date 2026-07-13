package com.ultracards.server.service.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.games.treseta.TresetaCard;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameHistoryDTO;
import com.ultracards.recorder.RecordedCard;
import com.ultracards.recorder.RecordedPlayer;
import com.ultracards.recorder.RecordedTresetaGame;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.games.TresetaGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TresetaGameHistoryService {
    private final TresetaGameRepository repository;

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
            var win = contains(dto.getWinners(), user.getId());
            if ("wins".equalsIgnoreCase(result) || "win".equalsIgnoreCase(result)) {
                if (win) output.add(dto);
            } else if ("loss".equalsIgnoreCase(result) || "losses".equalsIgnoreCase(result) || "lose".equalsIgnoreCase(result)) {
                if (!win) output.add(dto);
            } else output.add(dto);
        }
        return output;
    }

    @Transactional(readOnly = true)
    public TresetaGameHistoryDTO getGameHistory(UUID id) {
        var game = repository.findById(id).orElse(null);
        if (game == null) return null;
        var points = points(game);
        var rounds = new ArrayList<TresetaGameHistoryDTO.TresetaRoundHistoryDTO>();
        for (var round : game.rounds()) {
            var plays = new ArrayList<TresetaGameHistoryDTO.TresetaCardPlayHistoryDTO>();
            for (var play : round.plays())
                plays.add(new TresetaGameHistoryDTO.TresetaCardPlayHistoryDTO(play.order(), player(play.player()), card(play.card())));
            var value = Integer.parseInt(round.attributes().getOrDefault("points", "0"));
            add(game, points, round.winner(), value);
            var hands = new LinkedHashMap<GamePlayerDTO, List<GameCardDTO>>();
            for (var hand : round.startingHands()) {
                var cards = new ArrayList<GameCardDTO>();
                for (var recordedCard : hand.cards()) cards.add(card(recordedCard));
                hands.put(player(hand.player()), cards);
            }
            rounds.add(new TresetaGameHistoryDTO.TresetaRoundHistoryDTO(round.order(), hands, plays,
                    player(round.winner()), value, pointsDto(game, points)));
        }
        return new TresetaGameHistoryDTO(game.id(), game.lobbyId(), game.name(), owner(game), game.createdAt(),
                game.endedAt(), config(game), players(game), teams(game), rounds, pointsDto(game, points), winners(game, points));
    }

    private ShortGameHistoryDTO shortHistory(RecordedTresetaGame game) {
        var points = points(game);
        for (var round : game.rounds()) add(game, points, round.winner(), Integer.parseInt(round.attributes().getOrDefault("points", "0")));
        return new ShortGameHistoryDTO(game.id(), game.lobbyId(), game.name(), GameTypeDTO.Treseta, game.createdAt(),
                game.endedAt(), config(game), players(game), pointsDto(game, points), winners(game, points));
    }

    private Map<Long, Integer> points(RecordedTresetaGame game) {
        var points = new LinkedHashMap<Long, Integer>();
        for (var player : game.players()) points.put(player.id(), 0);
        return points;
    }

    private void add(RecordedTresetaGame game, Map<Long, Integer> points, RecordedPlayer winner, int value) {
        if (winner == null) return;
        points.put(winner.id(), points.getOrDefault(winner.id(), 0) + value);
        if (game.teamsEnabled()) {
            var ids = game.teamUserIds();
            var index = ids.indexOf(winner.id());
            if (index >= 0) {
                var teammate = ids.get(index < 2 ? 1 - index : 5 - index);
                points.put(teammate, points.getOrDefault(teammate, 0) + value);
            }
        }
    }

    private TresetaGameConfigDTO config(RecordedTresetaGame game) {
        var config = TresetaGameConfig.valueOf(game.gameConfig());
        return new TresetaGameConfigDTO(config.getNumberOfPlayers(), config.getCardsInHandNum(),
                game.teamsEnabled(), players(game));
    }

    private List<GamePlayerDTO> players(RecordedTresetaGame game) {
        var players = new ArrayList<GamePlayerDTO>();
        for (var player : game.players()) players.add(player(player));
        return players;
    }

    private List<List<GamePlayerDTO>> teams(RecordedTresetaGame game) {
        var teams = new ArrayList<List<GamePlayerDTO>>();
        if (!game.teamsEnabled()) return teams;
        var players = new HashMap<Long, GamePlayerDTO>();
        for (var player : game.players()) players.put(player.id(), player(player));
        var ids = game.teamUserIds();
        teams.add(List.of(players.get(ids.get(0)), players.get(ids.get(1))));
        teams.add(List.of(players.get(ids.get(2)), players.get(ids.get(3))));
        return teams;
    }

    private Map<GamePlayerDTO, Integer> pointsDto(RecordedTresetaGame game, Map<Long, Integer> points) {
        var output = new LinkedHashMap<GamePlayerDTO, Integer>();
        for (var player : game.players()) output.put(player(player), points.getOrDefault(player.id(), 0));
        return output;
    }

    private List<GamePlayerDTO> winners(RecordedTresetaGame game, Map<Long, Integer> points) {
        var best = points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var winners = new ArrayList<GamePlayerDTO>();
        for (var player : game.players()) if (points.getOrDefault(player.id(), 0) == best) winners.add(player(player));
        return winners;
    }

    private GamePlayerDTO owner(RecordedTresetaGame game) {
        for (var player : game.players()) if (player.id().equals(game.ownerUserId())) return player(player);
        return null;
    }

    private boolean contains(List<GamePlayerDTO> players, Long id) {
        for (var player : players) if (player.getId().equals(id)) return true;
        return false;
    }

    private GamePlayerDTO player(RecordedPlayer player) {
        return player == null ? null : new GamePlayerDTO(player.name(), player.id());
    }

    private GameCardDTO card(RecordedCard card) {
        var value = ItalianCardValue.values()[0];
        for (var candidate : ItalianCardValue.values()) if (candidate.getNumber() == card.number()) value = candidate;
        return GameCardDTO.createCardDTO(new TresetaCard(ItalianCardSuit.valueOf(card.suit()), value));
    }
}

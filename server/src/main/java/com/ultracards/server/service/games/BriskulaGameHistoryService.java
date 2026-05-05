package com.ultracards.server.service.games;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameHistoryDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayingFieldEntity;
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BriskulaGameHistoryService {
    private final BriskulaGameRepository briskulaGameRepository;

    @Transactional(readOnly = true)
    public List<ShortGameHistoryDTO> getPastGames(UserEntity user, int offset, String result, String timeSort) {
        var safeOffset = Math.max(offset, 0);
        var games = "oldest".equalsIgnoreCase(timeSort) || "asc".equalsIgnoreCase(timeSort)
                ? briskulaGameRepository.findPastGamesByUserIdOldest(user.getId(), safeOffset)
                : briskulaGameRepository.findPastGamesByUserIdLatest(user.getId(), safeOffset);
        var history = new ArrayList<ShortGameHistoryDTO>();
        for (var game : games) {
            var shortHistory = createShortHistory(game);
            if (matchesResultFilter(shortHistory, user, result)) {
                history.add(shortHistory);
            }
        }
        return history;
    }

    private boolean matchesResultFilter(ShortGameHistoryDTO game, UserEntity user, String result) {
        if ("wins".equalsIgnoreCase(result) || "win".equalsIgnoreCase(result)) {
            return containsPlayer(game.getWinners(), user);
        }
        if ("losses".equalsIgnoreCase(result) || "lose".equalsIgnoreCase(result) || "loss".equalsIgnoreCase(result)) {
            return !containsPlayer(game.getWinners(), user);
        }
        return true;
    }

    private boolean containsPlayer(List<GamePlayerDTO> players, UserEntity user) {
        for (var player : players) {
            if (player.getId().equals(user.getId())) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public BriskulaGameHistoryDTO getGameHistory(UUID gameId) {
        var game = briskulaGameRepository.findHistoryById(gameId).orElse(null);
        if (game == null) return null;

        var playersById = createPlayersById(game);
        var pointsByPlayerId = createInitialPoints(game.getPlayers());
        var rounds = new ArrayList<BriskulaGameHistoryDTO.BriskulaRoundHistoryDTO>();

        for (var playingField : game.getBriskulaPlayingFields()) {
            var round = createRound(playingField, playersById, pointsByPlayerId, game);
            rounds.add(round);
        }

        return new BriskulaGameHistoryDTO(
                game.getId(),
                game.getLobbyId(),
                game.getName(),
                toPlayerDto(game.getOwner()),
                game.getCreatedAt(),
                game.getEndedAt(),
                createConfigDto(game),
                GameCardDTO.createCardDTO(new BriskulaCard(game.getTrumpCardSuit(), game.getTrumpCardValue())),
                toPlayerDtos(game.getPlayers()),
                createTeams(game),
                rounds,
                createPointsDtoMap(game.getPlayers(), pointsByPlayerId),
                determineWinners(game, pointsByPlayerId)
        );
    }

    private ShortGameHistoryDTO createShortHistory(BriskulaGameEntity game) {
        var pointsByPlayerId = calculateFinalPoints(game);
        return new ShortGameHistoryDTO(
                game.getId(),
                game.getLobbyId(),
                game.getName(),
                game.getGameType(),
                game.getCreatedAt(),
                game.getEndedAt(),
                createConfigDto(game),
                toPlayerDtos(game.getPlayers()),
                createPointsDtoMap(game.getPlayers(), pointsByPlayerId),
                determineWinners(game, pointsByPlayerId)
        );
    }

    private Map<Long, Integer> calculateFinalPoints(BriskulaGameEntity game) {
        var pointsByPlayerId = createInitialPoints(game.getPlayers());
        for (var playingField : game.getBriskulaPlayingFields()) {
            addRoundPoints(playingField, game, pointsByPlayerId);
        }
        return pointsByPlayerId;
    }

    private BriskulaGameHistoryDTO.BriskulaRoundHistoryDTO createRound(
            BriskulaPlayingFieldEntity playingField,
            Map<Long, UserEntity> playersById,
            Map<Long, Integer> pointsByPlayerId,
            BriskulaGameEntity game) {
        var cardStrings = splitCsv(playingField.getPlayedCards());
        var playerIdStrings = splitCsv(playingField.getPlayedPlayerIds());
        var plays = new ArrayList<BriskulaGameHistoryDTO.BriskulaCardPlayHistoryDTO>();

        for (int i = 0; i < cardStrings.size(); i++) {
            var player = playersById.get(Long.parseLong(playerIdStrings.get(i)));
            var card = toBriskulaCard(cardStrings.get(i));
            plays.add(new BriskulaGameHistoryDTO.BriskulaCardPlayHistoryDTO(
                    i,
                    toPlayerDto(player),
                    GameCardDTO.createCardDTO(card)
            ));
        }

        addRoundPoints(playingField, game, pointsByPlayerId);

        return new BriskulaGameHistoryDTO.BriskulaRoundHistoryDTO(
                playingField.getFieldOrder(),
                createPlayerHands(playingField.getPlayerHands(), playersById),
                plays,
                toPlayerDto(playingField.getWinner()),
                playingField.getTotalPoints(),
                createPointsDtoMap(game.getPlayers(), pointsByPlayerId)
        );
    }

    private Map<GamePlayerDTO, List<GameCardDTO>> createPlayerHands(String playerHands, Map<Long, UserEntity> playersById) {
        var result = new LinkedHashMap<GamePlayerDTO, List<GameCardDTO>>();
        if (playerHands == null || playerHands.isBlank()) {
            return result;
        }

        var playerHandStrings = playerHands.split("\\|");
        for (var playerHandString : playerHandStrings) {
            var separatorIndex = playerHandString.indexOf(":");
            if (separatorIndex < 0) continue;

            var playerId = Long.parseLong(playerHandString.substring(0, separatorIndex));
            var player = playersById.get(playerId);
            var cards = new ArrayList<GameCardDTO>();
            var cardValues = playerHandString.substring(separatorIndex + 1);
            if (!cardValues.isBlank()) {
                var cardStrings = cardValues.split("-");
                for (var cardString : cardStrings) {
                    cards.add(GameCardDTO.createCardDTO(toBriskulaCard(cardString)));
                }
            }
            result.put(toPlayerDto(player), cards);
        }
        return result;
    }

    private void addRoundPoints(
            BriskulaPlayingFieldEntity playingField,
            BriskulaGameEntity game,
            Map<Long, Integer> pointsByPlayerId) {
        var winner = playingField.getWinner();
        if (winner == null || playingField.getTotalPoints() == null) {
            return;
        }

        addPoints(pointsByPlayerId, winner.getId(), playingField.getTotalPoints());
        if (game.getPersistedGameConfig().areTeamsEnabled()) {
            var teammate = findTeammate(game.getTeamPlayers(), winner);
            if (teammate != null) {
                addPoints(pointsByPlayerId, teammate.getId(), playingField.getTotalPoints());
            }
        }
    }

    private UserEntity findTeammate(List<UserEntity> teamPlayers, UserEntity user) {
        for (int i = 0; i < teamPlayers.size(); i++) {
            if (teamPlayers.get(i).equals(user)) {
                return i < 2 ? teamPlayers.get(1 - i) : teamPlayers.get(5 - i);
            }
        }
        return null;
    }

    private void addPoints(Map<Long, Integer> pointsByPlayerId, Long playerId, Integer points) {
        pointsByPlayerId.put(playerId, pointsByPlayerId.getOrDefault(playerId, 0) + points);
    }

    private Map<Long, Integer> createInitialPoints(List<UserEntity> players) {
        var pointsByPlayerId = new LinkedHashMap<Long, Integer>();
        for (var player : players) {
            pointsByPlayerId.put(player.getId(), 0);
        }
        return pointsByPlayerId;
    }

    private Map<Long, UserEntity> createPlayersById(BriskulaGameEntity game) {
        var playersById = new HashMap<Long, UserEntity>();
        for (var player : game.getPlayers()) {
            playersById.put(player.getId(), player);
        }
        for (var player : game.getTeamPlayers()) {
            playersById.put(player.getId(), player);
        }
        return playersById;
    }

    private BriskulaGameConfigDTO createConfigDto(BriskulaGameEntity game) {
        return new BriskulaGameConfigDTO(
                game.getPersistedGameConfig().getNumberOfPlayers(),
                game.getPersistedGameConfig().getCardsInHandNum(),
                game.getPersistedGameConfig().areTeamsEnabled(),
                game.getPersistedGameConfig().areTeamsEnabled() ? toPlayerDtos(game.getTeamPlayers()) : toPlayerDtos(game.getPlayers())
        );
    }

    private List<List<GamePlayerDTO>> createTeams(BriskulaGameEntity game) {
        var teams = new ArrayList<List<GamePlayerDTO>>();
        if (!game.getPersistedGameConfig().areTeamsEnabled()) {
            return teams;
        }

        var teamPlayers = game.getTeamPlayers();
        teams.add(List.of(toPlayerDto(teamPlayers.get(0)), toPlayerDto(teamPlayers.get(1))));
        teams.add(List.of(toPlayerDto(teamPlayers.get(2)), toPlayerDto(teamPlayers.get(3))));
        return teams;
    }

    private Map<GamePlayerDTO, Integer> createPointsDtoMap(List<UserEntity> players, Map<Long, Integer> pointsByPlayerId) {
        var points = new LinkedHashMap<GamePlayerDTO, Integer>();
        for (var player : players) {
            points.put(toPlayerDto(player), pointsByPlayerId.getOrDefault(player.getId(), 0));
        }
        return points;
    }

    private List<GamePlayerDTO> determineWinners(BriskulaGameEntity game, Map<Long, Integer> pointsByPlayerId) {
        if (game.getPersistedGameConfig().areTeamsEnabled()) {
            return determineTeamWinners(game, pointsByPlayerId);
        }
        return determinePlayerWinners(game.getPlayers(), pointsByPlayerId);
    }

    private List<GamePlayerDTO> determineTeamWinners(BriskulaGameEntity game, Map<Long, Integer> pointsByPlayerId) {
        var teamPlayers = game.getTeamPlayers();
        var team1Points = pointsByPlayerId.getOrDefault(teamPlayers.get(0).getId(), 0);
        var team2Points = pointsByPlayerId.getOrDefault(teamPlayers.get(2).getId(), 0);

        if (team1Points > team2Points) {
            return List.of(toPlayerDto(teamPlayers.get(0)), toPlayerDto(teamPlayers.get(1)));
        }
        if (team2Points > team1Points) {
            return List.of(toPlayerDto(teamPlayers.get(2)), toPlayerDto(teamPlayers.get(3)));
        }
        return toPlayerDtos(teamPlayers);
    }

    private List<GamePlayerDTO> determinePlayerWinners(List<UserEntity> players, Map<Long, Integer> pointsByPlayerId) {
        var highestPoints = Integer.MIN_VALUE;
        for (var player : players) {
            var points = pointsByPlayerId.getOrDefault(player.getId(), 0);
            if (points > highestPoints) {
                highestPoints = points;
            }
        }

        var winners = new ArrayList<GamePlayerDTO>();
        for (var player : players) {
            if (pointsByPlayerId.getOrDefault(player.getId(), 0) == highestPoints) {
                winners.add(toPlayerDto(player));
            }
        }
        return winners;
    }

    private List<String> splitCsv(String value) {
        var result = new ArrayList<String>();
        if (value == null || value.isBlank()) {
            return result;
        }

        var parts = value.split(",");
        result.addAll(Arrays.asList(parts));
        return result;
    }

    private BriskulaCard toBriskulaCard(String conciseCard) {
        ItalianCardSuit cardSuit = null;
        var cardSuitPrefix = conciseCard.substring(0, 1);
        for (var value : ItalianCardSuit.values()) {
            if (value.name().startsWith(cardSuitPrefix)) {
                cardSuit = value;
                break;
            }
        }
        if (cardSuit == null) {
            throw new IllegalArgumentException("Invalid concise card suit: " + conciseCard);
        }

        var cardValueNumber = Integer.parseInt(conciseCard.substring(1));
        ItalianCardValue cardValue = null;
        for (var value : ItalianCardValue.values()) {
            if (value.getNumber() == cardValueNumber) {
                cardValue = value;
                break;
            }
        }
        if (cardValue == null) {
            throw new IllegalArgumentException("Invalid concise card value: " + conciseCard);
        }

        return new BriskulaCard(cardSuit, cardValue);
    }

    private List<GamePlayerDTO> toPlayerDtos(List<UserEntity> users) {
        var players = new ArrayList<GamePlayerDTO>();
        for (var user : users) {
            players.add(toPlayerDto(user));
        }
        return players;
    }

    private GamePlayerDTO toPlayerDto(UserEntity user) {
        if (user == null) {
            return null;
        }
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }
}

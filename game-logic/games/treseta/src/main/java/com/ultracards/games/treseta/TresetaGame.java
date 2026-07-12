package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TresetaGame
        extends AbstractGame<ItalianCardSuit, ItalianCardValue, TresetaCard, TresetaHand, TresetaDeck, TresetaPlayer, TresetaPlayingField> {

    private final TresetaGameConfig gameConfig;
    private int roundNum;

    public TresetaGame(List<TresetaPlayer> tresetaPlayers, TresetaGameConfig gameConfig) {
        super(tresetaPlayers, 40, gameConfig.getCardsInHandNum());
        this.gameConfig = gameConfig;
        if (getNumberOfPlayers() != gameConfig.getNumberOfPlayers()) {
            throw new IllegalArgumentException("Player count does not match the selected Treseta game mode.");
        }
        this.roundNum = gameConfig.getRoundsNum();
    }

    @Override
    public TresetaDeck createDeck(int cardsNum) {
        return new TresetaDeck(getCardsNum(), getCardsInHandNum());
    }

    @Override
    public List<TresetaCard> removeNotNeededCards(TresetaDeck deck, int cardsInHandNum) {
        int excess = deck.getSize() % getNumberOfPlayers();
        return deck.drawXCards(excess);
    }

    @Override
    public TresetaPlayingField createPlayingField() {
        roundNum--;
        return new TresetaPlayingField(getPlayers(), this, roundNum);
    }

    @Override
    public void createPlayersHands(TresetaDeck deck, List<TresetaPlayer> tresetaPlayers) {
        for (var player : tresetaPlayers) {
            player.getHandFromDeck(deck, getCardsInHandNum());
        }
    }

    @Override
    public List<TresetaPlayer> createPlayers() {
        var players = getPlayers();
        if (players == null || players.isEmpty()) {
            var res = new ArrayList<TresetaPlayer>();
            for (int i = 0; i < getNumberOfPlayers(); i++) {
                res.add(new TresetaPlayer("Player #" + (i + 1)));
            }
            return res;
        }
        return players;
    }

    @Override
    public boolean isGameActive() {
        for (var player : getPlayers()) {
            if (!player.getHand().isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void postRoundWinnerDeterminedActions(TresetaPlayer roundWinner, TresetaPlayingField tresetaPlayingField) {
        var winPoints = tresetaPlayingField.calcTotalPoints();
        roundWinner.addPoints(winPoints, tresetaPlayingField.getPlayedCards());
        Collections.rotate(getPlayers(), getPlayers().indexOf(roundWinner) * -1);
        if (gameConfig.areTeamsEnabled()) {
            getPlayers().get(2).addPoints(winPoints, tresetaPlayingField.getPlayedCards()); // winner is now index 0, teammate is index 2
        }
    }

    @Override
    public void drawCards(List<TresetaPlayer> players, TresetaDeck deck) {
        if (gameConfig.equals(TresetaGameConfig.TWO_PLAYERS)) {
            for (var player : players) {
                if (!deck.isEmpty()) player.getHand().addCard(deck.drawCard());
            }
        }
    }

    @Override
    public List<TresetaPlayer> determineGameWinners() {
        var players = getPlayers();
        if (gameConfig.areTeamsEnabled()) {
            var team1Points = players.get(0).getPoints();
            var team2Points = players.get(1).getPoints();
            if (team1Points > team2Points) return List.of(players.get(0), players.get(2));
            if (team2Points > team1Points) return List.of(players.get(1), players.get(3));
            return players;
        }
        var winnerPoints = Integer.MIN_VALUE;
        for (var player : players) winnerPoints = Math.max(winnerPoints, player.getPoints());
        var winners = new ArrayList<TresetaPlayer>();
        for (var player : players) if (player.getPoints() == winnerPoints) winners.add(player);
        return winners;
    }

    @Override
    public void preGameCreateCheck(int numberOfPlayers, int cardsNum) {
        if (numberOfPlayers > 4 || numberOfPlayers < 2)
            throw new IllegalArgumentException("Number of players must be between 2 and 4.");
        if (cardsNum != 40)
            throw new IllegalArgumentException("Number of cards must be 40.");
    }
}

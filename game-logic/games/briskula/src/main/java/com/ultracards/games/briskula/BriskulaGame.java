package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BriskulaGame extends AbstractGame<ItalianCardSuit, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck, BriskulaPlayer, BriskulaPlayingField> {

    private BriskulaCard gameTrumpCard;
    private final boolean areTeamsEnabled;
    private final BriskulaGameConfig gameConfig;

    public BriskulaGame(BriskulaGameConfig gameConfig, List<BriskulaPlayer> players) {
        super(players, 40, gameConfig.getCardsInHandNum());
        areTeamsEnabled = gameConfig.areTeamsEnabled();
        this.gameConfig = gameConfig;
    }

    @Override
    public List<BriskulaCard> removeNotNeededCards(BriskulaDeck deck, int cardsInHandNum) {
        int cardsToRemoveNum = deck.getSize() % getNumberOfPlayers();
        return deck.drawXCards(cardsToRemoveNum);
    }

    @Override
    public void postRoundWinnerDeterminedActions(BriskulaPlayer roundWinner, BriskulaPlayingField briskulaPlayingField) {
        var winPoints = briskulaPlayingField.calcTotalPoints();
        roundWinner.addPoints(winPoints, briskulaPlayingField.getPlayedCards());

        Collections.rotate(getPlayers(), getPlayers().indexOf(roundWinner)*-1);

        if (areTeamsEnabled()) {
            getPlayers().get(2).addPoints(winPoints, briskulaPlayingField.getPlayedCards()); // Because the winning player is now 1st so the 3rd player is his teammate
        }
    }

    @Override
    public BriskulaDeck createDeck(int cardsNum) {
        var deck = new BriskulaDeck(cardsNum);
        var card = deck.drawCard();
        gameTrumpCard = card;
        deck.appendCard(card);
        return deck;
    }

    @Override
    public BriskulaPlayingField createPlayingField() {
        return new BriskulaPlayingField(getPlayers(), this, getGameTrumpCard().getSuit(), gameConfig);
    }

    @Override
    public void preGameCreateCheck(int numberOfPlayers, int cardsNum) {
        if (numberOfPlayers < 2 || numberOfPlayers > 4) {
            throw new IllegalArgumentException("Number of players must be between 2 and 4.");
        }
        if (cardsNum == 0) {
            throw new IllegalArgumentException("Number of cards must be 40.");
        }
    }

    @Override
    public void createPlayersHands(BriskulaDeck deck, List<BriskulaPlayer> briskulaPlayers) {
        for (var player : briskulaPlayers) {
            player.getHandFromDeck(deck, getCardsInHandNum());
        }
    }

    @Override
    public List<BriskulaPlayer> createPlayers() {
        var players = getPlayers();
        if (players == null || players.isEmpty()) {
            var res = new ArrayList<BriskulaPlayer>();

            for (int i = 0; i < getNumberOfPlayers(); i++) {
                res.add(new BriskulaPlayer("Player #" + (i + 1)));
            }

            return res;
        }
        return players;
    }

    @Override
    public boolean isGameActive() {
        for (var player : getPlayers()) {
            if (!player.getHand().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void drawCards(List<BriskulaPlayer> players, BriskulaDeck deck) {
        players.forEach(player -> {
            if (!deck.isEmpty()) player.getHand().addCard(deck.drawCard());
        });
        if (gameConfig.equals(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH)) {
            players.forEach(player -> {
                if (!deck.isEmpty()) player.getHand().addCard(deck.drawCard());
            });
        }
    }

    @Override
    public List<BriskulaPlayer> determineGameWinners() {
        var briskulaPlayers = getPlayers();
        if (areTeamsEnabled()) {
            var team1Points = briskulaPlayers.get(0).getPoints();
            var team2Points = briskulaPlayers.get(1).getPoints();

            if (team1Points > team2Points) {
                return List.of(briskulaPlayers.get(0), briskulaPlayers.get(2)); // 1st and 3rd players are the winners
            } else if (team2Points > team1Points) {
                return List.of(briskulaPlayers.get(1), briskulaPlayers.get(3)); // 2nd and 4th players are the winners
            } else {
                return briskulaPlayers;
            }
        }
        if (120 / getNumberOfPlayers() == briskulaPlayers.get(0).getPoints()) {
            return briskulaPlayers;
        }
        return List.of(briskulaPlayers.stream().max(
                Comparator.comparingInt(BriskulaPlayer::getPoints)
        ).orElseThrow(() -> new IllegalStateException("No players found")));
    }


    public BriskulaCard getGameTrumpCard() {
        return gameTrumpCard;
    }

    public boolean areTeamsEnabled() {
        return areTeamsEnabled;
    }
}

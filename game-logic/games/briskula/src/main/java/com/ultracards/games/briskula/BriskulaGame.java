package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractGame;

import java.util.List;

public class BriskulaGame extends AbstractGame<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck, BriskulaPlayer, BriskulaPlayingField> {

    public BriskulaGame(int numberOfPlayers, int cardsNum, int cardsInHandNum) {
        super(numberOfPlayers, cardsNum, cardsInHandNum);
    }

    @Override
    public BriskulaDeck createDeck(int cardsNum) {
        return null; // TODO: implement this method
    }

    @Override
    public BriskulaPlayingField createPlayingField() {
        return null; // TODO: implement this method
    }

    @Override
    public void playTurn(BriskulaPlayingField briskulaPlayingField, List<BriskulaPlayer> briskulaPlayers) {
        // TODO: implement this method
    }

    @Override
    public void preGameCreateCheck(int numberOfPlayers, int cardsNum) {
        // TODO: implement this method
    }

    @Override
    public void createPlayersHands(BriskulaDeck deck, List<BriskulaPlayer> briskulaPlayers) {
        // TODO: implement this method
    }

    @Override
    public List<BriskulaPlayer> createPlayers() {
        return List.of(); // TODO: implement this method
    }

    @Override
    public boolean isGameActive(BriskulaDeck deck, List<BriskulaPlayer> briskulaPlayers) {
        return false; // TODO: implement this method
    }
}

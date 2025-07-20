package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

public class BriskulaPlayer extends AbstractPlayer<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck> {
    private int points;
    private final List<BriskulaCard> wonCards;

    public BriskulaPlayer(String name) {
        super(name);
        wonCards = new ArrayList<BriskulaCard>();
    }

    public void addPoints(int points, List<BriskulaCard> wonCards) {
        this.points += points;
        this.wonCards.addAll(wonCards);
    }

    public int getPoints() {
        return points;
    }

    public List<BriskulaCard> getWonCards() {
        return wonCards;
    }
}

package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayer;

public class BriskulaPlayer extends AbstractPlayer<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck> {
    private int points;

    public BriskulaPlayer(String name) {
        super(name);
    }

    public void addPoints(int points) {
        this.points += points;
    }

    public int getPoints() {
        return points;
    }
}

package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

public class TresetaPlayer extends AbstractPlayer<ItalianCardSuit, ItalianCardValue, TresetaCard, TresetaHand, TresetaDeck> {
    private int points = 0;
    private final List<TresetaCard> wonCards = new ArrayList<>();

    public TresetaPlayer(String name) {
        super(name);
    }

    public void addPoints(int points, List<TresetaCard> wonCards) {
        this.points += points;
        getWonCards().addAll(wonCards);
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public List<TresetaCard> getWonCards() {
        return wonCards;
    }
}

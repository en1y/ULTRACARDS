package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

public class TresetaPlayer extends AbstractPlayer<ItalianCardSuit, ItalianCardValue, TresetaCard, TresetaHand, TresetaDeck> {
    private int points = 0;
    private final List<TresetaCard> wonCards = new ArrayList<>();
    private final List<TresetaDeclaration> declarations = new ArrayList<>();
    private int cardsPlayedNum = 0;

    public TresetaPlayer(String name) {
        super(name);
    }

    @Override
    public TresetaCard playCard(TresetaCard card) {
        var played = super.playCard(card);
        cardsPlayedNum++;
        return played;
    }

    public int declare(TresetaDeclaration declaration) {
        if (cardsPlayedNum > 0)
            throw new IllegalArgumentException("Declarations are only allowed before playing your first card.");
        var replacedPoints = 0;
        for (var existing : declarations) {
            if (existing.equals(declaration))
                throw new IllegalArgumentException("The exact same declaration can not be repeated.");
            if (existing.type() == declaration.type() && existing.suits().size() != declaration.suits().size()) {
                if (existing.suits().size() == 4)
                    throw new IllegalArgumentException("A four card declaration can not be changed back to three cards.");
                replacedPoints += existing.getPoints();
            }
        }
        for (var card : declaration.getCards())
            if (!getHand().getCards().contains(card))
                throw new IllegalArgumentException("Every declared card must be in the player's hand.");
        if (replacedPoints > 0)
            declarations.removeIf(existing -> existing.type() == declaration.type() && existing.suits().size() == 3);
        declarations.add(declaration);
        return declaration.getPoints() - replacedPoints;
    }

    public List<TresetaDeclaration> getDeclarations() {
        return declarations;
    }

    public boolean canDeclare() {
        return cardsPlayedNum == 0;
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

package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.cards.CardFactoryInterface;

import java.util.ArrayList;
import java.util.List;

public class TresetaCardFactory implements CardFactoryInterface<ItalianCardSuit, ItalianCardValue, TresetaCard> {
    @Override
    public List<TresetaCard> getCards() {
        var res = new ArrayList<TresetaCard>();
        for (var type : ItalianCardSuit.values()) {
            for (var value : ItalianCardValue.values()) {
                res.add(new TresetaCard(type, value));
            }
        }
        return res;
    }
}

package com.ultracards.cards;

import com.ultracards.templates.cards.CardFactoryInterface;

import java.util.ArrayList;
import java.util.List;

public class ItalianCardFactory implements CardFactoryInterface<ItalianCardType, ItalianCardValue, ItalianCard> {
    @Override
    public List<ItalianCard> getCards() {
        var res = new ArrayList<ItalianCard>();

        for (ItalianCardType type : ItalianCardType.values()) {
            for (ItalianCardValue value : ItalianCardValue.values()) {
                res.add(new ItalianCard(type, value));
            }
        }

        return res;
    }
}

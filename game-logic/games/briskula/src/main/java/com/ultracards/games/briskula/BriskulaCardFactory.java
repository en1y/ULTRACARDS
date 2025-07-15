package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.cards.CardFactoryInterface;

import java.util.ArrayList;
import java.util.List;

public class BriskulaCardFactory implements CardFactoryInterface<ItalianCardType, ItalianCardValue, BriskulaCard> {
    @Override
    public List<BriskulaCard> getCards() {
        var res = new ArrayList<BriskulaCard>();
        for (ItalianCardType type : ItalianCardType.values()) {
            for (ItalianCardValue value : ItalianCardValue.values()) {
                res.add(new BriskulaCard(type, value));
            }
        }
        return res;
    }
}

package com.ultracards.cardtopng;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.PokerCard;
import com.ultracards.cards.PokerCardType;
import com.ultracards.templates.cards.AbstractCard;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Objects;

public class CardToPngConverter {
    public static CardPictureDTO convert(AbstractCard card) throws IOException {
        if (card.getClass().equals(ItalianCard.class)) {
            return convertItalianCardToPng((ItalianCard) card);
        }
        if (card.getClass().equals(PokerCard.class)) {
            return convertPokerCardToPng((PokerCard) card);
        } else {
            throw new IllegalArgumentException("Card type not supported");
        }
    }

    private static CardPictureDTO convertPokerCardToPng(PokerCard card) throws IOException {
        return new CardPictureDTO(
                ImageIO.read(Objects.requireNonNull(
                        CardToPngConverter.class.getResourceAsStream(
                                "/images/poker-cards/face/" + card.getValue().getNumber() + ((PokerCardType)card.getType()).name().charAt(0) + ".png"))),
                ImageIO.read(Objects.requireNonNull(
                        CardToPngConverter.class.getResourceAsStream(
                                "/images/poker-cards/face/modiano.png"))));
    }

    private static CardPictureDTO convertItalianCardToPng(ItalianCard card) throws IOException {
        return new CardPictureDTO(
                ImageIO.read(Objects.requireNonNull(
                        CardToPngConverter.class.getResourceAsStream(
                                "/images/italian-cards/face/" + card.getValue().getNumber() + ((ItalianCardType)card.getType()).name().charAt(0) + ".png"))),
                ImageIO.read(Objects.requireNonNull(
                        CardToPngConverter.class.getResourceAsStream(
                                "/images/italian-cards/back/modiano.png"))));
    }
}

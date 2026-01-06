package com.ultracards.cardtopng;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.PokerCard;
import com.ultracards.cards.PokerCardSuit;
import com.ultracards.templates.cards.AbstractCard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class CardToPngConverter {

    public static BufferedImage getItalianCardBack() throws IOException{
        return ImageIO.read(Objects.requireNonNull(
                CardToPngConverter.class.getResourceAsStream(
                        "/images/italian-cards/back/modiano.png")));
    }
    public static BufferedImage convert(AbstractCard<?,?,?> card) throws IOException {
        if (card instanceof ItalianCard) {
            return convertItalianCardToPng((ItalianCard<?>) card);
        }
        if (card instanceof PokerCard) {
            return convertPokerCardToPng((PokerCard<?>) card);
        }
        throw new IllegalArgumentException("Card type not supported");
    }

    private static BufferedImage convertPokerCardToPng(PokerCard<?> card) throws IOException {
        return ImageIO.read(Objects.requireNonNull(
                        CardToPngConverter.class.getResourceAsStream(
                                "/images/poker-cards/face/" + card.getSuit().name().charAt(0) + card.getValue().getNumber() + ".png")));
    }

    private static BufferedImage convertItalianCardToPng(ItalianCard<?> card) throws IOException {
        return ImageIO.read(Objects.requireNonNull(
                        CardToPngConverter.class.getResourceAsStream(
                                "/images/italian-cards/face/" + card.getValue().getNumber() + (card.getSuit()).name().charAt(0) + ".png")));
    }

    public static BufferedImage getPokerBack() throws IOException{
        return ImageIO.read(Objects.requireNonNull(
                CardToPngConverter.class.getResourceAsStream(
                        "/images/poker-cards/back/purple.png")));
    }
}

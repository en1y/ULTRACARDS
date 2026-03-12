package com.ultracards.server.service.cards;

import com.ultracards.cards.*;
import com.ultracards.cardtopng.CardPictureDTO;
import com.ultracards.cardtopng.CardToPngConverter;
import com.ultracards.games.briskula.BriskulaCard;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CardImageService {

    private final Map<String, byte[]> faceCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> backCache = new ConcurrentHashMap<>();

    public byte[] italianCardFace(String suit, String value) {
        var key = suit.toUpperCase() + ":" + value.toUpperCase();
        var face = faceCache.get(key);
        if (face != null) {
            return face;
        }
        try {
            var card = new ItalianCard<>(
                    ItalianCardSuit.valueOf(suit.toUpperCase()),
                    ItalianCardValue.valueOf(value.toUpperCase())
            );
            face = toPngBytes(CardToPngConverter.convert(card));
            faceCache.put(key, face);
            return face;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown italian card: " + suit + "-" + value, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load card face", ex);
        }
    }

    public byte[] italianCardBack() {
        try {
            if (backCache.containsKey("Italian")) {
                return backCache.get("Italian");
            }
            var back = toPngBytes(CardToPngConverter.getItalianCardBack());
            backCache.put("Italian", back);
            return back;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load card back", ex);
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public byte[] pokerCardFace(String suit, String value) {
        var key = suit.toUpperCase() + ":" + value.toUpperCase();
        var face = faceCache.get(key);
        if (face != null) {
            return face;
        }
        try {
            var card = new PokerCard<>(
                    PokerCardSuit.valueOf(suit.toUpperCase()),
                    PokerCardValue.valueOf(value.toUpperCase())
            );
            face = toPngBytes(CardToPngConverter.convert(card));
            faceCache.put(key, face);
            return face;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown poker card: " + suit + "-" + value, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load card face", ex);
        }
    }

    public byte[] pokerCardBack() {
        try {
            if (backCache.containsKey("Poker")) {
                return backCache.get("Poker");
            }
            var back = toPngBytes(CardToPngConverter.getPokerBack());
            backCache.put("Poker", back);
            return back;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load card back", ex);
        }
    }
}

package com.ultracards.server.service.cards;

import com.ultracards.cardtopng.CardPictureDTO;
import com.ultracards.cardtopng.CardToPngConverter;
import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.games.briskula.BriskulaCard;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CardImageService {

    private final Map<String, String> faceCache = new ConcurrentHashMap<>();
    private final Map<String, String> backCache = new ConcurrentHashMap<>();

    public CardImagePayload italianCard(String suit, String value) {
        var key = suit.toUpperCase() + ":" + value.toUpperCase();
        var face = faceCache.get(key);
        var back = backCache.get(key);
        if (face != null && back != null) {
            return new CardImagePayload(face, back);
        }
        try {
            var card = new BriskulaCard(
                    ItalianCardType.valueOf(suit.toUpperCase()),
                    ItalianCardValue.valueOf(value.toUpperCase())
            );
            CardPictureDTO dto = CardToPngConverter.convert(card);
            if (face == null) {
                face = toDataUri(dto.getFace());
                faceCache.put(key, face);
            }
            if (back == null) {
                back = toDataUri(dto.getBack());
                backCache.put(key, back);
            }
            return new CardImagePayload(face, back);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown italian card: " + suit + "-" + value, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load card art", ex);
        }
    }

    private String toDataUri(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public record CardImagePayload(String face, String back) {}
}

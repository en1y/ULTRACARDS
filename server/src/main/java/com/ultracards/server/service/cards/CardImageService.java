package com.ultracards.server.service.cards;

import com.ultracards.cards.*;
import com.ultracards.cardtopng.CardToPngConverter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CardImageService {

    private static final int MAX_CARD_WIDTH = 300;
    private static final int MAX_CARD_HEIGHT = 546;
    private static final int ZOOM_SCALE = 3;

    private final Map<String, byte[]> faceCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> zoomFaceCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> backCache = new ConcurrentHashMap<>();

    public byte[] italianCardFace(String suit, String value) {
        return italianCardFace(suit, value, false);
    }

    public byte[] italianCardFace(String suit, String value, boolean zoom) {
        var key = suit.toUpperCase() + ":" + value.toUpperCase();
        var cache = zoom ? zoomFaceCache : faceCache;
        var face = cache.get(key);
        if (face != null) {
            return face;
        }
        try {
            var card = new ItalianCard<>(
                    ItalianCardSuit.valueOf(suit.toUpperCase()),
                    ItalianCardValue.valueOf(value.toUpperCase())
            );
            face = toPngBytes(CardToPngConverter.convert(card), zoom);
            cache.put(key, face);
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
        return toPngBytes(image, false);
    }

    private byte[] toPngBytes(BufferedImage image, boolean zoom) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(zoom ? scaleForZoom(image) : scaleForUi(image), "png", baos);
        return baos.toByteArray();
    }

    private BufferedImage scaleForZoom(BufferedImage image) {
        int width = image.getWidth() * ZOOM_SCALE;
        int height = image.getHeight() * ZOOM_SCALE;
        var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var transform = AffineTransform.getScaleInstance(ZOOM_SCALE, ZOOM_SCALE);
        new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC).filter(image, scaled);
        return scaled;
    }

    private BufferedImage scaleForUi(BufferedImage image) {
        double scale = Math.min(1, Math.min(
                (double) MAX_CARD_WIDTH / image.getWidth(),
                (double) MAX_CARD_HEIGHT / image.getHeight()
        ));
        if (scale == 1) {
            return image;
        }
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var transform = AffineTransform.getScaleInstance(
                (double) width / image.getWidth(),
                (double) height / image.getHeight()
        );
        new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR).filter(image, scaled);
        return scaled;
    }

    public byte[] pokerCardFace(String suit, String value) {
        return pokerCardFace(suit, value, false);
    }

    public byte[] pokerCardFace(String suit, String value, boolean zoom) {
        var key = suit.toUpperCase() + ":" + value.toUpperCase();
        var cache = zoom ? zoomFaceCache : faceCache;
        var face = cache.get(key);
        if (face != null) {
            return face;
        }
        try {
            var card = new PokerCard<>(
                    PokerCardSuit.valueOf(suit.toUpperCase()),
                    PokerCardValue.valueOf(value.toUpperCase())
            );
            face = toPngBytes(CardToPngConverter.convert(card), zoom);
            cache.put(key, face);
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

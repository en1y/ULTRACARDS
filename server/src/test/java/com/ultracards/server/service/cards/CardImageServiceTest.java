package com.ultracards.server.service.cards;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CardImageServiceTest {

    @Test
    void scalesLargeCardBacksToTheUiResolution() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(new CardImageService().italianCardBack()));

        assertTrue(image.getWidth() <= 300);
        assertTrue(image.getHeight() <= 546);
    }

    @Test
    void servesBothJokerFacesAlongsideTheSuitedPokerCards() throws Exception {
        var service = new CardImageService();
        for (var suit : new String[]{"RED_JOKER", "BLACK_JOKER"}) {
            var image = ImageIO.read(new ByteArrayInputStream(service.pokerCardFace(suit, "JOKER")));
            assertTrue(image.getWidth() <= 300);
            assertTrue(image.getHeight() <= 546);
        }
        // The suited poker faces are 16-bit RGBA, which the old AffineTransformOp path could not scale.
        var ace = ImageIO.read(new ByteArrayInputStream(service.pokerCardFace("HEARTS", "ACE")));
        assertTrue(ace.getWidth() <= 300);
        assertTrue(ace.getHeight() <= 546);
        var zoomed = ImageIO.read(new ByteArrayInputStream(service.pokerCardFace("SPADES", "TWO", true)));
        assertTrue(zoomed.getWidth() == 750 * 3);
    }

    @Test
    void rendersZoomCardFacesAtTripleResolution() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(
                new CardImageService().italianCardFace("COPPE", "ACE", true)
        ));

        assertTrue(image.getWidth() == 900);
        assertTrue(image.getHeight() == 1638);
    }
}

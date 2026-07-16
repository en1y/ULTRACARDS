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
    void rendersZoomCardFacesAtTripleResolution() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(
                new CardImageService().italianCardFace("COPPE", "ACE", true)
        ));

        assertTrue(image.getWidth() == 900);
        assertTrue(image.getHeight() == 1638);
    }
}

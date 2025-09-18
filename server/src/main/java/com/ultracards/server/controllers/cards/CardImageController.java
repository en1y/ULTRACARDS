package com.ultracards.server.controllers.cards;

import com.ultracards.server.service.cards.CardImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cards")
public class CardImageController {

    private final CardImageService cardImageService;

    public CardImageController(CardImageService cardImageService) {
        this.cardImageService = cardImageService;
    }

    @GetMapping("/italian/{suit}/{value}")
    public ResponseEntity<CardImageService.CardImagePayload> italianCard(
            @PathVariable String suit,
            @PathVariable String value
    ) {
        try {
            return ResponseEntity.ok(cardImageService.italianCard(suit, value));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}

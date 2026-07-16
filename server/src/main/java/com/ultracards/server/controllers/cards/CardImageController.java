package com.ultracards.server.controllers.cards;

import com.ultracards.server.service.cards.CardImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.TimeUnit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cards")
public class CardImageController {

    // Card art is immutable, so let browsers cache it aggressively to save bandwidth.
    private static final CacheControl CARD_CACHE = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic();

    private final CardImageService cardImageService;

    public CardImageController(CardImageService cardImageService) {
        this.cardImageService = cardImageService;
    }

    @GetMapping(value = "/italian/{suit}/{value}", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> italianCard(
            @PathVariable String suit,
            @PathVariable String value,
            @RequestParam(name = "zoom", defaultValue = "false") boolean zoom
    ) {
        try {
            return ResponseEntity.ok().cacheControl(CARD_CACHE).body(cardImageService.italianCardFace(suit, value, zoom));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/italian/back", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> italianCardBack() {
        try {
            return ResponseEntity.ok().cacheControl(CARD_CACHE).body(cardImageService.italianCardBack());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/poker/{suit}/{value}", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> pokerCard(
            @PathVariable String suit,
            @PathVariable String value,
            @RequestParam(name = "zoom", defaultValue = "false") boolean zoom
    ) {
        try {
            return ResponseEntity.ok().cacheControl(CARD_CACHE).body(cardImageService.pokerCardFace(suit, value, zoom));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/poker/back", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> pokerCardBack() {
        try {
            return ResponseEntity.ok().cacheControl(CARD_CACHE).body(cardImageService.pokerCardBack());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}

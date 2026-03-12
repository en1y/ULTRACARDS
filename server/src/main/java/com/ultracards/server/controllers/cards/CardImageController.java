package com.ultracards.server.controllers.cards;

import com.ultracards.server.service.cards.CardImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping(value = "/italian/{suit}/{value}", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> italianCard(
            @PathVariable String suit,
            @PathVariable String value
    ) {
        try {
            return ResponseEntity.ok(cardImageService.italianCardFace(suit, value));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/italian/back", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> italianCardBack() {
        try {
            return ResponseEntity.ok(cardImageService.italianCardBack());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/poker/{suit}/{value}", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> pokerCard(
            @PathVariable String suit,
            @PathVariable String value
    ) {
        try {
            return ResponseEntity.ok(cardImageService.pokerCardFace(suit, value));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/poker/back", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<byte[]> pokerCardBack() {
        try {
            return ResponseEntity.ok(cardImageService.pokerCardBack());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}

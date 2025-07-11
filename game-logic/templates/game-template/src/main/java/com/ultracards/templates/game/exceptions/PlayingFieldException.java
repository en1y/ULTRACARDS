package com.ultracards.templates.game.exceptions;

public class PlayingFieldException extends RuntimeException {
    public PlayingFieldException(String message, Object ... args) {
        super(String.format(message, args));
    }
}

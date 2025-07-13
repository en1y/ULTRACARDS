package com.ultracards.templates.game.exceptions;

public class HandException extends RuntimeException {
    public HandException(String message, Object ... args) {
        super(String.format(message, args));
    }
}

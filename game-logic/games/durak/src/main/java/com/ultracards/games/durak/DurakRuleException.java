package com.ultracards.games.durak;

/**
 * Thrown for every rejected Durak action or invalid configuration.
 * Carries a stable {@link DurakErrorCode} so callers never parse messages.
 */
public class DurakRuleException extends RuntimeException {
    private final DurakErrorCode code;

    public DurakRuleException(DurakErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DurakRuleException(DurakErrorCode code, String format, Object... args) {
        super(String.format(format, args));
        this.code = code;
    }

    public DurakErrorCode getCode() {
        return code;
    }
}

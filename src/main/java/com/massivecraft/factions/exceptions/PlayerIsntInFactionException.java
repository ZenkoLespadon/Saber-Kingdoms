package com.massivecraft.factions.exceptions;

public class NoFactionException extends RuntimeException {
    public NoFactionException(String message) {
        super(message);
    }

    public NoFactionException(String message, Throwable cause) {
        super(message, cause);
    }
}

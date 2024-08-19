package com.massivecraft.factions.exceptions;

public class PlayerIsntInFactionException extends RuntimeException {
    public PlayerIsntInFactionException(String message) {
        super(message);
    }

    public PlayerIsntInFactionException(String message, Throwable cause) {
        super(message, cause);
    }
}

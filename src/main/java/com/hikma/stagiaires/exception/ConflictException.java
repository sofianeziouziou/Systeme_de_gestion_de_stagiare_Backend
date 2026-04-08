package com.hikma.stagiaires.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException emailDejaUtilise(String email) {
        return new ConflictException("L'adresse email " + email + " est déjà utilisée.");
    }
}
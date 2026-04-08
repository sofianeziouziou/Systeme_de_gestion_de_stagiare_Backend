package com.hikma.stagiaires.exception;

import lombok.Getter;

/**
 * Accès interdit avec code métier → HTTP 403
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ForbiddenException roleInsuffisant() {
        return new ForbiddenException("ROLE_INSUFFICIENT",
                "Vous n'avez pas le rôle nécessaire pour cette action.");
    }

    public static ForbiddenException pasVotreStagiaire() {
        return new ForbiddenException("NOT_YOUR_STAGIAIRE",
                "Ce stagiaire n'est pas assigné à votre compte tuteur.");
    }

    public static ForbiddenException pasVotreProjet() {
        return new ForbiddenException("NOT_YOUR_PROJET",
                "Ce projet ne vous est pas assigné.");
    }
}
















































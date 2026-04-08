package com.hikma.stagiaires.exception;

/**
 * Ressource introuvable → HTTP 404
 * Utilisation : throw new ResourceNotFoundException("Stagiaire", id);
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String id) {
        super(resource + " introuvable avec l'identifiant : " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

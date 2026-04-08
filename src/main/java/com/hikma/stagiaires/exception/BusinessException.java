// src/main/java/com/hikma/sims/exception/BusinessException.java
package com.hikma.stagiaires.exception;

import lombok.Getter;

/**
 * Exception métier générique avec un code machine lisible par le frontend.
 * Utilisation : throw new BusinessException("STAGIAIRE_ALREADY_ASSIGNED", "Ce stagiaire est déjà assigné.");
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    // ── Codes métier SIMS ─────────────────────────────────────────────────
    // Centraliser ici évite les magic strings dispersées dans les services

    public static BusinessException stagiaireDejaAssigne() {
        return new BusinessException("STAGIAIRE_ALREADY_ASSIGNED",
                "Ce stagiaire est déjà assigné à un tuteur.");
    }

    public static BusinessException evaluationDejaExistante() {
        return new BusinessException("EVALUATION_ALREADY_EXISTS",
                "Une évaluation existe déjà pour ce stagiaire sur ce projet.");
    }

    public static BusinessException otpInvalide() {
        return new BusinessException("OTP_INVALID",
                "Code OTP invalide ou expiré.");
    }

    public static BusinessException otpExpire() {
        return new BusinessException("OTP_EXPIRED",
                "Le code OTP a expiré. Demandez-en un nouveau.");
    }

    public static BusinessException compteEnAttente() {
        return new BusinessException("ACCOUNT_PENDING",
                "Votre compte est en attente d'approbation par le RH.");
    }

    public static BusinessException compteRefuse() {
        return new BusinessException("ACCOUNT_REFUSED",
                "Votre compte a été refusé. Contactez le département RH.");
    }

    public static BusinessException projetDejaTermine() {
        return new BusinessException("PROJET_ALREADY_DONE",
                "Ce projet est déjà marqué comme terminé.");
    }

    public static BusinessException progressionInvalide(int value) {
        return new BusinessException("INVALID_PROGRESS",
                "La progression doit être entre 0 et 100, reçu : " + value);
    }

    public static BusinessException fichierTypeInvalide(String type) {
        return new BusinessException("INVALID_FILE_TYPE",
                "Type de fichier non autorisé : " + type + ". Seuls les PDF sont acceptés.");
    }
}
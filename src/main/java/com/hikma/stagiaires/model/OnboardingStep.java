package com.hikma.stagiaires.model;

/**
 * Étapes du profil onboarding stagiaire.
 * L'ordre est intentionnel — chaque étape débloque la suivante.
 */
public enum OnboardingStep {
    INFOS_PERSONNELLES,   // Étape 1 : nom, prénom, téléphone, bio
    FORMATION,            // Étape 2 : école, filière, niveau, compétences
    DOCUMENTS,            // Étape 3 : upload CV (PDF)
    CONFIRMATION          // Étape 4 : récap + validation finale
}
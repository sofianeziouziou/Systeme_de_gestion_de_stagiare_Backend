package com.hikma.stagiaires.dto.onboarding;

import com.hikma.stagiaires.model.CvData;
import com.hikma.stagiaires.model.EducationLevel;
import com.hikma.stagiaires.model.OnboardingStep;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class OnboardingDTOs {

    // ── Ce que le frontend envoie ─────────────────────────────────────────
    @Data
    public static class OnboardingStepRequest {

        private OnboardingStep step; // INFOS_PERSONNELLES / FORMATION / DOCUMENTS / CONFIRMATION

        // Étape 1
        private String firstName;
        private String lastName;
        private String phone;
        private String bio;

        // Étape 2
        private String         school;
        private String         fieldOfStudy;
        private EducationLevel level;
        private String         departement;
        private List<String>   technicalSkills;
        private List<String>   softSkills;
    }

    // ── Ce que le backend retourne ────────────────────────────────────────
    @Data
    @Builder
    public static class OnboardingStatusResponse {
        private boolean        profileCompleted;
        private OnboardingStep currentStep;
        private List<String>   missingFields;
        private int            completionScore;
        private boolean        cvUploaded;
        private CvData         cvAnalysis;
    }
}
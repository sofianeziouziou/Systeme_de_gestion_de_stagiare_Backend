package com.hikma.stagiaires.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stagiaires")
public class Stagiaire {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @Indexed(unique = true)
    private String email;

    private String phone;
    private String photoUrl;

    private String school;
    private String fieldOfStudy;
    private EducationLevel level;

    private String departement;
    private String tuteurId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationMonths;

    @Builder.Default
    private List<String> technicalSkills = List.of();

    @Builder.Default
    private List<String> softSkills = List.of();

    private String cvUrl;
    private String bio;

    // ── NOUVEAUX CHAMPS SPRINT 3 ──────────────────────────────────────
    @Builder.Default
    private boolean profileCompleted = false;

    private OnboardingStep currentStep;

    @Builder.Default
    private List<String> missingFields = List.of();

    private CvData cvAnalysis;
    // ──────────────────────────────────────────────────────────────────

    @Builder.Default
    private Double globalScore = 0.0;

    private Badge badge;

    @Builder.Default
    private List<ScoreHistory> scoreHistory = List.of();

    @Builder.Default
    private StagiaireStatus status = StagiaireStatus.EN_COURS;

    @Builder.Default
    private boolean deleted = false;

    private String userId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScoreHistory {
        private Double score;
        private LocalDateTime date;
        private String evaluationId;
    }
}
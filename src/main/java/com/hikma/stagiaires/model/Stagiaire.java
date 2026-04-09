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

    // Informations personnelles
    private String firstName;
    private String lastName;

    @Indexed(unique = true)
    private String email;

    private String phone;
    private String photoUrl;

    // Informations académiques
    private String school;
    private String fieldOfStudy;
    private EducationLevel level;

    // Informations de stage
    // NOTE: garde String pour compatibilité MongoDB (données existantes)
    // La conversion String→Departement se fait dans StagiaireService
    private String departement;
    private String tuteurId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationMonths;

    // Compétences
    @Builder.Default
    private List<String> technicalSkills = List.of();

    @Builder.Default
    private List<String> softSkills = List.of();

    // Documents
    private String cvUrl;
    private String bio;


    /** NOUVEAU — analyse CV (préparation F4) */
    private CvData cvAnalysis;

    // Score et évaluation
    @Builder.Default
    private Double globalScore = 0.0;

    private Badge badge;

    @Builder.Default
    private List<ScoreHistory> scoreHistory = List.of();

    // Statut
    @Builder.Default
    private StagiaireStatus status = StagiaireStatus.EN_COURS;
    

    @Builder.Default
    private boolean deleted = false;

    // Référence utilisateur (pour login)
    private String userId;

    /** NOUVEAU — onboarding F2 */
    @Builder.Default
    private boolean profileCompleted = false;

    private List<String> missingFields;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Classe interne pour l'historique du score
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScoreHistory {
        private Double score;
        private LocalDateTime date;
        private String evaluationId;
    }
}
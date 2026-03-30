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
    private String school;           // École / Université
    private String fieldOfStudy;     // Filière
    private EducationLevel level;    // Licence / Master / Ingénieur

    // Informations de stage
    private String departement;      // IT, Finance, Marketing, Production, Qualité...
    private String tuteurId;         // Référence vers User (tuteur)
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationMonths;

    // Compétences
    @Builder.Default
    private List<String> technicalSkills = List.of();   // tags dynamiques

    @Builder.Default
    private List<String> softSkills = List.of();

    // Documents
    private String cvUrl;// URL vers S3/MinIO
    private String bio;

    // Score et évaluation
    @Builder.Default
    private Double globalScore = 0.0;

    private Badge badge;             // EXCELLENCE, TRES_BIEN, BIEN, A_SURVEILLER

    @Builder.Default
    private List<ScoreHistory> scoreHistory = List.of();

    // Statut
    @Builder.Default
    private StagiaireStatus status = StagiaireStatus.EN_COURS;

    @Builder.Default
    private boolean deleted = false; // soft delete

    // Référence utilisateur (pour login)
    private String userId;

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
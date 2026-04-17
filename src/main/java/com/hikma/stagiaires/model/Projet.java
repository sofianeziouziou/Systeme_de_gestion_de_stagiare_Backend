// DESTINATION : src/main/java/com/hikma/stagiaires/model/Projet.java
package com.hikma.stagiaires.model;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "projets")
public class Projet {

    @Id private String id;
    private String title;
    private String description;

    private List<String> stagiaireIds;   // contient des userId
    private String tuteurId;

    private LocalDate startDate;
    private LocalDate plannedEndDate;
    private LocalDate actualEndDate;

    private String departement;

    @Builder.Default private Integer    progress   = 0;
    @Builder.Default private List<Sprint> sprints  = List.of();
    @Builder.Default private ProjetStatus status   = ProjetStatus.EN_COURS;
    @Builder.Default private List<String> technologies = List.of();

    // ── NOUVEAU : statut d'acceptation tuteur ─────────────────────────────
    // PENDING  = en attente de réponse du tuteur (état initial)
    // ACCEPTED = tuteur a accepté → projet actif
    // REFUSED  = tuteur a refusé → RH doit choisir un autre tuteur
    @Builder.Default
    private TuteurAcceptation tuteurAcceptation = TuteurAcceptation.PENDING;

    // Raison du refus (optionnel, renseigné par le tuteur)
    private String tuteurRefusRaison;

    private String reportUrl;
    private LocalDate reportSubmittedAt;

    @Builder.Default private boolean deleted = false;

    @CreatedDate  private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Sprint {
        private String id;
        private String title;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private String stagiaireId;
        @Builder.Default
        private String status = "EN_COURS";
    }

    // ── Enum acceptation ──────────────────────────────────────────────────
    public enum TuteurAcceptation {
        PENDING,   // En attente
        ACCEPTED,  // Accepté par le tuteur
        REFUSED    // Refusé par le tuteur
    }
}
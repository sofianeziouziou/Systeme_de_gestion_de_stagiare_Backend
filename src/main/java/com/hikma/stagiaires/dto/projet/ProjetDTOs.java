// DESTINATION : src/main/java/com/hikma/stagiaires/dto/projet/ProjetDTOs.java
package com.hikma.stagiaires.dto.projet;

import com.hikma.stagiaires.model.ProjetStatus;
import com.hikma.stagiaires.model.Projet.TuteurAcceptation;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ProjetDTOs {

    // ── Créer un projet (RH) ──────────────────────────────────────────────
    @Data
    public static class CreateRequest {
        @NotBlank @Size(max = 100)
        private String title;
        private String description;

        @NotBlank(message = "Le tuteur est obligatoire")
        private String tuteurId;

        @NotEmpty(message = "Au moins un stagiaire requis")
        private List<String> stagiaireIds;   // userId

        @NotNull private LocalDate startDate;
        @NotNull private LocalDate plannedEndDate;
        private List<String> technologies;
        private String departement;
    }

    // ── Modifier un projet (RH) ───────────────────────────────────────────
    @Data
    public static class UpdateRequest {
        @Size(max = 100) private String title;
        private String description;
        private String tuteurId;
        private List<String> stagiaireIds;
        private LocalDate startDate;
        private LocalDate plannedEndDate;
        private LocalDate actualEndDate;
        private Integer progress;
        private ProjetStatus status;
        private List<String> technologies;
        private List<SprintRequest> sprints;
        private String departement;
    }

    // ── NOUVEAU : Tuteur accepte le projet ────────────────────────────────
    @Data
    public static class AcceptRequest {
        // Pas de champ requis — l'acceptation ne nécessite pas de commentaire
        // On garde le DTO pour extensibilité future (ex: message de bienvenue)
        private String message;
    }

    // ── NOUVEAU : Tuteur refuse le projet ─────────────────────────────────
    @Data
    public static class RefuseRequest {
        // Raison du refus — optionnelle mais recommandée
        private String raison;
    }

    // ── NOUVEAU : RH réassigne un tuteur après refus ─────────────────────
    @Data
    public static class ReassignTuteurRequest {
        @NotBlank(message = "Le nouveau tuteur est obligatoire")
        private String tuteurId;
    }

    // ── Sprint ────────────────────────────────────────────────────────────
    @Data
    public static class SprintRequest {
        private String id;
        @NotBlank private String title;
        private String description;
        @NotNull private LocalDate startDate;
        @NotNull private LocalDate endDate;
        private String stagiaireId;
        private String status;
    }

    // ── Sprint réponse ────────────────────────────────────────────────────
    @Data
    public static class SprintResponse {
        private String id;
        private String title;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;
        private boolean overdue;
        private String stagiaireId;
        private String stagiaireName;
    }

    // ── Réponse projet complète ───────────────────────────────────────────
    @Data
    public static class ProjetResponse {
        private String id;
        private String title;
        private String description;
        private List<String> stagiaireIds;
        private List<StagiaireInfo> stagiaires;
        private String tuteurId;
        private String tuteurName;
        private LocalDate startDate;
        private LocalDate plannedEndDate;
        private LocalDate actualEndDate;
        private Integer progress;
        private ProjetStatus status;
        private List<String> technologies;
        private List<SprintResponse> sprints;
        private String reportUrl;
        private LocalDate reportSubmittedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String departement;

        // ── NOUVEAU : statut acceptation tuteur ───────────────────────────
        private TuteurAcceptation tuteurAcceptation;  // PENDING / ACCEPTED / REFUSED
        private String            tuteurRefusRaison;  // raison si REFUSED

        @Data
        public static class StagiaireInfo {
            private String id;         // userId
            private String firstName;
            private String lastName;
            private String photoUrl;
        }
    }
}
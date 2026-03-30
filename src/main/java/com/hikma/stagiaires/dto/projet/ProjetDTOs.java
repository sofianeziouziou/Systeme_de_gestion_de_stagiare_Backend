package com.hikma.stagiaires.dto.projet;

import com.hikma.stagiaires.model.ProjetStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ProjetDTOs {

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
    }

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
    }

    @Data
    public static class SprintRequest {
        private String id;
        @NotBlank private String title;
        private String description;
        @NotNull private LocalDate startDate;
        @NotNull private LocalDate endDate;
        private String stagiaireId;   // ← userId du stagiaire assigné à ce sprint
        private String status;
    }

    @Data
    public static class SprintResponse {
        private String id;
        private String title;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;
        private boolean overdue;
        private String stagiaireId;    // ← userId
        private String stagiaireName;  // ← prénom + nom résolu
    }

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

        @Data
        public static class StagiaireInfo {
            private String id;         // userId
            private String firstName;
            private String lastName;
            private String photoUrl;
        }
    }
}
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

    @Builder.Default private Integer progress = 0;
    @Builder.Default private List<Sprint> sprints = List.of();
    @Builder.Default private ProjetStatus status = ProjetStatus.EN_COURS;
    @Builder.Default private List<String> technologies = List.of();

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
        private String stagiaireId;      // userId du stagiaire assigné à ce sprint
        @Builder.Default
        private String status = "EN_COURS";
    }
}
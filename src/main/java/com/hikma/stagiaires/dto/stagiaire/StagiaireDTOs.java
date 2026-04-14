package com.hikma.stagiaires.dto.stagiaire;

import com.hikma.stagiaires.model.Badge;
import com.hikma.stagiaires.model.CvData;
import com.hikma.stagiaires.model.EducationLevel;
import com.hikma.stagiaires.model.StagiaireStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class StagiaireDTOs {

    // ─── REQUEST ────────────────────────────────────────────────────────

    @Data
    public static class CreateRequest {
        @NotBlank private String firstName;
        @NotBlank private String lastName;
        @NotBlank @Email private String email;
        private String phone;
        @NotBlank private String school;
        @NotBlank private String fieldOfStudy;
        @NotNull  private EducationLevel level;
        @NotBlank private String departement;
        private String tuteurId;
        @NotNull private LocalDate startDate;
        @NotNull private LocalDate endDate;
        private List<String> technicalSkills;
        private List<String> softSkills;
    }

    @Data
    public static class UpdateRequest {
        private String firstName;
        private String lastName;
        private String phone;
        private String school;
        private String fieldOfStudy;
        private EducationLevel level;
        private String departement;
        private String tuteurId;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> technicalSkills;
        private List<String> softSkills;
        private StagiaireStatus status;
        private String bio;
    }

    @Data
    public static class SearchFilter {
        private String search;
        private String departement;
        private Double minScore;
        private List<String> competences;
        private LocalDate periodeDebut;
        private LocalDate periodeFin;
        private StagiaireStatus projetStatus;
        private EducationLevel level;
        private String school;
        private Boolean badgeExcellence;
        private String tuteurId;
        private int page = 0;
        private int size = 20;
    }

    // ─── RESPONSE ───────────────────────────────────────────────────────

    @Data
    public static class StagiaireResponse {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String photoUrl;
        private String school;
        private String fieldOfStudy;
        private EducationLevel level;
        private String departement;
        private String tuteurId;
        private String tuteurName;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer durationMonths;
        private List<String> technicalSkills;
        private List<String> softSkills;
        private String cvUrl;
        private Double globalScore;
        private Badge badge;
        private StagiaireStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String bio;

        // ── NOUVEAUX CHAMPS SPRINT 3 ──────────────────────────────────
        private boolean      profileCompleted;
        private String       currentStep;
        private List<String> missingFields;
        private CvData       cvAnalysis;
        // ─────────────────────────────────────────────────────────────
    }

    @Data
    public static class StagiaireSummary {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String photoUrl;
        private String departement;
        private Double globalScore;
        private Badge badge;
        private StagiaireStatus status;
    }

    @Data
    public static class PagedResponse {
        private List<StagiaireResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
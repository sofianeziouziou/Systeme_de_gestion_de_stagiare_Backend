// DESTINATION : src/main/java/com/hikma/stagiaires/dto/search/SearchDTOs.java
// ACTION      : CRÉER ce fichier (nouveau)
// EXPLICATION : DTOs pour la requête de recherche et la réponse avec score

package com.hikma.stagiaires.dto.search;

import com.hikma.stagiaires.model.EducationLevel;
import lombok.Data;

import java.util.List;

public class SearchDTOs {

    // ── Requête de recherche ──────────────────────────────────────────────
    @Data
    public static class SearchRequest {
        private List<String> competences;       // compétences recherchées
        private String       departement;       // filtre département
        private EducationLevel level;           // filtre niveau études
        private Double       scoreMinimum;      // score global minimum
        private boolean      avecCvSeulement;  // uniquement stagiaires avec CV
        private int          page = 0;
        private int          size = 20;
    }

    // ── Résultat avec score de matching ──────────────────────────────────
    @Data
    public static class SearchResult {
        private String       stagiaireId;
        private String       firstName;
        private String       lastName;
        private String       email;
        private String       departement;
        private String       photoUrl;
        private String       cvUrl;
        private String       school;
        private String       level;
        private List<String> technicalSkills;
        private List<String> matchedSkills;    // compétences qui matchent
        private List<String> missingSkills;    // compétences manquantes
        private double       matchScore;       // score matching 0–100
        private double       globalScore;      // score évaluations
        private double       finalScore;       // score combiné final
        private String       badge;
        private int          rank;
    }

    // ── Réponse paginée ───────────────────────────────────────────────────
    @Data
    public static class SearchResponse {
        private List<SearchResult> results;
        private int                page;
        private int                size;
        private long               totalElements;
        private int                totalPages;
        private List<String>       competencesRecherchees;
        private List<String>       competencesSuggérees;  // suggestions auto
    }
}
// DESTINATION : src/main/java/com/hikma/stagiaires/controller/SearchController.java
// ACTION      : REMPLACER le fichier complet
// EXPLICATION : Ajout endpoint POST /api/v1/search/contact
//               qui envoie un email au stagiaire

package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.dto.search.SearchDTOs.*;
import com.hikma.stagiaires.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.hikma.stagiaires.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Recherche", description = "Recherche avancée stagiaires par compétences")
public class SearchController {

    private final SearchService searchService;

    // ── Recherche principale ──────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Recherche avancée par compétences avec scoring")
    public ResponseEntity<SearchResponse> search(
            @RequestBody SearchRequest req) {
        return ResponseEntity.ok(searchService.search(req));
    }

    // ── Recherche GET simple ──────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Recherche rapide par compétences (GET)")
    public ResponseEntity<SearchResponse> searchGet(
            @RequestParam(required = false) List<String> competences,
            @RequestParam(required = false) String       departement,
            @RequestParam(defaultValue = "0")  int       page,
            @RequestParam(defaultValue = "20") int       size) {

        SearchRequest req = new SearchRequest();
        req.setCompetences(competences);
        req.setDepartement(departement);
        req.setPage(page);
        req.setSize(size);

        return ResponseEntity.ok(searchService.search(req));
    }

    // ── Suggestions ───────────────────────────────────────────────────────
    @GetMapping("/suggest")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Suggestions de compétences populaires")
    public ResponseEntity<List<String>> suggest(
            @RequestParam(required = false) List<String> exclude) {

        SearchRequest req = new SearchRequest();
        req.setCompetences(exclude);
        req.setPage(0);
        req.setSize(Integer.MAX_VALUE);

        SearchResponse response = searchService.search(req);
        return ResponseEntity.ok(response.getCompetencesSuggérees());
    }

    // ── Score par stagiaire ───────────────────────────────────────────────
    @GetMapping("/stagiaire/{stagiaireId}")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR')")
    @Operation(summary = "Score de matching pour un stagiaire donné")
    public ResponseEntity<SearchResult> getScoreStagiaire(
            @PathVariable String       stagiaireId,
            @RequestParam List<String> competences) {

        SearchRequest req = new SearchRequest();
        req.setCompetences(competences);
        req.setPage(0);
        req.setSize(Integer.MAX_VALUE);

        return searchService.search(req).getResults().stream()
                .filter(r -> stagiaireId.equals(r.getStagiaireId()))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── NOUVEAU : Envoyer email de contact recrutement ────────────────────
    @PostMapping("/contact")
    @PreAuthorize("hasRole('RH')")
    @Operation(
            summary     = "Envoyer un email de contact recrutement à un stagiaire",
            description = "Envoie un email professionnel au stagiaire pour savoir " +
                    "s'il est intéressé par un poste chez Hikma"
    )
    public ResponseEntity<Map<String, String>> contacterStagiaire(
            @RequestBody ContactRequest req,
            @AuthenticationPrincipal User rhUser) {

        searchService.envoyerEmailContact(req, rhUser);

        return ResponseEntity.ok(Map.of(
                "message", "Email envoyé à " + req.getEmailStagiaire(),
                "status",  "SENT"
        ));
    }

    // ── DTO Contact ───────────────────────────────────────────────────────
    @Data
    public static class ContactRequest {
        private String stagiaireId;
        private String nomStagiaire;
        private String emailStagiaire;
        private String postePropose;      // poste proposé (optionnel)
        private String messagePersonnalise; // message custom (optionnel)
    }
}
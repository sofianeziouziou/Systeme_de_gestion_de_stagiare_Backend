// DESTINATION : src/main/java/com/hikma/stagiaires/controller/ProjectSubmissionController.java
package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.ProjectSubmission;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.service.ProjectSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projets/{projetId}/submissions")
@RequiredArgsConstructor
@Tag(name = "Soumissions", description = "Preuves d'avancement des projets")
public class ProjectSubmissionController {

    private final ProjectSubmissionService submissionService;

    // ── GET — Liste des soumissions d'un projet ───────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    @Operation(summary = "Liste des preuves d'avancement d'un projet")
    public ResponseEntity<List<ProjectSubmission>> getSubmissions(
            @PathVariable String projetId) {
        return ResponseEntity.ok(submissionService.getByProjet(projetId));
    }

    // ── POST — Upload fichier (PDF ou image) ──────────────────────────────
    @PostMapping("/file")
    @PreAuthorize("hasAnyRole('STAGIAIRE', 'TUTEUR')")
    @Operation(summary = "Soumettre un fichier comme preuve d'avancement (PDF, image)")
    public ResponseEntity<ProjectSubmission> uploadFile(
            @PathVariable String projetId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                submissionService.addFileSubmission(projetId, file, title, description, currentUser)
        );
    }

    // ── POST — Lien vidéo externe ─────────────────────────────────────────
    @PostMapping("/video")
    @PreAuthorize("hasAnyRole('STAGIAIRE', 'TUTEUR')")
    @Operation(summary = "Soumettre un lien vidéo comme preuve d'avancement")
    public ResponseEntity<ProjectSubmission> addVideoLink(
            @PathVariable String projetId,
            @RequestBody VideoLinkRequest req,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                submissionService.addVideoLink(
                        projetId, req.getUrl(), req.getTitle(), req.getDescription(), currentUser)
        );
    }

    // ── DELETE — Supprimer une soumission ─────────────────────────────────
    @DeleteMapping("/{submissionId}")
    @PreAuthorize("hasAnyRole('RH', 'STAGIAIRE', 'TUTEUR')")
    @Operation(summary = "Supprimer une preuve d'avancement")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable String projetId,
            @PathVariable String submissionId,
            @AuthenticationPrincipal User currentUser) {

        submissionService.delete(submissionId, currentUser);
        return ResponseEntity.ok(Map.of("message", "Soumission supprimée"));
    }

    @Data
    public static class VideoLinkRequest {
        private String url;
        private String title;
        private String description;
    }
}
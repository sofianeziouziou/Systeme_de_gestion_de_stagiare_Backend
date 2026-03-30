package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.dto.projet.ProjetDTOs.*;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.service.ProjetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projets")
@RequiredArgsConstructor
@Tag(name = "Projets", description = "Gestion des projets de stage")
public class ProjetController {

    private final ProjetService projetService;

    // ── RH SEULEMENT : créer un projet ────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "RH crée un projet et assigne tuteur + stagiaire(s)")
    public ResponseEntity<ProjetResponse> create(
            @Valid @RequestBody CreateRequest req,
            @AuthenticationPrincipal User currentUser) {
        ProjetResponse resp = projetService.create(req, currentUser.getId());
        return ResponseEntity.created(URI.create("/api/v1/projets/" + resp.getId())).body(resp);
    }

    // ── RH : liste paginée de tous les projets ────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<Page<ProjetResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projetService.getAll(page, size));
    }

    // ── TOUS : détail d'un projet par id ──────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    public ResponseEntity<ProjetResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(projetService.getById(id));
    }

    // ── TUTEUR : ses projets assignés par le RH ───────────────────────────
    @GetMapping("/my-projects")
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Tuteur — liste ses projets assignés par le RH")
    public ResponseEntity<List<ProjetResponse>> getMyProjects(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(projetService.getByTuteur(currentUser.getId()));
    }

    // ── RH / TUTEUR / STAGIAIRE : projets d'un stagiaire ─────────────────
    @GetMapping("/stagiaire/{stagiaireId}")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    public ResponseEntity<List<ProjetResponse>> getByStagiaire(@PathVariable String stagiaireId) {
        return ResponseEntity.ok(projetService.getByStagiaire(stagiaireId));
    }

    // ── RH SEULEMENT : modifier infos du projet ───────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "RH modifie les infos du projet (dates, stagiaires, statut...)")
    public ResponseEntity<ProjetResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(projetService.update(id, req, currentUser.getId()));
    }

    // ── TUTEUR : mettre à jour l'avancement ──────────────────────────────
    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Tuteur met à jour le % d'avancement du projet")
    public ResponseEntity<ProjetResponse> updateProgress(
            @PathVariable String id,
            @RequestParam Integer progress,
            @AuthenticationPrincipal User currentUser) {
        UpdateRequest req = new UpdateRequest();
        req.setProgress(progress);
        return ResponseEntity.ok(projetService.update(id, req, currentUser.getId()));
    }

    // ── TUTEUR : définir / mettre à jour les sprints ──────────────────────
    @PatchMapping("/{id}/sprints")
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Tuteur définit ou met à jour les sprints du projet")
    public ResponseEntity<ProjetResponse> updateSprints(
            @PathVariable String id,
            @RequestBody List<SprintRequest> sprints,
            @AuthenticationPrincipal User currentUser) {
        UpdateRequest req = new UpdateRequest();
        req.setSprints(sprints);
        return ResponseEntity.ok(projetService.update(id, req, currentUser.getId()));
    }

    // ── TUTEUR : marquer un sprint comme terminé ──────────────────────────
    @PatchMapping("/{projetId}/sprints/{sprintId}/complete")
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Tuteur marque un sprint comme terminé")
    public ResponseEntity<ProjetResponse> completeSprint(
            @PathVariable String projetId,
            @PathVariable String sprintId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(projetService.completeSprint(projetId, sprintId, currentUser.getId()));
    }

    // ── TUTEUR / STAGIAIRE : déposer le rapport final ─────────────────────
    @PostMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('TUTEUR', 'STAGIAIRE')")
    public ResponseEntity<ProjetResponse> uploadReport(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(projetService.uploadReport(id, file, currentUser.getId()));
    }

    // ── RH SEULEMENT : supprimer (soft delete) ───────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "RH supprime un projet (soft delete)")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        projetService.softDelete(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    // ── RH : déclencher manuellement le scheduler ─────────────────────────
    @PostMapping("/check-deadlines")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Déclencher manuellement la vérification des deadlines")
    public ResponseEntity<String> triggerDeadlineCheck() {
        projetService.checkProjectDeadlines();
        return ResponseEntity.ok("Vérification effectuée.");
    }
}
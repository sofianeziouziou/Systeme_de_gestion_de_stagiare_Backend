// DESTINATION : src/main/java/com/hikma/stagiaires/controller/EvaluationController.java
package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.model.ProjetStatus;
import com.hikma.stagiaires.repository.EvaluationRepository;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
@Tag(name = "Evaluations")
public class EvaluationController {

    private final EvaluationRepository evaluationRepository;
    private final StagiaireRepository  stagiaireRepository;
    private final UserRepository       userRepository;
    private final ProjetRepository     projetRepository;

    // ── Soumettre une évaluation (TUTEUR) ─────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Soumettre une évaluation sprint ou projet")
    public ResponseEntity<EvaluationResponse> create(
            @Valid @RequestBody EvaluationRequest req,
            @AuthenticationPrincipal User tuteur) {

        // 1. Projet existe et appartient au tuteur
        Projet projet = projetRepository.findById(req.getProjetId())
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + req.getProjetId()));

        if (!tuteur.getId().equals(projet.getTuteurId()))
            throw new IllegalArgumentException("Ce projet ne vous appartient pas.");

        // 2. Stagiaire résolution userId → fiche
        Stagiaire stagiaire = stagiaireRepository.findById(req.getStagiaireId())
                .orElseThrow(() -> new NoSuchElementException("Stagiaire introuvable : " + req.getStagiaireId()));

        String userId = stagiaire.getUserId();
        if (userId == null || projet.getStagiaireIds() == null
                || !projet.getStagiaireIds().contains(userId))
            throw new IllegalArgumentException("Ce stagiaire n'est pas assigné à ce projet.");

        // 3. Déterminer le type SPRINT ou PROJET
        EvaluationType type = (req.getSprintId() != null && !req.getSprintId().isBlank())
                ? EvaluationType.SPRINT : EvaluationType.PROJET;

        // 4. Vérifications anti-doublon
        if (type == EvaluationType.SPRINT) {
            // Un seul sprint = une seule évaluation par stagiaire
            if (req.getSprintId() == null || req.getSprintId().isBlank())
                throw new IllegalArgumentException("sprintId requis pour une évaluation de sprint.");

            boolean dejaEvalue = evaluationRepository
                    .findByProjetIdAndStagiaireId(req.getProjetId(), req.getStagiaireId())
                    .stream()
                    .anyMatch(e -> EvaluationType.SPRINT.equals(e.getType())
                            && req.getSprintId().equals(e.getSprintId()));
            if (dejaEvalue)
                throw new IllegalArgumentException(
                        "Ce sprint a déjà été évalué pour ce stagiaire.");

            // Vérifier que le sprint est bien TERMINE
            boolean sprintTermine = projet.getSprints() != null && projet.getSprints().stream()
                    .anyMatch(s -> req.getSprintId().equals(s.getId())
                            && "TERMINE".equals(s.getStatus()));
            if (!sprintTermine)
                throw new IllegalArgumentException(
                        "Le sprint doit être terminé avant d'être évalué.");

        } else {
            // Évaluation finale projet — 1 seule par stagiaire
            boolean dejaEvalue = evaluationRepository
                    .findByProjetIdAndStagiaireId(req.getProjetId(), req.getStagiaireId())
                    .stream()
                    .anyMatch(e -> EvaluationType.PROJET.equals(e.getType()));
            if (dejaEvalue)
                throw new IllegalArgumentException(
                        "L'évaluation finale de ce projet a déjà été soumise pour ce stagiaire.");

            // Vérifier que le projet est TERMINE
            if (!ProjetStatus.TERMINE.equals(projet.getStatus()))
                throw new IllegalArgumentException(
                        "Le projet doit être terminé avant l'évaluation finale.");
        }

        // 5. Récupérer le titre du sprint si applicable
        String sprintTitle = null;
        if (type == EvaluationType.SPRINT && projet.getSprints() != null) {
            sprintTitle = projet.getSprints().stream()
                    .filter(s -> req.getSprintId().equals(s.getId()))
                    .map(s -> s.getTitle())
                    .findFirst().orElse(null);
        }

        // 6. Créer l'évaluation
        Evaluation eval = Evaluation.builder()
                .stagiaireId(req.getStagiaireId())
                .tuteurId(tuteur.getId())
                .projetId(req.getProjetId())
                .type(type)
                .sprintId(req.getSprintId())
                .sprintTitle(sprintTitle)
                .qualiteTechnique(req.getQualiteTechnique())
                .respectDelais(req.getRespectDelais())
                .communication(req.getCommunication())
                .espritEquipe(req.getEspritEquipe())
                .commentaire(req.getCommentaire())
                .status(EvaluationStatus.SOUMISE)
                .build();

        eval.calculateScore();
        evaluationRepository.save(eval);

        // 7. Mettre à jour globalScore du stagiaire
        stagiaire.setGlobalScore(eval.getScoreGlobal());
        List<Stagiaire.ScoreHistory> history = new ArrayList<>(
                stagiaire.getScoreHistory() != null ? stagiaire.getScoreHistory() : List.of());
        history.add(new Stagiaire.ScoreHistory(
                eval.getScoreGlobal(), java.time.LocalDateTime.now(), eval.getId()));
        stagiaire.setScoreHistory(history);
        stagiaireRepository.save(stagiaire);

        return ResponseEntity.ok(toResponse(eval));
    }

    // ── Évaluations disponibles pour un projet (TUTEUR) ───────────────────
    // Retourne quels sprints/projet peuvent encore être évalués
    @GetMapping("/disponibles/{projetId}")
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Évaluations disponibles pour un projet (sprints/projet non encore évalués)")
    public ResponseEntity<EvaluationsDisponiblesResponse> getDisponibles(
            @PathVariable String projetId,
            @AuthenticationPrincipal User tuteur) {

        Projet projet = projetRepository.findById(projetId)
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + projetId));

        if (!tuteur.getId().equals(projet.getTuteurId()))
            throw new IllegalArgumentException("Ce projet ne vous appartient pas.");

        List<Evaluation> existantes = evaluationRepository.findByProjetId(projetId);

        EvaluationsDisponiblesResponse resp = new EvaluationsDisponiblesResponse();

        // Sprints terminés non encore évalués par stagiaire
        List<SprintEvalInfo> sprintsDisponibles = new ArrayList<>();
        if (projet.getSprints() != null) {
            for (var sprint : projet.getSprints()) {
                if (!"TERMINE".equals(sprint.getStatus())) continue;
                // Pour chaque stagiaire du projet
                if (projet.getStagiaireIds() != null) {
                    for (String sUserId : projet.getStagiaireIds()) {
                        stagiaireRepository.findByUserId(sUserId).ifPresent(stag -> {
                            boolean dejaEvalue = existantes.stream().anyMatch(e ->
                                    EvaluationType.SPRINT.equals(e.getType())
                                            && sprint.getId().equals(e.getSprintId())
                                            && stag.getId().equals(e.getStagiaireId()));
                            if (!dejaEvalue) {
                                SprintEvalInfo info = new SprintEvalInfo();
                                info.setSprintId(sprint.getId());
                                info.setSprintTitle(sprint.getTitle());
                                info.setStagiaireId(stag.getId());
                                info.setStagiaireNom(stag.getFirstName() + " " + stag.getLastName());
                                sprintsDisponibles.add(info);
                            }
                        });
                    }
                }
            }
        }
        resp.setSprintsAEvaluer(sprintsDisponibles);

        // Évaluation finale projet disponible ?
        boolean projetTermine = ProjetStatus.TERMINE.equals(projet.getStatus());
        List<StagiaireEvalInfo> projetDisponibles = new ArrayList<>();
        if (projetTermine && projet.getStagiaireIds() != null) {
            for (String sUserId : projet.getStagiaireIds()) {
                stagiaireRepository.findByUserId(sUserId).ifPresent(stag -> {
                    boolean dejaEvalue = existantes.stream().anyMatch(e ->
                            EvaluationType.PROJET.equals(e.getType())
                                    && stag.getId().equals(e.getStagiaireId()));
                    if (!dejaEvalue) {
                        StagiaireEvalInfo info = new StagiaireEvalInfo();
                        info.setStagiaireId(stag.getId());
                        info.setStagiaireNom(stag.getFirstName() + " " + stag.getLastName());
                        projetDisponibles.add(info);
                    }
                });
            }
        }
        resp.setProjetAEvaluer(projetDisponibles);
        resp.setProjetTermine(projetTermine);

        return ResponseEntity.ok(resp);
    }

    // ── Mes évaluations soumises (TUTEUR) ─────────────────────────────────
    @GetMapping("/mes-evaluations")
    @PreAuthorize("hasRole('TUTEUR')")
    public ResponseEntity<List<EvaluationResponse>> getMesEvaluations(
            @AuthenticationPrincipal User tuteur) {
        return ResponseEntity.ok(
                evaluationRepository.findByTuteurIdOrderByCreatedAtDesc(tuteur.getId())
                        .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ── Évaluations reçues (STAGIAIRE) — filtrées aux projets dont il est membre
    @GetMapping("/mes-resultats")
    @PreAuthorize("hasRole('STAGIAIRE')")
    public ResponseEntity<List<EvaluationResponse>> getMesResultats(
            @AuthenticationPrincipal User stagiaire) {
        return stagiaireRepository.findByUserId(stagiaire.getId())
                .map(s -> {
                    // Récupérer les projets où ce stagiaire est membre (via userId)
                    List<String> projetIds = projetRepository.findAll().stream()
                            .filter(p -> p.getStagiaireIds() != null
                                    && p.getStagiaireIds().contains(s.getUserId()))
                            .map(Projet::getId)
                            .collect(Collectors.toList());

                    // Filtrer les évals : stagiaireId = s.getId() ET projetId dans la liste
                    List<EvaluationResponse> evals = evaluationRepository
                            .findByStagiaireIdOrderByCreatedAtDesc(s.getId())
                            .stream()
                            .filter(e -> projetIds.contains(e.getProjetId()))
                            .map(this::toResponse)
                            .collect(Collectors.toList());

                    return ResponseEntity.ok(evals);
                })
                .orElse(ResponseEntity.ok(List.of()));
    }

    // ── Évaluations d'un stagiaire (RH / TUTEUR) ──────────────────────────
    @GetMapping("/stagiaire/{stagiaireId}")
    @PreAuthorize("hasAnyRole('RH','TUTEUR','STAGIAIRE')")
    public ResponseEntity<List<EvaluationResponse>> getByStagiaire(
            @PathVariable String stagiaireId) {
        return ResponseEntity.ok(
                evaluationRepository.findByStagiaireIdOrderByCreatedAtDesc(stagiaireId)
                        .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ── Toutes les évaluations (RH) ───────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<EvaluationResponse>> getAll() {
        return ResponseEntity.ok(
                evaluationRepository.findAll()
                        .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    @Data
    public static class EvaluationRequest {
        @NotBlank private String projetId;
        @NotBlank private String stagiaireId;   // stagiaires._id

        // NOUVEAU : optionnel — si présent = évaluation de sprint
        private String sprintId;

        @DecimalMin("0") @DecimalMax("100") private Double qualiteTechnique;
        @DecimalMin("0") @DecimalMax("100") private Double respectDelais;
        @DecimalMin("0") @DecimalMax("100") private Double communication;
        @DecimalMin("0") @DecimalMax("100") private Double espritEquipe;
        private String commentaire;
    }

    @Data
    public static class EvaluationResponse {
        private String id;
        private String stagiaireId;
        private String stagiaireNom;
        private String tuteurId;
        private String tuteurNom;
        private String projetId;
        private String projetTitle;
        // NOUVEAU
        private String type;          // "SPRINT" ou "PROJET"
        private String sprintId;
        private String sprintTitle;
        private Double qualiteTechnique;
        private Double respectDelais;
        private Double communication;
        private Double espritEquipe;
        private Double scoreGlobal;
        private String commentaire;
        private String status;
        private String createdAt;
    }

    @Data
    public static class EvaluationsDisponiblesResponse {
        private List<SprintEvalInfo> sprintsAEvaluer = new ArrayList<>();
        private List<StagiaireEvalInfo> projetAEvaluer = new ArrayList<>();
        private boolean projetTermine;
    }

    @Data
    public static class SprintEvalInfo {
        private String sprintId;
        private String sprintTitle;
        private String stagiaireId;
        private String stagiaireNom;
    }

    @Data
    public static class StagiaireEvalInfo {
        private String stagiaireId;
        private String stagiaireNom;
    }

    private EvaluationResponse toResponse(Evaluation e) {
        EvaluationResponse r = new EvaluationResponse();
        r.setId(e.getId());
        r.setStagiaireId(e.getStagiaireId());
        r.setTuteurId(e.getTuteurId());
        r.setProjetId(e.getProjetId());
        r.setType(e.getType() != null ? e.getType().name() : "PROJET");
        r.setSprintId(e.getSprintId());
        r.setSprintTitle(e.getSprintTitle());
        r.setQualiteTechnique(e.getQualiteTechnique());
        r.setRespectDelais(e.getRespectDelais());
        r.setCommunication(e.getCommunication());
        r.setEspritEquipe(e.getEspritEquipe());
        r.setScoreGlobal(e.getScoreGlobal());
        r.setCommentaire(e.getCommentaire());
        r.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        r.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);

        stagiaireRepository.findById(e.getStagiaireId()).ifPresent(s ->
                r.setStagiaireNom(s.getFirstName() + " " + s.getLastName()));

        if (e.getTuteurId() != null)
            userRepository.findById(e.getTuteurId()).ifPresent(u ->
                    r.setTuteurNom(u.getFirstName() + " " + u.getLastName()));

        if (e.getProjetId() != null)
            projetRepository.findById(e.getProjetId()).ifPresent(p ->
                    r.setProjetTitle(p.getTitle()));

        return r;
    }
}
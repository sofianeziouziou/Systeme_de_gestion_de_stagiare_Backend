package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.Evaluation;
import com.hikma.stagiaires.model.EvaluationStatus;
import com.hikma.stagiaires.model.Projet;
import com.hikma.stagiaires.model.User;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
@Tag(name = "Evaluations", description = "Gestion des évaluations de stagiaires")
public class EvaluationController {

    private final EvaluationRepository evaluationRepository;
    private final StagiaireRepository  stagiaireRepository;
    private final UserRepository       userRepository;
    private final ProjetRepository     projetRepository;

    // ── Soumettre une évaluation (TUTEUR) ─────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Soumettre une évaluation pour un stagiaire (projet obligatoire)")
    public ResponseEntity<EvaluationResponse> create(
            @Valid @RequestBody EvaluationRequest req,
            @AuthenticationPrincipal User tuteur) {

        // 1. Vérifier que le projet existe et appartient à ce tuteur
        Projet projet = projetRepository.findById(req.getProjetId())
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + req.getProjetId()));

        if (!tuteur.getId().equals(projet.getTuteurId())) {
            throw new IllegalArgumentException("Ce projet ne vous appartient pas.");
        }

        // 2. Vérifier que le stagiaire est bien dans ce projet
        if (projet.getStagiaireIds() == null || !projet.getStagiaireIds().contains(req.getStagiaireId())) {
            throw new IllegalArgumentException("Ce stagiaire n'est pas assigné à ce projet.");
        }

        // 3. Construire et calculer l'évaluation
        Evaluation eval = Evaluation.builder()
                .stagiaireId(req.getStagiaireId())
                .tuteurId(tuteur.getId())
                .projetId(req.getProjetId())
                .qualiteTechnique(req.getQualiteTechnique())
                .respectDelais(req.getRespectDelais())
                .communication(req.getCommunication())
                .espritEquipe(req.getEspritEquipe())
                .commentaire(req.getCommentaire())
                .status(EvaluationStatus.SOUMISE)
                .build();

        // Calcul pondéré côté serveur (tech×40% + délais×20% + comm×20% + équipe×20%)
        eval.calculateScore();
        evaluationRepository.save(eval);

        // 4. Met à jour globalScore + scoreHistory du stagiaire
        stagiaireRepository.findById(req.getStagiaireId()).ifPresent(s -> {
            s.setGlobalScore(eval.getScoreGlobal());
            java.util.List<com.hikma.stagiaires.model.Stagiaire.ScoreHistory> history =
                    new java.util.ArrayList<>(s.getScoreHistory());
            history.add(new com.hikma.stagiaires.model.Stagiaire.ScoreHistory(
                    eval.getScoreGlobal(), java.time.LocalDateTime.now(), eval.getId()));
            s.setScoreHistory(history);
            stagiaireRepository.save(s);
        });

        return ResponseEntity.ok(toResponse(eval));
    }

    // ── Mes évaluations soumises (TUTEUR) ─────────────────────────────
    @GetMapping("/mes-evaluations")
    @PreAuthorize("hasRole('TUTEUR')")
    @Operation(summary = "Évaluations soumises par le tuteur connecté")
    public ResponseEntity<List<EvaluationResponse>> getMesEvaluations(
            @AuthenticationPrincipal User tuteur) {

        return ResponseEntity.ok(
                evaluationRepository.findByTuteurIdOrderByCreatedAtDesc(tuteur.getId())
                        .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ── Évaluations reçues (STAGIAIRE) ────────────────────────────────
    @GetMapping("/mes-resultats")
    @PreAuthorize("hasRole('STAGIAIRE')")
    @Operation(summary = "Évaluations reçues par le stagiaire connecté")
    public ResponseEntity<List<EvaluationResponse>> getMesResultats(
            @AuthenticationPrincipal User stagiaire) {

        return stagiaireRepository.findByUserId(stagiaire.getId())
                .map(s -> ResponseEntity.ok(
                        evaluationRepository.findByStagiaireIdOrderByCreatedAtDesc(s.getId())
                                .stream().map(this::toResponse).collect(Collectors.toList())))
                .orElse(ResponseEntity.ok(List.of()));
    }

    // ── Évaluations d'un stagiaire (RH / TUTEUR) ─────────────────────
    @GetMapping("/stagiaire/{stagiaireId}")
    @PreAuthorize("hasAnyRole('RH','TUTEUR','STAGIAIRE')")
    @Operation(summary = "Évaluations d'un stagiaire donné")
    public ResponseEntity<List<EvaluationResponse>> getByStagiaire(
            @PathVariable String stagiaireId) {

        return ResponseEntity.ok(
                evaluationRepository.findByStagiaireIdOrderByCreatedAtDesc(stagiaireId)
                        .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ── Toutes les évaluations (RH) ───────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Toutes les évaluations")
    public ResponseEntity<List<EvaluationResponse>> getAll() {
        return ResponseEntity.ok(
                evaluationRepository.findAll()
                        .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ── DTOs ──────────────────────────────────────────────────────────
    @Data
    public static class EvaluationRequest {
        @NotBlank(message = "Le projet est obligatoire")
        private String projetId;

        @NotBlank(message = "Le stagiaire est obligatoire")
        private String stagiaireId;

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
        private Double qualiteTechnique;
        private Double respectDelais;
        private Double communication;
        private Double espritEquipe;
        private Double scoreGlobal;
        private String commentaire;
        private String status;
        private String createdAt;
    }

    private EvaluationResponse toResponse(Evaluation e) {
        EvaluationResponse r = new EvaluationResponse();
        r.setId(e.getId());
        r.setStagiaireId(e.getStagiaireId());
        r.setTuteurId(e.getTuteurId());
        r.setProjetId(e.getProjetId());
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
        userRepository.findById(e.getTuteurId()).ifPresent(u ->
                r.setTuteurNom(u.getFirstName() + " " + u.getLastName()));
        if (e.getProjetId() != null) {
            projetRepository.findById(e.getProjetId()).ifPresent(p ->
                    r.setProjetTitle(p.getTitle()));
        }

        return r;
    }
}
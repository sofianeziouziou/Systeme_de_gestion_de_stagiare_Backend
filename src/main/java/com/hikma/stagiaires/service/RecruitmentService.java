// DESTINATION : src/main/java/com/hikma/stagiaires/service/RecruitmentService.java
// ACTION      : CRÉER ce fichier (nouveau)

package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentService {

    private final StagiaireRepository  stagiaireRepository;
    private final EvaluationRepository evaluationRepository;
    private final ProjetRepository     projetRepository;

    // ── Calculer le classement complet ───────────────────────────────────
    public List<RecruitmentScore> calculerClassement() {

        // 1. Charger tous les stagiaires actifs — 1 seule requête
        List<Stagiaire> stagiaires = stagiaireRepository.findByDeletedFalse();

        if (stagiaires.isEmpty()) return List.of();

        // 2. Charger toutes les évaluations — 1 seule requête
        Map<String, List<Evaluation>> evalsByStagiaire = evaluationRepository.findAll()
                .stream()
                .filter(e -> EvaluationStatus.SOUMISE.equals(e.getStatus())
                        || EvaluationStatus.VALIDEE.equals(e.getStatus()))
                .collect(Collectors.groupingBy(Evaluation::getStagiaireId));

        // 3. Charger tous les projets — 1 seule requête
        List<Projet> tousLesProjets = projetRepository.findByDeletedFalse(
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        // 4. Calculer le score de chaque stagiaire
        List<RecruitmentScore> scores = new ArrayList<>();

        for (Stagiaire s : stagiaires) {
            RecruitmentScore score = calculerScoreStagiaire(
                    s,
                    evalsByStagiaire.getOrDefault(s.getId(), List.of()),
                    tousLesProjets
            );
            scores.add(score);
        }

        // 5. Trier par score total décroissant
        scores.sort(Comparator.comparingDouble(
                (RecruitmentScore rs) -> rs.getScoreTotal() != null ? rs.getScoreTotal() : 0
        ).reversed());

        log.info("[RECRUITMENT] Classement calculé pour {} stagiaires", scores.size());
        return scores;
    }

    // ── Calculer le score d'un seul stagiaire ────────────────────────────
    public RecruitmentScore calculerScoreStagiaire(
            Stagiaire stagiaire,
            List<Evaluation> evals,
            List<Projet> tousLesProjets) {

        // ── Critère 1 : Évaluations (40%) ─────────────────────────────
        double scoreEvals = 0;
        if (!evals.isEmpty()) {
            scoreEvals = evals.stream()
                    .mapToDouble(e -> e.getScoreGlobal() != null ? e.getScoreGlobal() : 0)
                    .average()
                    .orElse(0);
        }

        // ── Critère 2 : Projets (30%) ──────────────────────────────────
        // Taux de complétion moyen des projets du stagiaire
        List<Projet> projetsStagiaire = tousLesProjets.stream()
                .filter(p -> p.getStagiaireIds() != null
                        && p.getStagiaireIds().contains(stagiaire.getUserId() != null
                        ? stagiaire.getUserId() : stagiaire.getId()))
                .collect(Collectors.toList());

        double scoreProjets = 0;
        if (!projetsStagiaire.isEmpty()) {
            long termines = projetsStagiaire.stream()
                    .filter(p -> ProjetStatus.TERMINE.equals(p.getStatus()))
                    .count();
            // Taux completion × 100
            scoreProjets = ((double) termines / projetsStagiaire.size()) * 100;

            // Bonus avancement moyen si pas encore terminés
            double avgProgress = projetsStagiaire.stream()
                    .mapToDouble(p -> p.getProgress() != null ? p.getProgress() : 0)
                    .average().orElse(0);
            // Pondération : 60% taux terminés + 40% avancement moyen
            scoreProjets = (scoreProjets * 0.6) + (avgProgress * 0.4);
        }

        // ── Critère 3 : CV (20%) ───────────────────────────────────────
        double scoreCv = 0;
        if (stagiaire.getCvUrl() != null && !stagiaire.getCvUrl().isBlank()) {
            scoreCv += 50; // CV uploadé
        }
        if (stagiaire.getTechnicalSkills() != null
                && !stagiaire.getTechnicalSkills().isEmpty()) {
            // +5 par compétence, max 50 points
            scoreCv += Math.min(stagiaire.getTechnicalSkills().size() * 5, 50);
        }

        // ── Critère 4 : Assiduité sprints (10%) ───────────────────────
        double scoreAssiduite = 100; // par défaut excellent
        long totalSprints  = projetsStagiaire.stream()
                .filter(p -> p.getSprints() != null)
                .mapToLong(p -> p.getSprints().size())
                .sum();
        long sprintsRetard = projetsStagiaire.stream()
                .filter(p -> p.getSprints() != null)
                .flatMap(p -> p.getSprints().stream())
                .filter(sp -> "EN_RETARD".equals(sp.getStatus()))
                .count();

        if (totalSprints > 0) {
            double tauxRetard = (double) sprintsRetard / totalSprints;
            scoreAssiduite = Math.max(0, 100 - (tauxRetard * 100));
        }

        // ── Construire et calculer ─────────────────────────────────────
        RecruitmentScore score = RecruitmentScore.builder()
                .stagiaireId(stagiaire.getId())
                .firstName(stagiaire.getFirstName())
                .lastName(stagiaire.getLastName())
                .email(stagiaire.getEmail())
                .departement(stagiaire.getDepartement())
                .photoUrl(stagiaire.getPhotoUrl())
                .badge(stagiaire.getBadge() != null ? stagiaire.getBadge().name() : null)
                .scoreEvaluations(Math.round(scoreEvals     * 100.0) / 100.0)
                .scoreProjets    (Math.round(scoreProjets   * 100.0) / 100.0)
                .scoreCv         (Math.round(scoreCv        * 100.0) / 100.0)
                .scoreAssiduite  (Math.round(scoreAssiduite * 100.0) / 100.0)
                .calculatedAt(LocalDateTime.now())
                .build();

        score.calculateTotal();
        return score;
    }
}
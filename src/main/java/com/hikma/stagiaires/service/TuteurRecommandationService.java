package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TuteurRecommandationService {

    private final UserRepository      userRepository;
    private final StagiaireRepository stagiaireRepository;

    // ── DTO résultat ──────────────────────────────────────────────────────
    public record TuteurScore(
            String tuteurId,
            String firstName,
            String lastName,
            String email,
            double score,
            int    nbStagiaires,
            double scoreMoyenStagiaires,
            String raison
    ) {}

    // ── P3 FIX : Algorithme avec batch queries ────────────────────────────
    /**
     * AVANT : N+1 requêtes (1 par tuteur)
     * APRÈS : 2 requêtes fixes quelle que soit la taille des données
     *
     * Critères pondérés :
     *   40% — Charge       (moins de stagiaires = meilleur)
     *   30% — Historique   (score moyen de ses stagiaires passés)
     *   20% — Département  (a déjà encadré dans le même département)
     *   10% — Compétences  (overlap skills stagiaire ↔ stagiaires du tuteur)
     */
    public List<TuteurScore> recommander(String stagiaireId) {

        // 1. Charger le stagiaire cible
        Stagiaire stagiaire = stagiaireRepository.findById(stagiaireId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Stagiaire introuvable : " + stagiaireId));

        // 2. Charger tous les tuteurs actifs — REQUÊTE 1
        List<User> tuteurs = userRepository.findAll().stream()
                .filter(u -> Role.TUTEUR.equals(u.getRole()))
                .filter(u -> AccountStatus.APPROUVE.equals(u.getAccountStatus()))
                .collect(Collectors.toList());

        if (tuteurs.isEmpty()) return List.of();

        // P3 FIX — REQUÊTE 2 : charger TOUS les stagiaires en une fois
        // puis grouper en mémoire → O(n) au lieu de O(n×m)
        Map<String, List<Stagiaire>> stagiairesByTuteur =
                stagiaireRepository.findByDeletedFalse().stream()
                        .filter(s -> s.getTuteurId() != null)
                        .collect(Collectors.groupingBy(Stagiaire::getTuteurId));

        // 3. Calculer le score de chaque tuteur — ZÉRO requête supplémentaire
        List<TuteurScore> scores = new ArrayList<>();

        for (User tuteur : tuteurs) {
            List<Stagiaire> stagiairesDuTuteur =
                    stagiairesByTuteur.getOrDefault(tuteur.getId(), List.of());

            int    nbActuels  = stagiairesDuTuteur.size();
            double scoreMoyen = stagiairesDuTuteur.stream()
                    .mapToDouble(s -> s.getGlobalScore() != null ? s.getGlobalScore() : 0)
                    .average().orElse(0.0);

            // Critère 1 : Charge (40%)
            double scoreCharge = Math.max(0, 1.0 - (nbActuels / 5.0)) * 40;

            // Critère 2 : Historique performances (30%)
            double scoreHistorique = (scoreMoyen / 100.0) * 30;

            // Critère 3 : Département (20%)
            double scoreDept = 0;
            if (stagiaire.getDepartement() != null) {
                boolean memeDept = stagiairesDuTuteur.stream()
                        .anyMatch(s -> stagiaire.getDepartement()
                                .equalsIgnoreCase(s.getDepartement()));
                scoreDept = memeDept ? 20 : 0;
            }

            // Critère 4 : Compétences (10%)
            double scoreComp = 0;
            List<String> skillsStagiaire = stagiaire.getTechnicalSkills();
            if (skillsStagiaire != null && !skillsStagiaire.isEmpty()) {
                Set<String> skillsTuteur = stagiairesDuTuteur.stream()
                        .filter(s -> s.getTechnicalSkills() != null)
                        .flatMap(s -> s.getTechnicalSkills().stream())
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());

                long overlap = skillsStagiaire.stream()
                        .map(String::toLowerCase)
                        .filter(skillsTuteur::contains)
                        .count();

                scoreComp = ((double) overlap / skillsStagiaire.size()) * 10;
            }

            double scoreTotal = scoreCharge + scoreHistorique + scoreDept + scoreComp;

            scores.add(new TuteurScore(
                    tuteur.getId(),
                    tuteur.getFirstName(),
                    tuteur.getLastName(),
                    tuteur.getEmail(),
                    Math.round(scoreTotal * 10.0) / 10.0,
                    nbActuels,
                    Math.round(scoreMoyen * 10.0) / 10.0,
                    buildRaison(nbActuels, scoreMoyen, scoreDept > 0, scoreComp)
            ));
        }

        return scores.stream()
                .sorted(Comparator.comparingDouble(TuteurScore::score).reversed())
                .limit(3)
                .collect(Collectors.toList());
    }

    // ── Recommandation par charge uniquement (vue globale RH) ─────────────
    public List<TuteurScore> recommanderParCharge() {

        // P3 FIX : même pattern — 2 requêtes, groupement en mémoire
        List<User> tuteurs = userRepository.findAll().stream()
                .filter(u -> Role.TUTEUR.equals(u.getRole()))
                .filter(u -> AccountStatus.APPROUVE.equals(u.getAccountStatus()))
                .collect(Collectors.toList());

        Map<String, List<Stagiaire>> stagiairesByTuteur =
                stagiaireRepository.findByDeletedFalse().stream()
                        .filter(s -> s.getTuteurId() != null)
                        .collect(Collectors.groupingBy(Stagiaire::getTuteurId));

        return tuteurs.stream().map(tuteur -> {
                    List<Stagiaire> stagiaires =
                            stagiairesByTuteur.getOrDefault(tuteur.getId(), List.of());
                    int    nb    = stagiaires.size();
                    double score = stagiaires.stream()
                            .mapToDouble(s -> s.getGlobalScore() != null ? s.getGlobalScore() : 0)
                            .average().orElse(0.0);
                    double charge = Math.max(0, 1.0 - (nb / 5.0)) * 100;

                    return new TuteurScore(
                            tuteur.getId(),
                            tuteur.getFirstName(),
                            tuteur.getLastName(),
                            tuteur.getEmail(),
                            Math.round(charge * 10.0) / 10.0,
                            nb,
                            Math.round(score * 10.0) / 10.0,
                            nb == 0 ? "Disponible — aucun stagiaire actuellement" :
                                    nb <  3 ? "Charge légère (" + nb + " stagiaire(s))" :
                                    "Charge élevée (" + nb + " stagiaires)"
                    );
                })
                .sorted(Comparator.comparingDouble(TuteurScore::score).reversed())
                .collect(Collectors.toList());
    }

    // ── Builder raison lisible ────────────────────────────────────────────
    private String buildRaison(int nb, double scoreMoyen,
                               boolean memeDept, double scoreComp) {
        List<String> points = new ArrayList<>();
        if      (nb == 0) points.add("disponible (0 stagiaire)");
        else if (nb <= 2) points.add("charge légère (" + nb + " stagiaires)");
        else              points.add("charge élevée (" + nb + " stagiaires)");

        if      (scoreMoyen >= 75) points.add("excellent historique (" + Math.round(scoreMoyen) + "/100)");
        else if (scoreMoyen >= 50) points.add("bon historique (" + Math.round(scoreMoyen) + "/100)");

        if (memeDept)    points.add("même département");
        if (scoreComp > 0) points.add("compétences compatibles");

        return String.join(" · ", points);
    }
}
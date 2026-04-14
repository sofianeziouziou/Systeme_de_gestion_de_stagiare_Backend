// DESTINATION : src/main/java/com/hikma/stagiaires/model/RecruitmentScore.java
// ACTION      : CRÉER ce fichier (nouveau)

package com.hikma.stagiaires.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * Score de recrutement calculé pour chaque stagiaire.
 * Algorithme : Evals(40%) + Projets(30%) + CV(20%) + Assiduité(10%)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recruitmentScores")
public class RecruitmentScore {

    @Id
    private String id;

    @Indexed(unique = true)
    private String stagiaireId;

    // ── Scores par critère (0–100) ────────────────────────────────────
    private Double scoreEvaluations;   // 40% — moyenne des évaluations
    private Double scoreProjets;       // 30% — taux completion projets
    private Double scoreCv;            // 20% — CV uploadé + compétences
    private Double scoreAssiduite;     // 10% — respect des délais sprints

    // ── Score final ───────────────────────────────────────────────────
    private Double scoreTotal;

    // ── Infos stagiaire (dénormalisées pour affichage rapide) ─────────
    private String firstName;
    private String lastName;
    private String email;
    private String departement;
    private String photoUrl;
    private String badge;

    // ── Métadonnées ───────────────────────────────────────────────────
    private LocalDateTime calculatedAt;

    // ── Calcul pondéré ────────────────────────────────────────────────
    public void calculateTotal() {
        double evals     = scoreEvaluations != null ? scoreEvaluations : 0;
        double projets   = scoreProjets     != null ? scoreProjets     : 0;
        double cv        = scoreCv          != null ? scoreCv          : 0;
        double assiduite = scoreAssiduite   != null ? scoreAssiduite   : 0;

        this.scoreTotal = Math.round(
                (evals * 0.40 + projets * 0.30 + cv * 0.20 + assiduite * 0.10) * 100.0
        ) / 100.0;
    }
}
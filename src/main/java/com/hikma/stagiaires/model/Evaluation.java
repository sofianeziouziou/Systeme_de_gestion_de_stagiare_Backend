// DESTINATION : src/main/java/com/hikma/stagiaires/model/Evaluation.java
package com.hikma.stagiaires.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "evaluations")
public class Evaluation {

    @Id
    private String id;

    private String stagiaireId;   // stagiaires._id
    private String tuteurId;
    private String projetId;

    // ── NOUVEAU : type d'évaluation ───────────────────────────────────
    // SPRINT  = évaluation d'un sprint terminé (1 par sprint par stagiaire)
    // PROJET  = évaluation finale du projet terminé (1 par projet par stagiaire)
    @Builder.Default
    private EvaluationType type = EvaluationType.PROJET;

    // ── NOUVEAU : identifiant du sprint (si type = SPRINT) ────────────
    private String sprintId;
    private String sprintTitle;   // pour affichage

    // ── Critères (0.0–100.0) ──────────────────────────────────────────
    private Double qualiteTechnique;   // 40%
    private Double respectDelais;       // 20%
    private Double communication;       // 20%
    private Double espritEquipe;        // 20%

    private Double scoreGlobal;
    private String commentaire;

    @Builder.Default
    private EvaluationStatus status = EvaluationStatus.SOUMISE;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ── Calcule le score pondéré ──────────────────────────────────────
    public void calculateScore() {
        double tech   = qualiteTechnique != null ? qualiteTechnique : 0;
        double delais = respectDelais    != null ? respectDelais    : 0;
        double comm   = communication   != null ? communication    : 0;
        double equipe = espritEquipe     != null ? espritEquipe     : 0;
        this.scoreGlobal = Math.round(
                (tech * 0.4 + delais * 0.2 + comm * 0.2 + equipe * 0.2) * 100.0) / 100.0;
    }
}
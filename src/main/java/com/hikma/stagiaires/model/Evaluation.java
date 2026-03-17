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

    private String stagiaireId;
    private String tuteurId;
    private String projetId;

    // ── Critères (0.0–100.0) — même noms que l'ancien modèle ─────────
    private Double qualiteTechnique;   // = compétences techniques 40%
    private Double respectDelais;       // 20%
    private Double communication;       // 20%
    private Double espritEquipe;        // = travail en équipe 20%

    private Double scoreGlobal;         // calculé
    private String commentaire;

    @Builder.Default
    private EvaluationStatus status = EvaluationStatus.SOUMISE;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ── Calcule le score pondéré CDC ──────────────────────────────────
    public void calculateScore() {
        double tech   = qualiteTechnique != null ? qualiteTechnique : 0;
        double delais = respectDelais    != null ? respectDelais    : 0;
        double comm   = communication   != null ? communication    : 0;
        double equipe = espritEquipe     != null ? espritEquipe     : 0;
        this.scoreGlobal = Math.round((tech * 0.4 + delais * 0.2 + comm * 0.2 + equipe * 0.2) * 100.0) / 100.0;
    }
}
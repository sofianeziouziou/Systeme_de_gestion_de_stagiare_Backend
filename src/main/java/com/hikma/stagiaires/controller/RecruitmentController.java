// DESTINATION : src/main/java/com/hikma/stagiaires/controller/RecruitmentController.java
// ACTION      : CRÉER ce fichier (nouveau)

package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.RecruitmentScore;
import com.hikma.stagiaires.service.RecruitmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recrutement")
@RequiredArgsConstructor
@Tag(name = "Recrutement", description = "Classement et scoring des stagiaires pour recrutement")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    // ── Classement complet (RH) ───────────────────────────────────────────
    @GetMapping("/classement")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Classement de tous les stagiaires par score de recrutement")
    public ResponseEntity<List<RecruitmentScore>> getClassement() {
        return ResponseEntity.ok(recruitmentService.calculerClassement());
    }

    // ── Score d'un seul stagiaire (RH) ────────────────────────────────────
    @GetMapping("/stagiaire/{stagiaireId}")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Score de recrutement détaillé pour un stagiaire")
    public ResponseEntity<RecruitmentScore> getScoreStagiaire(
            @PathVariable String stagiaireId) {

        List<RecruitmentScore> classement = recruitmentService.calculerClassement();

        return classement.stream()
                .filter(s -> stagiaireId.equals(s.getStagiaireId()))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
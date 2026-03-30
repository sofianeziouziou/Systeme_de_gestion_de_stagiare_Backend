package com.hikma.stagiaires.service;

import com.hikma.stagiaires.dto.dashboard.DashboardDTOs.*;
import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final StagiaireRepository stagiaireRepository;
    private final ProjetRepository projetRepository;
    private final EvaluationRepository evaluationRepository;

    @Cacheable(value = "dashboard", key = "'global'")
    public DashboardStats getGlobalStats() {

        // ── KPIs principaux ──────────────────────────────────────────────
        long actifs    = stagiaireRepository.countByStatusAndDeletedFalse(StagiaireStatus.EN_COURS);
        long termines  = stagiaireRepository.countByStatusAndDeletedFalse(StagiaireStatus.TERMINE);
        long enRetard  = projetRepository.countByStatusAndDeletedFalse(ProjetStatus.EN_RETARD);
        long projetsEnCours  = projetRepository.countByStatusAndDeletedFalse(ProjetStatus.EN_COURS);
        long projetsTermines = projetRepository.countByStatusAndDeletedFalse(ProjetStatus.TERMINE);
        long totalProjets    = projetsEnCours + projetsTermines;

        // ── Score moyen global ───────────────────────────────────────────
        List<Stagiaire> allActive = stagiaireRepository.findByStatusAndDeletedFalse(StagiaireStatus.EN_COURS);
        double scoreMoyen = allActive.stream()
                .mapToDouble(s -> s.getGlobalScore() != null ? s.getGlobalScore() : 0)
                .average().orElse(0);

        // ── Top 10 stagiaires ────────────────────────────────────────────
        List<TopStagiaireDTO> top10 = stagiaireRepository
                .findTop10ByDeletedFalseOrderByGlobalScoreDesc()
                .stream()
                .map(s -> TopStagiaireDTO.builder()
                        .id(s.getId())
                        .firstName(s.getFirstName())
                        .lastName(s.getLastName())
                        .photoUrl(s.getPhotoUrl())
                        .departement(s.getDepartement())
                        .score(s.getGlobalScore())
                        .badge(s.getBadge() != null ? s.getBadge().name() : null)
                        .build())
                .collect(Collectors.toList());
        for (int i = 0; i < top10.size(); i++) top10.get(i).setRank(i + 1);

        // ── Score moyen par département ──────────────────────────────────
        List<String> depts = List.of("IT", "Finance", "Marketing", "Production", "Qualite");
        Map<String, Double> scoreParDept = new LinkedHashMap<>();
        for (String dept : depts) {
            List<Stagiaire> stagsInDept = stagiaireRepository.findByDepartementForAggregation(dept);
            double avg = stagsInDept.stream()
                    .mapToDouble(s -> s.getGlobalScore() != null ? s.getGlobalScore() : 0)
                    .average().orElse(0);
            scoreParDept.put(dept, Math.round(avg * 100.0) / 100.0);
        }

        // ── Distribution des scores (histogramme) ────────────────────────
        List<Stagiaire> allStagiaires = stagiaireRepository
                .findByDeletedFalse();

        Map<String, Long> distrib = new LinkedHashMap<>();
        distrib.put("0-20",   count(allStagiaires, 0,  20));
        distrib.put("21-40",  count(allStagiaires, 21, 40));
        distrib.put("41-60",  count(allStagiaires, 41, 60));
        distrib.put("61-75",  count(allStagiaires, 61, 75));
        distrib.put("76-89",  count(allStagiaires, 76, 89));
        distrib.put("90-100", count(allStagiaires, 90, 100));

        List<ScoreDistributionDTO> scoreDistrib = distrib.entrySet().stream()
                .map(e -> ScoreDistributionDTO.builder().range(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());

        // ── Moyennes par critère (radar chart) ───────────────────────────
        // Correction : findByStagiaireIdAndStatus n'existe pas globalement,
        // on utilise findAll() + filtre sur VALIDEE
        List<Evaluation> validatedEvals = evaluationRepository.findAll()
                .stream()
                .filter(e -> EvaluationStatus.VALIDEE.equals(e.getStatus()))
                .collect(Collectors.toList());

        CriteresPerformance criteres = CriteresPerformance.builder()
                .qualiteTechnique(avg(validatedEvals, Evaluation::getQualiteTechnique))
                .respectDelais(avg(validatedEvals, Evaluation::getRespectDelais))
                .communication(avg(validatedEvals, Evaluation::getCommunication))
                .espritEquipe(avg(validatedEvals, Evaluation::getEspritEquipe))
                .build();

        return DashboardStats.builder()
                .totalStagiairesActifs(actifs)
                .totalStagiairesTermines(termines)
                .totalStagiairesEnRetard(enRetard)
                .totalProjets(totalProjets)
                .projetsEnCours(projetsEnCours)
                .projetsTermines(projetsTermines)
                .scoreGlobalMoyen(Math.round(scoreMoyen * 100.0) / 100.0)
                .top10Stagiaires(top10)
                .scoreMoyenParDepartement(scoreParDept)
                .scoreDistribution(scoreDistrib)
                .moyennesCriteres(criteres)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private long count(List<Stagiaire> list, double min, double max) {
        return list.stream()
                .filter(s -> s.getGlobalScore() != null
                        && s.getGlobalScore() >= min
                        && s.getGlobalScore() <= max)
                .count();
    }

    private double avg(List<Evaluation> evals,
                       java.util.function.Function<Evaluation, Double> getter) {
        return evals.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }
}
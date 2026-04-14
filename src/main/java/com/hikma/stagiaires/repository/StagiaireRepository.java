package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.Stagiaire;
import com.hikma.stagiaires.model.StagiaireStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StagiaireRepository extends MongoRepository<Stagiaire, String> {

    // ── Lookup par userId / email ─────────────────────────────────────────
    Optional<Stagiaire> findByUserId(String userId);
    boolean existsByEmailAndDeletedFalse(String email);
    Optional<Stagiaire> findByEmailAndDeletedFalse(String email);

    // ── Toutes les fiches actives (P3 fix — 1 requête pour recommandations) ──
    List<Stagiaire> findByDeletedFalse();

    // ── Par tuteur ────────────────────────────────────────────────────────
    List<Stagiaire> findByTuteurIdAndDeletedFalse(String tuteurId);

    // ── DashboardService : count par statut ───────────────────────────────
    long countByStatusAndDeletedFalse(StagiaireStatus status);

    // ── DashboardService : liste par statut ──────────────────────────────
    List<Stagiaire> findByStatusAndDeletedFalse(StagiaireStatus status);

    // ── DashboardService : top 10 par score ──────────────────────────────
    List<Stagiaire> findTop10ByDeletedFalseOrderByGlobalScoreDesc();

    // ── DashboardService : par département (pour agrégation scores) ───────
    @Query("{ 'departement': ?0, 'deleted': false }")
    List<Stagiaire> findByDepartementForAggregation(String departement);

    // ── Recherche par département (standard) ─────────────────────────────
    List<Stagiaire> findByDepartementAndDeletedFalse(String departement);
    long countByTuteurIdAndDeletedFalse(String tuteurId);

}
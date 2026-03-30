package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.Stagiaire;
import com.hikma.stagiaires.model.StagiaireStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StagiaireRepository extends MongoRepository<Stagiaire, String> {

    // ── Email ─────────────────────────────────────────────────────────────
    Optional<Stagiaire> findByEmail(String email);
    Optional<Stagiaire> findByEmailAndDeletedFalse(String email);
    boolean existsByEmailAndDeletedFalse(String email);

    // ── userId ────────────────────────────────────────────────────────────
    Optional<Stagiaire> findByUserId(String userId);
    Optional<Stagiaire> findByUserIdAndDeletedFalse(String userId);

    // ── Tuteur ────────────────────────────────────────────────────────────
    List<Stagiaire> findByTuteurId(String tuteurId);

    // ── Soft delete ───────────────────────────────────────────────────────
    List<Stagiaire> findByDeletedFalse();
    List<Stagiaire> findByDeletedFalse(Pageable pageable);  // ← pour DashboardService

    // ── Statut ────────────────────────────────────────────────────────────
    List<Stagiaire> findByStatusAndDeletedFalse(StagiaireStatus status);
    long countByStatusAndDeletedFalse(StagiaireStatus status);

    // ── Top 10 par score ──────────────────────────────────────────────────
    List<Stagiaire> findTop10ByDeletedFalseOrderByGlobalScoreDesc();

    // ── Agrégation par département ────────────────────────────────────────
    @Query("{ 'departement': ?0, 'deleted': false }")
    List<Stagiaire> findByDepartementForAggregation(String departement);
}
package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.stagiaire.Stagiaire;
import com.hikma.stagiaires.model.stagiaire.StagiaireStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StagiaireRepository extends MongoRepository<Stagiaire, String> {

    // ── Lookup par userId / email ─────────────────────────────────────────
    Optional<Stagiaire> findByUserId(String userId);
    boolean existsByEmailAndDeletedFalse(String email);
    Optional<Stagiaire> findByEmailAndDeletedFalse(String email);

    // ── Toutes les fiches actives ─────────────────────────────────────────
    List<Stagiaire> findByDeletedFalse();

    // ── Par tuteur ────────────────────────────────────────────────────────
    List<Stagiaire> findByTuteurIdAndDeletedFalse(String tuteurId);

    // ── DashboardService : count par statut ───────────────────────────────
    long countByStatusAndDeletedFalse(StagiaireStatus status);

    // ── DashboardService : liste par statut ──────────────────────────────
    List<Stagiaire> findByStatusAndDeletedFalse(StagiaireStatus status);

    // ── DashboardService : top 10 par score ──────────────────────────────
    List<Stagiaire> findTop10ByDeletedFalseOrderByGlobalScoreDesc();

    // ── DashboardService : par département ───────────────────────────────
    @Query("{ 'departement': ?0, 'deleted': false }")
    List<Stagiaire> findByDepartementForAggregation(String departement);

    // ── Recherche par département (standard) ─────────────────────────────
    List<Stagiaire> findByDepartementAndDeletedFalse(String departement);
    long countByTuteurIdAndDeletedFalse(String tuteurId);

    // ── Batch lookup par liste de stagiaire._id ───────────────────────────
    List<Stagiaire> findByIdInAndDeletedFalse(List<String> ids);

    // ── Batch lookup par liste de userId ─────────────────────────────────
    List<Stagiaire> findByUserIdInAndDeletedFalse(List<String> userIds);

    // ✅ NOUVEAU — pour migration : tous avec userId non null
    @Query("{ 'userId': { $ne: null }, 'deleted': false }")
    List<Stagiaire> findAllWithUserId();
}
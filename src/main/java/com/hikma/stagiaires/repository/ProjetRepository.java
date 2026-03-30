package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.Projet;
import com.hikma.stagiaires.model.ProjetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ProjetRepository extends MongoRepository<Projet, String> {

    Page<Projet> findByDeletedFalse(Pageable pageable);

    List<Projet> findByTuteurIdAndDeletedFalse(String tuteurId);

    List<Projet> findByStagiaireIdsContainingAndDeletedFalse(String stagiaireId);

    List<Projet> findByStatusAndDeletedFalse(ProjetStatus status);

    long countByStatusAndDeletedFalse(ProjetStatus status);

    // ── CORRIGÉ : Spring Data convertit LocalDate automatiquement ────────

    // Deadlines proches : EN_COURS + plannedEndDate entre now et limit
    List<Projet> findByDeletedFalseAndStatusAndPlannedEndDateBetween(
            ProjetStatus status, LocalDate start, LocalDate end);

    // Projets en retard : pas TERMINE/ANNULE + plannedEndDate < today
    List<Projet> findByDeletedFalseAndStatusNotInAndPlannedEndDateBefore(
            List<ProjetStatus> excludedStatuses, LocalDate date);

    // Sans mise à jour depuis 5 jours : EN_COURS + updatedAt < limit
    List<Projet> findByDeletedFalseAndStatusAndUpdatedAtBefore(
            ProjetStatus status, LocalDateTime limit);
}
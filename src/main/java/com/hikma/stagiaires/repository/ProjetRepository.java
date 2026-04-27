package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.projet.Projet;
import com.hikma.stagiaires.model.projet.ProjetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ProjetRepository extends MongoRepository<Projet, String> {

    Page<Projet> findByDeletedFalse(Pageable pageable);

    // ✅ NOUVEAU — pour toResponseList() dans getAll()
    long countByDeletedFalse();

    // ✅ NOUVEAU — pour getByStagiaire() fallback sans pageable
    List<Projet> findByDeletedFalse();

    List<Projet> findByTuteurIdAndDeletedFalse(String tuteurId);

    List<Projet> findByStagiaireIdsContainingAndDeletedFalse(String stagiaireId);

    List<Projet> findByStatusAndDeletedFalse(ProjetStatus status);

    long countByStatusAndDeletedFalse(ProjetStatus status);

    List<Projet> findByDeletedFalseAndStatusAndPlannedEndDateBetween(
            ProjetStatus status, LocalDate start, LocalDate end);

    List<Projet> findByDeletedFalseAndStatusNotInAndPlannedEndDateBefore(
            List<ProjetStatus> excludedStatuses, LocalDate date);

    List<Projet> findByDeletedFalseAndStatusAndUpdatedAtBefore(
            ProjetStatus status, LocalDateTime limit);
}
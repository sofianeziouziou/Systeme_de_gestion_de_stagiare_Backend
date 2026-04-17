// DESTINATION : src/main/java/com/hikma/stagiaires/repository/EvaluationRepository.java
package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.Evaluation;
import com.hikma.stagiaires.model.EvaluationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EvaluationRepository extends MongoRepository<Evaluation, String> {
    List<Evaluation> findByTuteurId(String tuteurId);
    List<Evaluation> findByStagiaireId(String stagiaireId);
    List<Evaluation> findByTuteurIdOrderByCreatedAtDesc(String tuteurId);
    List<Evaluation> findByStagiaireIdOrderByCreatedAtDesc(String stagiaireId);
    List<Evaluation> findByStagiaireIdAndStatus(String stagiaireId, EvaluationStatus status);

    // NOUVEAU
    List<Evaluation> findByProjetId(String projetId);
    List<Evaluation> findByProjetIdAndStagiaireId(String projetId, String stagiaireId);
}
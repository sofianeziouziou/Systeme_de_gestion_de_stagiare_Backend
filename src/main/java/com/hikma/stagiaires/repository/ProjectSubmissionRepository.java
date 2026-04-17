// DESTINATION : src/main/java/com/hikma/stagiaires/repository/ProjectSubmissionRepository.java
package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.ProjectSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProjectSubmissionRepository extends MongoRepository<ProjectSubmission, String> {

    List<ProjectSubmission> findByProjetIdOrderByUploadedAtDesc(String projetId);

    long countByProjetId(String projetId);
}
// DESTINATION : src/main/java/com/hikma/stagiaires/repository/MessageRepository.java
package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByProjetIdOrderByCreatedAtAsc(String projetId);

    long countByProjetIdAndReadByNotContaining(String projetId, String userId);
}
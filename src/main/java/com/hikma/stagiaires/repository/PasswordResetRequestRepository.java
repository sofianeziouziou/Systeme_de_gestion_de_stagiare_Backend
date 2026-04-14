package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.PasswordResetRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetRequestRepository extends MongoRepository<PasswordResetRequest, String> {

    List<PasswordResetRequest> findByStatusOrderByRequestedAtDesc(String status);

    List<PasswordResetRequest> findByUserIdOrderByRequestedAtDesc(String userId);

    Optional<PasswordResetRequest> findByUserIdAndStatus(String userId, String status);

    boolean existsByUserIdAndStatus(String userId, String status);
}
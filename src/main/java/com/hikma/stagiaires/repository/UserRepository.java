package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.AccountStatus;
import com.hikma.stagiaires.model.Role;
import com.hikma.stagiaires.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByResetPasswordToken(String token);

    // ── NOUVEAU ──────────────────────────────────────────────────────────
    List<User> findByAccountStatus(AccountStatus accountStatus);
    List<User> findByRole(Role role);
    List<User> findByRoleAndAccountStatus(Role role, AccountStatus accountStatus);

}
package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.user.AccountStatus;
import com.hikma.stagiaires.model.commun.Departement;
import com.hikma.stagiaires.model.user.Role;
import com.hikma.stagiaires.model.user.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByResetPasswordToken(String token);

    List<User> findByAccountStatus(AccountStatus accountStatus);
    List<User> findByRole(Role role);
    List<User> findByRoleAndAccountStatus(Role role, AccountStatus accountStatus);

    List<User> findByRoleAndDepartement(Role role, Departement departement);

    List<User> findByRoleAndDepartementAndAccountStatus(
            Role role, Departement departement, AccountStatus status
    );

    // ✅ NOUVEAU — batch pour éviter N+1 sur tuteurs dans ProjetService
    List<User> findByIdIn(List<String> ids);
}
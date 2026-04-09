package com.hikma.stagiaires.repository;

import com.hikma.stagiaires.model.AccountStatus;
import com.hikma.stagiaires.model.Departement;
import com.hikma.stagiaires.model.Role;
import com.hikma.stagiaires.model.User;
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

    /** NOUVEAU — tuteurs par département */
    List<User> findByRoleAndDepartement(Role role, Departement departement);

    /** NOUVEAU — tuteurs approuvés par département */
    List<User> findByRoleAndDepartementAndAccountStatus(
            Role role, Departement departement, AccountStatus status
    );
}
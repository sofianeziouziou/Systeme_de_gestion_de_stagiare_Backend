package com.hikma.stagiaires.service;

import com.hikma.stagiaires.exception.ResourceNotFoundException;
import com.hikma.stagiaires.model.AccountStatus;
import com.hikma.stagiaires.model.Departement;
import com.hikma.stagiaires.model.Role;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartementService {

    private final UserRepository      userRepository;
    private final StagiaireRepository stagiaireRepository;
    private final ProjetRepository    projetRepository;

    // ── Liste de tous les départements ────────────────────────────────────

    public List<String> getAllDepartements() {
        return Arrays.stream(Departement.values())
                .map(Departement::getLabel)
                .toList();
    }

    // ── Assigner département à un utilisateur ────────────────────────────

    public User assignDepartementToUser(String userId, String departementStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Departement dept = Departement.fromString(departementStr);
        user.setDepartement(dept);
        User saved = userRepository.save(user);

        log.info("[DEPT] Département {} assigné à user {}", dept, userId);
        return saved;
    }

    // ── Tuteurs par département ───────────────────────────────────────────

    public List<User> getTuteursByDepartement(Departement departement) {
        if (departement == null) {
            return userRepository.findByRoleAndAccountStatus(
                    Role.TUTEUR, AccountStatus.APPROUVE
            );
        }
        return userRepository.findByRoleAndDepartementAndAccountStatus(
                Role.TUTEUR, departement, AccountStatus.APPROUVE
        );
    }

    // ── Stats par département ─────────────────────────────────────────────

    public record DeptStats(
            String departement,
            long   nbStagiaires,
            long   nbProjets,
            long   nbTuteurs
    ) {}

    public List<DeptStats> getStatsByDepartement() {
        return Arrays.stream(Departement.values())
                .map(dept -> {
                    long nbStagiaires = stagiaireRepository
                            .findByDepartementAndDeletedFalse(dept.getLabel()).size();
                    long nbProjets = projetRepository
                            .findByDeletedFalse(org.springframework.data.domain.Pageable.unpaged())
                            .stream()
                            .filter(p -> dept.getLabel().equals(p.getDepartement()))
                            .count();
                    long nbTuteurs = userRepository
                            .findByRoleAndDepartement(Role.TUTEUR, dept).size();

                    return new DeptStats(
                            dept.getLabel(),
                            nbStagiaires,
                            nbProjets,
                            nbTuteurs
                    );
                })
                .filter(s -> s.nbStagiaires() > 0 || s.nbProjets() > 0 || s.nbTuteurs() > 0)
                .toList();
    }
}
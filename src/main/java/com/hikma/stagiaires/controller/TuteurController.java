// DESTINATION : src/main/java/com/hikma/stagiaires/controller/TuteurController.java
package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.AccountStatus;
import com.hikma.stagiaires.model.Departement;
import com.hikma.stagiaires.model.Role;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tuteurs")
@RequiredArgsConstructor
public class TuteurController {

    private final UserRepository      userRepository;
    private final StagiaireRepository stagiaireRepository;

    @GetMapping
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<Map<String, Object>>> getTuteurs(
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String search) {

        // 1. Récupérer tous les tuteurs approuvés
        List<User> tuteurs = userRepository
                .findByRoleAndAccountStatus(Role.TUTEUR, AccountStatus.APPROUVE);

        // 2. Filtrer par département
        if (departement != null && !departement.isBlank()) {
            try {
                Departement dep = Departement.valueOf(departement);
                tuteurs = tuteurs.stream()
                        .filter(t -> dep.equals(t.getDepartement()))
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        // 3. Filtrer par recherche
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            tuteurs = tuteurs.stream()
                    .filter(t ->
                            (t.getFirstName() + " " + t.getLastName())
                                    .toLowerCase().contains(q) ||
                                    t.getEmail().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        // 4. Construire la réponse
        // FIX : utiliser HashMap au lieu de Map.of() car Map.of() rejette les valeurs null
        List<Map<String, Object>> result = tuteurs.stream().map(t -> {
            long nbStagiaires = stagiaireRepository
                    .countByTuteurIdAndDeletedFalse(t.getId());

            Map<String, Object> map = new HashMap<>();
            map.put("id",           t.getId());
            map.put("firstName",    t.getFirstName());
            map.put("lastName",     t.getLastName());
            map.put("email",        t.getEmail() != null        ? t.getEmail()                      : "");
            map.put("phone",        t.getPhone() != null        ? t.getPhone()                      : "");
            // FIX : retourner le label lisible du département (ex: "IT", "Finance")
            // null → chaîne vide pour éviter le crash Map.of()
            map.put("departement",  t.getDepartement() != null  ? t.getDepartement().getLabel()     : "");
            map.put("nbStagiaires", nbStagiaires);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
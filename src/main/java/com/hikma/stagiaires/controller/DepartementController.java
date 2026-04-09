package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.Departement;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.service.DepartementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Départements", description = "Gestion des départements")
public class DepartementController {

    private final DepartementService departementService;

    // GET /api/v1/departements
    @GetMapping("/departements")
    @Operation(summary = "Liste de tous les départements disponibles")
    public ResponseEntity<List<String>> getAllDepartements() {
        return ResponseEntity.ok(departementService.getAllDepartements());
    }

    // PUT /api/v1/users/{id}/departement
    @PutMapping("/users/{id}/departement")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Assigner un département à un utilisateur (tuteur)")
    public ResponseEntity<User> assignDepartement(
            @PathVariable String id,
            @RequestBody DepartementRequest request
    ) {
        return ResponseEntity.ok(
                departementService.assignDepartementToUser(id, request.departement())
        );
    }

    // GET /api/v1/users/tuteurs?departement=IT
    // Remplace l'ancienne méthode dans UserController (qui charge tous les users)
    @GetMapping("/users/tuteurs/by-departement")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR')")
    @Operation(summary = "Tuteurs filtrés par département")
    public ResponseEntity<List<User>> getTuteursByDepartement(
            @RequestParam(required = false) String departement
    ) {
        Departement dept = departement != null
                ? Departement.fromString(departement)
                : null;
        return ResponseEntity.ok(
                departementService.getTuteursByDepartement(dept)
        );
    }

    // GET /api/v1/departements/stats
    @GetMapping("/departements/stats")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Statistiques par département")
    public ResponseEntity<List<DepartementService.DeptStats>> getStats() {
        return ResponseEntity.ok(departementService.getStatsByDepartement());
    }

    record DepartementRequest(String departement) {}
}
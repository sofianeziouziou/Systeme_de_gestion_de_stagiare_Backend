// DESTINATION : src/main/java/com/hikma/stagiaires/controller/UserController.java
package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.AccountStatus;
import com.hikma.stagiaires.model.Departement;
import com.hikma.stagiaires.model.Role;
import com.hikma.stagiaires.model.Stagiaire;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import com.hikma.stagiaires.service.AuditLogService;
import com.hikma.stagiaires.service.StagiaireService;
import com.hikma.stagiaires.dto.stagiaire.StagiaireDTOs.StagiaireResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestion des comptes utilisateurs")
public class UserController {

    private final UserRepository      userRepository;
    private final AuditLogService     auditLogService;
    private final StagiaireService    stagiaireService;
    private final StagiaireRepository stagiaireRepository;   // FIX : ajouté
    private final PasswordEncoder     passwordEncoder;

    // ── Demandes en attente (RH) ─────────────────────────────────────────────
    @GetMapping("/pending")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Liste des comptes en attente d'approbation")
    public ResponseEntity<List<UserResponse>> getPendingUsers() {
        List<UserResponse> pending = userRepository.findAll().stream()
                .filter(u -> AccountStatus.EN_ATTENTE.equals(u.getAccountStatus()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pending);
    }

    // ── Tous les utilisateurs (RH) ──────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Liste tous les utilisateurs")
    public ResponseEntity<List<UserResponse>> getAll(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {

        List<UserResponse> users = userRepository.findAll().stream()
                .filter(u -> role == null || u.getRole().name().equals(role.toUpperCase()))
                .filter(u -> status == null || u.getAccountStatus().name().equals(status.toUpperCase()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // ── Approuver ───────────────────────────────────────────────────────────
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<UserResponse> approveUser(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + id));
        user.setAccountStatus(AccountStatus.APPROUVE);
        userRepository.save(user);

        if (Role.STAGIAIRE.equals(user.getRole())) {
            try {
                // 1. Créer la fiche si elle n'existe pas
                stagiaireService.createFicheForUser(user);

                // 2. FIX : calculer startDate/endDate depuis durationMonths
                stagiaireRepository.findByUserId(user.getId()).ifPresent(stagiaire -> {
                    Integer durationMonths = stagiaire.getDurationMonths();

                    // Calculer seulement si durée renseignée et dates pas encore fixées
                    if (durationMonths != null && durationMonths > 0
                            && stagiaire.getStartDate() == null) {
                        LocalDate startDate = LocalDate.now();
                        LocalDate endDate   = startDate.plusMonths(durationMonths);
                        stagiaire.setStartDate(startDate);
                        stagiaire.setEndDate(endDate);
                        stagiaireRepository.save(stagiaire);
                        log.info("[APPROVE] Dates calculées pour userId={} : {} → {} ({} mois)",
                                user.getId(), startDate, endDate, durationMonths);
                    }
                });

            } catch (Exception e) {
                log.warn("[APPROVE] Erreur calcul dates stagiaire userId={} : {}", id, e.getMessage());
            }
        }

        auditLogService.log(currentUser.getId(), "APPROVE_USER", "USER", id, null);
        return ResponseEntity.ok(toResponse(user));
    }

    // ── Refuser ─────────────────────────────────────────────────────────────
    @PostMapping("/{id}/refuse")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<UserResponse> refuseUser(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + id));
        user.setAccountStatus(AccountStatus.REFUSE);
        userRepository.save(user);
        auditLogService.log(currentUser.getId(), "REFUSE_USER", "USER", id, null);
        return ResponseEntity.ok(toResponse(user));
    }

    // ── DÉSACTIVER ──────────────────────────────────────────────────────────
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Désactiver un compte utilisateur")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getId().equals(id))
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte.");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + id));
        user.setAccountStatus(AccountStatus.REFUSE);
        userRepository.save(user);
        auditLogService.log(currentUser.getId(), "DEACTIVATE_USER", "USER", id, null);
        return ResponseEntity.ok(toResponse(user));
    }

    // ── RÉACTIVER ───────────────────────────────────────────────────────────
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Réactiver un compte utilisateur")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + id));
        user.setAccountStatus(AccountStatus.APPROUVE);
        userRepository.save(user);
        auditLogService.log(currentUser.getId(), "ACTIVATE_USER", "USER", id, null);
        return ResponseEntity.ok(toResponse(user));
    }

    // ── SUPPRIMER ───────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Supprimer définitivement un utilisateur")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getId().equals(id))
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte.");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + id));

        if (Role.STAGIAIRE.equals(user.getRole())) {
            try { stagiaireService.deleteByUserId(id); }
            catch (Exception ignored) {}
        }

        userRepository.deleteById(id);
        auditLogService.log(currentUser.getId(), "DELETE_USER", "USER", id, null);
        return ResponseEntity.noContent().build();
    }

    // ── Assigner tuteur à stagiaire (RH) ───────────────────────────────────
    @PutMapping("/stagiaires/{stagiaireId}/tuteur/{tuteurId}")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<StagiaireResponse> assignTuteur(
            @PathVariable String stagiaireId,
            @PathVariable String tuteurId,
            @AuthenticationPrincipal User currentUser) {

        User tuteur = userRepository.findById(tuteurId)
                .orElseThrow(() -> new NoSuchElementException("Tuteur introuvable : " + tuteurId));
        if (!Role.TUTEUR.equals(tuteur.getRole()))
            throw new IllegalArgumentException("Cet utilisateur n'est pas un tuteur.");

        com.hikma.stagiaires.dto.stagiaire.StagiaireDTOs.UpdateRequest req =
                new com.hikma.stagiaires.dto.stagiaire.StagiaireDTOs.UpdateRequest();
        req.setTuteurId(tuteurId);

        StagiaireResponse updated = stagiaireService.update(stagiaireId, req, currentUser.getId());
        auditLogService.log(currentUser.getId(), "ASSIGN_TUTEUR", "STAGIAIRE", stagiaireId, null);
        return ResponseEntity.ok(updated);
    }

    // ── Profil connecté ─────────────────────────────────────────────────────
    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(toResponse(currentUser));
    }

    @PutMapping("/me")
    @Operation(summary = "Modifier son propre profil")
    public ResponseEntity<UserResponse> updateMe(
            @RequestBody UpdateMeRequest req,
            @AuthenticationPrincipal User currentUser) {

        if (req.getFirstName() != null && !req.getFirstName().isBlank())
            currentUser.setFirstName(req.getFirstName());
        if (req.getLastName() != null && !req.getLastName().isBlank())
            currentUser.setLastName(req.getLastName());
        if (req.getPhone() != null)
            currentUser.setPhone(req.getPhone());
        if (req.getDepartement() != null && !req.getDepartement().isBlank()) {
            try {
                currentUser.setDepartement(
                        com.hikma.stagiaires.model.Departement.fromString(req.getDepartement()));
            } catch (IllegalArgumentException ignored) {}
        }

        userRepository.save(currentUser);
        return ResponseEntity.ok(toResponse(currentUser));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Changer son mot de passe")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal User currentUser) {

        if (!passwordEncoder.matches(req.getCurrentPassword(), currentUser.getPassword()))
            throw new IllegalArgumentException("Mot de passe actuel incorrect.");
        if (req.getNewPassword() == null || req.getNewPassword().length() < 8)
            throw new IllegalArgumentException("Le nouveau mot de passe doit contenir au moins 8 caractères.");

        currentUser.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(currentUser);
        return ResponseEntity.ok().build();
    }

    // ── Liste tuteurs approuvés ──────────────────────────────────────────────
    @GetMapping("/tuteurs")
    @PreAuthorize("hasRole('RH')")
    @Operation(summary = "Tuteurs approuvés, filtrables par département")
    public ResponseEntity<List<UserResponse>> getTuteurs(
            @RequestParam(required = false) String departement) {

        List<UserResponse> tuteurs = userRepository.findAll().stream()
                .filter(u -> Role.TUTEUR.equals(u.getRole())
                        && AccountStatus.APPROUVE.equals(u.getAccountStatus()))
                .filter(u -> {
                    if (departement == null || departement.isBlank()) return true;
                    try {
                        Departement dept = Departement.fromString(departement);
                        return dept.equals(u.getDepartement());
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tuteurs);
    }

    // ── Liste stagiaires approuvés ──────────────────────────────────────────
    @GetMapping("/stagiaires")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<UserResponse>> getStagiaires() {
        List<UserResponse> stagiaires = userRepository.findAll().stream()
                .filter(u -> Role.STAGIAIRE.equals(u.getRole())
                        && AccountStatus.APPROUVE.equals(u.getAccountStatus()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(stagiaires);
    }

    // ── DTOs ───────────────────────────────────────────────────────────────
    @Data public static class UserResponse {
        private String id, firstName, lastName, email, phone, role, accountStatus,
                photoUrl, departement, createdAt;
    }

    @Data public static class UpdateMeRequest {
        private String firstName, lastName, phone, departement;
    }

    @Data public static class ChangePasswordRequest {
        private String currentPassword, newPassword;
    }

    private UserResponse toResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId());
        r.setFirstName(u.getFirstName());
        r.setLastName(u.getLastName());
        r.setEmail(u.getEmail());
        r.setPhone(u.getPhone());
        r.setRole(u.getRole().name());
        r.setAccountStatus(u.getAccountStatus() != null ? u.getAccountStatus().name() : "EN_ATTENTE");
        r.setPhotoUrl(u.getPhotoUrl());
        r.setDepartement(u.getDepartement() != null ? u.getDepartement().getLabel() : null);
        r.setCreatedAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        return r;
    }

    // ── Logger ─────────────────────────────────────────────────────────────
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UserController.class);
}
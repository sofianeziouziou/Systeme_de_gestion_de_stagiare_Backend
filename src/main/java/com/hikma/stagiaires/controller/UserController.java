package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.AccountStatus;
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
    private final StagiaireRepository stagiaireRepository;
    private final AuditLogService     auditLogService;
    private final StagiaireService    stagiaireService;
    private final PasswordEncoder     passwordEncoder;

    // ── Demandes en attente (RH) ──────────────────────────────────────────
    @GetMapping("/pending")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<UserResponse>> getPendingUsers() {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .filter(u -> AccountStatus.EN_ATTENTE.equals(u.getAccountStatus()))
                        .map(this::toResponse).collect(Collectors.toList()));
    }

    // ── Tous les utilisateurs (RH) ────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<UserResponse>> getAll(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .filter(u -> role == null || u.getRole().name().equals(role.toUpperCase()))
                        .filter(u -> status == null || u.getAccountStatus().name().equals(status.toUpperCase()))
                        .map(this::toResponse).collect(Collectors.toList()));
    }

    // ── Approuver ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<UserResponse> approveUser(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + id));

        user.setAccountStatus(AccountStatus.APPROUVE);
        userRepository.save(user);

        // ── Auto-créer la fiche stagiaire si rôle STAGIAIRE ──────────────
        if (Role.STAGIAIRE.equals(user.getRole())) {
            boolean ficheExiste = stagiaireRepository.findByUserId(user.getId()).isPresent();
            if (!ficheExiste) {
                Stagiaire fiche = Stagiaire.builder()
                        .userId(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .status(com.hikma.stagiaires.model.StagiaireStatus.EN_COURS)
                        .build();
                stagiaireRepository.save(fiche);
            }
        }

        auditLogService.log(currentUser.getId(), "APPROVE_USER", "USER", id, null);
        return ResponseEntity.ok(toResponse(user));
    }

    // ── Refuser ───────────────────────────────────────────────────────────
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

    // ── Assigner tuteur à stagiaire (RH) ──────────────────────────────────
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

    // ── Profil connecté (GET) ─────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(toResponse(currentUser));
    }

    // ── Modifier son propre profil (PUT) ──────────────────────────────────
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @RequestBody UpdateMeRequest req,
            @AuthenticationPrincipal User currentUser) {

        if (req.getFirstName() != null && !req.getFirstName().isBlank())
            currentUser.setFirstName(req.getFirstName());
        if (req.getLastName() != null && !req.getLastName().isBlank())
            currentUser.setLastName(req.getLastName());
        if (req.getPhone() != null)
            currentUser.setPhone(req.getPhone());

        userRepository.save(currentUser);

        // Sync fiche stagiaire si nécessaire
        if (Role.STAGIAIRE.equals(currentUser.getRole())) {
            stagiaireRepository.findByUserId(currentUser.getId()).ifPresent(s -> {
                if (req.getFirstName() != null) s.setFirstName(req.getFirstName());
                if (req.getLastName()  != null) s.setLastName(req.getLastName());
                stagiaireRepository.save(s);
            });
        }

        return ResponseEntity.ok(toResponse(currentUser));
    }

    // ── Changer mot de passe ──────────────────────────────────────────────
    @PutMapping("/me/password")
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

    // ── Liste tuteurs approuvés ───────────────────────────────────────────
    @GetMapping("/tuteurs")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<UserResponse>> getTuteurs() {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .filter(u -> Role.TUTEUR.equals(u.getRole())
                                && AccountStatus.APPROUVE.equals(u.getAccountStatus()))
                        .map(this::toResponse).collect(Collectors.toList()));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────
    @Data
    public static class UserResponse {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String role;
        private String accountStatus;
        private String photoUrl;
        private String createdAt;
    }

    @Data
    public static class UpdateMeRequest {
        private String firstName;
        private String lastName;
        private String phone;
    }

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
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
        r.setCreatedAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        return r;
    }
}
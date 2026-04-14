package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.PasswordResetRequest;
import com.hikma.stagiaires.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService resetService;

    // ─── PUBLIC : Stagiaire / Tuteur envoie une demande ─────────────────────
    // Accessible sans être connecté (page login)

    @PostMapping("/request")
    public ResponseEntity<?> createRequest(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email requis."));
        }
        try {
            resetService.createRequest(email);
            return ResponseEntity.ok(
                    Map.of("message", "Demande envoyée. Le RH vous contactera par email.")
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── RH : liste des demandes en attente ─────────────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<List<PasswordResetRequest>> getPendingRequests() {
        return ResponseEntity.ok(resetService.getPendingRequests());
    }

    // ─── RH : approuver une demande ─────────────────────────────────────────

    @PostMapping("/approve/{id}")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<?> approveRequest(
            @PathVariable String id,
            Authentication auth) {
        try {
            resetService.approveRequest(id, auth.getName());
            return ResponseEntity.ok(
                    Map.of("message", "Demande approuvée. Nouveau mot de passe envoyé par email.")
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── RH : rejeter une demande ───────────────────────────────────────────

    @PostMapping("/reject/{id}")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<?> rejectRequest(
            @PathVariable String id,
            Authentication auth) {
        try {
            resetService.rejectRequest(id, auth.getName());
            return ResponseEntity.ok(
                    Map.of("message", "Demande rejetée. Utilisateur notifié par email.")
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── User connecté : changer son mot de passe ───────────────────────────

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String userId = body.get("userId");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (userId == null || oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Tous les champs sont requis."));
        }

        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le mot de passe doit contenir au moins 8 caractères."));
        }

        try {
            resetService.changePassword(userId, oldPassword, newPassword);
            return ResponseEntity.ok(
                    Map.of("message", "Mot de passe modifié avec succès.")
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
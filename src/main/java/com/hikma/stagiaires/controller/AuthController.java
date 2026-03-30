package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.dto.auth.*;
import com.hikma.stagiaires.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentification 2FA")
public class AuthController {

    private final AuthService authService;

    // ── Inscription ───────────────────────────────────────────────────────
    @PostMapping("/register")
    @Operation(summary = "Inscription (crée le compte, en attente d'approbation)")
    public ResponseEntity<String> register(@RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.ok("Compte créé. En attente d'approbation.");
    }

    // ── ÉTAPE 1 : email+password → envoie OTP ────────────────────────────
    @PostMapping("/login")
    @Operation(summary = "Étape 1 — Vérification identifiants + envoi OTP")
    public ResponseEntity<LoginStep1Response> loginStep1(
            @RequestBody LoginStep1Request req) {
        return ResponseEntity.ok(authService.loginStep1(req));
    }

    // ── ÉTAPE 2 : code OTP → reçoit JWT ──────────────────────────────────
    @PostMapping("/verify-otp")
    @Operation(summary = "Étape 2 — Vérification OTP + délivrance JWT")
    public ResponseEntity<AuthResponse> loginStep2(
            @RequestBody LoginStep2Request req) {
        return ResponseEntity.ok(authService.loginStep2(req));
    }

    // ── Renvoyer l'OTP ────────────────────────────────────────────────────
    @PostMapping("/resend-otp")
    @Operation(summary = "Renvoyer le code OTP (si non reçu ou expiré)")
    public ResponseEntity<String> resendOtp(@RequestBody ResendOtpRequest req) {
        authService.resendOtp(req);
        return ResponseEntity.ok("Code renvoyé.");
    }
}
package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.dto.onboarding.OnboardingDTOs.OnboardingStatusResponse;
import com.hikma.stagiaires.dto.onboarding.OnboardingDTOs.OnboardingStepRequest;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "Wizard complétion profil stagiaire")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/status")
    @PreAuthorize("hasRole('STAGIAIRE')")
    @Operation(summary = "Retourne l'état actuel du profil (%, étape, champs manquants)")
    public ResponseEntity<OnboardingStatusResponse> getStatus(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(onboardingService.getStatus(currentUser.getId()));
    }

    @PostMapping("/step")
    @PreAuthorize("hasRole('STAGIAIRE')")
    @Operation(summary = "Soumettre une étape du wizard")
    public ResponseEntity<OnboardingStatusResponse> submitStep(
            @RequestBody OnboardingStepRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(onboardingService.submitStep(currentUser.getId(), req));
    }
}
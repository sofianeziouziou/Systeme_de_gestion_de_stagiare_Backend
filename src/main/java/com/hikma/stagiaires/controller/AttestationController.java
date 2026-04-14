package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.service.AttestationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attestation")
@RequiredArgsConstructor
public class AttestationController {

    private final AttestationService attestationService;

    @GetMapping("/{stagiaireId}")
    @PreAuthorize("hasAnyRole('STAGIAIRE', 'RH')")
    public ResponseEntity<byte[]> getAttestation(
            @PathVariable String stagiaireId,
            Authentication auth) {
        try {
            // RH → accès libre
            // Stagiaire → vérifie que c'est son profil ET stage TERMINE
            boolean isRH = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_RH"));

            if (!isRH) {
                attestationService.verifierAccesStagiaire(stagiaireId, auth.getName());
            }

            byte[] pdf      = attestationService.genererAttestation(stagiaireId);
            String filename = "attestation_stage_" + stagiaireId + ".pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length))
                    .body(pdf);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
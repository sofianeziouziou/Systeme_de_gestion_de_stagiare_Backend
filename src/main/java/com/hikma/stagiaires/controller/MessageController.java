// DESTINATION : src/main/java/com/hikma/stagiaires/controller/MessageController.java
package com.hikma.stagiaires.controller;

import com.hikma.stagiaires.model.Message;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Messagerie bidirectionnelle par projet")
public class MessageController {

    private final MessageService messageService;

    // ── GET — Messages d'un projet ────────────────────────────────────────
    @GetMapping("/projet/{projetId}")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    @Operation(summary = "Récupérer tous les messages d'un projet")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable String projetId,
            @AuthenticationPrincipal User currentUser) {

        // Marquer comme lus automatiquement à la lecture
        messageService.markAsRead(projetId, currentUser.getId());
        return ResponseEntity.ok(messageService.getByProjet(projetId));
    }

    // ── POST — Envoyer un message ─────────────────────────────────────────
    @PostMapping("/projet/{projetId}")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    @Operation(summary = "Envoyer un message dans un projet")
    public ResponseEntity<Message> send(
            @PathVariable String projetId,
            @RequestBody SendMessageRequest req,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                messageService.send(projetId, req.getContent(), currentUser)
        );
    }

    // ── GET — Nombre de messages non lus dans un projet ───────────────────
    @GetMapping("/projet/{projetId}/unread")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    @Operation(summary = "Nombre de messages non lus pour l'utilisateur connecté")
    public ResponseEntity<Map<String, Long>> countUnread(
            @PathVariable String projetId,
            @AuthenticationPrincipal User currentUser) {

        long count = messageService.countUnread(projetId, currentUser.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ── PATCH — Marquer tous les messages d'un projet comme lus ──────────
    @PatchMapping("/projet/{projetId}/read")
    @PreAuthorize("hasAnyRole('RH', 'TUTEUR', 'STAGIAIRE')")
    @Operation(summary = "Marquer tous les messages du projet comme lus")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String projetId,
            @AuthenticationPrincipal User currentUser) {

        messageService.markAsRead(projetId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class SendMessageRequest {
        private String content;
    }
}
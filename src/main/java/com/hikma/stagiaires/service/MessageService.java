// DESTINATION : src/main/java/com/hikma/stagiaires/service/MessageService.java
package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.MessageRepository;
import com.hikma.stagiaires.repository.NotificationRepository;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository    messageRepository;
    private final ProjetRepository     projetRepository;
    private final UserRepository       userRepository;
    private final NotificationService  notificationService;

    // ── Envoyer un message dans un projet ────────────────────────────────
    public Message send(String projetId, String content, User sender) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide");
        }

        // Vérifier que le projet existe
        Projet projet = projetRepository.findById(projetId)
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + projetId));

        Message message = Message.builder()
                .projetId(projetId)
                .senderId(sender.getId())
                .senderName(sender.getFirstName() + " " + sender.getLastName())
                .senderRole(sender.getRole().name())
                .content(content.trim())
                .readBy(List.of(sender.getId())) // l'expéditeur a déjà lu son propre message
                .build();

        Message saved = messageRepository.save(message);
        log.info("[MESSAGE] Envoyé dans projet={} par userId={}", projetId, sender.getId());

        // ── Notifier les autres participants du projet ─────────────────────
        notifyParticipants(projet, saved, sender);

        return saved;
    }

    // ── Récupérer les messages d'un projet ────────────────────────────────
    public List<Message> getByProjet(String projetId) {
        projetRepository.findById(projetId)
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + projetId));
        return messageRepository.findByProjetIdOrderByCreatedAtAsc(projetId);
    }

    // ── Marquer les messages comme lus ────────────────────────────────────
    public void markAsRead(String projetId, String userId) {
        List<Message> messages = messageRepository.findByProjetIdOrderByCreatedAtAsc(projetId);
        List<Message> toUpdate = new ArrayList<>();

        for (Message m : messages) {
            if (!m.getReadBy().contains(userId)) {
                List<String> newReadBy = new ArrayList<>(m.getReadBy());
                newReadBy.add(userId);
                m.setReadBy(newReadBy);
                toUpdate.add(m);
            }
        }

        if (!toUpdate.isEmpty()) {
            messageRepository.saveAll(toUpdate);
        }
    }

    // ── Compter les messages non lus pour un user dans un projet ──────────
    public long countUnread(String projetId, String userId) {
        return messageRepository.countByProjetIdAndReadByNotContaining(projetId, userId);
    }

    // ── Helper : notifier les participants ────────────────────────────────
    private void notifyParticipants(Projet projet, Message message, User sender) {
        String notifTitle   = "Nouveau message de " + sender.getFirstName();
        String notifContent = message.getContent().length() > 60
                ? message.getContent().substring(0, 60) + "..."
                : message.getContent();

        // Notifier le tuteur (si c'est pas lui l'expéditeur)
        if (projet.getTuteurId() != null && !projet.getTuteurId().equals(sender.getId())) {
            notificationService.createNotification(
                    projet.getTuteurId(),
                    NotificationType.PROJET_ASSIGNE, // on réutilise un type existant
                    notifTitle,
                    notifContent,
                    projet.getId()
            );
        }

        // Notifier les stagiaires (si c'est pas eux l'expéditeur)
        if (projet.getStagiaireIds() != null) {
            for (String userId : projet.getStagiaireIds()) {
                if (!userId.equals(sender.getId())) {
                    notificationService.createNotification(
                            userId,
                            NotificationType.PROJET_ASSIGNE,
                            notifTitle,
                            notifContent,
                            projet.getId()
                    );
                }
            }
        }
    }
}
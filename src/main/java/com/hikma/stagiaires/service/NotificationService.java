package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.NotificationRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;  // ← AJOUTÉ pour trouver les RH

    // ── Deadline proche ───────────────────────────────────────────────────
    public void notifyDeadlineProche(Projet projet) {
        // 1. Notifier le tuteur
        if (projet.getTuteurId() != null) {
            createIfNotExists(
                    projet.getTuteurId(),
                    NotificationType.PROJET_DEADLINE_PROCHE,
                    "Deadline approche",
                    "Le projet \"" + projet.getTitle() + "\" arrive à échéance dans moins de 7 jours.",
                    projet.getId()
            );
        }

        // 2. Notifier tous les RH approuvés
        notifyAllRH(
                NotificationType.PROJET_DEADLINE_PROCHE,
                "Deadline approche",
                "Le projet \"" + projet.getTitle() + "\" arrive à échéance dans moins de 7 jours.",
                projet.getId()
        );
    }

    // ── Projet en retard ──────────────────────────────────────────────────
    public void notifyProjetEnRetard(Projet projet) {
        // 1. Notifier le tuteur
        if (projet.getTuteurId() != null) {
            createIfNotExists(
                    projet.getTuteurId(),
                    NotificationType.PROJET_EN_RETARD,
                    "Projet en retard",
                    "Le projet \"" + projet.getTitle() + "\" a dépassé sa date de fin prévue.",
                    projet.getId()
            );
        }

        // 2. Notifier tous les RH approuvés
        notifyAllRH(
                NotificationType.PROJET_EN_RETARD,
                "Projet en retard",
                "Le projet \"" + projet.getTitle() + "\" a dépassé sa date de fin prévue.",
                projet.getId()
        );
    }

    // ── Sans mise à jour ──────────────────────────────────────────────────
    public void notifySansMiseAJour(Projet projet) {
        if (projet.getTuteurId() != null) {
            createIfNotExists(
                    projet.getTuteurId(),
                    NotificationType.PROJET_SANS_MISE_A_JOUR,
                    "Projet sans mise à jour",
                    "Le projet \"" + projet.getTitle() + "\" n'a pas été mis à jour depuis 5 jours.",
                    projet.getId()
            );
        }
    }

    // ── Évaluation soumise ────────────────────────────────────────────────
    public void notifyEvaluationSoumise(Evaluation evaluation) {
        // Notifier tous les RH
        notifyAllRH(
                NotificationType.EVALUATION_SOUMISE,
                "Nouvelle évaluation soumise",
                "Une évaluation a été soumise pour le stagiaire ID : " + evaluation.getStagiaireId(),
                evaluation.getId()
        );
        log.info("Évaluation soumise pour stagiaire {}", evaluation.getStagiaireId());
    }

    // ── Évaluation validée ────────────────────────────────────────────────
    public void notifyEvaluationValidee(Evaluation evaluation) {
        // Notifier le stagiaire
        createIfNotExists(
                evaluation.getStagiaireId(),
                NotificationType.EVALUATION_VALIDEE,
                "Évaluation validée",
                "Votre évaluation a été validée. Score : " + evaluation.getScoreGlobal(),
                evaluation.getId()
        );
    }

    // ── Récupérer les notifications ───────────────────────────────────────
    public List<Notification> getForUser(String userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    public long countUnread(String userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    public void markAsRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipientId().equals(userId)) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    // ── Helpers privés ────────────────────────────────────────────────────

    /**
     * Envoie une notification à tous les comptes RH approuvés.
     */
    private void notifyAllRH(NotificationType type, String title, String message, String relatedEntityId) {
        userRepository.findByRoleAndAccountStatus(Role.RH, AccountStatus.APPROUVE)
                .forEach(rh -> createIfNotExists(rh.getId(), type, title, message, relatedEntityId));
    }

    /**
     * Crée une notification seulement si elle n'existe pas déjà (non lue)
     * pour éviter les doublons à chaque passage du scheduler.
     */
    private void createIfNotExists(String recipientId, NotificationType type,
                                   String title, String message, String relatedEntityId) {
        // Vérifie qu'il n'existe pas déjà une notification non lue du même type pour la même entité
        boolean exists = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .anyMatch(n -> type.equals(n.getType())
                        && relatedEntityId != null
                        && relatedEntityId.equals(n.getRelatedEntityId())
                        && !n.isRead());

        if (!exists) {
            Notification notif = Notification.builder()
                    .recipientId(recipientId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .relatedEntityId(relatedEntityId)
                    .build();
            notificationRepository.save(notif);
        }
    }

    // Méthode publique pour créer une notification manuelle (depuis d'autres services)
    public void createNotification(String recipientId, NotificationType type,
                                   String title, String message, String relatedEntityId) {
        createIfNotExists(recipientId, type, title, message, relatedEntityId);
    }
}
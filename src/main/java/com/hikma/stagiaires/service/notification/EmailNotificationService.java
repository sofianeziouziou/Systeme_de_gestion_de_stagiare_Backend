// DESTINATION : src/main/java/com/hikma/stagiaires/service/EmailNotificationService.java
package com.hikma.stagiaires.service.notification;

import com.hikma.stagiaires.model.projet.Projet;
import com.hikma.stagiaires.model.user.AccountStatus;
import com.hikma.stagiaires.model.user.Role;
import com.hikma.stagiaires.model.user.User;
import com.hikma.stagiaires.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    private static final String FROM    = "noreply@sims.ma";
    private static final String COMPANY = "SIMS — Gestion des Stagiaires";

    // ─────────────────────────────────────────────────────────────────────
    // 1. Nouveau projet assigné → Tuteur (email standard sans acceptation)
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailNouveauProjet(Projet projet) {
        if (projet.getTuteurId() == null) return;

        userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
            String sujet = "Nouveau projet assigné : " + projet.getTitle();
            String corps = buildHeader("Nouveau projet assigné")
                    + "<p style='color:#374151'>Bonjour <strong>"
                    + tuteur.getFirstName() + " " + tuteur.getLastName() + "</strong>,</p>"
                    + "<p style='color:#374151;line-height:1.7'>Un nouveau projet vous a été assigné par le département RH.</p>"
                    + buildInfoBox("📁 " + projet.getTitle(),
                    "Début : " + formatDate(projet.getStartDate())
                            + " &nbsp;|&nbsp; Fin prévue : " + formatDate(projet.getPlannedEndDate()))
                    + buildButton("Voir le projet", "http://localhost:8082/tuteur/projets")
                    + buildFooter();

            sendEmail(tuteur.getEmail(), sujet, corps);
            log.info("[EMAIL] Nouveau projet tuteur → {}", tuteur.getEmail());
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU 1 : Nouveau projet → Tuteur avec boutons Accepter / Refuser
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailNouveauProjetAvecAcceptation(Projet projet) {
        if (projet.getTuteurId() == null) return;

        userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
            String sujet = "🔔 Action requise — Nouveau projet à accepter : " + projet.getTitle();

            // Lien direct vers la page projets tuteur
            String lienAccepter = "http://localhost:8082/tuteur/projets";
            String lienRefuser  = "http://localhost:8082/tuteur/projets";

            String corps = buildHeader("Nouveau projet à accepter")
                    + "<p style='color:#374151'>Bonjour <strong>"
                    + tuteur.getFirstName() + " " + tuteur.getLastName() + "</strong>,</p>"

                    + "<p style='color:#374151;line-height:1.7'>"
                    + "Le département RH vous a assigné un nouveau projet. "
                    + "<strong>Votre confirmation est requise</strong> pour que le projet démarre.</p>"

                    + buildInfoBox("📁 " + projet.getTitle(),
                    "Début : " + formatDate(projet.getStartDate())
                            + " &nbsp;|&nbsp; Fin prévue : " + formatDate(projet.getPlannedEndDate())
                            + (projet.getDepartement() != null
                            ? " &nbsp;|&nbsp; Département : " + projet.getDepartement()
                            : ""))

                    + (projet.getDescription() != null && !projet.getDescription().isBlank()
                    ? "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;"
                      + "padding:14px;margin:16px 0'>"
                      + "<p style='margin:0;color:#374151;font-size:14px'><em>"
                      + projet.getDescription() + "</em></p></div>"
                    : "")

                    + "<p style='color:#374151;line-height:1.7;font-weight:bold;'>"
                    + "Merci de vous connecter à SIMS pour accepter ou refuser ce projet :</p>"

                    // Deux boutons côte à côte
                    + "<div style='text-align:center;margin:28px 0;display:flex;"
                    + "gap:16px;justify-content:center;flex-wrap:wrap'>"

                    + "<a href='" + lienAccepter + "' style='background:#16a34a;color:white;"
                    + "padding:14px 32px;border-radius:8px;text-decoration:none;"
                    + "font-weight:bold;font-size:15px;display:inline-block'>"
                    + "✅ Accepter le projet</a>"

                    + "<a href='" + lienRefuser + "' style='background:#dc2626;color:white;"
                    + "padding:14px 32px;border-radius:8px;text-decoration:none;"
                    + "font-weight:bold;font-size:15px;display:inline-block'>"
                    + "❌ Refuser le projet</a>"

                    + "</div>"

                    + buildWarningBox(
                    "⏰ Action requise dans les 24 heures",
                    "Sans réponse de votre part, le RH sera notifié et pourra réassigner le projet.")

                    + buildFooter();

            sendEmail(tuteur.getEmail(), sujet, corps);
            log.info("[EMAIL] Nouveau projet (acceptation requise) → {}", tuteur.getEmail());
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU 2 : Projet accepté → Stagiaires + RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetAccepte(Projet projet, String messageTuteur) {
        // Nom du tuteur
        String tuteurNom = projet.getTuteurId() != null
                ? userRepository.findById(projet.getTuteurId())
                  .map(u -> u.getFirstName() + " " + u.getLastName())
                  .orElse("Le tuteur")
                : "Le tuteur";

        // ── Email aux stagiaires ──────────────────────────────────────────
        if (projet.getStagiaireIds() != null) {
            for (String userId : projet.getStagiaireIds()) {
                userRepository.findById(userId).ifPresent(stagiaire -> {
                    String sujet = "🎉 Votre projet a été accepté : " + projet.getTitle();

                    String corps = buildHeader("Projet accepté !")
                            + "<p style='color:#374151'>Bonjour <strong>"
                            + stagiaire.getFirstName() + " " + stagiaire.getLastName() + "</strong>,</p>"

                            + "<p style='color:#374151;line-height:1.7'>Bonne nouvelle ! "
                            + "<strong>" + tuteurNom + "</strong> a accepté d'encadrer votre projet. "
                            + "Votre stage peut maintenant commencer !</p>"

                            + buildSuccessBox("📁 " + projet.getTitle(),
                            "Tuteur : " + tuteurNom
                                    + " &nbsp;|&nbsp; Début : " + formatDate(projet.getStartDate())
                                    + " &nbsp;|&nbsp; Fin : " + formatDate(projet.getPlannedEndDate()))

                            + (messageTuteur != null && !messageTuteur.isBlank()
                            ? "<div style='background:#f0fdf4;border:1px solid #86efac;"
                              + "border-radius:8px;padding:14px;margin:16px 0'>"
                              + "<p style='margin:0;color:#14532d;font-weight:bold'>Message de votre tuteur :</p>"
                              + "<p style='margin:8px 0 0;color:#16a34a;font-style:italic'>\""
                              + messageTuteur + "\"</p></div>"
                            : "")

                            + "<p style='color:#374151;line-height:1.7'>"
                            + "Connectez-vous à SIMS pour consulter les détails de votre projet.</p>"

                            + buildButton("Voir mon projet", "http://localhost:8082/stagiaire/projets")
                            + buildFooter();

                    sendEmail(stagiaire.getEmail(), sujet, corps);
                    log.info("[EMAIL] Projet accepté → stagiaire {}", stagiaire.getEmail());
                });
            }
        }

        // ── Email au RH ───────────────────────────────────────────────────
        String sujetRH = "✅ Projet accepté par " + tuteurNom + " : " + projet.getTitle();
        envoyerEmailTousRH(sujetRH,
                buildHeader("Projet accepté !")
                        + "<p style='color:#374151'>Le tuteur <strong>" + tuteurNom
                        + "</strong> a accepté d'encadrer le projet "
                        + "<strong>" + projet.getTitle() + "</strong>.</p>"
                        + buildSuccessBox(
                        "Projet : " + projet.getTitle(),
                        "Le projet est maintenant actif et les stagiaires ont été notifiés.")
                        + buildButton("Voir dans le dashboard", "http://localhost:8082/rh/projets")
                        + buildFooter()
        );

        log.info("[EMAIL] Projet accepté notifications envoyées pour projet={}", projet.getId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU 3 : Projet refusé → RH (avec raison)
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetRefuse(Projet projet, String raison) {
        String tuteurNom = projet.getTuteurId() != null
                ? userRepository.findById(projet.getTuteurId())
                  .map(u -> u.getFirstName() + " " + u.getLastName())
                  .orElse("Le tuteur")
                : "Le tuteur";

        String sujet = "⚠️ Projet refusé — Action requise : " + projet.getTitle();

        // Lien vers la page projets RH pour réassigner
        String lienReassigner = "http://localhost:8082/rh/projets";

        envoyerEmailTousRH(sujet,
                buildHeader("Projet refusé — Action requise")

                        + "<p style='color:#374151'>Le tuteur <strong>" + tuteurNom
                        + "</strong> a <strong style='color:#dc2626'>refusé</strong> "
                        + "d'encadrer le projet <strong>" + projet.getTitle() + "</strong>.</p>"

                        + buildDangerBox(
                        "Raison du refus : " + (raison != null && !raison.isBlank()
                                ? raison : "Aucune raison fournie"),
                        "Projet : " + projet.getTitle()
                                + " &nbsp;|&nbsp; Début prévu : " + formatDate(projet.getStartDate()))

                        + "<p style='color:#374151;line-height:1.7;font-weight:bold'>"
                        + "Action requise : vous devez assigner un nouveau tuteur à ce projet.</p>"

                        + "<p style='color:#374151;line-height:1.7'>"
                        + "Connectez-vous à SIMS, accédez aux projets et cliquez sur "
                        + "<strong>\"Réassigner un tuteur\"</strong> pour ce projet.</p>"

                        + buildButton("Gérer le projet", lienReassigner)
                        + buildFooter()
        );

        log.info("[EMAIL] Projet refusé notifications RH envoyées pour projet={}", projet.getId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU 4 : Rappel acceptation — projet en attente depuis +24h
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailRappelAcceptation(Projet projet) {
        if (projet.getTuteurId() == null) return;

        userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
            String sujet = "⏰ Rappel — Projet en attente de votre réponse : " + projet.getTitle();

            String corps = buildHeader("Rappel — Action requise")
                    + "<p style='color:#374151'>Bonjour <strong>"
                    + tuteur.getFirstName() + " " + tuteur.getLastName() + "</strong>,</p>"

                    + "<p style='color:#374151;line-height:1.7'>"
                    + "Un projet vous attend toujours. Merci de confirmer votre disponibilité "
                    + "en acceptant ou refusant ce projet.</p>"

                    + buildWarningBox("📁 " + projet.getTitle(),
                    "En attente depuis hier &nbsp;|&nbsp; Fin prévue : "
                            + formatDate(projet.getPlannedEndDate()))

                    + "<div style='text-align:center;margin:28px 0;display:flex;"
                    + "gap:16px;justify-content:center;flex-wrap:wrap'>"

                    + "<a href='http://localhost:8082/tuteur/projets' "
                    + "style='background:#16a34a;color:white;padding:14px 32px;"
                    + "border-radius:8px;text-decoration:none;font-weight:bold;"
                    + "font-size:15px;display:inline-block'>✅ Accepter</a>"

                    + "<a href='http://localhost:8082/tuteur/projets' "
                    + "style='background:#dc2626;color:white;padding:14px 32px;"
                    + "border-radius:8px;text-decoration:none;font-weight:bold;"
                    + "font-size:15px;display:inline-block'>❌ Refuser</a>"

                    + "</div>"
                    + buildFooter();

            sendEmail(tuteur.getEmail(), sujet, corps);
            log.info("[EMAIL] Rappel acceptation → {}", tuteur.getEmail());
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. Deadline dans 3 jours → Tuteur + tous les RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailDeadlineProche(Projet projet) {
        long joursRestants = projet.getPlannedEndDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), projet.getPlannedEndDate()) : 0;
        String sujet = "⏰ Deadline dans " + joursRestants + " jour(s) : " + projet.getTitle();

        if (projet.getTuteurId() != null) {
            userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
                String corps = buildHeader("Deadline approche !")
                        + "<p style='color:#374151'>Bonjour <strong>"
                        + tuteur.getFirstName() + " " + tuteur.getLastName() + "</strong>,</p>"
                        + "<p style='color:#374151;line-height:1.7'>Le projet <strong>"
                        + projet.getTitle() + "</strong> arrive à échéance dans "
                        + "<strong style='color:#d97706'>" + joursRestants + " jour(s)</strong>.</p>"
                        + buildWarningBox(
                        "Avancement actuel : " + (projet.getProgress() != null ? projet.getProgress() : 0) + "%",
                        "Date limite : " + formatDate(projet.getPlannedEndDate()))
                        + buildButton("Mettre à jour le projet", "http://localhost:8082/tuteur/projets")
                        + buildFooter();
                sendEmail(tuteur.getEmail(), sujet, corps);
                log.info("[EMAIL] Deadline proche tuteur → {}", tuteur.getEmail());
            });
        }

        envoyerEmailTousRH(sujet,
                buildHeader("Deadline approche !")
                        + "<p style='color:#374151'>Le projet <strong>" + projet.getTitle()
                        + "</strong> arrive à échéance dans <strong style='color:#d97706'>"
                        + joursRestants + " jour(s)</strong>.</p>"
                        + buildInfoBox("Projet : " + projet.getTitle(),
                        "Avancement : " + (projet.getProgress() != null ? projet.getProgress() : 0)
                                + "% &nbsp;|&nbsp; Fin : " + formatDate(projet.getPlannedEndDate()))
                        + buildButton("Voir dans le dashboard", "http://localhost:8082/rh/dashboard")
                        + buildFooter());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. Projet en retard → Tuteur + tous les RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetEnRetard(Projet projet) {
        long joursRetard = projet.getPlannedEndDate() != null
                ? ChronoUnit.DAYS.between(projet.getPlannedEndDate(), LocalDate.now()) : 0;
        String sujet = "🔴 Projet en retard : " + projet.getTitle();

        if (projet.getTuteurId() != null) {
            userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
                String corps = buildHeader("Projet en retard")
                        + "<p style='color:#374151'>Bonjour <strong>"
                        + tuteur.getFirstName() + " " + tuteur.getLastName() + "</strong>,</p>"
                        + "<p style='color:#374151;line-height:1.7'>Le projet <strong>"
                        + projet.getTitle() + "</strong> a dépassé sa date de fin prévue de "
                        + "<strong style='color:#dc2626'>" + joursRetard + " jour(s)</strong>.</p>"
                        + buildDangerBox("Retard : " + joursRetard + " jour(s)",
                        "Avancement : " + (projet.getProgress() != null ? projet.getProgress() : 0)
                                + "% &nbsp;|&nbsp; Fin prévue : " + formatDate(projet.getPlannedEndDate()))
                        + buildButton("Voir le projet", "http://localhost:8082/tuteur/projets")
                        + buildFooter();
                sendEmail(tuteur.getEmail(), sujet, corps);
                log.info("[EMAIL] Projet retard tuteur → {}", tuteur.getEmail());
            });
        }

        envoyerEmailTousRH(sujet,
                buildHeader("Projet en retard !")
                        + "<p style='color:#374151'>Le projet <strong>" + projet.getTitle()
                        + "</strong> est en retard de <strong style='color:#dc2626'>"
                        + joursRetard + " jour(s)</strong>.</p>"
                        + buildDangerBox("Tuteur : " + getTuteurNom(projet.getTuteurId()),
                        "Avancement : " + (projet.getProgress() != null ? projet.getProgress() : 0)
                                + "% &nbsp;|&nbsp; Fin prévue : " + formatDate(projet.getPlannedEndDate()))
                        + buildButton("Voir dans le dashboard", "http://localhost:8082/rh/dashboard")
                        + buildFooter());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. Projet terminé → tous les RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetTermine(Projet projet) {
        String sujet = "✅ Projet terminé : " + projet.getTitle();
        envoyerEmailTousRH(sujet,
                buildHeader("Projet terminé !")
                        + "<p style='color:#374151'>Le projet <strong>" + projet.getTitle()
                        + "</strong> vient d'être marqué comme terminé.</p>"
                        + buildSuccessBox("Tuteur : " + getTuteurNom(projet.getTuteurId()),
                        "Avancement final : " + (projet.getProgress() != null ? projet.getProgress() : 100) + "%")
                        + "<p style='color:#374151;line-height:1.7'>"
                        + "N'oubliez pas de valider les évaluations des stagiaires concernés.</p>"
                        + buildButton("Voir les évaluations", "http://localhost:8082/rh/evaluations")
                        + buildFooter());
        log.info("[EMAIL] Projet terminé → RH notifiés");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. Nouveau projet → stagiaire assigné
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetAssigneStagiaire(Projet projet, String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            String tuteurNom = projet.getTuteurId() != null
                    ? userRepository.findById(projet.getTuteurId())
                      .map(t -> t.getFirstName() + " " + t.getLastName()).orElse("—")
                    : "—";
            String sujet = "Vous avez été assigné à un projet : " + projet.getTitle();
            String corps = buildHeader("Nouveau projet de stage")
                    + "<p style='color:#374151'>Bonjour <strong>"
                    + user.getFirstName() + " " + user.getLastName() + "</strong>,</p>"
                    + "<p style='color:#374151;line-height:1.7'>"
                    + "Le département RH vous a assigné à un nouveau projet de stage.</p>"
                    + buildInfoBox("📁 " + projet.getTitle(),
                    "Tuteur : " + tuteurNom
                            + " &nbsp;|&nbsp; Début : " + formatDate(projet.getStartDate())
                            + " &nbsp;|&nbsp; Fin : " + formatDate(projet.getPlannedEndDate()))
                    + buildButton("Voir mon projet", "http://localhost:8082/stagiaire/projets")
                    + buildFooter();
            sendEmail(user.getEmail(), sujet, corps);
            log.info("[EMAIL] Projet assigné stagiaire → {}", user.getEmail());
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6-8. Reset mot de passe
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void sendPasswordResetRequestToRH(String userName, String userEmail, String role) {
        String sujet = "🔑 Demande reset mot de passe — " + userName;
        envoyerEmailTousRH(sujet,
                buildHeader("Demande de réinitialisation")
                        + "<p style='color:#374151'>Un utilisateur demande la réinitialisation de son mot de passe.</p>"
                        + buildInfoBox("👤 " + userName + " (" + role + ")", "Email : " + userEmail)
                        + buildButton("Gérer les demandes", "http://localhost:8082/rh/comptes")
                        + buildFooter());
        log.info("[EMAIL] Reset request → RH notifié pour : {}", userEmail);
    }

    @Async
    public void envoyerEmailCompteApprouve(User user) {
        String sujet = "✅ Votre compte SIMS a été approuvé";
        String corps = buildHeader("Compte approuvé !")
                + "<p style='color:#374151'>Bonjour <strong>"
                + user.getFirstName() + " " + user.getLastName() + "</strong>,</p>"
                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre compte a été approuvé par le département RH. "
                + "Vous pouvez maintenant vous connecter à SIMS.</p>"
                + buildSuccessBox("Rôle : " + user.getRole().name(),
                "Votre accès est maintenant actif.")
                + buildButton("Se connecter", "http://localhost:8082/login")
                + buildFooter();

        sendEmail(user.getEmail(), sujet, corps);
        log.info("[EMAIL] Compte approuvé → {}", user.getEmail());
    }

    @Async
    public void envoyerEmailCompteRefuse(User user) {
        String sujet = "❌ Votre demande de compte SIMS a été refusée";
        String corps = buildHeader("Demande refusée")
                + "<p style='color:#374151'>Bonjour <strong>"
                + user.getFirstName() + " " + user.getLastName() + "</strong>,</p>"
                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre demande de création de compte a été refusée par le département RH.</p>"
                + buildDangerBox("Demande refusée",
                "Contactez directement le département RH pour plus d'informations.")
                + buildFooter();

        sendEmail(user.getEmail(), sujet, corps);
        log.info("[EMAIL] Compte refusé → {}", user.getEmail());
    }

    @Async
    public void sendNewPasswordToUser(String toEmail, String userName, String newPassword) {
        String sujet = "🔐 Votre nouveau mot de passe — SIMS Hikma";
        String corps = buildHeader("Nouveau mot de passe")
                + "<p style='color:#374151'>Bonjour <strong>" + userName + "</strong>,</p>"
                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre demande de réinitialisation a été approuvée par le département RH.</p>"
                + buildSuccessBox("🔑 Mot de passe temporaire : " + newPassword,
                "Connectez-vous et changez-le immédiatement depuis votre profil.")
                + buildButton("Se connecter", "http://localhost:8082/login")
                + buildFooter();
        sendEmail(toEmail, sujet, corps);
        log.info("[EMAIL] Nouveau mdp envoyé à : {}", toEmail);
    }

    @Async
    public void sendPasswordResetRejected(String toEmail, String userName) {
        String sujet = "❌ Demande refusée — SIMS Hikma";
        String corps = buildHeader("Demande refusée")
                + "<p style='color:#374151'>Bonjour <strong>" + userName + "</strong>,</p>"
                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre demande de réinitialisation de mot de passe a été refusée par le département RH.</p>"
                + buildDangerBox("Demande refusée",
                "Contactez directement le département RH pour plus d'informations.")
                + buildFooter();
        sendEmail(toEmail, sujet, corps);
        log.info("[EMAIL] Reset refusé notifié à : {}", toEmail);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers privés
    // ─────────────────────────────────────────────────────────────────────

    private void envoyerEmailTousRH(String sujet, String corps) {
        List<User> rhUsers = userRepository.findByRoleAndAccountStatus(Role.RH, AccountStatus.APPROUVE);
        rhUsers.forEach(rh -> {
            sendEmail(rh.getEmail(), sujet, corps);
            log.info("[EMAIL] → RH : {}", rh.getEmail());
        });
    }

    private void sendEmail(String to, String sujet, String corps) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM);
            helper.setTo(to);
            helper.setSubject(sujet);
            helper.setText(corps, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[EMAIL] Erreur envoi à {} : {}", to, e.getMessage());
        }
    }

    private String getTuteurNom(String tuteurId) {
        if (tuteurId == null) return "Non assigné";
        return userRepository.findById(tuteurId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("Inconnu");
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "—";
        return date.getDayOfMonth() + "/" + date.getMonthValue() + "/" + date.getYear();
    }

    // ── Builders HTML ─────────────────────────────────────────────────────

    private String buildHeader(String titre) {
        return "<html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px'>"
                + "<div style='background:#1d4ed8;padding:20px;border-radius:12px 12px 0 0;text-align:center'>"
                + "<h1 style='color:white;margin:0;font-size:20px'>" + COMPANY + "</h1></div>"
                + "<div style='background:white;padding:28px;border:1px solid #e2e8f0;border-radius:0 0 12px 12px'>"
                + "<h2 style='color:#1e293b;margin:0 0 16px'>" + titre + "</h2>";
    }

    private String buildInfoBox(String ligne1, String ligne2) {
        return "<div style='background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;padding:14px;margin:16px 0'>"
                + "<p style='margin:0;color:#1d4ed8;font-weight:bold'>" + ligne1 + "</p>"
                + "<p style='margin:6px 0 0;color:#3b82f6;font-size:14px'>" + ligne2 + "</p></div>";
    }

    private String buildWarningBox(String ligne1, String ligne2) {
        return "<div style='background:#fffbeb;border:1px solid #fcd34d;border-radius:8px;padding:14px;margin:16px 0'>"
                + "<p style='margin:0;color:#92400e;font-weight:bold'>" + ligne1 + "</p>"
                + "<p style='margin:6px 0 0;color:#b45309;font-size:14px'>" + ligne2 + "</p></div>";
    }

    private String buildDangerBox(String ligne1, String ligne2) {
        return "<div style='background:#fef2f2;border:1px solid #fca5a5;border-radius:8px;padding:14px;margin:16px 0'>"
                + "<p style='margin:0;color:#991b1b;font-weight:bold'>" + ligne1 + "</p>"
                + "<p style='margin:6px 0 0;color:#dc2626;font-size:14px'>" + ligne2 + "</p></div>";
    }

    private String buildSuccessBox(String ligne1, String ligne2) {
        return "<div style='background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:14px;margin:16px 0'>"
                + "<p style='margin:0;color:#14532d;font-weight:bold'>" + ligne1 + "</p>"
                + "<p style='margin:6px 0 0;color:#16a34a;font-size:14px'>" + ligne2 + "</p></div>";
    }

    private String buildButton(String texte, String url) {
        return "<div style='text-align:center;margin:24px 0'>"
                + "<a href='" + url + "' style='background:#1d4ed8;color:white;padding:12px 28px;"
                + "border-radius:8px;text-decoration:none;font-weight:bold;font-size:15px'>"
                + texte + "</a></div>";
    }

    private String buildFooter() {
        return "<hr style='border:none;border-top:1px solid #e2e8f0;margin:24px 0'/>"
                + "<p style='font-size:12px;color:#94a3b8;text-align:center'>"
                + "Cet email a été envoyé automatiquement par SIMS.<br/>"
                + "Ne pas répondre à cet email.</p>"
                + "</div></body></html>";
    }
}
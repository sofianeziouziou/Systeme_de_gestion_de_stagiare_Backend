package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.*;
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

    private static final String FROM    = "noreply@hikma.ma";
    private static final String COMPANY = "Hikma Pharmaceuticals — SIMS";

    // ─────────────────────────────────────────────────────────────────────
    // 1. Nouveau projet assigné → Tuteur
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailNouveauProjet(Projet projet) {
        if (projet.getTuteurId() == null) return;

        userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
            String sujet = "Nouveau projet assigné : " + projet.getTitle();

            String corps = buildHeader("Nouveau projet assigné")
                    + "<p style='color:#374151'>Bonjour <strong>"
                    + tuteur.getFirstName() + " " + tuteur.getLastName()
                    + "</strong>,</p>"
                    + "<p style='color:#374151;line-height:1.7'>"
                    + "Un nouveau projet vous a été assigné par le département RH.</p>"
                    + buildInfoBox(
                    "📁 " + projet.getTitle(),
                    "Début : " + formatDate(projet.getStartDate())
                            + " &nbsp;|&nbsp; Fin prévue : "
                            + formatDate(projet.getPlannedEndDate())
            )
                    + "<p style='color:#374151;line-height:1.7'>"
                    + "Connectez-vous à SIMS pour consulter les détails.</p>"
                    + buildButton("Voir le projet", "http://localhost:8082/tuteur/projets")
                    + buildFooter();

            sendEmail(tuteur.getEmail(), sujet, corps);
            log.info("[EMAIL] Nouveau projet → {}", tuteur.getEmail());
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. Deadline dans 3 jours → Tuteur + tous les RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailDeadlineProche(Projet projet) {
        long joursRestants = projet.getPlannedEndDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), projet.getPlannedEndDate())
                : 0;

        String sujet = "⏰ Deadline dans " + joursRestants + " jour(s) : " + projet.getTitle();

        if (projet.getTuteurId() != null) {
            userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
                String corps = buildHeader("Deadline approche !")
                        + "<p style='color:#374151'>Bonjour <strong>"
                        + tuteur.getFirstName() + " " + tuteur.getLastName()
                        + "</strong>,</p>"
                        + "<p style='color:#374151;line-height:1.7'>Le projet <strong>"
                        + projet.getTitle() + "</strong> arrive à échéance dans "
                        + "<strong style='color:#d97706'>" + joursRestants + " jour(s)</strong>.</p>"
                        + buildWarningBox(
                        "Avancement actuel : " + (projet.getProgress() != null ? projet.getProgress() : 0) + "%",
                        "Date limite : " + formatDate(projet.getPlannedEndDate())
                )
                        + buildButton("Mettre à jour le projet", "http://localhost:8082/tuteur/projets")
                        + buildFooter();

                sendEmail(tuteur.getEmail(), sujet, corps);
                log.info("[EMAIL] Deadline proche tuteur → {}", tuteur.getEmail());
            });
        }

        envoyerEmailTousRH(sujet, buildHeader("Deadline approche !")
                        + "<p style='color:#374151'>Le projet <strong>" + projet.getTitle()
                        + "</strong> arrive à échéance dans <strong style='color:#d97706'>"
                        + joursRestants + " jour(s)</strong>.</p>"
                        + buildInfoBox(
                        "Projet : " + projet.getTitle(),
                        "Avancement : " + (projet.getProgress() != null ? projet.getProgress() : 0)
                                + "% &nbsp;|&nbsp; Fin : " + formatDate(projet.getPlannedEndDate())
                )
                        + buildButton("Voir dans le dashboard", "http://localhost:8082/rh/dashboard")
                        + buildFooter()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. Projet en retard → Tuteur + tous les RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetEnRetard(Projet projet) {
        long joursRetard = projet.getPlannedEndDate() != null
                ? ChronoUnit.DAYS.between(projet.getPlannedEndDate(), LocalDate.now())
                : 0;

        String sujet = "🔴 Projet en retard : " + projet.getTitle();

        if (projet.getTuteurId() != null) {
            userRepository.findById(projet.getTuteurId()).ifPresent(tuteur -> {
                String corps = buildHeader("Projet en retard")
                        + "<p style='color:#374151'>Bonjour <strong>"
                        + tuteur.getFirstName() + " " + tuteur.getLastName()
                        + "</strong>,</p>"
                        + "<p style='color:#374151;line-height:1.7'>Le projet <strong>"
                        + projet.getTitle() + "</strong> a dépassé sa date de fin prévue de "
                        + "<strong style='color:#dc2626'>" + joursRetard + " jour(s)</strong>.</p>"
                        + buildDangerBox(
                        "Retard : " + joursRetard + " jour(s)",
                        "Avancement : " + (projet.getProgress() != null ? projet.getProgress() : 0)
                                + "% &nbsp;|&nbsp; Fin prévue : " + formatDate(projet.getPlannedEndDate())
                )
                        + buildButton("Voir le projet", "http://localhost:8082/tuteur/projets")
                        + buildFooter();

                sendEmail(tuteur.getEmail(), sujet, corps);
                log.info("[EMAIL] Projet retard tuteur → {}", tuteur.getEmail());
            });
        }

        envoyerEmailTousRH(sujet, buildHeader("Projet en retard !")
                        + "<p style='color:#374151'>Le projet <strong>" + projet.getTitle()
                        + "</strong> est en retard de <strong style='color:#dc2626'>"
                        + joursRetard + " jour(s)</strong>.</p>"
                        + buildDangerBox(
                        "Tuteur : " + getTuteurNom(projet.getTuteurId()),
                        "Avancement : " + (projet.getProgress() != null ? projet.getProgress() : 0)
                                + "% &nbsp;|&nbsp; Fin prévue : " + formatDate(projet.getPlannedEndDate())
                )
                        + buildButton("Voir dans le dashboard", "http://localhost:8082/rh/dashboard")
                        + buildFooter()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. Projet terminé → tous les RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailProjetTermine(Projet projet) {
        String sujet = "✅ Projet terminé : " + projet.getTitle();

        envoyerEmailTousRH(sujet, buildHeader("Projet terminé !")
                        + "<p style='color:#374151'>Le projet <strong>" + projet.getTitle()
                        + "</strong> vient d'être marqué comme terminé.</p>"
                        + buildSuccessBox(
                        "Tuteur : " + getTuteurNom(projet.getTuteurId()),
                        "Avancement final : " + (projet.getProgress() != null ? projet.getProgress() : 100) + "%"
                )
                        + "<p style='color:#374151;line-height:1.7'>"
                        + "N'oubliez pas de valider les évaluations des stagiaires concernés.</p>"
                        + buildButton("Voir les évaluations", "http://localhost:8082/rh/evaluations")
                        + buildFooter()
        );

        log.info("[EMAIL] Projet terminé → RH notifiés");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. Reset mot de passe — Notifier RH
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void sendPasswordResetRequestToRH(String userName, String userEmail, String role) {
        String sujet = "🔑 Demande reset mot de passe — " + userName;

        envoyerEmailTousRH(sujet, buildHeader("Demande de réinitialisation")
                        + "<p style='color:#374151'>Un utilisateur demande la réinitialisation de son mot de passe.</p>"
                        + buildInfoBox(
                        "👤 " + userName + " (" + role + ")",
                        "Email : " + userEmail
                )
                        + "<p style='color:#374151;line-height:1.7'>"
                        + "Connectez-vous à SIMS pour approuver ou rejeter cette demande.</p>"
                        + buildButton("Gérer les demandes", "http://localhost:8082/rh/comptes")
                        + buildFooter()
        );

        log.info("[EMAIL] Reset request → RH notifié pour : {}", userEmail);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6. Reset mot de passe — Envoyer nouveau mdp au user
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void sendNewPasswordToUser(String toEmail, String userName, String newPassword) {
        String sujet = "🔐 Votre nouveau mot de passe — SIMS Hikma";

        String corps = buildHeader("Nouveau mot de passe")
                + "<p style='color:#374151'>Bonjour <strong>" + userName + "</strong>,</p>"
                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre demande de réinitialisation a été approuvée par le département RH.</p>"
                + buildSuccessBox(
                "🔑 Mot de passe temporaire : " + newPassword,
                "Connectez-vous et changez-le immédiatement depuis votre profil."
        )
                + buildButton("Se connecter", "http://localhost:8082/login")
                + buildFooter();

        sendEmail(toEmail, sujet, corps);
        log.info("[EMAIL] Nouveau mdp envoyé à : {}", toEmail);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7. Reset mot de passe — Notifier rejet au user
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void sendPasswordResetRejected(String toEmail, String userName) {
        String sujet = "❌ Demande refusée — SIMS Hikma";

        String corps = buildHeader("Demande refusée")
                + "<p style='color:#374151'>Bonjour <strong>" + userName + "</strong>,</p>"
                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre demande de réinitialisation de mot de passe a été refusée par le département RH.</p>"
                + buildDangerBox(
                "Demande refusée",
                "Contactez directement le département RH pour plus d'informations."
        )
                + buildFooter();

        sendEmail(toEmail, sujet, corps);
        log.info("[EMAIL] Reset refusé notifié à : {}", toEmail);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper — envoyer à tous les RH approuvés
    // ─────────────────────────────────────────────────────────────────────

    private void envoyerEmailTousRH(String sujet, String corps) {
        List<User> rhUsers = userRepository
                .findByRoleAndAccountStatus(Role.RH, AccountStatus.APPROUVE);
        rhUsers.forEach(rh -> {
            sendEmail(rh.getEmail(), sujet, corps);
            log.info("[EMAIL] → RH : {}", rh.getEmail());
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper — envoyer un email
    // ─────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────
    // Helpers — utilitaires
    // ─────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────
    // Builders HTML
    // ─────────────────────────────────────────────────────────────────────

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
                + "Cet email a été envoyé automatiquement par SIMS — Hikma Pharmaceuticals.<br/>"
                + "Ne pas répondre à cet email.</p>"
                + "</div></body></html>";
    }
}
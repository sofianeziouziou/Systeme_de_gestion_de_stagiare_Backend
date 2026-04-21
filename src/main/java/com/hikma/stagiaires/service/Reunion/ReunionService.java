package com.hikma.stagiaires.service.Reunion;

import com.hikma.stagiaires.model.notification.NotificationType;
import com.hikma.stagiaires.model.reunion.Reunion;
import com.hikma.stagiaires.repository.ReunionRepository;
import com.hikma.stagiaires.repository.UserRepository;
import com.hikma.stagiaires.service.notification.NotificationService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReunionService {

    private final ReunionRepository   reunionRepository;
    private final UserRepository      userRepository;
    private final JavaMailSender      mailSender;
    private final NotificationService notificationService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRENCH);
    private static final String FROM = "noreply@hikma.ma";

    // ── Créer ─────────────────────────────────────────────────────────────
    public Reunion creer(Reunion reunion) {
        Reunion saved = reunionRepository.save(reunion);
        envoyerEmailCreation(saved);

        if (saved.getStagiaireIds() != null) {
            for (String userId : saved.getStagiaireIds()) {
                notificationService.createNotification(
                        userId,
                        NotificationType.REUNION_PLANIFIEE,
                        "Nouvelle réunion planifiée",
                        "Le tuteur " + saved.getTuteurName()
                                + " a planifié une réunion : \"" + saved.getSujet() + "\"."
                                + " Rendez-vous le " + (saved.getDateHeure() != null
                                ? saved.getDateHeure().format(FMT) : "—"),
                        "/stagiaire/calendrier"
                );
            }
        }

        log.info("[REUNION] Créée id={} tuteur={}", saved.getId(), saved.getTuteurId());
        return saved;
    }

    // ── Modifier ──────────────────────────────────────────────────────────
    public Reunion modifier(String id, Reunion update) {
        Reunion existing = findById(id);

        if (update.getSujet()          != null) existing.setSujet(update.getSujet());
        if (update.getDateHeure()      != null) existing.setDateHeure(update.getDateHeure());
        if (update.getDureeMins()      >  0   ) existing.setDureeMins(update.getDureeMins());
        if (update.getLieu()           != null) existing.setLieu(update.getLieu());
        if (update.getNotes()          != null) existing.setNotes(update.getNotes());
        if (update.getStagiaireIds()   != null) existing.setStagiaireIds(update.getStagiaireIds());
        if (update.getStagiaireNames() != null) existing.setStagiaireNames(update.getStagiaireNames());

        Reunion saved = reunionRepository.save(existing);
        envoyerEmailModification(saved);

        if (saved.getStagiaireIds() != null) {
            for (String userId : saved.getStagiaireIds()) {
                notificationService.createNotification(
                        userId,
                        NotificationType.REUNION_PLANIFIEE,
                        "Réunion modifiée",
                        "La réunion \"" + saved.getSujet() + "\" a été modifiée."
                                + " Nouvelle date : " + (saved.getDateHeure() != null
                                ? saved.getDateHeure().format(FMT) : "—"),
                        "/stagiaire/calendrier"
                );
            }
        }

        log.info("[REUNION] Modifiée id={}", id);
        return saved;
    }

    // ── Supprimer ─────────────────────────────────────────────────────────
    public void supprimer(String id) {
        Reunion r = findById(id);

        if (r.getStagiaireIds() != null) {
            for (String userId : r.getStagiaireIds()) {
                notificationService.createNotification(
                        userId,
                        NotificationType.REUNION_DECLINEE,
                        "Réunion annulée",
                        "La réunion \"" + r.getSujet() + "\" a été annulée par le tuteur.",
                        "/stagiaire/calendrier"
                );
            }
        }

        reunionRepository.deleteById(id);
        log.info("[REUNION] Supprimée id={}", id);
    }

    // ── Confirmer ─────────────────────────────────────────────────────────
    public Reunion confirmer(String id) {
        Reunion r = findById(id);
        r.setStatut("CONFIRMEE");
        Reunion saved = reunionRepository.save(r);
        envoyerEmailConfirmation(saved);

        notificationService.createNotification(
                saved.getTuteurId(),
                NotificationType.REUNION_CONFIRMEE,
                "Réunion confirmée ✅",
                "La réunion \"" + saved.getSujet() + "\" a été confirmée par le(s) stagiaire(s).",
                "/tuteur/calendrier"
        );

        log.info("[REUNION] Confirmée id={}", id);
        return saved;
    }

    // ── Décliner ──────────────────────────────────────────────────────────
    public Reunion decliner(String id) {
        Reunion r = findById(id);
        r.setStatut("ANNULEE");
        Reunion saved = reunionRepository.save(r);
        envoyerEmailDeclin(saved);

        notificationService.createNotification(
                saved.getTuteurId(),
                NotificationType.REUNION_DECLINEE,
                "Réunion déclinée ❌",
                "La réunion \"" + saved.getSujet() + "\" a été déclinée par le(s) stagiaire(s).",
                "/tuteur/calendrier"
        );

        log.info("[REUNION] Déclinée id={}", id);
        return saved;
    }

    // ── Réunions du tuteur ────────────────────────────────────────────────
    public List<Reunion> getByTuteur(String tuteurId) {
        return reunionRepository.findByTuteurId(tuteurId);
    }

    // ── Réunions du stagiaire ─────────────────────────────────────────────
    public List<Reunion> getByStagiaire(String stagiaireId) {
        return reunionRepository.findByStagiaireIdsContaining(stagiaireId);
    }

    // ── Helper ────────────────────────────────────────────────────────────
    private Reunion findById(String id) {
        return reunionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Réunion introuvable : " + id));
    }

    // ─────────────────────────────────────────────────────────────────────
    // EMAILS
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void envoyerEmailCreation(Reunion r) {
        if (r.getStagiaireIds() == null) return;
        String dateStr = r.getDateHeure() != null ? r.getDateHeure().format(FMT) : "—";
        String corps = buildEmailHtml("📅 Nouvelle réunion planifiée",
                r.getSujet(), dateStr, r.getDureeMins(), r.getLieu(), r.getTuteurName(), "PLANIFIEE");
        for (String userId : r.getStagiaireIds()) {
            userRepository.findById(userId).ifPresent(u ->
                    sendEmail(u.getEmail(), "Nouvelle réunion : " + r.getSujet(), corps));
        }
    }

    @Async
    public void envoyerEmailModification(Reunion r) {
        if (r.getStagiaireIds() == null) return;
        String dateStr = r.getDateHeure() != null ? r.getDateHeure().format(FMT) : "—";
        String corps = buildEmailHtml("✏️ Réunion modifiée",
                r.getSujet(), dateStr, r.getDureeMins(), r.getLieu(), r.getTuteurName(), "MODIFIEE");
        for (String userId : r.getStagiaireIds()) {
            userRepository.findById(userId).ifPresent(u ->
                    sendEmail(u.getEmail(), "Réunion modifiée : " + r.getSujet(), corps));
        }
    }

    @Async
    public void envoyerEmailConfirmation(Reunion r) {
        userRepository.findById(r.getTuteurId()).ifPresent(tuteur -> {
            String dateStr = r.getDateHeure() != null ? r.getDateHeure().format(FMT) : "—";
            String corps = buildEmailHtml("✅ Réunion confirmée", r.getSujet(), dateStr,
                    r.getDureeMins(), r.getLieu(),
                    String.join(", ", r.getStagiaireNames() != null ? r.getStagiaireNames() : List.of()),
                    "CONFIRMEE");
            sendEmail(tuteur.getEmail(), "Réunion confirmée : " + r.getSujet(), corps);
        });
    }

    @Async
    public void envoyerEmailDeclin(Reunion r) {
        userRepository.findById(r.getTuteurId()).ifPresent(tuteur -> {
            String dateStr = r.getDateHeure() != null ? r.getDateHeure().format(FMT) : "—";
            String corps = buildEmailHtml("❌ Réunion déclinée", r.getSujet(), dateStr,
                    r.getDureeMins(), r.getLieu(),
                    String.join(", ", r.getStagiaireNames() != null ? r.getStagiaireNames() : List.of()),
                    "ANNULEE");
            sendEmail(tuteur.getEmail(), "Réunion déclinée : " + r.getSujet(), corps);
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
            log.error("[REUNION EMAIL] Erreur envoi à {} : {}", to, e.getMessage());
        }
    }

    private String buildEmailHtml(String titre, String sujet, String date,
                                  int duree, String lieu, String personne, String statut) {
        String couleur = switch (statut) {
            case "CONFIRMEE" -> "#16a34a";
            case "ANNULEE"   -> "#dc2626";
            default          -> "#1d4ed8";
        };
        return "<html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px'>"
                + "<div style='background:" + couleur + ";padding:20px;border-radius:12px 12px 0 0;text-align:center'>"
                + "<h1 style='color:white;margin:0;font-size:20px'>— SIMS</h1></div>"
                + "<div style='background:white;padding:28px;border:1px solid #e2e8f0;border-radius:0 0 12px 12px'>"
                + "<h2 style='color:#1e293b;margin:0 0 16px'>" + titre + "</h2>"
                + "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0'>"
                + "<p style='margin:0;color:#1d4ed8;font-weight:bold;font-size:16px'>" + sujet + "</p>"
                + "<p style='margin:8px 0 0;color:#374151'>📅 " + date + "</p>"
                + "<p style='margin:4px 0 0;color:#374151'>⏱ " + duree + " minutes</p>"
                + "<p style='margin:4px 0 0;color:#374151'>📍 " + (lieu != null ? lieu : "—") + "</p>"
                + "<p style='margin:4px 0 0;color:#374151'>👤 " + personne + "</p>"
                + "</div>"
                + "<hr style='border:none;border-top:1px solid #e2e8f0;margin:24px 0'/>"
                + "<p style='font-size:12px;color:#94a3b8;text-align:center'>Email automatique — SIMS </p>"
                + "</div></body></html>";
    }
}
package com.hikma.stagiaires.service.projet;

import com.hikma.stagiaires.dto.projet.ProjetDTOs.*;
import com.hikma.stagiaires.model.notification.NotificationType;
import com.hikma.stagiaires.model.projet.Projet;
import com.hikma.stagiaires.model.projet.Projet.TuteurAcceptation;
import com.hikma.stagiaires.model.projet.ProjetStatus;
import com.hikma.stagiaires.model.user.AccountStatus;
import com.hikma.stagiaires.model.user.Role;
import com.hikma.stagiaires.model.user.User;
import com.hikma.stagiaires.model.stagiaire.Stagiaire;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import com.hikma.stagiaires.service.commun.AuditLogService;
import com.hikma.stagiaires.service.commun.FileStorageService;
import com.hikma.stagiaires.service.notification.EmailNotificationService;
import com.hikma.stagiaires.service.notification.NotificationService;
import com.hikma.stagiaires.service.stagiaire.StagiaireResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository          projetRepository;
    private final StagiaireRepository       stagiaireRepository;
    private final UserRepository            userRepository;
    private final NotificationService       notificationService;
    private final FileStorageService        fileStorageService;
    private final AuditLogService           auditLogService;
    private final EmailNotificationService  emailNotificationService;
    private final StagiaireResolverService  stagiaireResolver;  // ✅ NOUVEAU

    // ─────────────────────────────────────────────────────────────────────
    // CRÉER un projet (RH)
    // ─────────────────────────────────────────────────────────────────────
    public ProjetResponse create(CreateRequest req, String rhId) {

        // ✅ Validation stagiaires actifs — batch (2 queries au lieu de N×3)
        if (req.getStagiaireIds() != null && !req.getStagiaireIds().isEmpty()) {
            Map<String, Stagiaire> resolved =
                    stagiaireResolver.resolveBatch(req.getStagiaireIds());

            for (String sid : req.getStagiaireIds()) {
                boolean hasActive = projetRepository
                        .findByStagiaireIdsContainingAndDeletedFalse(sid)
                        .stream()
                        .anyMatch(p -> p.getStatus() != ProjetStatus.TERMINE
                                && p.getStatus() != ProjetStatus.ANNULE);
                if (hasActive) {
                    Stagiaire s = resolved.get(sid);
                    String nom = s != null
                            ? s.getFirstName() + " " + s.getLastName()
                            : sid;
                    throw new IllegalArgumentException(
                            "Le stagiaire " + nom + " a déjà un projet actif en cours.");
                }
            }
        }

        Projet projet = Projet.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .stagiaireIds(req.getStagiaireIds())
                .tuteurId(req.getTuteurId())
                .startDate(req.getStartDate())
                .plannedEndDate(req.getPlannedEndDate())
                .technologies(req.getTechnologies() != null ? req.getTechnologies() : List.of())
                .departement(req.getDepartement())
                .sprints(List.of())
                .tuteurAcceptation(TuteurAcceptation.PENDING)
                .build();

        Projet saved = projetRepository.save(projet);
        auditLogService.log(rhId, "CREATE", "PROJET", saved.getId(), null);

        // ✅ Mise à jour tuteurId sur les stagiaires — batch resolver
        if (req.getStagiaireIds() != null && req.getTuteurId() != null) {
            Map<String, Stagiaire> resolved =
                    stagiaireResolver.resolveBatch(req.getStagiaireIds());

            for (String sid : req.getStagiaireIds()) {
                Stagiaire s = resolved.get(sid);
                if (s != null
                        && (s.getTuteurId() == null || s.getTuteurId().isBlank())) {
                    s.setTuteurId(req.getTuteurId());
                    stagiaireRepository.save(s);
                    log.info("[PROJET] tuteurId mis à jour pour stagiaire sid={}", sid);
                }
            }
        }

        if (req.getTuteurId() != null) {
            notificationService.createNotification(
                    req.getTuteurId(),
                    NotificationType.PROJET_ASSIGNE,
                    "Nouveau projet à accepter : " + req.getTitle(),
                    "Le RH vous a assigné un projet. Connectez-vous pour l'accepter ou le refuser.",
                    saved.getId());
            emailNotificationService.envoyerEmailNouveauProjetAvecAcceptation(saved);
        }

        log.info("[PROJET] Créé id={} statut acceptation=PENDING", saved.getId());
        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // TUTEUR ACCEPTE le projet
    // ─────────────────────────────────────────────────────────────────────
    public ProjetResponse accepter(String projetId, String tuteurUserId, String message) {
        Projet p = findActiveById(projetId);

        if (!tuteurUserId.equals(p.getTuteurId()))
            throw new IllegalArgumentException("Ce projet ne vous est pas assigné.");
        if (TuteurAcceptation.ACCEPTED.equals(p.getTuteurAcceptation()))
            throw new IllegalArgumentException("Ce projet est déjà accepté.");

        p.setTuteurAcceptation(TuteurAcceptation.ACCEPTED);
        Projet saved = projetRepository.save(p);
        log.info("[PROJET] Accepté id={} par tuteurId={}", projetId, tuteurUserId);

        emailNotificationService.envoyerEmailProjetAccepte(saved, message);

        if (p.getStagiaireIds() != null) {
            String tuteurNom = userRepository.findById(tuteurUserId)
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse("Le tuteur");
            for (String sid : p.getStagiaireIds()) {
                notificationService.createNotification(
                        sid, NotificationType.PROJET_ASSIGNE,
                        "Projet accepté : " + p.getTitle(),
                        tuteurNom + " a accepté d'encadrer votre projet.",
                        projetId);
            }
        }

        userRepository.findByRoleAndAccountStatus(Role.RH, AccountStatus.APPROUVE)
                .forEach(rh -> notificationService.createNotification(
                        rh.getId(), NotificationType.PROJET_ASSIGNE,
                        "Projet accepté : " + p.getTitle(),
                        "Le tuteur a accepté d'encadrer le projet \"" + p.getTitle() + "\".",
                        projetId));

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // TUTEUR REFUSE le projet
    // ─────────────────────────────────────────────────────────────────────
    public ProjetResponse refuser(String projetId, String tuteurUserId, String raison) {
        Projet p = findActiveById(projetId);

        if (!tuteurUserId.equals(p.getTuteurId()))
            throw new IllegalArgumentException("Ce projet ne vous est pas assigné.");
        if (TuteurAcceptation.ACCEPTED.equals(p.getTuteurAcceptation()))
            throw new IllegalArgumentException("Vous avez déjà accepté ce projet.");

        p.setTuteurAcceptation(TuteurAcceptation.REFUSED);
        p.setTuteurRefusRaison(raison != null ? raison : "Aucune raison fournie");
        Projet saved = projetRepository.save(p);
        log.info("[PROJET] Refusé id={} par tuteurId={} raison={}", projetId, tuteurUserId, raison);

        emailNotificationService.envoyerEmailProjetRefuse(saved, raison);

        String tuteurNom = userRepository.findById(tuteurUserId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("Le tuteur");

        userRepository.findByRoleAndAccountStatus(Role.RH, AccountStatus.APPROUVE)
                .forEach(rh -> notificationService.createNotification(
                        rh.getId(), NotificationType.PROJET_EN_RETARD,
                        "⚠️ Projet refusé : " + p.getTitle(),
                        tuteurNom + " a refusé d'encadrer ce projet."
                                + (raison != null ? " Raison : " + raison : "")
                                + " Veuillez assigner un autre tuteur.",
                        projetId));

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // RH RÉASSIGNE un nouveau tuteur
    // ─────────────────────────────────────────────────────────────────────
    public ProjetResponse reassignerTuteur(String projetId, String nouveauTuteurId, String rhId) {
        Projet p = findActiveById(projetId);

        if (!TuteurAcceptation.REFUSED.equals(p.getTuteurAcceptation()))
            throw new IllegalArgumentException(
                    "Le projet n'est pas en état REFUSED. Statut actuel : "
                            + p.getTuteurAcceptation());

        User nouveauTuteur = userRepository.findById(nouveauTuteurId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Tuteur introuvable : " + nouveauTuteurId));
        if (!Role.TUTEUR.equals(nouveauTuteur.getRole()))
            throw new IllegalArgumentException("Cet utilisateur n'est pas un tuteur.");

        String ancienTuteurId = p.getTuteurId();
        p.setTuteurId(nouveauTuteurId);
        p.setTuteurAcceptation(TuteurAcceptation.PENDING);
        p.setTuteurRefusRaison(null);
        Projet saved = projetRepository.save(p);

        auditLogService.log(rhId, "REASSIGN_TUTEUR", "PROJET", projetId, null);
        log.info("[PROJET] Tuteur réassigné id={} ancien={} nouveau={}",
                projetId, ancienTuteurId, nouveauTuteurId);

        // ✅ Mise à jour tuteurId sur stagiaires — batch
        if (p.getStagiaireIds() != null && !p.getStagiaireIds().isEmpty()) {
            Map<String, Stagiaire> resolved =
                    stagiaireResolver.resolveBatch(p.getStagiaireIds());
            resolved.values().forEach(s -> {
                s.setTuteurId(nouveauTuteurId);
                stagiaireRepository.save(s);
            });
        }

        notificationService.createNotification(
                nouveauTuteurId, NotificationType.PROJET_ASSIGNE,
                "Nouveau projet à accepter : " + p.getTitle(),
                "Le RH vous a assigné un projet suite à un refus.",
                projetId);
        emailNotificationService.envoyerEmailNouveauProjetAvecAcceptation(saved);

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GETTERS
    // ─────────────────────────────────────────────────────────────────────

    public ProjetResponse getById(String id) {
        return toResponse(findActiveById(id));
    }

    // ✅ getAll — batch complet via toResponseList
    public Page<ProjetResponse> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<Projet> projets = projetRepository.findByDeletedFalse(pageable).getContent();
        List<ProjetResponse> responses = toResponseList(projets);
        long total = projetRepository.countByDeletedFalse();
        return new PageImpl<>(responses, pageable, total);
    }

    // ✅ getByTuteur — batch
    public List<ProjetResponse> getByTuteur(String tuteurId) {
        List<Projet> projets =
                projetRepository.findByTuteurIdAndDeletedFalse(tuteurId);
        return toResponseList(projets);
    }

    // ✅ getByStagiaire — corrigé + batch
    public List<ProjetResponse> getByStagiaire(String userId) {
        List<Projet> projets = new ArrayList<>(
                projetRepository.findByStagiaireIdsContainingAndDeletedFalse(userId));

        if (projets.isEmpty()) {
            String stagiaireId = stagiaireRepository.findByUserId(userId)
                    .map(Stagiaire::getId)
                    .orElse(null);
            if (stagiaireId != null && !stagiaireId.equals(userId)) {
                List<Projet> parId = projetRepository
                        .findByStagiaireIdsContainingAndDeletedFalse(stagiaireId);
                projets.addAll(parId);
                log.info("[PROJET] getByStagiaire userId={} → stagiaireId={} → {} projets",
                        userId, stagiaireId, parId.size());
            }
        }

        return toResponseList(projets);
    }

    public List<ProjetResponse> getPendingForTuteur(String tuteurId) {
        List<Projet> projets = projetRepository
                .findByTuteurIdAndDeletedFalse(tuteurId)
                .stream()
                .filter(p -> TuteurAcceptation.PENDING.equals(p.getTuteurAcceptation()))
                .collect(Collectors.toList());
        return toResponseList(projets);
    }

    public List<ProjetResponse> getRefusedProjets() {
        List<Projet> projets = projetRepository
                .findByDeletedFalse(Pageable.unpaged())
                .stream()
                .filter(p -> TuteurAcceptation.REFUSED.equals(p.getTuteurAcceptation()))
                .collect(Collectors.toList());
        return toResponseList(projets);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────

    public ProjetResponse update(String id, UpdateRequest req, String userId) {
        Projet p = findActiveById(id);

        if (req.getTitle()          != null) p.setTitle(req.getTitle());
        if (req.getDescription()    != null) p.setDescription(req.getDescription());
        if (req.getStagiaireIds()   != null) p.setStagiaireIds(req.getStagiaireIds());
        if (req.getTuteurId()       != null) p.setTuteurId(req.getTuteurId());
        if (req.getStartDate()      != null) p.setStartDate(req.getStartDate());
        if (req.getPlannedEndDate() != null) p.setPlannedEndDate(req.getPlannedEndDate());
        if (req.getActualEndDate()  != null) p.setActualEndDate(req.getActualEndDate());
        if (req.getTechnologies()   != null) p.setTechnologies(req.getTechnologies());
        if (req.getStatus()         != null) p.setStatus(req.getStatus());
        if (req.getDepartement()    != null) p.setDepartement(req.getDepartement());

        if (req.getProgress() != null) {
            p.setProgress(req.getProgress());
            if (req.getProgress() >= 100) {
                p.setStatus(ProjetStatus.TERMINE);
                p.setActualEndDate(LocalDate.now());
                emailNotificationService.envoyerEmailProjetTermine(p);
            }
        }

        if (req.getSprints() != null)
            p.setSprints(buildSprints(req.getSprints()));

        return toResponse(projetRepository.save(p));
    }

    // ─────────────────────────────────────────────────────────────────────
    // SPRINT
    // ─────────────────────────────────────────────────────────────────────

    public ProjetResponse completeSprint(String projetId, String sprintId, String userId) {
        Projet p = findActiveById(projetId);

        List<Projet.Sprint> updated = p.getSprints().stream().map(s -> {
            if (sprintId.equals(s.getId())) s.setStatus("TERMINE");
            return s;
        }).collect(Collectors.toList());

        p.setSprints(updated);

        long total    = updated.size();
        long termines = updated.stream()
                .filter(s -> "TERMINE".equals(s.getStatus())).count();

        if (total > 0) {
            int progress = (int) Math.round((double) termines / total * 100);
            p.setProgress(progress);
            if (progress >= 100) {
                p.setStatus(ProjetStatus.TERMINE);
                p.setActualEndDate(LocalDate.now());
                emailNotificationService.envoyerEmailProjetTermine(p);
            }
        }

        return toResponse(projetRepository.save(p));
    }

    public ProjetResponse uploadReport(String id, MultipartFile file, String userId) {
        Projet p = findActiveById(id);
        String url = fileStorageService.uploadFile(file, "rapports/" + id);
        p.setReportUrl(url);
        p.setReportSubmittedAt(LocalDate.now());
        return toResponse(projetRepository.save(p));
    }

    public void softDelete(String id, String userId) {
        Projet p = findActiveById(id);
        p.setDeleted(true);
        projetRepository.save(p);
        auditLogService.log(userId, "DELETE", "PROJET", id, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULER
    // ─────────────────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 8 * * *")
    public void checkProjectDeadlines() {
        LocalDate today   = LocalDate.now();
        LocalDate in3Days = today.plusDays(3);
        LocalDate in7Days = today.plusDays(7);

        projetRepository
                .findByDeletedFalseAndStatusAndPlannedEndDateBetween(
                        ProjetStatus.EN_COURS, today, in3Days)
                .stream()
                .filter(p -> TuteurAcceptation.ACCEPTED.equals(p.getTuteurAcceptation()))
                .forEach(p -> {
                    notificationService.notifyDeadlineProche(p);
                    emailNotificationService.envoyerEmailDeadlineProche(p);
                });

        projetRepository
                .findByDeletedFalseAndStatusAndPlannedEndDateBetween(
                        ProjetStatus.EN_COURS, in3Days, in7Days)
                .stream()
                .filter(p -> TuteurAcceptation.ACCEPTED.equals(p.getTuteurAcceptation()))
                .forEach(p -> notificationService.notifyDeadlineProche(p));

        List<ProjetStatus> exclus =
                List.of(ProjetStatus.TERMINE, ProjetStatus.ANNULE, ProjetStatus.SUSPENDU);

        projetRepository
                .findByDeletedFalseAndStatusNotInAndPlannedEndDateBefore(exclus, today)
                .stream()
                .filter(p -> TuteurAcceptation.ACCEPTED.equals(p.getTuteurAcceptation()))
                .forEach(p -> {
                    if (!ProjetStatus.EN_RETARD.equals(p.getStatus())) {
                        p.setStatus(ProjetStatus.EN_RETARD);
                        projetRepository.save(p);
                        emailNotificationService.envoyerEmailProjetEnRetard(p);
                    }
                    notificationService.notifyProjetEnRetard(p);
                });

        LocalDateTime fiveDaysAgo = LocalDateTime.now().minusDays(5);
        projetRepository
                .findByDeletedFalseAndStatusAndUpdatedAtBefore(ProjetStatus.EN_COURS, fiveDaysAgo)
                .stream()
                .filter(p -> TuteurAcceptation.ACCEPTED.equals(p.getTuteurAcceptation()))
                .forEach(p -> notificationService.notifySansMiseAJour(p));

        // ✅ Sprints en retard — batch stagiaires par projet
        projetRepository.findByDeletedFalse(Pageable.unpaged()).forEach(p -> {
            if (p.getSprints() == null || p.getSprints().isEmpty()) return;

            boolean changed = false;
            for (Projet.Sprint sprint : p.getSprints()) {
                if ("TERMINE".equals(sprint.getStatus())) continue;
                if (sprint.getEndDate() != null && sprint.getEndDate().isBefore(today)) {
                    sprint.setStatus("EN_RETARD");
                    changed = true;
                    notificationService.createNotification(
                            p.getTuteurId(), NotificationType.PROJET_EN_RETARD,
                            "Sprint en retard",
                            "Le sprint \"" + sprint.getTitle() + "\" du projet \""
                                    + p.getTitle() + "\" a dépassé sa date de fin.",
                            p.getId());
                }
            }
            if (changed) projetRepository.save(p);
        });

        projetRepository.findByDeletedFalse(Pageable.unpaged())
                .stream()
                .filter(p -> TuteurAcceptation.PENDING.equals(p.getTuteurAcceptation()))
                .filter(p -> p.getCreatedAt() != null
                        && p.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24)))
                .forEach(p -> {
                    log.warn("[SCHEDULER] Projet {} en attente +24h", p.getId());
                    emailNotificationService.envoyerEmailRappelAcceptation(p);
                });

        log.info("[SCHEDULER] checkProjectDeadlines terminé — {}", today);
    }

    // ─────────────────────────────────────────────────────────────────────
    // toResponse — unitaire (appels isolés : getById, create, update...)
    // ─────────────────────────────────────────────────────────────────────
    public ProjetResponse toResponse(Projet p) {
        // ✅ Pour un projet seul : 2 queries batch stagiaires + 1 query tuteur
        List<String> stagiaireIds = p.getStagiaireIds() != null
                ? p.getStagiaireIds() : List.of();

        Map<String, Stagiaire> stagiaireMap =
                stagiaireResolver.resolveBatch(stagiaireIds);

        User tuteur = p.getTuteurId() != null
                ? userRepository.findById(p.getTuteurId()).orElse(null)
                : null;

        return buildResponse(p, stagiaireMap, tuteur);
    }

    // ─────────────────────────────────────────────────────────────────────
    // toResponseList — batch (listes — évite N+1)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * ✅ Convertit une liste de Projets en réponses.
     * 3 queries max pour N projets (au lieu de N×3 + N queries).
     */
    public List<ProjetResponse> toResponseList(List<Projet> projets) {
        if (projets == null || projets.isEmpty()) return Collections.emptyList();

        // ── Collecter tous les stagiaireIds distincts ──────────────────────
        List<String> allStagiaireIds = projets.stream()
                .filter(p -> p.getStagiaireIds() != null)
                .flatMap(p -> p.getStagiaireIds().stream())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // ✅ 2 queries pour TOUS les stagiaires de TOUS les projets
        Map<String, Stagiaire> stagiaireMap =
                stagiaireResolver.resolveBatch(allStagiaireIds);

        // ── Collecter tous les tuteurIds distincts ─────────────────────────
        List<String> allTuteurIds = projets.stream()
                .map(Projet::getTuteurId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // ✅ 1 query pour TOUS les tuteurs
        Map<String, User> tuteurMap = allTuteurIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findByIdIn(allTuteurIds).stream()
                  .collect(Collectors.toMap(User::getId, u -> u));

        // ── Construire les réponses sans requêtes supplémentaires ──────────
        return projets.stream()
                .map(p -> buildResponse(
                        p,
                        stagiaireMap,
                        tuteurMap.get(p.getTuteurId())))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // buildResponse — construit ProjetResponse depuis cache (zéro query)
    // ─────────────────────────────────────────────────────────────────────
    private ProjetResponse buildResponse(
            Projet p,
            Map<String, Stagiaire> stagiaireMap,
            User tuteur) {

        ProjetResponse r = new ProjetResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setDescription(p.getDescription());
        r.setStagiaireIds(p.getStagiaireIds());
        r.setTuteurId(p.getTuteurId());
        r.setStartDate(p.getStartDate());
        r.setPlannedEndDate(p.getPlannedEndDate());
        r.setActualEndDate(p.getActualEndDate());
        r.setProgress(p.getProgress());
        r.setStatus(p.getStatus());
        r.setTechnologies(p.getTechnologies());
        r.setReportUrl(p.getReportUrl());
        r.setReportSubmittedAt(p.getReportSubmittedAt());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        r.setDepartement(p.getDepartement());
        r.setTuteurAcceptation(p.getTuteurAcceptation());
        r.setTuteurRefusRaison(p.getTuteurRefusRaison());

        // ── Tuteur depuis cache ────────────────────────────────────────────
        if (tuteur != null) {
            r.setTuteurName(tuteur.getFirstName() + " " + tuteur.getLastName());
            r.setTuteurPhotoUrl(tuteur.getPhotoUrl());
        }

        // ── Sprints ────────────────────────────────────────────────────────
        LocalDate today = LocalDate.now();
        if (p.getSprints() != null) {
            r.setSprints(p.getSprints().stream().map(s -> {
                SprintResponse sr = new SprintResponse();
                sr.setId(s.getId());
                sr.setTitle(s.getTitle());
                sr.setDescription(s.getDescription());
                sr.setStartDate(s.getStartDate());
                sr.setEndDate(s.getEndDate());
                sr.setStatus(s.getStatus());
                sr.setStagiaireId(s.getStagiaireId());
                sr.setOverdue(!("TERMINE".equals(s.getStatus()))
                        && s.getEndDate() != null
                        && s.getEndDate().isBefore(today));

                // ✅ Nom stagiaire du sprint depuis cache (zéro query)
                if (s.getStagiaireId() != null) {
                    Stagiaire st = stagiaireMap.get(s.getStagiaireId());
                    if (st != null) {
                        sr.setStagiaireName(st.getFirstName() + " " + st.getLastName());
                    } else {
                        log.warn("[SPRINT] stagiaire introuvable pour id={}", s.getStagiaireId());
                    }
                }
                return sr;
            }).collect(Collectors.toList()));
        }

        // ── Stagiaires depuis cache ────────────────────────────────────────
        if (p.getStagiaireIds() != null) {
            r.setStagiaires(p.getStagiaireIds().stream()
                    .filter(sid -> sid != null && !sid.isBlank())
                    .map(sid -> {
                        Stagiaire s = stagiaireMap.get(sid);
                        if (s == null) {
                            log.warn("[PROJET] stagiaire introuvable pour sid={}", sid);
                            return null;
                        }
                        ProjetResponse.StagiaireInfo si = new ProjetResponse.StagiaireInfo();
                        si.setId(s.getId());
                        si.setFirstName(s.getFirstName());
                        si.setLastName(s.getLastName());
                        si.setPhotoUrl(s.getPhotoUrl());
                        return si;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        return r;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS privés
    // ─────────────────────────────────────────────────────────────────────

    private List<Projet.Sprint> buildSprints(List<SprintRequest> requests) {
        if (requests == null) return new ArrayList<>();
        return requests.stream().map(r -> {
            Projet.Sprint s = new Projet.Sprint();
            s.setId(r.getId() != null ? r.getId() : UUID.randomUUID().toString());
            s.setTitle(r.getTitle());
            s.setDescription(r.getDescription());
            s.setStartDate(r.getStartDate());
            s.setEndDate(r.getEndDate());
            s.setStagiaireId(r.getStagiaireId());
            s.setStatus(r.getStatus() != null ? r.getStatus() : "EN_COURS");
            return s;
        }).collect(Collectors.toList());
    }

    private Projet findActiveById(String id) {
        return projetRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + id));
    }
}
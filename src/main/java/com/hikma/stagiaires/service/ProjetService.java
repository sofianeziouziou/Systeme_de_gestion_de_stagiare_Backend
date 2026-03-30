package com.hikma.stagiaires.service;

import com.hikma.stagiaires.dto.projet.ProjetDTOs.*;
import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository    projetRepository;
    private final StagiaireRepository stagiaireRepository;
    private final UserRepository      userRepository;
    private final NotificationService notificationService;
    private final FileStorageService  fileStorageService;
    private final AuditLogService     auditLogService;

    // ── Créer un projet (RH) ──────────────────────────────────────────────
    public ProjetResponse create(CreateRequest req, String rhId) {

        // Règle : 1 stagiaire = 1 projet actif à la fois
        if (req.getStagiaireIds() != null) {
            for (String userId : req.getStagiaireIds()) {
                boolean hasActive = projetRepository
                        .findByStagiaireIdsContainingAndDeletedFalse(userId)
                        .stream()
                        .anyMatch(p -> p.getStatus() != ProjetStatus.TERMINE
                                && p.getStatus() != ProjetStatus.ANNULE);
                if (hasActive) {
                    // Trouver le nom via fiche stagiaire ou user directement
                    String nom = stagiaireRepository.findByUserId(userId)
                            .map(s -> s.getFirstName() + " " + s.getLastName())
                            .orElseGet(() -> userRepository.findById(userId)
                                    .map(u -> u.getFirstName() + " " + u.getLastName())
                                    .orElse(userId));
                    throw new IllegalArgumentException(
                            "Le stagiaire " + nom + " a déjà un projet actif en cours.");
                }
            }
        }

        Projet projet = Projet.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .stagiaireIds(req.getStagiaireIds())   // stocke userId
                .tuteurId(req.getTuteurId())
                .startDate(req.getStartDate())
                .plannedEndDate(req.getPlannedEndDate())
                .technologies(req.getTechnologies() != null ? req.getTechnologies() : List.of())
                .sprints(List.of())
                .build();

        Projet saved = projetRepository.save(projet);
        auditLogService.log(rhId, "CREATE", "PROJET", saved.getId(), null);

        // Notifier le tuteur
        if (req.getTuteurId() != null) {
            notificationService.createNotification(
                    req.getTuteurId(),
                    NotificationType.PROJET_ASSIGNE,
                    "Nouveau projet assigné",
                    "Le RH vous a assigné le projet \"" + req.getTitle() + "\".",
                    saved.getId()
            );
        }

        return toResponse(saved);
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public ProjetResponse getById(String id) {
        return toResponse(findActiveById(id));
    }

    public Page<ProjetResponse> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return projetRepository.findByDeletedFalse(pageable).map(this::toResponse);
    }

    public List<ProjetResponse> getByTuteur(String tuteurId) {
        return projetRepository.findByTuteurIdAndDeletedFalse(tuteurId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<ProjetResponse> getByStagiaire(String userId) {
        // userId passé → cherche par stagiaireIds (qui contient des userId)
        return projetRepository.findByStagiaireIdsContainingAndDeletedFalse(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Update ────────────────────────────────────────────────────────────
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

        if (req.getProgress() != null) {
            p.setProgress(req.getProgress());
            if (req.getProgress() >= 100) {
                p.setStatus(ProjetStatus.TERMINE);
                p.setActualEndDate(LocalDate.now());
            }
        }

        if (req.getSprints() != null) {
            p.setSprints(buildSprints(req.getSprints()));
        }

        return toResponse(projetRepository.save(p));
    }

    // ── Terminer un sprint ────────────────────────────────────────────────
    public ProjetResponse completeSprint(String projetId, String sprintId, String userId) {
        Projet p = findActiveById(projetId);

        List<Projet.Sprint> updated = p.getSprints().stream().map(s -> {
            if (sprintId.equals(s.getId())) s.setStatus("TERMINE");
            return s;
        }).collect(Collectors.toList());

        p.setSprints(updated);

        long total    = updated.size();
        long termines = updated.stream().filter(s -> "TERMINE".equals(s.getStatus())).count();
        if (total > 0) {
            int progress = (int) Math.round((double) termines / total * 100);
            p.setProgress(progress);
            if (progress >= 100) {
                p.setStatus(ProjetStatus.TERMINE);
                p.setActualEndDate(LocalDate.now());
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

    // ── Scheduler ─────────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 8 * * *")
    public void checkProjectDeadlines() {
        LocalDate today   = LocalDate.now();
        LocalDate in7Days = today.plusDays(7);

        projetRepository
                .findByDeletedFalseAndStatusAndPlannedEndDateBetween(ProjetStatus.EN_COURS, today, in7Days)
                .forEach(p -> notificationService.notifyDeadlineProche(p));

        List<ProjetStatus> exclus = List.of(ProjetStatus.TERMINE, ProjetStatus.ANNULE, ProjetStatus.SUSPENDU);
        projetRepository
                .findByDeletedFalseAndStatusNotInAndPlannedEndDateBefore(exclus, today)
                .forEach(p -> {
                    if (!ProjetStatus.EN_RETARD.equals(p.getStatus())) {
                        p.setStatus(ProjetStatus.EN_RETARD);
                        projetRepository.save(p);
                    }
                    notificationService.notifyProjetEnRetard(p);
                });

        LocalDateTime fiveDaysAgo = LocalDateTime.now().minusDays(5);
        projetRepository
                .findByDeletedFalseAndStatusAndUpdatedAtBefore(ProjetStatus.EN_COURS, fiveDaysAgo)
                .forEach(p -> notificationService.notifySansMiseAJour(p));

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

        projetRepository
                .findByDeletedFalseAndStatusAndPlannedEndDateBetween(ProjetStatus.EN_COURS, today, in7Days)
                .forEach(p -> notificationService.createNotification(
                        p.getTuteurId(), NotificationType.RAPPEL_RAPPORT,
                        "Rappel : évaluation à venir",
                        "Le projet \"" + p.getTitle() + "\" se termine dans moins de 7 jours.",
                        p.getId()));

        log.info("checkProjectDeadlines terminé — {}", today);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private List<Projet.Sprint> buildSprints(List<SprintRequest> requests) {
        if (requests == null) return new ArrayList<>();
        return requests.stream().map(r -> {
            Projet.Sprint s = new Projet.Sprint();
            s.setId(r.getId() != null ? r.getId() : UUID.randomUUID().toString());
            s.setTitle(r.getTitle());
            s.setDescription(r.getDescription());
            s.setStartDate(r.getStartDate());
            s.setEndDate(r.getEndDate());
            s.setStagiaireId(r.getStagiaireId());     // ← assignation stagiaire par sprint
            s.setStatus(r.getStatus() != null ? r.getStatus() : "EN_COURS");
            return s;
        }).collect(Collectors.toList());
    }

    private Projet findActiveById(String id) {
        return projetRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + id));
    }

    public ProjetResponse toResponse(Projet p) {
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

        // Sprints → SprintResponse
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
                sr.setStagiaireId(s.getStagiaireId());  // ← inclus dans la réponse
                sr.setOverdue(!("TERMINE".equals(s.getStatus()))
                        && s.getEndDate() != null
                        && s.getEndDate().isBefore(today));

                // Résoudre le nom du stagiaire assigné au sprint
                if (s.getStagiaireId() != null) {
                    stagiaireRepository.findByUserId(s.getStagiaireId())
                            .ifPresent(st -> sr.setStagiaireName(st.getFirstName() + " " + st.getLastName()));
                    if (sr.getStagiaireName() == null) {
                        userRepository.findById(s.getStagiaireId())
                                .ifPresent(u -> sr.setStagiaireName(u.getFirstName() + " " + u.getLastName()));
                    }
                }
                return sr;
            }).collect(Collectors.toList()));
        }

        // Nom du tuteur
        if (p.getTuteurId() != null) {
            userRepository.findById(p.getTuteurId())
                    .ifPresent(u -> r.setTuteurName(u.getFirstName() + " " + u.getLastName()));
        }

        // ── CORRECTION : stagiaires — stagiaireIds contient des userId ──────
        if (p.getStagiaireIds() != null) {
            r.setStagiaires(p.getStagiaireIds().stream()
                    .map(userId -> {
                        // 1. Chercher fiche stagiaire par userId
                        return stagiaireRepository.findByUserId(userId)
                                .map(s -> {
                                    ProjetResponse.StagiaireInfo si = new ProjetResponse.StagiaireInfo();
                                    si.setId(userId);                  // userId pour cohérence
                                    si.setFirstName(s.getFirstName());
                                    si.setLastName(s.getLastName());
                                    si.setPhotoUrl(s.getPhotoUrl());
                                    return si;
                                })
                                // 2. Fallback : chercher dans User si fiche absente
                                .orElseGet(() -> userRepository.findById(userId).map(u -> {
                                    ProjetResponse.StagiaireInfo si = new ProjetResponse.StagiaireInfo();
                                    si.setId(userId);
                                    si.setFirstName(u.getFirstName());
                                    si.setLastName(u.getLastName());
                                    return si;
                                }).orElse(null));
                    })
                    .filter(si -> si != null)
                    .collect(Collectors.toList()));
        }

        return r;
    }
}
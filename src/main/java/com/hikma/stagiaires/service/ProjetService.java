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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository projetRepository;
    private final StagiaireRepository stagiaireRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    public ProjetResponse create(CreateRequest req, String tuteurId) {

        // ── Règle : 1 stagiaire = 1 projet actif à la fois ───────────────
        if (req.getStagiaireIds() != null) {
            for (String stagiaireId : req.getStagiaireIds()) {
                boolean hasActiveProjet = projetRepository
                        .findByStagiaireIdsContainingAndDeletedFalse(stagiaireId)
                        .stream()
                        .anyMatch(p -> p.getStatus() != ProjetStatus.TERMINE
                                && p.getStatus() != ProjetStatus.ANNULE);
                if (hasActiveProjet) {
                    // Récupérer le nom du stagiaire pour le message
                    String nom = stagiaireRepository.findById(stagiaireId)
                            .map(s -> s.getFirstName() + " " + s.getLastName())
                            .orElse(stagiaireId);
                    throw new IllegalArgumentException(
                            "Le stagiaire " + nom + " a déjà un projet actif en cours.");
                }
            }
        }

        List<Projet.Jalon> jalons = req.getJalons() == null ? List.of() :
                req.getJalons().stream()
                        .map(j -> new Projet.Jalon(j.getTitle(), j.getDate(), j.isCompleted()))
                        .collect(Collectors.toList());

        Projet projet = Projet.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .stagiaireIds(req.getStagiaireIds())
                .tuteurId(tuteurId)
                .startDate(req.getStartDate())
                .plannedEndDate(req.getPlannedEndDate())
                .technologies(req.getTechnologies() != null ? req.getTechnologies() : List.of())
                .jalons(jalons)
                .build();

        Projet saved = projetRepository.save(projet);
        auditLogService.log(tuteurId, "CREATE", "PROJET", saved.getId(), null);
        return toResponse(saved);
    }

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

    public List<ProjetResponse> getByStagiaire(String stagiaireId) {
        return projetRepository.findByStagiaireIdsContainingAndDeletedFalse(stagiaireId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ProjetResponse update(String id, UpdateRequest req, String userId) {
        Projet p = findActiveById(id);

        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getStagiaireIds() != null) p.setStagiaireIds(req.getStagiaireIds());
        if (req.getStartDate() != null) p.setStartDate(req.getStartDate());
        if (req.getPlannedEndDate() != null) p.setPlannedEndDate(req.getPlannedEndDate());
        if (req.getActualEndDate() != null) p.setActualEndDate(req.getActualEndDate());
        if (req.getProgress() != null) p.setProgress(req.getProgress());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        if (req.getTechnologies() != null) p.setTechnologies(req.getTechnologies());
        if (req.getJalons() != null) {
            p.setJalons(req.getJalons().stream()
                    .map(j -> new Projet.Jalon(j.getTitle(), j.getDate(), j.isCompleted()))
                    .collect(Collectors.toList()));
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

    // ─── Scheduled Notifications ─────────────────────────────────────────

    @Scheduled(cron = "0 0 8 * * *") // Tous les jours à 8h
    public void checkProjectDeadlines() {
        LocalDate now = LocalDate.now();
        LocalDate limit = now.plusDays(7);

        // Deadlines proches
        projetRepository.findProjetsByDeadlineApproching(now, limit)
                .forEach(p -> notificationService.notifyDeadlineProche(p));

        // Projets en retard
        projetRepository.findOverdueProjects(now)
                .forEach(p -> {
                    p.setStatus(ProjetStatus.EN_RETARD);
                    projetRepository.save(p);
                    notificationService.notifyProjetEnRetard(p);
                });

        // Sans mise à jour depuis 5 jours
        LocalDateTime fiveDaysAgo = LocalDateTime.now().minusDays(5);
        projetRepository.findProjectsWithoutRecentUpdate(fiveDaysAgo)
                .forEach(p -> notificationService.notifySansMiseAJour(p));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Projet findActiveById(String id) {
        return projetRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + id));
    }

    private ProjetResponse toResponse(Projet p) {
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

        // Jalons
        r.setJalons(p.getJalons().stream().map(j -> {
            ProjetResponse.JalonResponse jr = new ProjetResponse.JalonResponse();
            jr.setTitle(j.getTitle());
            jr.setDate(j.getDate());
            jr.setCompleted(j.isCompleted());
            return jr;
        }).collect(Collectors.toList()));

        // Infos tuteur
        userRepository.findById(p.getTuteurId()).ifPresent(u ->
                r.setTuteurName(u.getFirstName() + " " + u.getLastName()));

        // Infos stagiaires
        if (p.getStagiaireIds() != null) {
            List<ProjetResponse.StagiaireInfo> infos = p.getStagiaireIds().stream()
                    .map(sid -> stagiaireRepository.findById(sid).map(s -> {
                        ProjetResponse.StagiaireInfo si = new ProjetResponse.StagiaireInfo();
                        si.setId(s.getId());
                        si.setFirstName(s.getFirstName());
                        si.setLastName(s.getLastName());
                        si.setPhotoUrl(s.getPhotoUrl());
                        return si;
                    }).orElse(null))
                    .filter(si -> si != null)
                    .collect(Collectors.toList());
            r.setStagiaires(infos);
        }
        return r;
    }
}
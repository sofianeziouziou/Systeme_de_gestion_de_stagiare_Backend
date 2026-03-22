package com.hikma.stagiaires.service;

import com.hikma.stagiaires.dto.stagiaire.StagiaireDTOs.*;
import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StagiaireService {

    private final StagiaireRepository stagiaireRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    // ─── CRUD ────────────────────────────────────────────────────────────

    public StagiaireResponse create(CreateRequest req, String createdByUserId) {
        if (stagiaireRepository.existsByEmailAndDeletedFalse(req.getEmail())) {
            throw new IllegalArgumentException("Un stagiaire avec cet email existe déjà.");
        }

        long months = ChronoUnit.MONTHS.between(req.getStartDate(), req.getEndDate());

        Stagiaire stagiaire = Stagiaire.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .school(req.getSchool())
                .fieldOfStudy(req.getFieldOfStudy())
                .level(req.getLevel())
                .departement(req.getDepartement())
                .tuteurId(req.getTuteurId())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .durationMonths((int) months)
                .technicalSkills(req.getTechnicalSkills() != null ? req.getTechnicalSkills() : List.of())
                .softSkills(req.getSoftSkills() != null ? req.getSoftSkills() : List.of())
                .status(StagiaireStatus.EN_COURS)
                .build();

        Stagiaire saved = stagiaireRepository.save(stagiaire);
        auditLogService.log(createdByUserId, "CREATE", "STAGIAIRE", saved.getId(), null);
        return toResponse(saved);
    }

    public StagiaireResponse getById(String id) {
        return toResponse(findActiveById(id));
    }

    // ── NOUVEAU : profil du stagiaire connecté ───────────────────────────
    public StagiaireResponse getByUserId(String userId) {
        Stagiaire stagiaire = stagiaireRepository.findByUserId(userId).orElseThrow(() -> new NoSuchElementException(
                "Aucun profil stagiaire trouvé pour cet utilisateur"));
        return toResponse(stagiaire);
    }   // ← accolade fermante correcte

    public PagedResponse search(SearchFilter filter) {
        Query query = new Query();
        query.addCriteria(Criteria.where("deleted").is(false));

        if (StringUtils.hasText(filter.getDepartement()))
            query.addCriteria(Criteria.where("departement").is(filter.getDepartement()));

        if (filter.getMinScore() != null)
            query.addCriteria(Criteria.where("globalScore").gte(filter.getMinScore()));

        if (filter.getCompetences() != null && !filter.getCompetences().isEmpty())
            query.addCriteria(Criteria.where("technicalSkills").in(filter.getCompetences()));

        if (filter.getPeriodeDebut() != null)
            query.addCriteria(Criteria.where("startDate").gte(filter.getPeriodeDebut()));

        if (filter.getPeriodeFin() != null)
            query.addCriteria(Criteria.where("endDate").lte(filter.getPeriodeFin()));

        if (filter.getLevel() != null)
            query.addCriteria(Criteria.where("level").is(filter.getLevel()));

        if (StringUtils.hasText(filter.getSchool()))
            query.addCriteria(Criteria.where("school").regex(filter.getSchool(), "i"));

        if (Boolean.TRUE.equals(filter.getBadgeExcellence()))
            query.addCriteria(Criteria.where("badge").is(Badge.EXCELLENCE));

        if (StringUtils.hasText(filter.getTuteurId()))
            query.addCriteria(Criteria.where("tuteurId").is(filter.getTuteurId()));

        // ── Recherche textuelle (nom, email, école) ──────────────────────
        if (StringUtils.hasText(filter.getSearch())) {
            String regex = filter.getSearch();
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("firstName").regex(regex, "i"),
                    Criteria.where("lastName").regex(regex, "i"),
                    Criteria.where("email").regex(regex, "i"),
                    Criteria.where("school").regex(regex, "i"),
                    Criteria.where("departement").regex(regex, "i")
            ));
        }

        long total = mongoTemplate.count(query, Stagiaire.class);
        Pageable pageable = PageRequest.of(
                filter.getPage(),
                filter.getSize(),
                Sort.by("globalScore").descending()
        );
        query.with(pageable);

        List<Stagiaire> stagiaires = mongoTemplate.find(query, Stagiaire.class);
        List<StagiaireResponse> responses = stagiaires.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PagedResponse paged = new PagedResponse();
        paged.setContent(responses);
        paged.setPage(filter.getPage());
        paged.setSize(filter.getSize());
        paged.setTotalElements(total);
        paged.setTotalPages((int) Math.ceil((double) total / filter.getSize()));
        return paged;
    }

    public StagiaireResponse update(String id, UpdateRequest req, String updatedByUserId) {
        Stagiaire s = findActiveById(id);

        if (req.getFirstName()      != null) s.setFirstName(req.getFirstName());
        if (req.getLastName()       != null) s.setLastName(req.getLastName());
        if (req.getPhone()          != null) s.setPhone(req.getPhone());
        if (req.getSchool()         != null) s.setSchool(req.getSchool());
        if (req.getFieldOfStudy()   != null) s.setFieldOfStudy(req.getFieldOfStudy());
        if (req.getLevel()          != null) s.setLevel(req.getLevel());
        if (req.getDepartement()    != null) s.setDepartement(req.getDepartement());
        if (req.getTuteurId()       != null) s.setTuteurId(req.getTuteurId());
        if (req.getStartDate()      != null) s.setStartDate(req.getStartDate());
        if (req.getEndDate()        != null) s.setEndDate(req.getEndDate());
        if (req.getTechnicalSkills()!= null) s.setTechnicalSkills(req.getTechnicalSkills());
        if (req.getSoftSkills()     != null) s.setSoftSkills(req.getSoftSkills());
        if (req.getStatus()         != null) s.setStatus(req.getStatus());

        Stagiaire saved = stagiaireRepository.save(s);
        auditLogService.log(updatedByUserId, "UPDATE", "STAGIAIRE", id, null);
        return toResponse(saved);
    }

    public void softDelete(String id, String deletedByUserId) {
        Stagiaire s = findActiveById(id);
        s.setDeleted(true);
        stagiaireRepository.save(s);
        auditLogService.log(deletedByUserId, "DELETE", "STAGIAIRE", id, null);
    }

    public StagiaireResponse uploadCv(String id, MultipartFile file, String userId) {
        Stagiaire s = findActiveById(id);
        String url = fileStorageService.uploadFile(file, "cv/" + id);
        s.setCvUrl(url);
        return toResponse(stagiaireRepository.save(s));
    }

    // ── AJOUTÉ : méthode uploadPhoto ─────────────────────────────────────
    public StagiaireResponse uploadPhoto(String id, MultipartFile file, String userId) {
        Stagiaire s = findActiveById(id);
        String url = fileStorageService.uploadFile(file, "photos/" + id);
        s.setPhotoUrl(url);
        return toResponse(stagiaireRepository.save(s));
    }

    // ─── Score & Badge ───────────────────────────────────────────────────

    public void updateScore(String stagiaireId, Double newScore, String evaluationId) {
        Stagiaire s = findActiveById(stagiaireId);
        s.setGlobalScore(newScore);
        s.setBadge(calculateBadge(newScore));

        List<Stagiaire.ScoreHistory> history = new ArrayList<>(s.getScoreHistory());
        history.add(new Stagiaire.ScoreHistory(newScore, LocalDateTime.now(), evaluationId));
        s.setScoreHistory(history);

        stagiaireRepository.save(s);
    }

    private Badge calculateBadge(Double score) {
        if (score >= 90) return Badge.EXCELLENCE;
        if (score >= 75) return Badge.TRES_BIEN;
        if (score >= 60) return Badge.BIEN;
        return Badge.A_SURVEILLER;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Stagiaire findActiveById(String id) {
        return stagiaireRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Stagiaire introuvable : " + id));
    }

    public StagiaireResponse toResponse(Stagiaire s) {
        StagiaireResponse r = new StagiaireResponse();
        r.setId(s.getId());
        r.setFirstName(s.getFirstName());
        r.setLastName(s.getLastName());
        r.setEmail(s.getEmail());
        r.setPhone(s.getPhone());
        r.setPhotoUrl(s.getPhotoUrl());
        r.setSchool(s.getSchool());
        r.setFieldOfStudy(s.getFieldOfStudy());
        r.setLevel(s.getLevel());
        r.setDepartement(s.getDepartement());
        r.setTuteurId(s.getTuteurId());
        r.setStartDate(s.getStartDate());
        r.setEndDate(s.getEndDate());
        r.setDurationMonths(s.getDurationMonths());
        r.setTechnicalSkills(s.getTechnicalSkills());
        r.setSoftSkills(s.getSoftSkills());
        r.setCvUrl(s.getCvUrl());
        r.setGlobalScore(s.getGlobalScore());
        r.setBadge(s.getBadge());
        r.setStatus(s.getStatus());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());

        if (s.getTuteurId() != null) {
            userRepository.findById(s.getTuteurId()).ifPresent(u ->
                    r.setTuteurName(u.getFirstName() + " " + u.getLastName()));
        }
        return r;
    }
    public void deleteByUserId(String userId) {
        stagiaireRepository.findByUserId(userId)
                .ifPresent(s -> stagiaireRepository.deleteById(s.getId()));
    }
    // ── Créer fiche stagiaire automatiquement lors de l'approbation ──────
    // ═══════════════════════════════════════════════════════════════════════

    public void createFicheForUser(User user) {
        // 1. Si une fiche existe déjà pour ce userId → on sort
        if (stagiaireRepository.findByUserId(user.getId()).isPresent()) {
            log.info("Fiche stagiaire déjà existante pour userId={}", user.getId());
            return;
        }

        // 2. Si une fiche existe déjà pour cet email → on lie juste le userId
        if (user.getEmail() != null) {
            Optional<Stagiaire> existingByEmail =
                    stagiaireRepository.findByEmailAndDeletedFalse(user.getEmail());
            if (existingByEmail.isPresent()) {
                Stagiaire s = existingByEmail.get();
                s.setUserId(user.getId());
                stagiaireRepository.save(s);
                log.info("UserId lié à la fiche existante email={}", user.getEmail());
                return;
            }
        }

        // 3. Création d'une nouvelle fiche minimale
        Stagiaire stagiaire = Stagiaire.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())          // ← crucial : évite la contrainte unique null
                .phone(user.getPhone())
                .status(StagiaireStatus.EN_COURS)
                .technicalSkills(List.of())
                .softSkills(List.of())
                .scoreHistory(List.of())
                .globalScore(0.0)
                .deleted(false)
                .build();

        stagiaireRepository.save(stagiaire);
        log.info("Fiche stagiaire créée pour userId={} email={}", user.getId(), user.getEmail());
    }
}
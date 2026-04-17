// DESTINATION : src/main/java/com/hikma/stagiaires/service/StagiaireService.java
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
import org.springframework.security.access.AccessDeniedException;
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
    private final UserRepository       userRepository;
    private final MongoTemplate        mongoTemplate;
    private final FileStorageService   fileStorageService;
    private final AuditLogService      auditLogService;
    private final CvAnalysisService    cvAnalysisService;
    private final OnboardingService    onboardingService;

    public StagiaireResponse create(CreateRequest req, String createdByUserId) {
        if (stagiaireRepository.existsByEmailAndDeletedFalse(req.getEmail())) {
            throw new IllegalArgumentException("Un stagiaire avec cet email existe deja.");
        }
        long months = ChronoUnit.MONTHS.between(req.getStartDate(), req.getEndDate());
        Stagiaire stagiaire = Stagiaire.builder()
                .firstName(req.getFirstName()).lastName(req.getLastName())
                .email(req.getEmail()).phone(req.getPhone()).school(req.getSchool())
                .fieldOfStudy(req.getFieldOfStudy()).level(req.getLevel())
                .departement(req.getDepartement()).tuteurId(req.getTuteurId())
                .startDate(req.getStartDate()).endDate(req.getEndDate())
                .durationMonths((int) months)
                .technicalSkills(req.getTechnicalSkills() != null ? req.getTechnicalSkills() : List.of())
                .softSkills(req.getSoftSkills() != null ? req.getSoftSkills() : List.of())
                .status(StagiaireStatus.EN_COURS).build();
        Stagiaire saved = stagiaireRepository.save(stagiaire);
        auditLogService.log(createdByUserId, "CREATE", "STAGIAIRE", saved.getId(), null);
        return toResponse(saved);
    }

    public StagiaireResponse getById(String id) { return toResponse(findActiveById(id)); }

    public StagiaireResponse getByUserId(String userId) {
        Stagiaire stagiaire = stagiaireRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Aucun profil stagiaire trouve pour cet utilisateur"));
        return toResponse(stagiaire);
    }

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
        if (StringUtils.hasText(filter.getSearch())) {
            String regex = filter.getSearch();
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("firstName").regex(regex, "i"),
                    Criteria.where("lastName").regex(regex, "i"),
                    Criteria.where("email").regex(regex, "i"),
                    Criteria.where("school").regex(regex, "i"),
                    Criteria.where("departement").regex(regex, "i")));
        }
        long total = mongoTemplate.count(query, Stagiaire.class);
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("globalScore").descending());
        query.with(pageable);
        List<StagiaireResponse> responses = mongoTemplate.find(query, Stagiaire.class).stream()
                .map(this::toResponse).collect(Collectors.toList());
        PagedResponse paged = new PagedResponse();
        paged.setContent(responses); paged.setPage(filter.getPage());
        paged.setSize(filter.getSize()); paged.setTotalElements(total);
        paged.setTotalPages((int) Math.ceil((double) total / filter.getSize()));
        return paged;
    }

    public StagiaireResponse update(String id, UpdateRequest req, String callerUserId) {
        Stagiaire s = findActiveById(id);
        User caller = userRepository.findById(callerUserId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        switch (caller.getRole()) {
            case STAGIAIRE -> { if (!callerUserId.equals(s.getUserId())) throw new AccessDeniedException("Non autorise."); }
            case TUTEUR    -> { if (!callerUserId.equals(s.getTuteurId())) throw new AccessDeniedException("Non encadrant."); }
            case RH        -> { }
            default        -> throw new AccessDeniedException("Role non reconnu.");
        }
        if (req.getFirstName()       != null) s.setFirstName(req.getFirstName());
        if (req.getLastName()        != null) s.setLastName(req.getLastName());
        if (req.getPhone()           != null) s.setPhone(req.getPhone());
        if (req.getSchool()          != null) s.setSchool(req.getSchool());
        if (req.getFieldOfStudy()    != null) s.setFieldOfStudy(req.getFieldOfStudy());
        if (req.getLevel()           != null) s.setLevel(req.getLevel());
        if (req.getDepartement()     != null) s.setDepartement(req.getDepartement());
        if (req.getTuteurId()        != null) s.setTuteurId(req.getTuteurId());
        if (req.getStartDate()       != null) s.setStartDate(req.getStartDate());
        if (req.getEndDate()         != null) s.setEndDate(req.getEndDate());
        if (req.getTechnicalSkills() != null) s.setTechnicalSkills(req.getTechnicalSkills());
        if (req.getSoftSkills()      != null) s.setSoftSkills(req.getSoftSkills());
        if (req.getStatus()          != null) s.setStatus(req.getStatus());
        if (req.getBio()             != null) s.setBio(req.getBio());
        List<String> missing = onboardingService.computeMissingFields(s);
        s.setMissingFields(missing); s.setProfileCompleted(missing.isEmpty());
        Stagiaire saved = stagiaireRepository.save(s);
        auditLogService.log(callerUserId, "UPDATE", "STAGIAIRE", id, null);
        return toResponse(saved);
    }

    public void softDelete(String id, String deletedByUserId) {
        Stagiaire s = findActiveById(id);
        s.setDeleted(true);
        stagiaireRepository.save(s);
        auditLogService.log(deletedByUserId, "DELETE", "STAGIAIRE", id, null);
    }

    public StagiaireResponse uploadCv(String id, MultipartFile file, String userId) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf"))
            throw new IllegalArgumentException("Seuls les fichiers PDF sont acceptes.");
        Stagiaire s = findActiveById(id);
        String url = fileStorageService.uploadFile(file, "cv/" + id);
        s.setCvUrl(url);
        try {
            CvData analysis = cvAnalysisService.analyze(file);
            s.setCvAnalysis(analysis);
            if (s.getSchool() == null && analysis.getDetectedSchool() != null) s.setSchool(analysis.getDetectedSchool());
            if (s.getLevel() == null && analysis.getDetectedLevel() != null) {
                try { s.setLevel(EducationLevel.valueOf(analysis.getDetectedLevel())); } catch (IllegalArgumentException ignored) { }
            }
            if ((s.getTechnicalSkills() == null || s.getTechnicalSkills().isEmpty())
                    && analysis.getDetectedSkills() != null && !analysis.getDetectedSkills().isEmpty())
                s.setTechnicalSkills(analysis.getDetectedSkills());
        } catch (Exception e) { log.warn("[StagiaireService] CV analyse echec {} : {}", id, e.getMessage()); }
        List<String> missing = onboardingService.computeMissingFields(s);
        s.setMissingFields(missing); s.setProfileCompleted(missing.isEmpty());
        return toResponse(stagiaireRepository.save(s));
    }

    public StagiaireResponse uploadPhoto(String id, MultipartFile file, String userId) {
        Stagiaire s = findActiveById(id);
        String url = fileStorageService.uploadFile(file, "photos/" + id);
        s.setPhotoUrl(url);
        return toResponse(stagiaireRepository.save(s));
    }

    public void updateScore(String stagiaireId, Double newScore, String evaluationId) {
        Stagiaire s = findActiveById(stagiaireId);
        s.setGlobalScore(newScore); s.setBadge(calculateBadge(newScore));
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

    private Stagiaire findActiveById(String id) {
        return stagiaireRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Stagiaire introuvable : " + id));
    }

    public StagiaireResponse toResponse(Stagiaire s) {
        StagiaireResponse r = new StagiaireResponse();
        r.setId(s.getId());
        r.setUserId(s.getUserId());   // FIX : expose userId = users._id pour stagiaireIds du projet
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
        r.setBio(s.getBio());
        r.setProfileCompleted(s.isProfileCompleted());
        r.setCurrentStep(s.getCurrentStep() != null ? s.getCurrentStep().name() : null);
        r.setMissingFields(s.getMissingFields());
        r.setCvAnalysis(s.getCvAnalysis());
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

    public void createFicheForUser(User user) {
        if (stagiaireRepository.findByUserId(user.getId()).isPresent()) {
            log.info("Fiche stagiaire deja existante pour userId={}", user.getId());
            return;
        }
        if (user.getEmail() != null) {
            Optional<Stagiaire> existingByEmail = stagiaireRepository.findByEmailAndDeletedFalse(user.getEmail());
            if (existingByEmail.isPresent()) {
                Stagiaire s = existingByEmail.get();
                s.setUserId(user.getId());
                stagiaireRepository.save(s);
                log.info("UserId lie a la fiche existante email={}", user.getEmail());
                return;
            }
        }
        Stagiaire stagiaire = Stagiaire.builder()
                .userId(user.getId()).firstName(user.getFirstName()).lastName(user.getLastName())
                .email(user.getEmail()).phone(user.getPhone()).status(StagiaireStatus.EN_COURS)
                .technicalSkills(List.of()).softSkills(List.of()).scoreHistory(List.of())
                .globalScore(0.0).deleted(false).build();
        stagiaireRepository.save(stagiaire);
        log.info("Fiche stagiaire creee pour userId={}", user.getId());
    }
}
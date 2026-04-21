package com.hikma.stagiaires.service.stagiaire;

import com.hikma.stagiaires.dto.onboarding.OnboardingDTOs.OnboardingStatusResponse;
import com.hikma.stagiaires.dto.onboarding.OnboardingDTOs.OnboardingStepRequest;
import com.hikma.stagiaires.model.stagiaire.OnboardingStep;
import com.hikma.stagiaires.model.stagiaire.Stagiaire;
import com.hikma.stagiaires.repository.StagiaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final StagiaireRepository stagiaireRepository;

    // ── Récupérer l'état actuel ───────────────────────────────────────────

    public OnboardingStatusResponse getStatus(String userId) {
        Stagiaire s = findByUserId(userId);
        return buildStatus(s);
    }

    // ── Soumettre une étape ───────────────────────────────────────────────

    public OnboardingStatusResponse submitStep(String userId, OnboardingStepRequest req) {
        Stagiaire s = findByUserId(userId);

        switch (req.getStep()) {

            case INFOS_PERSONNELLES -> {
                if (req.getFirstName() != null) s.setFirstName(req.getFirstName().trim());
                if (req.getLastName()  != null) s.setLastName(req.getLastName().trim());
                if (req.getPhone()     != null) s.setPhone(req.getPhone().trim());
                if (req.getBio()       != null) s.setBio(req.getBio().trim());
                if (req.getDurationMonths() != null) s.setDurationMonths(req.getDurationMonths());

                s.setCurrentStep(OnboardingStep.FORMATION);
            }

            case FORMATION -> {
                if (req.getSchool()          != null) s.setSchool(req.getSchool().trim());
                if (req.getFieldOfStudy()    != null) s.setFieldOfStudy(req.getFieldOfStudy().trim());
                if (req.getLevel()           != null) s.setLevel(req.getLevel());
                if (req.getDepartement()     != null) s.setDepartement(req.getDepartement().trim());
                if (req.getTechnicalSkills() != null) s.setTechnicalSkills(req.getTechnicalSkills());
                if (req.getSoftSkills()      != null) s.setSoftSkills(req.getSoftSkills());
                s.setCurrentStep(OnboardingStep.DOCUMENTS);
            }

            case DOCUMENTS -> {
                // Le CV est uploadé via /stagiaires/{id}/cv
                // Cette étape confirme juste la navigation
                s.setCurrentStep(OnboardingStep.CONFIRMATION);
            }

            case CONFIRMATION -> {
                List<String> missing = computeMissingFields(s);
                s.setMissingFields(missing);
                s.setProfileCompleted(missing.isEmpty());
                s.setCurrentStep(OnboardingStep.CONFIRMATION);
                log.info("[Onboarding] userId={} → profileCompleted={}", userId, s.isProfileCompleted());
            }
        }

        // Recalcul à chaque étape pour le % affiché dans le stepper
        s.setMissingFields(computeMissingFields(s));

        stagiaireRepository.save(s);
        return buildStatus(s);
    }

    // ── Calcul des champs manquants ───────────────────────────────────────

    public List<String> computeMissingFields(Stagiaire s) {
        List<String> missing = new ArrayList<>();
        if (isBlank(s.getPhone()))        missing.add("phone");
        if (isBlank(s.getSchool()))       missing.add("school");
        if (isBlank(s.getFieldOfStudy())) missing.add("fieldOfStudy");
        if (s.getLevel() == null)         missing.add("level");
        if (isBlank(s.getDepartement()))  missing.add("departement");
        if (isBlank(s.getCvUrl()))        missing.add("cvUrl");
        return missing;
    }

    // ── Score de complétion 0-100 ─────────────────────────────────────────

    public int completionScore(Stagiaire s) {
        int total   = 6;
        int missing = computeMissingFields(s).size();
        return (int) Math.round((double)(total - missing) / total * 100);
    }

    // ── Helpers privés ────────────────────────────────────────────────────

    private OnboardingStatusResponse buildStatus(Stagiaire s) {
        List<String> missing = s.getMissingFields() != null
                ? s.getMissingFields()
                : computeMissingFields(s);

        return OnboardingStatusResponse.builder()
                .profileCompleted(s.isProfileCompleted())
                .currentStep(s.getCurrentStep())
                .missingFields(missing)
                .completionScore(completionScore(s))
                .cvUploaded(s.getCvUrl() != null && !s.getCvUrl().isBlank())
                .cvAnalysis(s.getCvAnalysis())
                .build();
    }

    private Stagiaire findByUserId(String userId) {
        return stagiaireRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Profil stagiaire introuvable pour userId=" + userId));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
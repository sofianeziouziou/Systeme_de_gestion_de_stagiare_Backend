package com.hikma.stagiaires.service.Reunion;

import com.hikma.stagiaires.dto.Calendrier.CalendrierEventDTO;
import com.hikma.stagiaires.model.projet.Projet;
import com.hikma.stagiaires.model.stagiaire.Stagiaire;
import com.hikma.stagiaires.model.reunion.Reunion;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.ReunionRepository;
import com.hikma.stagiaires.repository.StagiaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendrierService {

    private final ReunionRepository   reunionRepository;
    private final ProjetRepository    projetRepository;
    private final StagiaireRepository stagiaireRepository;

    // ─────────────────────────────────────────────────────────────────────
    // TUTEUR
    // ─────────────────────────────────────────────────────────────────────
    public List<CalendrierEventDTO> getEvenementsTuteur(String tuteurId) {
        List<CalendrierEventDTO> events = new ArrayList<>();

        for (Reunion r : reunionRepository.findByTuteurId(tuteurId)) {
            CalendrierEventDTO dto = fromReunion(r, true);
            if (dto != null) events.add(dto);
        }

        for (Projet p : projetRepository.findByTuteurIdAndDeletedFalse(tuteurId)) {

            if (p.getSprints() != null) {
                for (Projet.Sprint sprint : p.getSprints()) {
                    if (sprint.getEndDate() == null) continue;
                    boolean enRetard = "EN_RETARD".equals(sprint.getStatus());
                    events.add(CalendrierEventDTO.builder()
                            .id("sprint-" + sprint.getId())
                            .title("Sprint : " + sprint.getTitle())
                            .start(sprint.getEndDate().atTime(8, 30))
                            .end(sprint.getEndDate().atTime(17, 0))
                            .type("SPRINT_DEADLINE")
                            .color(enRetard ? "#EF4444" : "#F59E0B")
                            .projetId(p.getId())
                            .stagiaireId(sprint.getStagiaireId())
                            .editable(false)
                            .build());
                }
            }

            if (p.getStagiaireIds() != null) {
                for (String userId : p.getStagiaireIds()) {
                    stagiaireRepository.findByUserId(userId).ifPresent(s ->
                            events.addAll(buildStageEvents(s, p.getId(), false)));
                }
            }
        }

        log.info("[CALENDRIER] Tuteur {} — {} événements", tuteurId, events.size());
        return events;
    }

    // ─────────────────────────────────────────────────────────────────────
    // STAGIAIRE
    // ─────────────────────────────────────────────────────────────────────
    public List<CalendrierEventDTO> getEvenementsStagiaire(String stagiaireId) {
        List<CalendrierEventDTO> events = new ArrayList<>();

        for (Reunion r : reunionRepository.findByStagiaireIdsContaining(stagiaireId)) {
            CalendrierEventDTO dto = fromReunion(r, false);
            if (dto != null) events.add(dto);
        }

        stagiaireRepository.findById(stagiaireId).ifPresent(s -> {
            events.addAll(buildStageEvents(s, null, false));

            if (s.getUserId() != null) {
                for (Projet p : projetRepository
                        .findByStagiaireIdsContainingAndDeletedFalse(s.getUserId())) {

                    if (p.getSprints() == null) continue;

                    for (Projet.Sprint sprint : p.getSprints()) {
                        if (sprint.getEndDate() == null) continue;
                        if (sprint.getStagiaireId() != null
                                && !sprint.getStagiaireId().equals(s.getUserId())
                                && !sprint.getStagiaireId().equals(stagiaireId)) continue;

                        boolean enRetard = "EN_RETARD".equals(sprint.getStatus());
                        events.add(CalendrierEventDTO.builder()
                                .id("sprint-" + sprint.getId())
                                .title("Sprint : " + sprint.getTitle())
                                .start(sprint.getEndDate().atTime(8, 30))
                                .end(sprint.getEndDate().atTime(17, 0))
                                .type("SPRINT_DEADLINE")
                                .color(enRetard ? "#EF4444" : "#F59E0B")
                                .projetId(p.getId())
                                .stagiaireId(stagiaireId)
                                .editable(false)
                                .build());
                    }
                }
            }
        });

        log.info("[CALENDRIER] Stagiaire {} — {} événements", stagiaireId, events.size());
        return events;
    }

    // ─────────────────────────────────────────────────────────────────────
    // RH
    // ─────────────────────────────────────────────────────────────────────
    public List<CalendrierEventDTO> getEvenementsRh() {
        List<CalendrierEventDTO> events = new ArrayList<>();

        for (Reunion r : reunionRepository.findAll()) {
            CalendrierEventDTO dto = fromReunion(r, false);
            if (dto != null) events.add(dto);
        }

        for (Stagiaire s : stagiaireRepository.findByDeletedFalse()) {
            events.addAll(buildStageEvents(s, null, false));
        }

        log.info("[CALENDRIER] RH — {} événements", events.size());
        return events;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private CalendrierEventDTO fromReunion(Reunion r, boolean editable) {
        String statut = r.getStatut() != null ? r.getStatut() : "PLANIFIEE";

        if ("ANNULEE".equals(statut)) return null;

        String color = switch (statut) {
            case "CONFIRMEE" -> "#10B981";
            default          -> "#3B82F6";
        };

        LocalDateTime end = r.getDateHeure() != null
                ? r.getDateHeure().plusMinutes(r.getDureeMins())
                : null;

        return CalendrierEventDTO.builder()
                .id("reunion-" + r.getId())
                .title("Réunion : " + r.getSujet())
                .start(r.getDateHeure())
                .end(end)
                .type("REUNION")
                .color(color)
                .stagiaireId(r.getStagiaireIds() != null && !r.getStagiaireIds().isEmpty()
                        ? r.getStagiaireIds().get(0) : null)
                .editable(editable)
                .build();
    }

    private List<CalendrierEventDTO> buildStageEvents(
            Stagiaire s, String projetId, boolean editable) {

        List<CalendrierEventDTO> events = new ArrayList<>();
        String nom = s.getFirstName() + " " + s.getLastName();

        // ✅ Début de stage à 8h30
        if (s.getStartDate() != null) {
            events.add(CalendrierEventDTO.builder()
                    .id("stage-debut-" + s.getId())
                    .title("Début stage : " + nom)
                    .start(s.getStartDate().atTime(8, 30))
                    .end(s.getStartDate().atTime(17, 0))
                    .type("STAGE_DEBUT")
                    .color("#10B981")
                    .projetId(projetId)
                    .stagiaireId(s.getId())
                    .editable(editable)
                    .build());
        }

        // ✅ Fin de stage à 8h30
        if (s.getEndDate() != null) {
            events.add(CalendrierEventDTO.builder()
                    .id("stage-fin-" + s.getId())
                    .title("Fin stage : " + nom)
                    .start(s.getEndDate().atTime(8, 30))
                    .end(s.getEndDate().atTime(17, 0))
                    .type("STAGE_FIN")
                    .color("#8B5CF6")
                    .projetId(projetId)
                    .stagiaireId(s.getId())
                    .editable(editable)
                    .build());
        }

        // ✅ Période complète — garde minuit pour couvrir toute la durée
        if (s.getStartDate() != null && s.getEndDate() != null) {
            events.add(CalendrierEventDTO.builder()
                    .id("stage-periode-" + s.getId())
                    .title("Stage : " + nom)
                    .start(s.getStartDate().atStartOfDay())
                    .end(s.getEndDate().atTime(17, 0))
                    .type("STAGE_PERIODE")
                    .color("#DBEAFE")
                    .stagiaireId(s.getId())
                    .editable(false)
                    .build());
        }

        return events;
    }
}
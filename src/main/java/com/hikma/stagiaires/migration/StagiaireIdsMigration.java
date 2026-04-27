package com.hikma.stagiaires.migration;

import com.hikma.stagiaires.model.projet.Projet;
import com.hikma.stagiaires.model.stagiaire.Stagiaire;
import com.hikma.stagiaires.repository.ProjetRepository;
import com.hikma.stagiaires.repository.StagiaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Migration one-shot : normalise stagiaireIds pour qu'ils
 * contiennent TOUJOURS le userId (pas le stagiaire._id).
 *
 * ── Comment l'utiliser ──────────────────────────────────────────
 * 1. Lancer le backend avec : --spring.profiles.active=migration
 * 2. Vérifier les logs : "MIGRATION TERMINÉE"
 * 3. Relancer sans le profile migration
 * ────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@Profile("migration")
@RequiredArgsConstructor
public class StagiaireIdsMigration implements CommandLineRunner {

    private final ProjetRepository    projetRepository;
    private final StagiaireRepository stagiaireRepository;

    @Override
    public void run(String... args) {
        log.info("=== DÉMARRAGE MIGRATION stagiaireIds ===");

        // ── Étape 1 : construire index stagiaire._id → userId ─────────────
        Map<String, String> idToUserId   = new HashMap<>();
        Map<String, String> idToFullName = new HashMap<>();

        stagiaireRepository.findAllWithUserId().forEach(s -> {
            if (s.getUserId() != null) {
                idToUserId.put(s.getId(), s.getUserId());
                idToFullName.put(s.getId(),
                        s.getFirstName() + " " + s.getLastName());
            }
        });

        log.info("Index construit : {} stagiaires avec userId", idToUserId.size());

        // ── Étape 2 : parcourir tous les projets ──────────────────────────
        List<Projet> projets  = projetRepository.findAll();
        int migrated  = 0;
        int alreadyOk = 0;
        int skipped   = 0;

        for (Projet projet : projets) {
            if (projet.getStagiaireIds() == null
                    || projet.getStagiaireIds().isEmpty()) {
                skipped++;
                continue;
            }

            List<String> normalizedIds = new ArrayList<>();
            boolean changed = false;

            for (String sid : projet.getStagiaireIds()) {
                if (idToUserId.containsKey(sid)) {
                    // C'est un stagiaire._id → convertir en userId
                    String userId = idToUserId.get(sid);
                    normalizedIds.add(userId);
                    changed = true;
                    log.info("Projet [{}] '{}' : stagiaire._id {} ({}) → userId {}",
                            projet.getId(),
                            projet.getTitle(),
                            sid,
                            idToFullName.getOrDefault(sid, "?"),
                            userId);
                } else {
                    // Déjà un userId ou id inconnu → garder tel quel
                    normalizedIds.add(sid);
                }
            }

            if (changed) {
                projet.setStagiaireIds(normalizedIds);
                projetRepository.save(projet);
                migrated++;
            } else {
                alreadyOk++;
            }
        }

        // ── Rapport final ─────────────────────────────────────────────────
        log.info("=== MIGRATION TERMINÉE ===");
        log.info("  Projets migrés   : {}", migrated);
        log.info("  Déjà corrects    : {}", alreadyOk);
        log.info("  Sans stagiaires  : {}", skipped);
        log.info("  Total traités    : {}", projets.size());
        log.info("=========================");
    }
}
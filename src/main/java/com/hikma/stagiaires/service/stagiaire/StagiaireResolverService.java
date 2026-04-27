package com.hikma.stagiaires.service.stagiaire;

import com.hikma.stagiaires.model.stagiaire.Stagiaire;
import com.hikma.stagiaires.repository.StagiaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Résout les stagiaireIds d'un projet en Stagiaires
 * via UN SEUL batch MongoDB au lieu d'un triple lookup par ID.
 *
 * Règle : stagiaireIds contient soit stagiaire._id soit stagiaire.userId
 * → Query 1 : findByIdInAndDeletedFalse
 * → Query 2 : findByUserIdInAndDeletedFalse (pour les ids non trouvés)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StagiaireResolverService {

    private final StagiaireRepository stagiaireRepository;

    /**
     * Résout une liste de stagiaireIds en Map<idOriginal, Stagiaire>.
     * Maximum 2 queries MongoDB quelle que soit la taille de la liste.
     *
     * @param stagiaireIds liste mixte de stagiaire._id ou userId
     * @return Map dont la clé est l'id original passé en entrée
     */
    public Map<String, Stagiaire> resolveBatch(List<String> stagiaireIds) {
        if (stagiaireIds == null || stagiaireIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Stagiaire> result = new LinkedHashMap<>();

        // ── Query 1 : chercher par stagiaire._id ──────────────────────────
        List<Stagiaire> foundById =
                stagiaireRepository.findByIdInAndDeletedFalse(stagiaireIds);

        Set<String> foundByIdKeys = new HashSet<>();
        for (Stagiaire s : foundById) {
            result.put(s.getId(), s);
            foundByIdKeys.add(s.getId());
        }

        // ── Ids non trouvés → peut-être des userIds ───────────────────────
        List<String> notFoundIds = stagiaireIds.stream()
                .filter(id -> !foundByIdKeys.contains(id))
                .collect(Collectors.toList());

        if (!notFoundIds.isEmpty()) {
            // ── Query 2 : chercher par userId ─────────────────────────────
            List<Stagiaire> foundByUserId =
                    stagiaireRepository.findByUserIdInAndDeletedFalse(notFoundIds);

            for (Stagiaire s : foundByUserId) {
                // Clé = userId original (ce qui était dans stagiaireIds)
                result.put(s.getUserId(), s);
            }

            // Ids toujours introuvables → warning
            Set<String> resolvedUserIds = foundByUserId.stream()
                    .map(Stagiaire::getUserId)
                    .collect(Collectors.toSet());

            List<String> stillMissing = notFoundIds.stream()
                    .filter(id -> !resolvedUserIds.contains(id))
                    .collect(Collectors.toList());

            if (!stillMissing.isEmpty()) {
                log.warn("[StagiaireResolver] {} id(s) non résolus : {}",
                        stillMissing.size(), stillMissing);
            }
        }

        return result;
    }

    /**
     * Retourne la liste ordonnée dans le même ordre que stagiaireIds.
     */
    public List<Stagiaire> resolveList(List<String> stagiaireIds) {
        if (stagiaireIds == null || stagiaireIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Stagiaire> resolved = resolveBatch(stagiaireIds);
        return stagiaireIds.stream()
                .map(resolved::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
// DESTINATION : src/main/java/com/hikma/stagiaires/service/SearchService.java
// ACTION      : REMPLACER le fichier complet
// EXPLICATION : JavaMailSender injecté correctement
//               + méthode envoyerEmailContact séparée proprement

package com.hikma.stagiaires.service;

import com.hikma.stagiaires.controller.SearchController.ContactRequest;
import com.hikma.stagiaires.dto.search.SearchDTOs.*;
import com.hikma.stagiaires.model.Stagiaire;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.repository.StagiaireRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final StagiaireRepository stagiaireRepository;
    private final JavaMailSender       mailSender;

    // ─────────────────────────────────────────────────────────────────────
    // Recherche principale avec scoring
    // ─────────────────────────────────────────────────────────────────────

    public SearchResponse search(SearchRequest req) {

        // 1. Charger tous les stagiaires actifs
        List<Stagiaire> tous = stagiaireRepository.findByDeletedFalse();

        // 2. Filtres de base
        List<Stagiaire> filtres = appliquerFiltres(tous, req);

        // 3. Calculer scores
        List<SearchResult> results = filtres.stream()
                .map(s -> calculerScore(s, req.getCompetences()))
                .filter(r -> req.getScoreMinimum() == null
                        || r.getFinalScore() >= req.getScoreMinimum())
                .sorted(Comparator
                        .comparingDouble(SearchResult::getFinalScore)
                        .reversed())
                .collect(Collectors.toList());

        // 4. Numéroter rangs
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRank(i + 1);
        }

        // 5. Pagination en mémoire
        int page  = req.getPage();
        int size  = req.getSize();
        int total = results.size();
        int from  = Math.min(page * size, total);
        int to    = Math.min(from + size, total);
        List<SearchResult> pageResults = results.subList(from, to);

        // 6. Suggestions
        List<String> suggestions = getSuggestions(tous, req.getCompetences());

        // 7. Réponse
        SearchResponse response = new SearchResponse();
        response.setResults(pageResults);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(total);
        response.setTotalPages((int) Math.ceil((double) total / size));
        response.setCompetencesRecherchees(
                req.getCompetences() != null
                        ? req.getCompetences() : List.of());
        response.setCompetencesSuggérees(suggestions);

        log.info("[SEARCH] {} résultats pour compétences={}",
                total, req.getCompetences());

        return response;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Filtres de base
    // ─────────────────────────────────────────────────────────────────────

    private List<Stagiaire> appliquerFiltres(
            List<Stagiaire> stagiaires, SearchRequest req) {

        return stagiaires.stream()
                .filter(s -> {
                    if (StringUtils.hasText(req.getDepartement())) {
                        if (s.getDepartement() == null) return false;
                        if (!s.getDepartement()
                                .equalsIgnoreCase(req.getDepartement()))
                            return false;
                    }
                    if (req.getLevel() != null) {
                        if (!req.getLevel().equals(s.getLevel()))
                            return false;
                    }
                    if (req.isAvecCvSeulement()) {
                        if (s.getCvUrl() == null || s.getCvUrl().isBlank())
                            return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Calcul score matching
    // matchScore(60%) + globalScore(40%) = finalScore
    // ─────────────────────────────────────────────────────────────────────

    private SearchResult calculerScore(
            Stagiaire stagiaire,
            List<String> competencesRecherchees) {

        SearchResult result = new SearchResult();
        result.setStagiaireId(stagiaire.getId());
        result.setFirstName(stagiaire.getFirstName());
        result.setLastName(stagiaire.getLastName());
        result.setEmail(stagiaire.getEmail());
        result.setDepartement(stagiaire.getDepartement());
        result.setPhotoUrl(stagiaire.getPhotoUrl());
        result.setCvUrl(stagiaire.getCvUrl());
        result.setSchool(stagiaire.getSchool());
        result.setLevel(stagiaire.getLevel() != null
                ? stagiaire.getLevel().name() : null);
        result.setTechnicalSkills(
                stagiaire.getTechnicalSkills() != null
                        ? stagiaire.getTechnicalSkills() : List.of());
        result.setBadge(stagiaire.getBadge() != null
                ? stagiaire.getBadge().name() : null);

        double globalScore = stagiaire.getGlobalScore() != null
                ? stagiaire.getGlobalScore() : 0;
        result.setGlobalScore(globalScore);

        // Pas de filtre compétences
        if (competencesRecherchees == null || competencesRecherchees.isEmpty()) {
            result.setMatchedSkills(List.of());
            result.setMissingSkills(List.of());
            result.setMatchScore(100.0);
            result.setFinalScore(round(globalScore));
            return result;
        }

        // Compétences du stagiaire (profil + CV)
        List<String> skillsStagiaire = new ArrayList<>();
        if (stagiaire.getTechnicalSkills() != null) {
            stagiaire.getTechnicalSkills().stream()
                    .map(String::toLowerCase)
                    .forEach(skillsStagiaire::add);
        }
        if (stagiaire.getCvAnalysis() != null
                && stagiaire.getCvAnalysis().getDetectedSkills() != null) {
            stagiaire.getCvAnalysis().getDetectedSkills().stream()
                    .map(String::toLowerCase)
                    .forEach(skillsStagiaire::add);
        }

        // Matched / missing
        List<String> matched = competencesRecherchees.stream()
                .filter(c -> skillsStagiaire.stream()
                        .anyMatch(s -> s.contains(c.toLowerCase())
                                || c.toLowerCase().contains(s)))
                .collect(Collectors.toList());

        List<String> missing = competencesRecherchees.stream()
                .filter(c -> matched.stream()
                        .noneMatch(m -> m.equalsIgnoreCase(c)))
                .collect(Collectors.toList());

        double matchScore = ((double) matched.size()
                / competencesRecherchees.size()) * 100.0;
        double finalScore = (matchScore * 0.60) + (globalScore * 0.40);

        result.setMatchedSkills(matched);
        result.setMissingSkills(missing);
        result.setMatchScore(round(matchScore));
        result.setFinalScore(round(finalScore));

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Suggestions compétences populaires
    // ─────────────────────────────────────────────────────────────────────

    private List<String> getSuggestions(
            List<Stagiaire> stagiaires,
            List<String> dejaRecherchees) {

        Map<String, Long> freq = stagiaires.stream()
                .filter(s -> s.getTechnicalSkills() != null)
                .flatMap(s -> s.getTechnicalSkills().stream())
                .collect(Collectors.groupingBy(
                        String::toLowerCase, Collectors.counting()));

        Set<String> deja = dejaRecherchees != null
                ? dejaRecherchees.stream()
                  .map(String::toLowerCase)
                  .collect(Collectors.toSet())
                : Set.of();

        return freq.entrySet().stream()
                .filter(e -> !deja.contains(e.getKey()))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Envoi email recrutement
    // ─────────────────────────────────────────────────────────────────────

    public void envoyerEmailContact(ContactRequest req, User rhUser) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setTo(req.getEmailStagiaire());
            helper.setSubject(
                    "Opportunite de recrutement - Hikma Pharmaceuticals");

            String poste = (req.getPostePropose() != null
                    && !req.getPostePropose().isBlank())
                    ? req.getPostePropose()
                    : "un poste au sein de notre equipe";

            String messageCustom = (req.getMessagePersonnalise() != null
                    && !req.getMessagePersonnalise().isBlank())
                    ? "<p style='color:#374151'>"
                      + req.getMessagePersonnalise() + "</p>"
                    : "";

            String html = buildEmailHtml(
                    req.getNomStagiaire(),
                    poste,
                    messageCustom,
                    rhUser.getEmail(),
                    rhUser.getFirstName(),
                    rhUser.getLastName()
            );

            helper.setText(html, true);
            mailSender.send(message);

            log.info("[CONTACT] Email envoye a {} par {}",
                    req.getEmailStagiaire(), rhUser.getEmail());

        } catch (Exception e) {
            log.error("[CONTACT] Erreur envoi email a {} : {}",
                    req.getEmailStagiaire(), e.getMessage(), e);
            throw new RuntimeException(
                    "Erreur envoi email : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Template HTML email
    // ─────────────────────────────────────────────────────────────────────

    private String buildEmailHtml(
            String nomStagiaire,
            String poste,
            String messageCustom,
            String rhEmail,
            String rhPrenom,
            String rhNom) {

        return "<html><body style='font-family:Arial,sans-serif;"
                + "max-width:600px;margin:0 auto;padding:20px;color:#1e293b'>"

                + "<div style='background:#1d4ed8;padding:24px;"
                + "border-radius:12px 12px 0 0;text-align:center'>"
                + "<h1 style='color:white;margin:0;font-size:22px'>"
                + "Hikma Pharmaceuticals</h1>"
                + "<p style='color:#bfdbfe;margin:8px 0 0;font-size:14px'>"
                + "Systeme Intelligent de Gestion des Stagiaires</p>"
                + "</div>"

                + "<div style='background:white;padding:32px;"
                + "border:1px solid #e2e8f0;"
                + "border-radius:0 0 12px 12px'>"

                + "<p style='font-size:16px;color:#374151'>Bonjour "
                + "<strong>" + nomStagiaire + "</strong>,</p>"

                + "<p style='color:#374151;line-height:1.7'>"
                + "Nous avons examine votre profil avec attention dans le cadre "
                + "de notre processus de recrutement chez "
                + "<strong>Hikma Pharmaceuticals</strong>.</p>"

                + "<p style='color:#374151;line-height:1.7'>"
                + "Votre parcours et vos competences nous ont particulierement "
                + "impressionnes, et nous souhaiterions vous proposer "
                + "<strong>" + poste + "</strong>.</p>"

                + messageCustom

                + "<div style='background:#f0f9ff;border:1px solid #bae6fd;"
                + "border-radius:8px;padding:16px;margin:24px 0'>"
                + "<p style='margin:0;color:#0369a1;font-weight:bold'>"
                + "Etes-vous interesse(e) par cette opportunite ?</p>"
                + "<p style='margin:8px 0 0;color:#0284c7;font-size:14px'>"
                + "Merci de nous repondre directement a cet email ou de "
                + "contacter notre departement RH.</p>"
                + "</div>"

                + "<div style='text-align:center;margin:28px 0'>"
                + "<a href='mailto:" + rhEmail
                + "?subject=Reponse%20opportunite%20Hikma' "
                + "style='background:#1d4ed8;color:white;padding:12px 28px;"
                + "border-radius:8px;text-decoration:none;"
                + "font-weight:bold;font-size:15px'>"
                + "Repondre a cette offre</a>"
                + "</div>"

                + "<hr style='border:none;border-top:1px solid #e2e8f0;"
                + "margin:24px 0'/>"

                + "<p style='font-size:13px;color:#94a3b8;text-align:center'>"
                + "Cet email a ete envoye par <strong>"
                + rhPrenom + " " + rhNom
                + "</strong> - Departement RH, Hikma Pharmaceuticals.<br/>"
                + "Si vous ne souhaitez pas etre contacte(e), "
                + "ignorez simplement cet email.</p>"

                + "</div></body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper arrondi
    // ─────────────────────────────────────────────────────────────────────

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
// DESTINATION : src/main/java/com/hikma/stagiaires/service/CvAnalysisService.java
// ACTION      : REMPLACER le fichier complet
// EXPLICATION : - Méthode analyze() ajoutée (alias de analyzeCv)
//               - languages et experience ajoutés dans CvData builder
//               - Dictionnaires langues et expérience réintégrés

package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.CvData;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CvAnalysisService {

    // ── Compétences techniques ────────────────────────────────────────────
    private static final List<String> TECH_SKILLS = List.of(
            "java", "python", "javascript", "typescript", "c++", "c#", "php",
            "ruby", "go", "rust", "kotlin", "swift", "scala", "r", "matlab",
            "dart", "perl", "bash", "shell", "powershell", "vba", "cobol",
            "react", "angular", "vue", "nextjs", "nuxtjs", "svelte",
            "html", "css", "sass", "tailwind", "bootstrap", "jquery",
            "redux", "zustand", "webpack", "vite", "figma",
            "spring", "spring boot", "django", "flask", "fastapi",
            "nodejs", "express", "nestjs", "laravel", "symfony",
            "asp.net", "rails", "graphql", "rest", "soap",
            "microservices", "api", "jwt", "oauth",
            "android", "ios", "flutter", "react native", "xamarin",
            "mysql", "postgresql", "mongodb", "redis", "oracle",
            "sqlite", "cassandra", "elasticsearch", "mariadb",
            "sql server", "firebase", "dynamodb", "neo4j",
            "docker", "kubernetes", "jenkins", "gitlab ci", "github actions",
            "aws", "azure", "gcp", "terraform", "ansible", "linux",
            "nginx", "apache", "ci/cd", "devops", "prometheus", "grafana",
            "machine learning", "deep learning", "tensorflow", "pytorch",
            "scikit-learn", "pandas", "numpy", "matplotlib", "spark",
            "hadoop", "tableau", "power bi", "data science", "nlp",
            "computer vision", "keras", "openai", "langchain",
            "git", "github", "gitlab", "bitbucket", "jira", "confluence",
            "postman", "swagger", "sonarqube", "maven", "gradle",
            "intellij", "vscode", "eclipse",
            "agile", "scrum", "kanban", "tdd", "bdd", "uml", "merise",
            "design patterns", "solid", "clean architecture"
    );

    // ── Soft skills ───────────────────────────────────────────────────────
    private static final List<String> SOFT_SKILLS = List.of(
            "communication", "travail en équipe", "leadership", "autonomie",
            "adaptabilité", "créativité", "résolution de problèmes",
            "gestion du temps", "organisation", "rigueur", "curiosité",
            "esprit d'analyse", "proactivité", "empathie", "négociation",
            "présentation", "rédaction", "veille technologique",
            "esprit critique", "gestion de projet"
    );

    // ── Langues humaines ──────────────────────────────────────────────────
    private static final List<String> LANGUAGES = List.of(
            "français", "anglais", "arabe", "espagnol", "allemand",
            "italien", "portugais", "chinois", "japonais", "russe",
            "french", "english", "arabic", "spanish", "german",
            "italian", "portuguese", "chinese", "japanese", "russian",
            "darija", "amazigh", "tamazight"
    );

    // ── Expérience ────────────────────────────────────────────────────────
    private static final List<String> EXPERIENCE_KEYWORDS = List.of(
            "stage", "stagiaire", "intern", "internship",
            "développeur", "developer", "ingénieur", "engineer",
            "analyste", "analyst", "consultant", "chef de projet",
            "project manager", "lead", "senior", "junior", "alternance",
            "cdi", "cdd", "freelance", "mission", "projet", "expérience"
    );

    // ── Écoles ────────────────────────────────────────────────────────────
    private static final List<String> SCHOOL_KEYWORDS = List.of(
            "université", "university", "école", "school", "institute",
            "institut", "faculty", "faculté", "campus", "college",
            "ensias", "ensa", "enset", "est", "fst", "fsjes", "fsac",
            "emsi", "emi", "ehtp", "insea", "iscae", "hec", "encg",
            "sup", "esig", "supmti", "supinfo", "epitech", "42"
    );

    // ── Niveaux d'études ──────────────────────────────────────────────────
    private static final List<String> LEVEL_KEYWORDS = List.of(
            "doctorat", "phd",
            "master", "mba", "bac+5", "ingénieur", "ingenieur",
            "licence", "bachelor", "bac+3",
            "bts", "dut", "bac+2", "bac+4"
    );

    // ── Certifications ────────────────────────────────────────────────────
    private static final List<String> CERTIFICATIONS = List.of(
            "aws certified", "microsoft certified", "oracle certified",
            "cisco", "pmp", "itil", "scrum master", "comptia",
            "google cloud", "azure certified", "kubernetes certified"
    );

    // ─────────────────────────────────────────────────────────────────────
    // MÉTHODES PUBLIQUES
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Point d'entrée principal — appelé depuis StagiaireService.
     * Alias de analyzeCv() pour compatibilité.
     */
    public CvData analyze(MultipartFile file) {
        return analyzeCv(file);
    }

    /**
     * Analyse depuis MultipartFile.
     */
    public CvData analyzeCv(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return analyzeBytes(bytes, file.getOriginalFilename());
        } catch (IOException e) {
            log.error("[CV] Erreur lecture fichier : {}", e.getMessage(), e);
            return buildEmptyCvData();
        }
    }

    /**
     * Analyse depuis bytes — appelé depuis OnboardingService.
     */
    public CvData analyzeCvFromBytes(byte[] bytes, String filename) {
        return analyzeBytes(bytes, filename);
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOGIQUE INTERNE
    // ─────────────────────────────────────────────────────────────────────

    private CvData analyzeBytes(byte[] bytes, String filename) {
        try {
            String rawText = extractTextFromPdf(bytes);

            if (rawText == null || rawText.isBlank()) {
                log.warn("[CV] Texte vide extrait du PDF : {}", filename);
                return buildEmptyCvData();
            }

            log.info("[CV] Texte extrait — {} caractères depuis {}",
                    rawText.length(), filename);

            List<String> skills     = extractSkills(rawText);
            List<String> languages  = extractLanguages(rawText);
            List<String> experience = extractExperience(rawText);
            String       school     = extractSchool(rawText);
            String       level      = extractLevel(rawText);
            double       confidence = calculateConfidence(
                    skills, school, level, languages);

            log.info("[CV] Résultat — {} compétences, {} langues, école={}, niveau={}, confiance={}%",
                    skills.size(), languages.size(), school, level,
                    Math.round(confidence * 100));

            return CvData.builder()
                    .rawText(rawText)
                    .detectedSkills(skills)
                    .languages(languages)
                    .experience(experience)
                    .detectedSchool(school)
                    .detectedLevel(level)
                    .analysisConfidence(confidence)
                    .analyzedAt(LocalDateTime.now())
                    .build();

        } catch (IOException e) {
            log.error("[CV] Erreur analyse PDF {} : {}", filename, e.getMessage(), e);
            return buildEmptyCvData();
        }
    }

    // ── Extraction texte PDF (PDFBox 3.x) ────────────────────────────────

    private String extractTextFromPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            return stripper.getText(document);
        }
    }

    // ── Extraction compétences ────────────────────────────────────────────

    private List<String> extractSkills(String text) {
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();

        for (String skill : TECH_SKILLS) {
            if (containsWord(lower, skill)) {
                found.add(capitalize(skill));
            }
        }
        for (String soft : SOFT_SKILLS) {
            if (lower.contains(soft)) {
                found.add(capitalize(soft));
            }
        }
        for (String cert : CERTIFICATIONS) {
            if (lower.contains(cert)) {
                found.add(capitalize(cert));
            }
        }

        return found.stream().distinct().sorted().collect(Collectors.toList());
    }

    // ── Extraction langues ────────────────────────────────────────────────

    private List<String> extractLanguages(String text) {
        String lower = text.toLowerCase();
        return LANGUAGES.stream()
                .filter(lang -> containsWord(lower, lang))
                .map(this::capitalize)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Extraction expérience ─────────────────────────────────────────────

    private List<String> extractExperience(String text) {
        String lower = text.toLowerCase();
        List<String> found = EXPERIENCE_KEYWORDS.stream()
                .filter(kw -> lower.contains(kw))
                .map(this::capitalize)
                .distinct()
                .collect(Collectors.toList());

        // Chercher "X an(s)" ou "X year(s)"
        extractYearsOfExperience(text).ifPresent(found::add);

        return found;
    }

    // ── Extraction école ──────────────────────────────────────────────────

    private String extractSchool(String text) {
        String lower = text.toLowerCase();
        for (String keyword : SCHOOL_KEYWORDS) {
            if (lower.contains(keyword)) {
                String line = extractLineContaining(text, keyword);
                if (line != null && !line.isBlank()) {
                    return truncate(line.trim(), 80);
                }
                return capitalize(keyword);
            }
        }
        return null;
    }

    // ── Extraction niveau ─────────────────────────────────────────────────

    private String extractLevel(String text) {
        String lower = text.toLowerCase();
        for (String keyword : LEVEL_KEYWORDS) {
            if (lower.contains(keyword)) {
                return capitalize(keyword);
            }
        }
        return null;
    }

    // ── Calcul confiance ──────────────────────────────────────────────────

    private double calculateConfidence(
            List<String> skills, String school,
            String level, List<String> languages) {

        double score = 0.0;
        score += Math.min(skills.size() * 0.05, 0.40);   // max 40%
        if (school    != null) score += 0.25;             // +25%
        if (level     != null) score += 0.20;             // +20%
        if (!languages.isEmpty()) score += 0.15;          // +15%
        return Math.min(score, 1.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean containsWord(String text, String word) {
        if (word.contains(" ")) {
            return text.contains(word);
        }
        return text.matches(
                "(?s).*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*"
        );
    }

    private Optional<String> extractYearsOfExperience(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d+)\\s*(an[s]?|année[s]?|year[s]?)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1) + " an(s) d'expérience");
        }
        return Optional.empty();
    }

    private String extractLineContaining(String text, String keyword) {
        String lower = text.toLowerCase();
        int idx = lower.indexOf(keyword);
        if (idx < 0) return null;
        int start = text.lastIndexOf('\n', idx);
        int end   = text.indexOf('\n', idx);
        start = start < 0 ? 0 : start + 1;
        end   = end   < 0 ? text.length() : end;
        return text.substring(start, end);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    private CvData buildEmptyCvData() {
        return CvData.builder()
                .detectedSkills(List.of())
                .languages(List.of())
                .experience(List.of())
                .detectedSchool(null)
                .detectedLevel(null)
                .analysisConfidence(0.0)
                .rawText("")
                .analyzedAt(LocalDateTime.now())
                .build();
    }
}
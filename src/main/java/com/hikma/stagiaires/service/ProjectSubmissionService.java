// DESTINATION : src/main/java/com/hikma/stagiaires/service/ProjectSubmissionService.java
package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.ProjectSubmission;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.repository.ProjectSubmissionRepository;
import com.hikma.stagiaires.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSubmissionService {

    private final ProjectSubmissionRepository submissionRepository;
    private final ProjetRepository            projetRepository;
    private final FileStorageService          fileStorageService;

    // ── Lister les soumissions d'un projet ───────────────────────────────
    public List<ProjectSubmission> getByProjet(String projetId) {
        // Vérifier que le projet existe
        projetRepository.findById(projetId)
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + projetId));
        return submissionRepository.findByProjetIdOrderByUploadedAtDesc(projetId);
    }

    // ── Ajouter une preuve fichier (PDF ou image) ─────────────────────────
    public ProjectSubmission addFileSubmission(
            String projetId,
            MultipartFile file,
            String title,
            String description,
            User currentUser) {

        // Vérifier projet
        projetRepository.findById(projetId)
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + projetId));

        // Déterminer type
        String contentType = file.getContentType();
        String type;
        if (contentType != null && contentType.startsWith("image/")) {
            type = "IMAGE";
        } else if (contentType != null && contentType.equals("application/pdf")) {
            type = "PDF";
        } else {
            type = "FICHIER";
        }

        // Upload
        String folder = "submissions/" + projetId;
        String fileUrl = fileStorageService.uploadFile(file, folder);

        ProjectSubmission submission = ProjectSubmission.builder()
                .projetId(projetId)
                .uploadedByUserId(currentUser.getId())
                .uploadedByName(currentUser.getFirstName() + " " + currentUser.getLastName())
                .uploadedByRole(currentUser.getRole().name())
                .type(type)
                .fileUrl(fileUrl)
                .title(title != null && !title.isBlank() ? title : file.getOriginalFilename())
                .description(description)
                .build();

        ProjectSubmission saved = submissionRepository.save(submission);
        log.info("[SUBMISSION] Fichier ajouté pour projet={} par userId={}", projetId, currentUser.getId());
        return saved;
    }

    // ── Ajouter un lien vidéo externe (YouTube, Drive...) ─────────────────
    public ProjectSubmission addVideoLink(
            String projetId,
            String videoUrl,
            String title,
            String description,
            User currentUser) {

        projetRepository.findById(projetId)
                .orElseThrow(() -> new NoSuchElementException("Projet introuvable : " + projetId));

        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("L'URL de la vidéo est requise");
        }

        ProjectSubmission submission = ProjectSubmission.builder()
                .projetId(projetId)
                .uploadedByUserId(currentUser.getId())
                .uploadedByName(currentUser.getFirstName() + " " + currentUser.getLastName())
                .uploadedByRole(currentUser.getRole().name())
                .type("VIDEO_URL")
                .fileUrl(videoUrl)
                .title(title != null && !title.isBlank() ? title : "Vidéo de démonstration")
                .description(description)
                .build();

        ProjectSubmission saved = submissionRepository.save(submission);
        log.info("[SUBMISSION] Lien vidéo ajouté pour projet={} par userId={}", projetId, currentUser.getId());
        return saved;
    }

    // ── Supprimer une soumission (seulement son auteur) ────────────────────
    public void delete(String submissionId, User currentUser) {
        ProjectSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NoSuchElementException("Soumission introuvable"));

        if (!sub.getUploadedByUserId().equals(currentUser.getId())
                && !currentUser.getRole().name().equals("RH")) {
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer cette soumission");
        }

        submissionRepository.deleteById(submissionId);
        log.info("[SUBMISSION] Supprimée id={} par userId={}", submissionId, currentUser.getId());
    }
}
package com.hikma.stagiaires.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Sert les fichiers uploadés.
     * Ex: GET /files/cv/69b6bb.../Sofiane CVF.pdf
     */
    @GetMapping("/{folder}/{subfolder}/{filename}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String folder,
            @PathVariable String subfolder,
            @PathVariable String filename) {
        return serveResource(Paths.get(uploadDir, folder, subfolder, filename));
    }

    /**
     * Variante sans sous-dossier.
     * Ex: GET /files/photos/uuid_photo.jpg
     */
    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<Resource> serveFileFlat(
            @PathVariable String folder,
            @PathVariable String filename) {
        return serveResource(Paths.get(uploadDir, folder, filename));
    }

    private ResponseEntity<Resource> serveResource(Path filePath) {
        try {
            Resource resource = new UrlResource(filePath.toAbsolutePath().toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Fichier introuvable : {}", filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            // Détection du type MIME
            String contentType = detectContentType(filePath.getFileName().toString());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("URL malformée pour le fichier : {}", filePath, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".doc"))  return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}
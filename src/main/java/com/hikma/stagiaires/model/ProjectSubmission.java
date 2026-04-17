// DESTINATION : src/main/java/com/hikma/stagiaires/model/ProjectSubmission.java
package com.hikma.stagiaires.model;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "project_submissions")
public class ProjectSubmission {

    @Id
    private String id;

    private String projetId;
    private String uploadedByUserId;
    private String uploadedByName;
    private String uploadedByRole;   // STAGIAIRE ou TUTEUR

    // Type : PDF, IMAGE, VIDEO_URL
    private String type;

    // URL du fichier stocké OU lien vidéo externe (YouTube, Drive...)
    private String fileUrl;

    // Description / commentaire sur la preuve
    private String description;

    // Titre optionnel
    private String title;

    @CreatedDate
    private LocalDateTime uploadedAt;
}
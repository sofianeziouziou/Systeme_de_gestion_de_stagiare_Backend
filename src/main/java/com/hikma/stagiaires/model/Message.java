// DESTINATION : src/main/java/com/hikma/stagiaires/model/Message.java
package com.hikma.stagiaires.model;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    // Projet concerné
    private String projetId;

    // Expéditeur
    private String senderId;
    private String senderName;
    private String senderRole;   // RH, TUTEUR, STAGIAIRE

    // Contenu
    private String content;

    // Lu par (liste de userIds)
    @Builder.Default
    private List<String> readBy = List.of();

    @CreatedDate
    private LocalDateTime createdAt;
}
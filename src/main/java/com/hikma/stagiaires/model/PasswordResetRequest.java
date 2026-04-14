package com.hikma.stagiaires.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "password_reset_requests")
public class PasswordResetRequest {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;
    private String userName;
    private String userRole;

    @Builder.Default
    private String status = "PENDING"; // PENDING / APPROVED / REJECTED

    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    private LocalDateTime processedAt;
    private String processedBy; // email du RH qui a traité
}
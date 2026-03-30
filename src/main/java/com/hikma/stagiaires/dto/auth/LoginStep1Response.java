package com.hikma.stagiaires.dto.auth;
import lombok.AllArgsConstructor; import lombok.Data;
@Data @AllArgsConstructor
public class LoginStep1Response {
    private String message;  // "Code envoyé à so***@gmail.com"
    private String channel;  // "EMAIL" ou "SMS"
    private String email;    // pour l'étape 2
}
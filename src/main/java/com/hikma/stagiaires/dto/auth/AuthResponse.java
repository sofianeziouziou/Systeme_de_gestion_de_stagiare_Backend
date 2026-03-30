package com.hikma.stagiaires.dto.auth;
import lombok.Builder; import lombok.Data;
@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}
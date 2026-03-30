package com.hikma.stagiaires.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDTOs {

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 6)
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8)
        private String password;

        @NotBlank
        private String firstName;

        @NotBlank
        private String lastName;

        private String role; // RH, TUTEUR, STAGIAIRE
    }

    @Data
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private UserInfo user;

        // ── NOUVEAU ──────────────────────────────────────────────────────
        private boolean pendingApproval = false;   // true si EN_ATTENTE
        private String message;                    // message informatif

        @Data
        public static class UserInfo {
            private String id;
            private String email;
            private String firstName;
            private String lastName;
            private String role;
            private String photoUrl;
            private String accountStatus;          // EN_ATTENTE / APPROUVE / REFUSE
        }
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class ForgotPasswordRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;

        @NotBlank @Size(min = 8)
        private String newPassword;
    }

}
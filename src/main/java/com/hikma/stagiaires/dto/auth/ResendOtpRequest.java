package com.hikma.stagiaires.dto.auth;
import lombok.Data;
@Data
public class ResendOtpRequest {
    private String email;
    private String otpChannel; // "EMAIL" ou "SMS"
}
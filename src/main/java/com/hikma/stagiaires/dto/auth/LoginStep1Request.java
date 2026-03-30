package com.hikma.stagiaires.dto.auth;
import lombok.Data;
@Data
public class LoginStep1Request {
    private String email;
    private String password;
    private String otpChannel; // "EMAIL" ou "SMS"
}
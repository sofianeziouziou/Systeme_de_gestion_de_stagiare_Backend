package com.hikma.stagiaires.dto.auth;
import lombok.Data;
@Data
public class LoginStep2Request {
    private String email;
    private String code; // 6 chiffres
}
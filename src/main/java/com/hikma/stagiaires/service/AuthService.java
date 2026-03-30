package com.hikma.stagiaires.service;

import com.hikma.stagiaires.dto.auth.*;
import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.UserRepository;
import com.hikma.stagiaires.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository   userRepository;
    private final PasswordEncoder  passwordEncoder;
    private final JwtService       jwtService;
    private final OtpService       otpService;
    private final StagiaireService stagiaireService;

    // ── INSCRIPTION ───────────────────────────────────────────────────────
    public void register(RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }

        AccountStatus status = Role.RH.equals(req.getRole())
                ? AccountStatus.APPROUVE
                : AccountStatus.EN_ATTENTE;

        User user = User.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(req.getRole())
                .accountStatus(status)
                .build();

        userRepository.save(user);
        log.info("Nouveau compte {} enregistré : {}", req.getRole(), req.getEmail());
    }

    // ── ÉTAPE 1 : Login → envoie OTP ─────────────────────────────────────
    public LoginStep1Response loginStep1(LoginStep1Request req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email ou mot de passe incorrect."));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email ou mot de passe incorrect.");
        }

        if (AccountStatus.EN_ATTENTE.equals(user.getAccountStatus())) {
            throw new IllegalStateException("Votre compte est en attente d'approbation.");
        }
        if (AccountStatus.REFUSE.equals(user.getAccountStatus())) {
            throw new IllegalStateException("Votre compte a été désactivé. Contactez le RH.");
        }

        String canal = req.getOtpChannel(); // "EMAIL" ou "SMS"
        if ("SMS".equals(canal)) {
            if (user.getPhone() == null || user.getPhone().isBlank()) {
                throw new IllegalArgumentException(
                        "Aucun numéro de téléphone enregistré. Choisissez la vérification par email.");
            }
            otpService.sendOtpBySms(user.getEmail(), user.getPhone(), user.getFirstName());
            return new LoginStep1Response(
                    "SMS envoyé au " + maskPhone(user.getPhone()), "SMS", user.getEmail());
        } else {
            otpService.sendOtpByEmail(user.getEmail(), user.getFirstName());
            return new LoginStep1Response(
                    "Code envoyé à " + maskEmail(user.getEmail()), "EMAIL", user.getEmail());
        }
    }

    // ── ÉTAPE 2 : Vérifier OTP → délivre JWT ─────────────────────────────
    public AuthResponse loginStep2(LoginStep2Request req) {
        if (!otpService.verifyOtp(req.getEmail(), req.getCode())) {
            throw new IllegalArgumentException("Code invalide ou expiré.");
        }

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("Connexion 2FA réussie : {} ({})", user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }

    // ── Renvoyer OTP ──────────────────────────────────────────────────────
    public void resendOtp(ResendOtpRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        if ("SMS".equals(req.getOtpChannel())) {
            otpService.sendOtpBySms(user.getEmail(), user.getPhone(), user.getFirstName());
        } else {
            otpService.sendOtpByEmail(user.getEmail(), user.getFirstName());
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (phone.length() < 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }
}
package com.hikma.stagiaires.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OtpService {

    @Value("${otp.expiration-minutes:5}")
    private int expirationMinutes;

    @Value("${otp.length:6}")
    private int otpLength;

    // true = mode développement (code visible dans les logs)
    // false = mode production (SendGrid + Twilio)
    @Value("${otp.dev-mode:true}")
    private boolean devMode;

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    // ── Envoyer OTP par EMAIL ─────────────────────────────────────────────
    public void sendOtpByEmail(String email, String firstName) {
        String code = generateCode();
        otpStore.put(email, new OtpEntry(code, LocalDateTime.now().plusMinutes(expirationMinutes)));

        if (devMode) {
            log.warn("╔══════════════════════════════════════╗");
            log.warn("║  OTP DEV MODE — email : {}  ║", email);
            log.warn("║  CODE : {}                            ║", code);
            log.warn("║  Expire dans {} minutes               ║", expirationMinutes);
            log.warn("╚══════════════════════════════════════╝");
        } else {
            sendViaEmail(email, firstName, code);
        }
    }

    // ── Envoyer OTP par SMS ───────────────────────────────────────────────
    public void sendOtpBySms(String email, String phoneNumber, String firstName) {
        String code = generateCode();
        otpStore.put(email, new OtpEntry(code, LocalDateTime.now().plusMinutes(expirationMinutes)));

        if (devMode) {
            log.warn("╔══════════════════════════════════════╗");
            log.warn("║  OTP DEV MODE — SMS vers : {}  ║", phoneNumber);
            log.warn("║  CODE : {}                            ║", code);
            log.warn("║  Expire dans {} minutes               ║", expirationMinutes);
            log.warn("╚══════════════════════════════════════╝");
        } else {
            sendViaSms(phoneNumber, firstName, code);
        }
    }

    // ── Vérifier le code ──────────────────────────────────────────────────
    public boolean verifyOtp(String email, String code) {
        OtpEntry entry = otpStore.get(email);
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiresAt())) {
            otpStore.remove(email);
            return false;
        }
        if (!entry.code().equals(code.trim())) return false;
        otpStore.remove(email);
        return true;
    }

    public boolean hasPendingOtp(String email) {
        OtpEntry entry = otpStore.get(email);
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiresAt())) {
            otpStore.remove(email);
            return false;
        }
        return true;
    }

    private void sendViaEmail(String email, String firstName, String code) {
        throw new RuntimeException("SendGrid non configuré — définir otp.dev-mode=false seulement après config sendgrid.api-key");
    }

    private void sendViaSms(String phoneNumber, String firstName, String code) {
        throw new RuntimeException("Twilio non configuré — définir otp.dev-mode=false seulement après config twilio.*");
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < otpLength; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    private record OtpEntry(String code, LocalDateTime expiresAt) {}
}
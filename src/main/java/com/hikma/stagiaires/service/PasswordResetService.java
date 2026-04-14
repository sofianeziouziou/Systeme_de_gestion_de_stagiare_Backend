package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.PasswordResetRequest;
import com.hikma.stagiaires.model.User;
import com.hikma.stagiaires.repository.PasswordResetRequestRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetRequestRepository resetRepository;
    private final UserRepository userRepository;
    private final EmailNotificationService emailService;
    private final PasswordEncoder passwordEncoder;

    // ─── Stagiaire / Tuteur : envoyer une demande ───────────────────────────

    public void createRequest(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Aucun compte trouvé avec cet email."));

        if (resetRepository.existsByUserIdAndStatus(user.getId(), "PENDING")) {
            throw new RuntimeException("Une demande est déjà en attente. Veuillez patienter.");
        }

        PasswordResetRequest request = PasswordResetRequest.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userName(user.getFirstName() + " " + user.getLastName())
                .userRole(user.getRole().name())
                .build();

        resetRepository.save(request);

        emailService.sendPasswordResetRequestToRH(
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                user.getRole().name()
        );

        log.info("Demande reset mot de passe créée pour : {}", email);
    }

    // ─── RH : liste des demandes en attente ─────────────────────────────────

    public List<PasswordResetRequest> getPendingRequests() {
        return resetRepository.findByStatusOrderByRequestedAtDesc("PENDING");
    }

    // ─── RH : approuver une demande ─────────────────────────────────────────

    public void approveRequest(String requestId, String rhEmail) {
        PasswordResetRequest request = getRequestOrThrow(requestId);

        String newPassword = generatePassword();

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        emailService.sendNewPasswordToUser(
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                newPassword
        );

        request.setStatus("APPROVED");
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(rhEmail);
        resetRepository.save(request);

        log.info("Demande {} approuvée par {}", requestId, rhEmail);
    }

    // ─── RH : rejeter une demande ───────────────────────────────────────────

    public void rejectRequest(String requestId, String rhEmail) {
        PasswordResetRequest request = getRequestOrThrow(requestId);

        emailService.sendPasswordResetRejected(
                request.getUserEmail(),
                request.getUserName()
        );

        request.setStatus("REJECTED");
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(rhEmail);
        resetRepository.save(request);

        log.info("Demande {} rejetée par {}", requestId, rhEmail);
    }

    // ─── User connecté : changer son mot de passe ───────────────────────────

    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Ancien mot de passe incorrect.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Mot de passe changé pour : {}", user.getEmail());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private PasswordResetRequest getRequestOrThrow(String requestId) {
        return resetRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Demande introuvable."));
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$!";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
package com.hikma.stagiaires.config;

import com.hikma.stagiaires.model.*;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL    = "admin@hikma.ma";
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Override
    public void run(String... args) {
        try {
            // P2 FIX : findByEmail() = 1 requête indexée
            // AVANT : findAll().stream().anyMatch() → charge TOUS les users
            // APRÈS : findByEmail()                → 1 seule requête O(1)
            boolean adminExists = userRepository.findByEmail(ADMIN_EMAIL).isPresent();

            if (!adminExists) {
                User admin = User.builder()
                        .firstName("Admin")
                        .lastName("RH")
                        .email(ADMIN_EMAIL)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role(Role.RH)
                        .accountStatus(AccountStatus.APPROUVE)
                        .build();

                userRepository.save(admin);

                log.info("══════════════════════════════════════════════");
                log.info("  Compte RH admin créé automatiquement");
                log.info("  Email    : {}", ADMIN_EMAIL);
                log.info("  Password : {}", ADMIN_PASSWORD);
                log.info("  ⚠ Changez le mot de passe après connexion !");
                log.info("══════════════════════════════════════════════");
            } else {
                log.info("Compte admin déjà existant — aucune action.");
            }

        } catch (Exception e) {
            // Ne jamais bloquer le démarrage à cause de l'initialisation
            log.warn("DataInitializer ignoré : {}", e.getMessage());
        }
    }
}
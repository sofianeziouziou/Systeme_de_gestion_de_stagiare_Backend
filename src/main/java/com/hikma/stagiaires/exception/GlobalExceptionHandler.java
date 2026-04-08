// src/main/java/com/hikma/sims/exception/GlobalExceptionHandler.java
package com.hikma.stagiaires.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Réponse standard ─────────────────────────────────────────────────

    private record ApiError(
            String  code,
            String  message,
            int     status,
            Instant timestamp
    ) {}

    private static ResponseEntity<ApiError> error(
            String code, String message, HttpStatus status
    ) {
        return ResponseEntity
                .status(status)
                .body(new ApiError(code, message, status.value(), Instant.now()));
    }

    // ── Exceptions métier SIMS ────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        log.warn("[SIMS] Ressource introuvable : {}", ex.getMessage());
        return error("RESOURCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("[SIMS] Erreur métier [{}] : {}", ex.getCode(), ex.getMessage());
        return error(ex.getCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        log.warn("[SIMS] Conflit : {}", ex.getMessage());
        return error("CONFLICT", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        log.warn("[SIMS] Accès interdit : {}", ex.getMessage());
        return error(ex.getCode(), ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    // ── Sécurité Spring ───────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return error("INVALID_CREDENTIALS", "Email ou mot de passe incorrect.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
        return error("ACCOUNT_DISABLED", "Votre compte a été désactivé. Contactez le RH.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiError> handleLocked(LockedException ex) {
        return error("ACCOUNT_LOCKED", "Votre compte est en attente d'approbation.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return error("ACCESS_DENIED", "Vous n'avez pas les droits pour cette action.", HttpStatus.FORBIDDEN);
    }

    // ── Validation @Valid ─────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code",      "VALIDATION_ERROR");
        body.put("message",   "Données invalides — vérifiez les champs.");
        body.put("fields",    fieldErrors);
        body.put("status",    HttpStatus.BAD_REQUEST.value());
        body.put("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    // ── Upload ────────────────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return error("FILE_TOO_LARGE", "Le fichier dépasse la taille maximale autorisée (5 MB).", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // ── Fallback ──────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex) {
        log.error("[SIMS] Erreur inattendue : {}", ex.getMessage(), ex);
        return error(
                "INTERNAL_ERROR",
                "Une erreur interne est survenue. Réessayez ou contactez l'administrateur.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
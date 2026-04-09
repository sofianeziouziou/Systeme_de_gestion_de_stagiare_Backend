package com.hikma.stagiaires.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Données extraites du CV par l'analyse NLP (F4).
 * Stocké comme document embedded dans Stagiaire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvData {
    private List<String> skills;
    private String       education;
    private List<String> languages;
    private List<String> experience;
    private String       rawText;
    private LocalDateTime analyzedAt;
}
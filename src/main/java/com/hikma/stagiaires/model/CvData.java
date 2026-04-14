// DESTINATION : src/main/java/com/hikma/stagiaires/model/CvData.java
// ACTION      : REMPLACER le fichier complet
// EXPLICATION : Ajout de languages et experience qui manquaient

package com.hikma.stagiaires.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvData {
    private List<String> detectedSkills;
    private String       detectedSchool;
    private String       detectedLevel;
    private List<String> languages;      // ← AJOUTÉ
    private List<String> experience;     // ← AJOUTÉ

    @Builder.Default
    private double analysisConfidence = 0.0;

    private LocalDateTime analyzedAt;
    private String        rawText;
}
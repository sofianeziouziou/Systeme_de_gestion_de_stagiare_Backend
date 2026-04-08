// src/main/java/com/hikma/sims/enums/Departement.java
package com.hikma.stagiaires.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Departement {

    IT("IT"),
    RH("RH"),
    FINANCE("Finance"),
    MARKETING("Marketing"),
    R_AND_D("R&D"),
    PRODUCTION("Production"),
    QUALITE("Qualité"),
    LOGISTIQUE("Logistique");

    private final String label;

    Departement(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /** Conversion depuis string — insensible à la casse */
    public static Departement fromString(String value) {
        if (value == null) return null;
        for (Departement d : values()) {
            if (d.name().equalsIgnoreCase(value)
                    || d.label.equalsIgnoreCase(value)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Département inconnu : " + value);
    }
}
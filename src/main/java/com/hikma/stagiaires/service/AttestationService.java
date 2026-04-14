package com.hikma.stagiaires.service;

import com.hikma.stagiaires.model.Stagiaire;
import com.hikma.stagiaires.model.StagiaireStatus;
import com.hikma.stagiaires.repository.StagiaireRepository;
import com.hikma.stagiaires.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttestationService {

    private final StagiaireRepository stagiaireRepository;
    private final UserRepository       userRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    // ─────────────────────────────────────────────────────────────────────
    // Vérification accès Stagiaire (status TERMINE requis)
    // ─────────────────────────────────────────────────────────────────────

    public void verifierAccesStagiaire(String stagiaireId, String userEmail) {
        Stagiaire s = stagiaireRepository.findById(stagiaireId)
                .orElseThrow(() -> new RuntimeException("Stagiaire introuvable."));

        if (!s.getEmail().equals(userEmail)) {
            throw new RuntimeException("Accès refusé.");
        }

        if (s.getStatus() != StagiaireStatus.TERMINE) {
            throw new RuntimeException("L'attestation n'est disponible qu'à la fin du stage.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Générer l'attestation PDF (RH = toujours, Stagiaire = après vérif)
    // ─────────────────────────────────────────────────────────────────────

    public byte[] genererAttestation(String stagiaireId) {

        // 1. Récupérer le stagiaire
        Stagiaire s = stagiaireRepository.findById(stagiaireId)
                .orElseThrow(() -> new RuntimeException("Stagiaire introuvable."));

        // 2. Récupérer le nom du tuteur
        String tuteurNom = "—";
        if (s.getTuteurId() != null) {
            tuteurNom = userRepository.findById(s.getTuteurId())
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse("—");
        }

        // 3. Préparer les données
        String nomComplet  = s.getFirstName() + " " + s.getLastName();
        String ecole       = s.getSchool()         != null ? s.getSchool()         : "—";
        String filiere     = s.getFieldOfStudy()   != null ? s.getFieldOfStudy()   : "—";
        String niveau      = s.getLevel()          != null ? s.getLevel().name()   : "—";
        String departement = s.getDepartement()    != null ? s.getDepartement()    : "—";
        String debut       = s.getStartDate()      != null ? s.getStartDate().format(FMT) : "—";
        String fin         = s.getEndDate()        != null ? s.getEndDate().format(FMT)   : "—";
        int    duree       = s.getDurationMonths() != null ? s.getDurationMonths() : 0;

        // 4. Générer le PDF
        try (PDDocument doc = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float W      = PDRectangle.A4.getWidth();
            float H      = PDRectangle.A4.getHeight();
            float margin = 70f;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // ── Bordure extérieure ────────────────────────────────────
                cs.setLineWidth(2f);
                cs.setStrokingColor(0.11f, 0.30f, 0.69f);
                cs.addRect(30, 30, W - 60, H - 60);
                cs.stroke();

                // ── Bordure intérieure fine ───────────────────────────────
                cs.setLineWidth(0.5f);
                cs.addRect(38, 38, W - 76, H - 76);
                cs.stroke();

                // ── En-tête société ───────────────────────────────────────
                drawCenteredText(cs, "HIKMA PHARMACEUTICALS", W, H - 90,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16f,
                        0.11f, 0.30f, 0.69f);

                drawCenteredText(cs, "Système de Gestion des Stagiaires — SIMS", W, H - 112,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 10f,
                        0.4f, 0.4f, 0.4f);

                // ── Ligne séparatrice ─────────────────────────────────────
                cs.setLineWidth(1f);
                cs.setStrokingColor(0.11f, 0.30f, 0.69f);
                cs.moveTo(margin, H - 128);
                cs.lineTo(W - margin, H - 128);
                cs.stroke();

                // ── Titre principal ───────────────────────────────────────
                drawCenteredText(cs, "ATTESTATION DE STAGE", W, H - 175,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20f,
                        0.05f, 0.05f, 0.05f);

                // ── Ligne séparatrice sous titre ──────────────────────────
                cs.setLineWidth(0.5f);
                cs.setStrokingColor(0.7f, 0.7f, 0.7f);
                cs.moveTo(margin + 40, H - 190);
                cs.lineTo(W - margin - 40, H - 190);
                cs.stroke();

                // ── Corps du texte ────────────────────────────────────────
                float yStart = H - 230;
                float lineH  = 22f;

                PDType1Font fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontBold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                drawCenteredText(cs,
                        "Le département des Ressources Humaines de Hikma Pharmaceuticals",
                        W, yStart, fontNormal, 11f, 0.1f, 0.1f, 0.1f);
                drawCenteredText(cs, "atteste que :",
                        W, yStart - lineH, fontNormal, 11f, 0.1f, 0.1f, 0.1f);

                drawCenteredText(cs, nomComplet.toUpperCase(),
                        W, yStart - lineH * 3,
                        fontBold, 15f, 0.11f, 0.30f, 0.69f);

                drawCenteredText(cs, "a effectué un stage au sein de notre entreprise",
                        W, yStart - lineH * 4.5f, fontNormal, 11f, 0.1f, 0.1f, 0.1f);

                drawCenteredText(cs, "dans les conditions suivantes :",
                        W, yStart - lineH * 5.5f, fontNormal, 11f, 0.1f, 0.1f, 0.1f);

                // ── Tableau des infos ─────────────────────────────────────
                float tableY = yStart - lineH * 7.5f;
                float labelX = margin + 20;
                float valueX = W / 2f + 10;
                float rowH   = 26f;

                String[][] rows = {
                        { "École / Université", ecole       },
                        { "Filière",            filiere     },
                        { "Niveau",             niveau      },
                        { "Département",        departement },
                        { "Encadrant",          tuteurNom   },
                        { "Date de début",      debut       },
                        { "Date de fin",        fin         },
                        { "Durée",              duree + " mois" },
                };

                for (int i = 0; i < rows.length; i++) {
                    float rowY = tableY - i * rowH;

                    if (i % 2 == 0) {
                        cs.setNonStrokingColor(0.95f, 0.97f, 1.0f);
                        cs.addRect(labelX - 8, rowY - 6, W - (labelX - 8) * 2, rowH - 2);
                        cs.fill();
                    }

                    cs.setNonStrokingColor(0.3f, 0.3f, 0.3f);
                    cs.beginText();
                    cs.setFont(fontBold, 10f);
                    cs.newLineAtOffset(labelX, rowY + 6);
                    cs.showText(rows[i][0] + " :");
                    cs.endText();

                    cs.setNonStrokingColor(0.05f, 0.05f, 0.05f);
                    cs.beginText();
                    cs.setFont(fontNormal, 10f);
                    cs.newLineAtOffset(valueX, rowY + 6);
                    cs.showText(rows[i][1]);
                    cs.endText();
                }

                // ── Phrase de clôture ─────────────────────────────────────
                float closeY = tableY - rows.length * rowH - 30;

                drawCenteredText(cs,
                        "Ce stage a été effectué avec sérieux et a donné entière satisfaction.",
                        W, closeY, fontNormal, 11f, 0.1f, 0.1f, 0.1f);

                drawCenteredText(cs,
                        "Cette attestation est délivrée pour servir et valoir ce que de droit.",
                        W, closeY - lineH, fontNormal, 11f, 0.1f, 0.1f, 0.1f);

                // ── Date d'émission ───────────────────────────────────────
                String dateEmission = "Fait à Casablanca, le " +
                        java.time.LocalDate.now().format(FMT);
                drawCenteredText(cs, dateEmission, W, closeY - lineH * 3,
                        fontNormal, 10f, 0.4f, 0.4f, 0.4f);

                // ── Signature ─────────────────────────────────────────────
                float sigY = 110f;

                cs.beginText();
                cs.setFont(fontBold, 10f);
                cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                cs.newLineAtOffset(W - margin - 160, sigY + 40);
                cs.showText("Le Responsable RH");
                cs.endText();

                cs.setLineWidth(0.5f);
                cs.setStrokingColor(0.5f, 0.5f, 0.5f);
                cs.moveTo(W - margin - 160, sigY + 10);
                cs.lineTo(W - margin, sigY + 10);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontNormal, 9f);
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.newLineAtOffset(W - margin - 130, sigY - 5);
                cs.showText("Signature et cachet");
                cs.endText();

                // ── Pied de page ──────────────────────────────────────────
                cs.setLineWidth(0.5f);
                cs.setStrokingColor(0.11f, 0.30f, 0.69f);
                cs.moveTo(margin, 65);
                cs.lineTo(W - margin, 65);
                cs.stroke();

                drawCenteredText(cs,
                        "Hikma Pharmaceuticals — SIMS | Document généré automatiquement",
                        W, 52, fontNormal, 8f, 0.5f, 0.5f, 0.5f);

                drawCenteredText(cs,
                        "Ce document est valide sans signature manuscrite.",
                        W, 40, fontNormal, 8f, 0.5f, 0.5f, 0.5f);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            log.info("[ATTESTATION] PDF généré pour : {}", nomComplet);
            return out.toByteArray();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ATTESTATION] Erreur génération PDF : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la génération de l'attestation.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper — texte centré
    // ─────────────────────────────────────────────────────────────────────

    private void drawCenteredText(PDPageContentStream cs, String text, float pageWidth,
                                  float y, PDType1Font font, float fontSize,
                                  float r, float g, float b) throws Exception {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (pageWidth - textWidth) / 2;
        cs.setNonStrokingColor(r, g, b);
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
}
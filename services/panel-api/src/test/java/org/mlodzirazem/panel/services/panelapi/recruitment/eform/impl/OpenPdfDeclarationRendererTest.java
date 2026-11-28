/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mlodzirazem.panel.services.panelapi.recruitment.eform.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;

import java.io.IOException;
import java.time.LocalDate;

class OpenPdfDeclarationRendererTest {

    @Test
    void templateCarriesTheInformationDutyAcknowledgementAndTheBumpedVersion() throws IOException {
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setCandidateFormalFirstName("Ina");
        candidateCase.setCandidateFormalLastName("Testowa");
        candidateCase.setCandidateFormalIdentifier("00000000000");

        byte[] template = new OpenPdfDeclarationRenderer(() -> LocalDate.of(2026, 11, 28))
            .render(candidateCase, null);

        String text = text(template);

        assertThat(text).contains("Potwierdzam zapoznanie", "informacyjnym: tak");
        assertThat(text).contains(RecruitmentEformRules.TEMPLATE_VERSION);
        assertThat(RecruitmentEformRules.TEMPLATE_VERSION).isEqualTo("MR-deklaracja-2026.2");
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

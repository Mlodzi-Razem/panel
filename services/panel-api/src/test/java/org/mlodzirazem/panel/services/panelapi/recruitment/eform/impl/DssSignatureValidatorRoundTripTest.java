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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationSignatureValidator;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.PadesSignatures;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.SyntheticPesel;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renderer → PAdES-B fixture signer → offline validator round trip without Spring. The pipeline
 * exercises the DSS discovery modules exactly the way the service uses them at runtime: the
 * self-signed fixture certificate (no trust anchor, no revocation) must pass the integrity verdict
 * with the trusted-chain/qualified determination deferred (reported as INDETERMINATE names in
 * validation_result), and the serialNumber RDN must surface as the candidate-identity match input.
 */
class DssSignatureValidatorRoundTripTest {
    private static final char[] PASSWORD = "rekrut-fix".toCharArray();

    /**
     * A byte inside the first ByteRange segment of the signed PDF (the post-header comment), so the
     * flip must break the covered digest; a byte in the middle of the file may land inside the
     * /Contents gap (even in the CMS certificate block), which no document digest covers.
     */
    private static final int TAMPERED_BYTE = 20;

    @Test
    void selfSignedPadesBPassesStructureIntegrityAndIdentityExtraction() throws Exception {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 77);
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setCandidateFirstName("Ryta");
        candidateCase.setCandidateLastName("Renderowa");
        candidateCase.setCandidateEmail("ryta@example.org");
        candidateCase.setCandidateFormalFirstName("Rytma");
        candidateCase.setCandidateFormalLastName("Renderowa");
        candidateCase.setCandidateFormalIdentifier(pesel);

        byte[] template = new OpenPdfDeclarationRenderer().render(candidateCase, null);
        assertThat(new String(template, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        byte[] p12 = PadesSignatures.selfSignedP12("Rytma Renderowa (test)", pesel, PASSWORD);
        byte[] signed = PadesSignatures.sign(template, p12, PASSWORD);

        DeclarationSignatureValidator.ValidatedSignature validated =
            new DssSignatureValidator().validate(signed);

        assertThat(validated.valid())
            .as("failed=%s", validated.failedChecks())
            .isTrue();
        assertThat(validated.signerName()).contains("Rytma");
        assertThat(validated.signerSerialNumber()).isEqualTo(pesel);
        assertThat(validated.performedChecks()).contains("PADES_STRUCTURE", "SIGNATURE_INTEGRITY",
            "SIGNING_CERTIFICATE", "CERTIFICATE_SERIAL_NUMBER");
        assertThat(validated.failedChecks()).doesNotContain("SIGNATURE_MISSING");
    }

    @Test
    void modificationsOfTheSignedDocumentFailTheIntegrityVerdict() throws Exception {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 78);
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setCandidateFirstName("Rysiu");
        candidateCase.setCandidateLastName("Podpisowy");
        candidateCase.setCandidateFormalIdentifier(pesel);

        byte[] template = new OpenPdfDeclarationRenderer().render(candidateCase, null);
        byte[] p12 = PadesSignatures.selfSignedP12("Rysiu Podpisowy (test)", pesel, PASSWORD);
        byte[] signed = PadesSignatures.sign(template, p12, PASSWORD);

        byte[] tampered = signed.clone();
        tampered[TAMPERED_BYTE] = (byte) (tampered[TAMPERED_BYTE] ^ 0x04);

        DeclarationSignatureValidator.ValidatedSignature validated =
            new DssSignatureValidator().validate(tampered);

        assertThat(validated.valid()).as("failed=%s", validated.failedChecks()).isFalse();
    }
}

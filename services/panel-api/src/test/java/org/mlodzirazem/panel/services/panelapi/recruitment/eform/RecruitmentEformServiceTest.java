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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class RecruitmentEformServiceTest extends DomainTestSupport {
    private static final char[] KEYSTORE_PASSWORD = "rekrut-fix".toCharArray();

    @Autowired
    RecruitmentEformService eform;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @Autowired
    TRecruitmentCaseEventRepository caseEvents;

    @DynamicPropertySource
    static void declarationStorage(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("declarations-test");
        registry.add("panel.api.recruitment.declaration.storage-dir", () -> storage.toString());
    }

    private long districtActor() {
        TMember actor = newMember("Ewa", "Eelektroniczna", "mazowieckie");
        grant(actor, board("mazowieckie"), "COORDINATOR");
        return actor.getId();
    }

    private TRecruitmentCase awaitingCase(String email, LocalDate birthDate) {
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setDistrict(district("mazowieckie"));
        candidateCase.setCandidateFirstName("Kolendra");
        candidateCase.setCandidateLastName("Kowalska");
        candidateCase.setCandidateEmail(email);
        candidateCase.setCandidateBirthDate(birthDate);
        candidateCase.setStatus(RecruitmentCaseStatus.AWAITING_DECLARATION);
        return caseRepository.saveAndFlush(candidateCase);
    }

    private TRecruitmentCase awaitingCase(String email) {
        return awaitingCase(email, LocalDate.of(2007, 4, 1));
    }

    private RecruitmentEformService.DeclarationFormSubmission submission(String pesel,
                                                                         String formalFirst) {
        return new RecruitmentEformService.DeclarationFormSubmission(
            formalFirst, "Formalna", "Falka", "Formalniakowa", "ona", pesel,
            "+48 600 100 200", "kolendra.kontakt@example.org", true, true);
    }

    private String submissionPesel() {
        return SyntheticPesel.of(LocalDate.of(2007, 4, 1), 1);
    }

    private String filledToken(String email) {
        return filledToken(email, submissionPesel());
    }

    private String filledToken(String email, String pesel) {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase(email, LocalDate.of(2007, 4, 1));
        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);
        eform.submitForm(link.token(), submission(pesel, "Felicja"));
        return link.token();
    }

    @Test
    void linkIsOnePerCaseExpiringThePreviousOne() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("wielorazowa@example.org");

        RecruitmentEformService.DeclarationLink first =
            eform.createDeclarationLink(candidateCase.getId(), actor);
        RecruitmentEformService.DeclarationLink second =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        assertThat(first.token()).isNotBlank().isNotEqualTo(second.token());
        assertThat(second.formUrl()).contains(second.token());
        assertThat(second.expiresAt()).isNotNull();

        List<String> tokenStatuses = jdbc.queryForList(
            "SELECT status::text FROM declaration_form_sessions WHERE case_id = ? ORDER BY id",
            String.class, candidateCase.getId());

        assertThat(tokenStatuses).containsExactly("EXPIRED", "CREATED");

        List<String> linkEvents = caseEvents
            .findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(candidateCase.getId())
            .stream().map(event -> event.getEventType().name()).toList();

        assertThat(linkEvents).contains("DECLARATION_LINK_CREATED", "DECLARATION_LINK_CREATED");

        assertThat(auditCount("recruitment :: declaration-link")).isEqualTo(2);
        assertThat(auditPayloads("recruitment :: declaration-link"))
            .contains("token_hash")
            .doesNotContain(second.token());
    }

    @Test
    void linkRequiresManageInTheCaseDistrictAndAwaitingStatus() {
        long actor = districtActor();
        TMember stranger = newMember("Bo", "Bezuprawnien", "slaskie");

        TRecruitmentCase candidateCase = awaitingCase("uprawnieniowa@example.org");
        assertThatThrownBy(() -> eform.createDeclarationLink(candidateCase.getId(), stranger.getId()))
            .isInstanceOf(ForbiddenException.class)
            .extracting("code").isEqualTo("RECRUITMENT_MANAGE_REQUIRED");

        TRecruitmentCase fresh = new TRecruitmentCase();
        fresh.setDistrict(district("mazowieckie"));
        fresh.setCandidateFirstName("Swiezo");
        fresh.setCandidateLastName("Zalozona");
        fresh.setCandidateEmail("swieza@example.org");
        caseRepository.saveAndFlush(fresh);

        assertThatThrownBy(() -> eform.createDeclarationLink(fresh.getId(), actor))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("NOT_AWAITING_DECLARATION");
    }

    @Test
    void landingReportsTheSessionStateWithoutTheTokenSecrets() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("ladowa@example.org");
        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        RecruitmentEformService.DeclarationForm landing = eform.getForm(link.token());

        assertThat(landing.status()).isEqualTo("CREATED");
        assertThat(landing.greeting()).isEqualTo("Kolendra Kowalska");
        assertThat(landing.pdfUrl()).isNull();

        assertThatThrownBy(() -> eform.getForm("nie-ma-takiego-tokena"))
            .isInstanceOf(DeclarationFormAccessException.class)
            .extracting("code").isEqualTo("TOKEN_INVALID");

        jdbc.update("UPDATE declaration_form_sessions SET expires_at = now() - interval '1 hour'"
            + " WHERE case_id = ?", candidateCase.getId());

        assertThatThrownBy(() -> eform.getForm(link.token()))
            .isInstanceOf(DeclarationFormAccessException.class)
            .extracting("code").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void submitFillsTheCaseRendersTheTemplateAndRecordsTheConsents() {
        long actor = districtActor();
        String pesel = submissionPesel();
        TRecruitmentCase candidateCase = awaitingCase("wypelnianiowa@example.org");
        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        RecruitmentEformService.DeclarationForm filled = eform.submitForm(link.token(),
            submission(pesel, "Felicja"));

        assertThat(filled.status()).isEqualTo("FILLED");
        assertThat(filled.pdfUrl()).contains(link.token()).endsWith("/pdf");

        TRecruitmentCase stored = caseRepository.findById(candidateCase.getId()).orElseThrow();
        assertThat(stored.getCandidateFormalFirstName()).isEqualTo("Felicja");
        assertThat(stored.getCandidateFormalLastName()).isEqualTo("Formalna");
        assertThat(stored.getCandidatePreferredFirstName()).isEqualTo("Falka");
        assertThat(stored.getCandidatePronouns()).isEqualTo("ona");
        assertThat(stored.getCandidateFormalIdentifier()).isEqualTo(pesel);
        assertThat(stored.getStatus()).isEqualTo(RecruitmentCaseStatus.AWAITING_DECLARATION);

        List<String> consentKinds = jdbc.queryForList(
            "SELECT consent_kind::text FROM recruitment_consents WHERE case_id = ? ORDER BY id",
            String.class, candidateCase.getId());

        assertThat(consentKinds).containsExactlyInAnyOrder("IMAGE", "SPECIAL_CATEGORY_9_2D");

        List<String> artifactKinds = jdbc.queryForList(
            "SELECT artifact_kind::text FROM declaration_artifacts WHERE case_id = ?",
            String.class, candidateCase.getId());

        assertThat(artifactKinds).containsExactly("TEMPLATE_PDF");

        String templateVersion = jdbc.queryForObject(
            "SELECT template_version FROM declaration_artifacts WHERE case_id = ?",
            String.class, candidateCase.getId());

        assertThat(templateVersion).isEqualTo(RecruitmentEformRules.TEMPLATE_VERSION);

        String sessionFields = jdbc.queryForObject(
            "SELECT submitted_fields::text FROM declaration_form_sessions WHERE case_id = ?"
                + " AND status = 'FILLED'",
            String.class, candidateCase.getId());

        assertThat(sessionFields).contains("candidate_formal_identifier").doesNotContain(pesel);

        byte[] pdf = eform.declarationPdf(link.token());
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        assertThat(auditPayloads("recruitment :: declaration-submit"))
            .contains("candidate_formal_identifier")
            .doesNotContain(pesel);
    }

    @Test
    void submitValidatesTheMandatoryFieldsAndTheAge() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("walidacyjna@example.org");
        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        RecruitmentEformService.DeclarationFormSubmission noConsent =
            new RecruitmentEformService.DeclarationFormSubmission(
                "Felicja", "Formalna", null, null, null, submissionPesel(), null, null, true, false);
        assertThatThrownBy(() -> eform.submitForm(link.token(), noConsent))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        RecruitmentEformService.DeclarationFormSubmission halfAPair =
            new RecruitmentEformService.DeclarationFormSubmission(
                "Felicja", null, null, null, null, submissionPesel(), null, null, true, true);
        assertThatThrownBy(() -> eform.submitForm(link.token(), halfAPair))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("NAMES_ARE_A_PAIR");

        String wrongChecksum = submissionPesel().substring(0, 10)
            + (submissionPesel().charAt(10) == '9' ? '0' : '9');
        assertThatThrownBy(() -> eform.submitForm(link.token(),
            submission(wrongChecksum, "Felicja")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("PERSONAL_ID_INVALID");

        LocalDate tooYoung = LocalDate.of(2012, 6, 1);
        TRecruitmentCase youngCase = awaitingCase("mloda@example.org", tooYoung);
        RecruitmentEformService.DeclarationLink youngLink =
            eform.createDeclarationLink(youngCase.getId(), actor);

        assertThatThrownBy(() -> eform.submitForm(youngLink.token(),
            submission(SyntheticPesel.of(tooYoung, 7), "Felicja")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
    }

    @Test
    void submitBlocksBansMembersAndDuplicateOpenCasesGenerically() {
        long actor = districtActor();

        String banPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 41);
        TRecruitmentCase bannedCase = awaitingCase("zbanowana@example.org");
        jdbc.update("INSERT INTO admission_bans (formal_identifier, decision_signature,"
            + " effective_from, effective_until, note) VALUES (?, 'ZO/MZ/BAN/26',"
            + " CURRENT_DATE - 30, CURRENT_DATE + 300, 'arta 9 ust. 5')", banPesel);

        RecruitmentEformService.DeclarationLink bannedLink =
            eform.createDeclarationLink(bannedCase.getId(), actor);

        org.assertj.core.api.ThrowableAssert.ThrowingCallable bannedSubmit =
            () -> eform.submitForm(bannedLink.token(), submission(banPesel, "Felicja"));

        assertThatThrownBy(bannedSubmit)
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("DECLARATION_BLOCKED");
        assertThatThrownBy(bannedSubmit)
            .isInstanceOf(RuleViolationException.class)
            .extracting("message").isEqualTo("Deklaracja nie może być przyjęta");

        String memberPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 42);
        TMember existingMember = newMember("Marcin", "Juzmember", "mazowieckie");
        jdbc.update("UPDATE members SET formal_identifier = ? WHERE id = ?", memberPesel,
            existingMember.getId());

        TRecruitmentCase memberCase = awaitingCase("poscispa@example.org");
        RecruitmentEformService.DeclarationLink memberLink =
            eform.createDeclarationLink(memberCase.getId(), actor);

        assertThatThrownBy(() -> eform.submitForm(memberLink.token(),
            submission(memberPesel, "Felicja")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("DECLARATION_BLOCKED");

        String dupPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 43);
        TRecruitmentCase firstCase = awaitingCase("pierwsza@example.org");
        RecruitmentEformService.DeclarationLink firstLink =
            eform.createDeclarationLink(firstCase.getId(), actor);
        eform.submitForm(firstLink.token(), submission(dupPesel, "Felicja"));

        TRecruitmentCase secondCase = awaitingCase("druga@example.org");
        RecruitmentEformService.DeclarationLink secondLink =
            eform.createDeclarationLink(secondCase.getId(), actor);

        assertThatThrownBy(() -> eform.submitForm(secondLink.token(), submission(dupPesel, "Feliks")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("DECLARATION_BLOCKED");
    }

    @Test
    void signedDeclarationClosesTheLoopIntoTheStatutoryWorkflow() throws Exception {
        String pesel = submissionPesel();
        long actor = districtActor();
        long caseId = awaitingCase("podpisana@example.org").getId();
        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(caseId, actor);
        eform.submitForm(link.token(), submission(pesel, "Felicja"));

        byte[] template = eform.declarationPdf(link.token());
        byte[] p12 = PadesSignatures.selfSignedP12("Felicja Formalna (test)", pesel,
            KEYSTORE_PASSWORD);
        byte[] signed = PadesSignatures.sign(template, p12, KEYSTORE_PASSWORD);

        RecruitmentEformService.DeclarationForm result = eform.attachSignedDeclaration(link.token(),
            signed);

        assertThat(result.status()).isEqualTo("SIGNED");

        TRecruitmentCase stored = caseRepository.findById(caseId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RecruitmentCaseStatus.DECLARATION_RECEIVED);
        assertThat(stored.getDeclarationForm().name()).isEqualTo("ELECTRONIC");
        assertThat(stored.getDeclarationReceivedOn()).isEqualTo(LocalDate.now());
        assertThat(stored.getAssessmentNotBeforeOn()).isNotNull();
        assertThat(stored.getAssessmentNotLaterOn()).isNotNull();

        List<String> artifactKinds = jdbc.queryForList(
            "SELECT artifact_kind::text FROM declaration_artifacts WHERE case_id = ? ORDER BY id",
            String.class, caseId);

        assertThat(artifactKinds).containsExactly("TEMPLATE_PDF", "SIGNED_PDF");

        String validationResult = jdbc.queryForObject(
            "SELECT validation_result::text FROM declaration_artifacts"
                + " WHERE case_id = ? AND artifact_kind = 'SIGNED_PDF'",
            String.class, caseId);

        assertThat(validationResult).contains("SIGNATURE_INTEGRITY").doesNotContain("-----BEGIN");

        List<String> timeline = caseEvents.findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(caseId)
            .stream().map(event -> event.getEventType().name()).toList();

        assertThat(timeline).contains("DECLARATION_LINK_CREATED", "DECLARATION_RECORDED",
            "STATUS_CHANGED");

        assertThat(auditPayloads("recruitment :: declaration-signature"))
            .contains("signature_type")
            .doesNotContain(pesel);
    }

    @Test
    void tamperedAndMismatchedSignaturesAreRejected() throws Exception {
        String pesel = submissionPesel();
        long actor = districtActor();
        String token = filledToken("oszukana@example.org", pesel);

        byte[] template = eform.declarationPdf(token);
        byte[] p12 = PadesSignatures.selfSignedP12("Ktos Inny (test)", "03243010117",
            KEYSTORE_PASSWORD);
        byte[] signed = PadesSignatures.sign(template, p12, KEYSTORE_PASSWORD);

        // a byte from the first ByteRange segment: the covered digest must break (a mid-file
        // byte can land inside the /Contents gap, where no document digest covers it)
        byte[] tampered = signed.clone();
        tampered[20] = (byte) (tampered[20] ^ 0x04);

        assertThatThrownBy(() -> eform.attachSignedDeclaration(token, tampered))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("SIGNATURE_INVALID");

        assertThatThrownBy(() -> eform.attachSignedDeclaration(token, signed))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("SIGNATURE_INVALID");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM declaration_artifacts"
            + " WHERE artifact_kind = 'SIGNED_PDF'", Integer.class)).isZero();
    }

    @Test
    void rejectsUnsignedUploadsAndUploadsOnFilledOnly() throws Exception {
        String token = filledToken("bezpodpisowa@example.org");

        byte[] unsigned = eform.declarationPdf(token);
        assertThatThrownBy(() -> eform.attachSignedDeclaration(token, unsigned))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("SIGNATURE_INVALID");

        assertThatThrownBy(() -> eform.attachSignedDeclaration(token,
            "<html>to nie pdf</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FILE_INVALID");
    }

    @Test
    void submitCanBeRepeatedUntilSignedAndThenTheSessionIsTerminal() throws Exception {
        String pesel = submissionPesel();
        long actor = districtActor();
        long caseId = awaitingCase("poprawiana@example.org").getId();
        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(caseId, actor);

        eform.submitForm(link.token(), submission(pesel, "Felicja"));
        eform.submitForm(link.token(), new RecruitmentEformService.DeclarationFormSubmission(
            "Felicja", "Formalna", "Falka", "Formalniakowa", "ona/on", pesel, null, null, true, true));

        Integer consentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_consents WHERE case_id = ?", Integer.class, caseId);

        assertThat(consentCount).isEqualTo(2);

        byte[] template = eform.declarationPdf(link.token());
        byte[] p12 = PadesSignatures.selfSignedP12("Felicja Formalna (test)", pesel,
            KEYSTORE_PASSWORD);
        eform.attachSignedDeclaration(link.token(), PadesSignatures.sign(template, p12,
            KEYSTORE_PASSWORD));

        assertThatThrownBy(() -> eform.submitForm(link.token(), submission(pesel, "Potemna")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("DECLARATION_ALREADY_SIGNED");

        assertThatThrownBy(() -> eform.attachSignedDeclaration(link.token(), template))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("FORM_NOT_FILLED");
    }

    @Test
    void uploadSizeIsCapped() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("wielka@example.org");
        byte[] oversized = new byte[11 * 1024 * 1024 + 8];
        oversized[0] = '%';
        oversized[1] = 'P';
        oversized[2] = 'D';
        oversized[3] = 'F';

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, oversized))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void paperScanFallbackRecordsTheDeclarationWithoutTheElectronicSession() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("papierowa@example.org");

        byte[] scan = "%PDF-1.4 papierowy skan".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RecruitmentEformService.DeclarationForm result = eform.attachScan(candidateCase.getId(),
            actor, scan);

        assertThat(result.status()).isEqualTo("SIGNED");
        assertThat(result.greeting()).isEqualTo("Kolendra Kowalska");

        TRecruitmentCase stored = caseRepository.findById(candidateCase.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RecruitmentCaseStatus.DECLARATION_RECEIVED);
        assertThat(stored.getDeclarationForm().name()).isEqualTo("SCAN");

        String signerName = jdbc.queryForObject(
            "SELECT signer_name FROM declaration_artifacts WHERE case_id = ? AND artifact_kind = 'SCAN'",
            String.class, candidateCase.getId());

        assertThat(signerName).isEqualTo("Kolendra Kowalska");

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, scan))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("NOT_AWAITING_DECLARATION");

        assertThat(auditCount("recruitment :: declaration-scan")).isGreaterThanOrEqualTo(1);
    }
}

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailDispatch;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.mlodzirazem.panel.services.panelapi.mail.api.TransactionalMail;
import org.mockito.ArgumentCaptor;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentAdmissionService;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@PostgresTest
class RecruitmentEformServiceTest extends DomainTestSupport {
    private static final char[] KEYSTORE_PASSWORD = "rekrut-fix".toCharArray();

    @Autowired
    RecruitmentEformService eform;

    @Autowired
    RecruitmentWorkflowService workflow;

    @Autowired
    RecruitmentAdmissionService admission;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @Autowired
    TRecruitmentCaseEventRepository caseEvents;

    @MockitoBean
    TransactionalMail transactionalMail;

    @BeforeEach
    void mailAccepts() {
        reset(transactionalMail);
        when(transactionalMail.sendAfterCommit(any())).thenReturn(MailDispatch.SCHEDULED);
    }

    @DynamicPropertySource
    static void declarationStorage(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("declarations-test");
        registry.add("panel.api.recruitment.declaration.storage-dir", () -> storage.toString());
        registry.add("panel.api.recruitment.declaration.web-url", () -> "https://panel.example.org/");
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
            "+48 600 100 200", "kolendra.kontakt@example.org", true, true, true);
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
        assertThat(second.formUrl())
            .isEqualTo("https://panel.example.org/rekrutacja/deklaracja/" + second.token());
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
    void linkIsMailedToTheCandidateWithoutNameOrAddressInTheAudit() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("kandydatka@example.org");

        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        assertThat(link.emailDispatch()).isEqualTo("SCHEDULED");

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(transactionalMail).sendAfterCommit(sent.capture());
        MailMessage message = sent.getValue();

        assertThat(message.recipient()).isEqualTo("kandydatka@example.org");
        assertThat(message.purpose()).isEqualTo("recruitment-declaration-link");
        assertThat(message.reference()).isEqualTo("recruitment-case:" + candidateCase.getId());
        assertThat(message.subject()).isEqualTo("Młodzi Razem — Twoja deklaracja członkowska");
        assertThat(message.textBody())
            .contains(link.formUrl())
            .contains("nie przekazuj go")
            .contains("zignoruj tę wiadomość")
            .contains("unieważnia")
            .doesNotContain("Kolendra")
            .doesNotContain("Kowalska");
        assertThat(message.textBody()).containsPattern("ważny do \\d{1,2} \\p{L}+ \\d{4}, \\d{2}:\\d{2}");

        String payloads = auditPayloads("recruitment :: declaration-link");
        assertThat(payloads).contains("SCHEDULED")
            .doesNotContain("kandydatka@example.org")
            .doesNotContain(link.token());
    }

    @Test
    void linkMailRepliesToTheDistrictWhenItHasAnAddress() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("kandydatka@example.org");
        var district = candidateCase.getDistrict();
        String original = district.getEmail();
        district.setEmail("mazowieckie@example.org");
        bodyRepository.saveAndFlush(district);

        try {
            eform.createDeclarationLink(candidateCase.getId(), actor);
        } finally {
            district.setEmail(original);
            bodyRepository.saveAndFlush(district);
        }

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(transactionalMail).sendAfterCommit(sent.capture());
        assertThat(sent.getValue().replyTo()).isEqualTo("mazowieckie@example.org");
        assertThat(sent.getValue().textBody()).contains("Odpowiedz na tę wiadomość")
            .doesNotContain("nie odpowiadaj na nią");
    }

    @Test
    void linkMailAsksNotToReplyWhenTheDistrictHasNoAddress() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("kandydatka@example.org");
        var district = candidateCase.getDistrict();
        String original = district.getEmail();
        district.setEmail(null);
        bodyRepository.saveAndFlush(district);

        try {
            eform.createDeclarationLink(candidateCase.getId(), actor);
        } finally {
            district.setEmail(original);
            bodyRepository.saveAndFlush(district);
        }

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(transactionalMail).sendAfterCommit(sent.capture());
        assertThat(sent.getValue().replyTo()).isNull();
        assertThat(sent.getValue().textBody()).contains("nie odpowiadaj na nią")
            .doesNotContain("Odpowiedz na tę wiadomość");
    }

    @Test
    void linkWithoutCandidateAddressSendsNothing() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase(null);

        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        assertThat(link.emailDispatch()).isEqualTo("NO_EMAIL");
        assertThat(link.token()).isNotBlank();
        verify(transactionalMail, never()).sendAfterCommit(any());
    }

    @Test
    void linkReportsTheMailGuardOutcome() {
        when(transactionalMail.sendAfterCommit(any())).thenReturn(MailDispatch.REFUSED_LIMIT);

        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(
            awaitingCase("limit@example.org").getId(), districtActor());

        assertThat(link.emailDispatch()).isEqualTo("REFUSED_LIMIT");
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
        // the API path, independent of the web address the candidate link is built on
        assertThat(filled.pdfUrl()).isEqualTo("/public/declaration-form/" + link.token() + "/pdf");

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

        assertThat(consentKinds).containsExactlyInAnyOrder("IMAGE", "SPECIAL_CATEGORY_9_2D",
            "INFO_DUTY_ACKNOWLEDGED");

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
                "Felicja", "Formalna", null, null, null, submissionPesel(), null, null, true, false, true);
        assertThatThrownBy(() -> eform.submitForm(link.token(), noConsent))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        RecruitmentEformService.DeclarationFormSubmission halfAPair =
            new RecruitmentEformService.DeclarationFormSubmission(
                "Felicja", null, null, null, null, submissionPesel(), null, null, true, true, true);
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
    void submitRefusesTheFormWithoutTheInformationDutyAcknowledgement() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("bez-obowiazku@example.org");
        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        RecruitmentEformService.DeclarationFormSubmission withoutDuty =
            new RecruitmentEformService.DeclarationFormSubmission(
                "Felicja", "Formalna", null, null, null, submissionPesel(), null, null, true, true, false);

        assertThatThrownBy(() -> eform.submitForm(link.token(), withoutDuty))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        Integer consentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_consents WHERE case_id = ?", Integer.class,
            candidateCase.getId());
        assertThat(consentCount).isZero();
    }

    @Test
    void paperScanRecordsTheInformationDutyOnlyWhenItIsConfirmed() {
        long actor = districtActor();
        TRecruitmentCase confirmed = awaitingCase("skan-potwierdzony@example.org");
        eform.attachScan(confirmed.getId(), actor, SCAN, submission(submissionPesel(), "Felicja"));

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_consents WHERE case_id = ?"
                + " AND consent_kind::text = 'INFO_DUTY_ACKNOWLEDGED' AND granted_by = 'paper'",
            Integer.class, confirmed.getId())).isEqualTo(1);

        TRecruitmentCase unconfirmed = awaitingCase("skan-bez-potwierdzenia@example.org");
        RecruitmentEformService.DeclarationFormSubmission noDuty =
            new RecruitmentEformService.DeclarationFormSubmission("Felicja", "Formalna", null, null, null,
                SyntheticPesel.of(LocalDate.of(2007, 4, 1), 5), null, null, true, true, false);
        eform.attachScan(unconfirmed.getId(), actor, SCAN, noDuty);

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_consents WHERE case_id = ?"
                + " AND consent_kind::text = 'INFO_DUTY_ACKNOWLEDGED'",
            Integer.class, unconfirmed.getId())).isZero();
    }

    @Test
    void submitRejectsTheCandidateStillTwentySixAfterTheBirthdayYearEnded() {
        // Statute art. 7 ust. 1 pkt 2: candidacy ends with the calendar year of the 26th birthday.
        // A candidate born on 31 December is still 26 in completed years the whole next year — the
        // completed-age band alone used to let them through (G1 §7 item 15); the calendar-year bound
        // does not. The service clock is UTC, so the fixture is computed in UTC to match it.
        LocalDate turnedTwentySixLastYear =
            LocalDate.of(LocalDate.now(java.time.ZoneOffset.UTC).getYear() - 27, 12, 31);

        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("po-roku@example.org", turnedTwentySixLastYear);
        RecruitmentEformService.DeclarationLink link =
            eform.createDeclarationLink(candidateCase.getId(), actor);

        assertThatThrownBy(() -> eform.submitForm(link.token(),
            submission(SyntheticPesel.of(turnedTwentySixLastYear, 7), "Felicja")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
    }

    @Test
    void submitBlocksBansMembersAndDuplicateOpenCasesGenerically() {
        long actor = districtActor();

        String banPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 41);
        TRecruitmentCase bannedCase = awaitingCase("zbanowana@example.org");
        jdbc.update("INSERT INTO admission_bans (formal_identifier, decision_signature,"
            + " effective_from, effective_until, reason, note) VALUES (?, 'ZO/MZ/BAN/26',"
            + " CURRENT_DATE - 30, CURRENT_DATE + 300, 'arta 9 ust. 5 — uchwała ZK', 'arta 9 ust. 5')", banPesel);

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

        // BAN-1: the full-width spelling of the banned PESEL hits the same ban
        String fullWidthBanPesel = banPesel.chars()
            .mapToObj(c -> String.valueOf((char) ('０' + (c - '0'))))
            .collect(java.util.stream.Collectors.joining());
        assertThatThrownBy(() -> eform.submitForm(bannedLink.token(),
            submission(fullWidthBanPesel, "Felicja")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("DECLARATION_BLOCKED");

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
    void refusedDeclarationLeavesOneBlockEventWithCodesOnlyAndKeepsTheCaseUntouched() {
        long actor = districtActor();
        String memberPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 51);
        TMember existingMember = newMember("Marcin", "Juzblock", "mazowieckie");
        jdbc.update("UPDATE members SET formal_identifier = ? WHERE id = ?", memberPesel,
            existingMember.getId());

        TRecruitmentCase candidateCase = awaitingCase("blokada-czlonek@example.org");
        long caseId = candidateCase.getId();
        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(caseId, actor);

        assertThatThrownBy(() -> eform.submitForm(link.token(), submission(memberPesel, "Felicja")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("DECLARATION_BLOCKED");

        List<java.util.Map<String, Object>> events = blockEvents(caseId);
        assertThat(events).hasSize(1);
        String payload = events.get(0).get("payload").toString();
        assertThat(payload).contains("EXISTING_MEMBER", "EFORM")
            .doesNotContain(memberPesel)
            .doesNotContainPattern("[0-9]");
        assertThat(events.get(0).get("created_by_user_id")).isNull();
        assertThat(auditCount("recruitment :: declaration-blocked")).isEqualTo(1);

        // the refusal rolled the submission back: no formal data, no session change
        TRecruitmentCase stored = caseRepository.findById(caseId).orElseThrow();
        assertThat(stored.getCandidateFormalFirstName()).isNull();
        assertThat(stored.getCandidateFormalIdentifier()).isNull();

        // a retry within 24 hours does not flood the timeline
        assertThatThrownBy(() -> eform.submitForm(link.token(), submission(memberPesel, "Felicja")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("DECLARATION_BLOCKED");
        assertThat(blockEvents(caseId)).hasSize(1);
        assertThat(auditCount("recruitment :: declaration-blocked")).isEqualTo(1);
    }

    @Test
    void banAndDuplicateRefusalsEachLeaveTheirOwnBlockEvent() {
        long actor = districtActor();

        String banPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);
        TRecruitmentCase bannedCase = awaitingCase("blokada-zakaz@example.org");
        jdbc.update("INSERT INTO admission_bans (formal_identifier, decision_signature,"
            + " effective_from, effective_until, reason, note) VALUES (?, 'ZO/MZ/BAN/26',"
            + " CURRENT_DATE - 30, CURRENT_DATE + 300, 'arta 9 ust. 5 — uchwała ZK', 'arta 9 ust. 5')", banPesel);
        RecruitmentEformService.DeclarationLink bannedLink = eform.createDeclarationLink(bannedCase.getId(), actor);

        assertThatThrownBy(() -> eform.submitForm(bannedLink.token(), submission(banPesel, "Felicja")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("DECLARATION_BLOCKED");

        List<java.util.Map<String, Object>> banEvents = blockEvents(bannedCase.getId());
        assertThat(banEvents).hasSize(1);
        assertThat(banEvents.get(0).get("payload").toString()).contains("ADMISSION_BAN", "EFORM")
            .doesNotContain(banPesel);

        String dupPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 62);
        TRecruitmentCase firstCase = awaitingCase("blokada-pierwsza@example.org");
        eform.submitForm(eform.createDeclarationLink(firstCase.getId(), actor).token(),
            submission(dupPesel, "Felicja"));

        TRecruitmentCase secondCase = awaitingCase("blokada-druga@example.org");
        RecruitmentEformService.DeclarationLink secondLink = eform.createDeclarationLink(secondCase.getId(), actor);
        assertThatThrownBy(() -> eform.submitForm(secondLink.token(), submission(dupPesel, "Feliks")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("DECLARATION_BLOCKED");

        List<java.util.Map<String, Object>> dupEvents = blockEvents(secondCase.getId());
        assertThat(dupEvents).hasSize(1);
        assertThat(dupEvents.get(0).get("payload").toString()).contains("DUPLICATE_OPEN_CASE", "EFORM")
            .doesNotContain(dupPesel);
        assertThat(blockEvents(firstCase.getId())).isEmpty();
    }

    private List<java.util.Map<String, Object>> blockEvents(long caseId) {
        return jdbc.queryForList("SELECT payload::text AS payload, created_by_user_id"
            + " FROM recruitment_case_events WHERE case_id = ? AND event_type = 'DECLARATION_BLOCKED' ORDER BY id",
            caseId);
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
            "Felicja", "Formalna", "Falka", "Formalniakowa", "ona/on", pesel, null, null, true, true, true));

        Integer consentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_consents WHERE case_id = ?", Integer.class, caseId);

        assertThat(consentCount).isEqualTo(3);

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

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, oversized, null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void paperScanFallbackRecordsTheDeclarationWithoutTheElectronicSession() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("papierowa@example.org");

        byte[] scan = SCAN;
        RecruitmentEformService.DeclarationForm result = eform.attachScan(candidateCase.getId(),
            actor, scan, submission(submissionPesel(), "Felicja"));

        assertThat(result.status()).isEqualTo("SIGNED");
        assertThat(result.greeting()).isEqualTo("Falka Formalniakowa");

        TRecruitmentCase stored = caseRepository.findById(candidateCase.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RecruitmentCaseStatus.DECLARATION_RECEIVED);
        assertThat(stored.getDeclarationForm().name()).isEqualTo("SCAN");
        assertThat(stored.getCandidateFormalFirstName()).isEqualTo("Felicja");
        assertThat(stored.getCandidateFormalLastName()).isEqualTo("Formalna");
        assertThat(stored.getCandidateFormalIdentifier()).isEqualTo(submissionPesel());

        String signerName = jdbc.queryForObject(
            "SELECT signer_name FROM declaration_artifacts WHERE case_id = ? AND artifact_kind = 'SCAN'",
            String.class, candidateCase.getId());

        assertThat(signerName).isEqualTo("Felicja Formalna");

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, scan, null))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("NOT_AWAITING_DECLARATION");

        assertThat(auditCount("recruitment :: declaration-scan")).isGreaterThanOrEqualTo(1);
    }

    private static final byte[] SCAN = "%PDF-1.4 papierowy skan".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void scanWithoutDataOnACaseLackingFormalDataIsRefused() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("bez-danych@example.org");

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN, null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        // no special-category consent ticked on the paper declaration: the same refusal as in the e-form
        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN,
            new RecruitmentEformService.DeclarationFormSubmission("Felicja", "Formalna", null, null, null,
                submissionPesel(), null, null, true, false, false)))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        assertThat(caseRepository.findById(candidateCase.getId()).orElseThrow().getStatus())
            .isEqualTo(RecruitmentCaseStatus.AWAITING_DECLARATION);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM declaration_artifacts WHERE case_id = ?",
            Integer.class, candidateCase.getId())).isZero();
    }

    @Test
    void scanWithDataFillsTheCaseRecordsPaperConsentsAndExpiresLiveSessions() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("skan-dane@example.org");
        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(candidateCase.getId(), actor);

        eform.attachScan(candidateCase.getId(), actor, SCAN, new RecruitmentEformService.DeclarationFormSubmission(
            "Felicja", "Formalna", "Fela", "Falkowa", "ona", submissionPesel(), "+48 600 100 200",
            "skan.kontakt@example.org", false, true, false));

        TRecruitmentCase stored = caseRepository.findById(candidateCase.getId()).orElseThrow();
        assertThat(stored.getCandidatePreferredFirstName()).isEqualTo("Fela");
        assertThat(stored.getCandidatePreferredLastName()).isEqualTo("Falkowa");
        assertThat(stored.getCandidatePronouns()).isEqualTo("ona");
        assertThat(stored.getCandidatePhone()).isEqualTo("+48 600 100 200");
        assertThat(stored.getCandidateEmail()).isEqualTo("skan.kontakt@example.org");

        // only the special-category consent was ticked; both rest on the SCAN artifact and say "paper"
        List<java.util.Map<String, Object>> consentRows = jdbc.queryForList(
            "SELECT c.consent_kind::text AS kind, c.granted_by, a.artifact_kind::text AS artifact"
                + " FROM recruitment_consents c JOIN declaration_artifacts a ON a.id = c.artifact_id"
                + " WHERE c.case_id = ?", candidateCase.getId());

        assertThat(consentRows).hasSize(1);
        assertThat(consentRows.get(0)).containsEntry("kind", "SPECIAL_CATEGORY_9_2D")
            .containsEntry("granted_by", "paper").containsEntry("artifact", "SCAN");

        assertThat(jdbc.queryForList("SELECT status::text FROM declaration_form_sessions WHERE case_id = ?",
            String.class, candidateCase.getId())).containsExactly("EXPIRED");

        // the e-form link is dead: no electronic signature can arrive after the paper declaration
        assertThatThrownBy(() -> eform.getForm(link.token()))
            .isInstanceOf(DeclarationFormAccessException.class);

        String audit = auditPayloads("recruitment :: declaration-scan");
        assertThat(audit).contains("candidate_formal_identifier", "recruitment_consents")
            .doesNotContain(submissionPesel(), "Felicja", "Formalna", "skan.kontakt@example.org");
    }

    @Test
    void scanDataIsValidatedExactlyLikeTheEForm() {
        long actor = districtActor();
        // no known birth date: the age check, not the PESEL/birth-date match, decides
        TRecruitmentCase candidateCase = awaitingCase("skan-bledy@example.org", null);

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN,
            submission("00000000000", "Felicja")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("PERSONAL_ID_INVALID");

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN,
            submission(SyntheticPesel.of(LocalDate.of(1985, 3, 3), 5), "Felicja")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("ADMISSION_AGE");

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN,
            new RecruitmentEformService.DeclarationFormSubmission("Felicja", null, null, null, null,
                submissionPesel(), null, null, true, true, false)))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NAMES_ARE_A_PAIR");

        TMember existing = newMember("Istniejaca", "Czlonkini", "slaskie");
        existing.setFormalIdentifier(submissionPesel());
        memberRepository.saveAndFlush(existing);

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN,
            submission(submissionPesel(), "Felicja")))
            .isInstanceOf(RuleViolationException.class)
            .hasMessageNotContaining("Istniejaca")
            .extracting("code").isEqualTo("DECLARATION_BLOCKED");

        assertThat(caseRepository.findById(candidateCase.getId()).orElseThrow().getStatus())
            .isEqualTo(RecruitmentCaseStatus.AWAITING_DECLARATION);

        // the refused paper scan leaves a block event too, attributed to the board member who uploaded it
        List<java.util.Map<String, Object>> events = blockEvents(candidateCase.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("payload").toString()).contains("EXISTING_MEMBER", "SCAN")
            .doesNotContainPattern("[0-9]");
        assertThat(((Number) events.get(0).get("created_by_user_id")).longValue()).isEqualTo(actor);
    }

    @Test
    void scanAfterTheEFormRefusesNewDataButStillRecordsTheDeclaration() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = awaitingCase("skan-po-eformie@example.org");
        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(candidateCase.getId(), actor);
        eform.submitForm(link.token(), submission(submissionPesel(), "Felicja"));

        assertThatThrownBy(() -> eform.attachScan(candidateCase.getId(), actor, SCAN,
            submission(submissionPesel(), "Zmiana")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("FORMAL_DATA_PRESENT");

        // without data the paper signature simply lands; consents given in the e-form stay
        eform.attachScan(candidateCase.getId(), actor, SCAN, null);

        TRecruitmentCase stored = caseRepository.findById(candidateCase.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RecruitmentCaseStatus.DECLARATION_RECEIVED);
        assertThat(stored.getCandidateFormalFirstName()).isEqualTo("Felicja");
        assertThat(jdbc.queryForList("SELECT granted_by FROM recruitment_consents WHERE case_id = ?",
            String.class, candidateCase.getId())).containsOnly("self");
        assertThat(jdbc.queryForObject(
            "SELECT signer_name FROM declaration_artifacts WHERE case_id = ? AND artifact_kind = 'SCAN'",
            String.class, candidateCase.getId())).isEqualTo("Felicja Formalna");
        assertThat(jdbc.queryForList("SELECT status::text FROM declaration_form_sessions WHERE case_id = ?",
            String.class, candidateCase.getId())).containsExactly("EXPIRED");
    }

    private TRecruitmentCase caseWithFormalData(long actor, String email) {
        TRecruitmentCase candidateCase = awaitingCase(email);
        eform.attachScan(candidateCase.getId(), actor, SCAN, submission(submissionPesel(), "Felicja"));
        return caseRepository.findById(candidateCase.getId()).orElseThrow();
    }

    private static RecruitmentEformService.CandidateDataCorrection correction(
        String formalFirst, String formalLast, String preferredFirst, String preferredLast, String pronouns,
        String personalId, String reason) {
        return new RecruitmentEformService.CandidateDataCorrection(formalFirst, formalLast, preferredFirst,
            preferredLast, pronouns, personalId, reason);
    }

    @Test
    void correctionChangesOnlyGivenFieldsAndWritesReasonedHistoryWithoutValues() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = caseWithFormalData(actor, "korekta@example.org");
        long caseId = candidateCase.getId();
        String newPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 9);

        eform.correctCandidateData(caseId, actor, correction("Felicjana", "Formalna", "", "", "oni", newPesel,
            "  Literówka w papierowej deklaracji  "));

        TRecruitmentCase stored = caseRepository.findById(caseId).orElseThrow();
        assertThat(stored.getCandidateFormalFirstName()).isEqualTo("Felicjana");
        assertThat(stored.getCandidateFormalLastName()).isEqualTo("Formalna");
        assertThat(stored.getCandidatePreferredFirstName()).isNull();
        assertThat(stored.getCandidatePreferredLastName()).isNull();
        assertThat(stored.getCandidatePronouns()).isEqualTo("oni");
        assertThat(stored.getCandidateFormalIdentifier()).isEqualTo(newPesel);

        String payload = jdbc.queryForObject(
            "SELECT payload::text FROM recruitment_case_events WHERE case_id = ?"
                + " AND event_type = 'CANDIDATE_DATA_CORRECTED'", String.class, caseId);

        assertThat(payload).contains("Literówka w papierowej deklaracji")
            .contains("candidate_formal_first_name", "candidate_formal_identifier", "candidate_pronouns",
                "candidate_preferred_first_name")
            .doesNotContain("candidate_formal_last_name", newPesel, "Felicjana");

        String audit = auditPayloads("recruitment :: candidate-data-correction");
        assertThat(audit).contains("candidate_formal_first_name")
            .doesNotContain("Literówka", newPesel, "Felicjana");
    }

    @Test
    void correctionNeedsAReasonAndAChange() {
        long actor = districtActor();
        long caseId = caseWithFormalData(actor, "korekta-powod@example.org").getId();

        for (String reason : new String[]{null, "", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
                correction("Inna", "Formalna", null, null, null, null, reason)))
                .isInstanceOf(RuleViolationException.class)
                .extracting("code").isEqualTo("CORRECTION_REQUIRES_REASON");
        }

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
            correction("Felicja", "Formalna", null, null, null, submissionPesel(), "Bez zmian")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NO_CHANGES");

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
            correction("Inna", null, null, null, null, null, "Połowa pary")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NAMES_ARE_A_PAIR");

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
            correction("", "", null, null, null, null, "Czyszczenie")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("FIELD_REQUIRED");

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
            correction(null, null, null, null, null, "00000000000", "Zły numer")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("PERSONAL_ID_INVALID");

        assertThat(auditCount("recruitment :: candidate-data-correction")).isZero();
    }

    @Test
    void correctionIsRefusedForAClosedCaseAMissingFormalDataAndAForeignDistrict() {
        long actor = districtActor();
        TRecruitmentCase noData = awaitingCase("korekta-brak@example.org");

        assertThatThrownBy(() -> eform.correctCandidateData(noData.getId(), actor,
            correction("Inna", "Formalna", null, null, null, null, "Powód")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("FORMAL_DATA_MISSING");

        long caseId = caseWithFormalData(actor, "korekta-uprawnienia@example.org").getId();
        TMember outsider = newMember("Obcy", "Okregowy", "slaskie");
        grant(outsider, board("slaskie"), "COORDINATOR");

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, outsider.getId(),
            correction("Inna", "Formalna", null, null, null, null, "Powód")))
            .isInstanceOf(ForbiddenException.class);

        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'NOT_FIT',"
            + " closed_at = CURRENT_DATE WHERE id = ?", caseId);

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
            correction("Inna", "Formalna", null, null, null, null, "Powód")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CASE_NOT_OPEN");
    }

    @Test
    void correctionRefusedForAnExistingMemberLeavesACorrectionEventWithTheActorAndNoChange() {
        long actor = districtActor();
        TRecruitmentCase candidateCase = caseWithFormalData(actor, "korekta-blokada@example.org");
        long caseId = candidateCase.getId();
        String memberPesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 52);
        TMember existingMember = newMember("Marcin", "Juzkorekta", "mazowieckie");
        jdbc.update("UPDATE members SET formal_identifier = ? WHERE id = ?", memberPesel, existingMember.getId());

        assertThatThrownBy(() -> eform.correctCandidateData(caseId, actor,
            correction("Inna", "Formalna", null, null, null, memberPesel, "Literówka w numerze")))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("DECLARATION_BLOCKED");

        TRecruitmentCase stored = caseRepository.findById(caseId).orElseThrow();
        assertThat(stored.getCandidateFormalFirstName()).isEqualTo("Felicja");
        assertThat(stored.getCandidateFormalIdentifier()).isEqualTo(submissionPesel());

        List<java.util.Map<String, Object>> events = blockEvents(caseId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("payload").toString()).contains("EXISTING_MEMBER", "CORRECTION")
            .doesNotContain(memberPesel, "Literówka");
        assertThat(events.get(0).get("created_by_user_id")).isNotNull();
        assertThat(auditPayloads("recruitment :: declaration-blocked")).contains("EXISTING_MEMBER")
            .doesNotContain(memberPesel);
    }

    @Test
    void aScanOnlyCaseWalksToAcceptedAndGetsAMember() {
        long actor = districtActor();
        TMember zkMember = newMember("Bogna", "Krajowa", "mazowieckie");
        grant(zkMember, body("national-board"), "CO_CHAIR");
        long zk = zkMember.getId();

        long caseId = caseWithFormalData(actor, "skan-e2e@example.org").getId();

        // the scan was recorded today: move the clocks of the case back so the decisions are in the past
        jdbc.update("UPDATE recruitment_cases SET declaration_received_on = CURRENT_DATE - 80,"
            + " assessment_not_before_on = CURRENT_DATE - 18, assessment_not_later_on = CURRENT_DATE - 4"
            + " WHERE id = ?", caseId);

        TRecruitmentCase probation = workflow.startProbation(caseId, actor);
        workflow.recordZoRecommendation(probation.getId(), actor, board("mazowieckie").getId(),
            LocalDate.now().minusDays(15), "ZO/MZ/REC/27", null);
        workflow.startZkVoting(caseId, zk);
        workflow.recordZkApproval(caseId, zk, body("national-board").getId(), LocalDate.now().minusDays(2),
            "ZK/91/2027", null);
        workflow.recordAdmissionResolution(caseId, zk, board("mazowieckie").getId(),
            LocalDate.now().minusDays(1), "ZO/MZ/92/2027", null);

        TMember member = admission.admit(caseId, zk).member();

        assertThat(member.getFormalNameFirst()).isEqualTo("Felicja");
        assertThat(member.getFormalNameLast()).isEqualTo("Formalna");
        assertThat(member.getFormalIdentifier()).isEqualTo(submissionPesel());
        assertThat(caseRepository.findById(caseId).orElseThrow().getStatus())
            .isEqualTo(RecruitmentCaseStatus.ACCEPTED);
    }
}

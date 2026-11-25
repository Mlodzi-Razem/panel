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

package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBan;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.SyntheticPesel;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class AdmissionBanServiceTest extends DomainTestSupport {
    private static final String BAN_CREATE_OPERATION = "recruitment :: ban-create";

    @Autowired
    AdmissionBanService admissionBans;

    private final LocalDate today = LocalDate.now();

    @Test
    void checkAcceptsAUchwałaWithinTheStatutoryBounds() {
        assertThat(admissionBans.check(new NewAdmissionBan(
            SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61),
            today, today.plusDays(365), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).matches(AdmissionBanCheck::allowed);

        // a backdated resolution: the uchwała started earlier, its period still fits the bounds
        assertThat(admissionBans.check(new NewAdmissionBan(
            SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61),
            today.minusDays(30), today.plusDays(300), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).matches(AdmissionBanCheck::allowed);
    }

    @Test
    void createRecordsTheUchwałaAndAuditsTheFieldNamesOnly() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);

        TAdmissionBan ban = admissionBans.create(new NewAdmissionBan(
            " " + pesel + " ", null, today.plusDays(300), "  ZK/BAN/41/26  ",
            "  negatywna ocena ZK w okresie wstępnym  ", null), null);

        assertThat(ban.getFormalIdentifier()).isEqualTo(pesel);
        assertThat(ban.getDecisionSignature()).isEqualTo("ZK/BAN/41/26");
        assertThat(ban.getReason()).isEqualTo("negatywna ocena ZK w okresie wstępnym");
        assertThat(ban.getEffectiveFrom()).isEqualTo(today); // the uchwała takes effect on the recording day
        assertThat(ban.getEffectiveUntil()).isEqualTo(today.plusDays(300));
        assertThat(ban.getId()).isNotNull();

        // §0.5: the audit row carries ids and field names, never the values — the PESEL and the
        // reason of the ban are personal data that stays inside the database row
        String payloads = auditPayloads(BAN_CREATE_OPERATION);

        assertThat(auditCount(BAN_CREATE_OPERATION)).isEqualTo(1);
        assertThat(payloads).contains("formalIdentifier", "reason", "decisionSignature",
            "effectiveFrom", "effectiveUntil", "banId", "CREATED");
        assertThat(payloads).doesNotContain(pesel, "negatywna ocena", "ZK/BAN/41/26");
    }

    @Test
    void createMarksTheRecordingActor() {
        TMember actor = newMember("Robot", "Kontrolny", "mazowieckie");

        TAdmissionBan ban = admissionBans.create(new NewAdmissionBan(
            SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61),
            today, today.plusDays(30), "ZK/BAN/42/26", "uchwała ZK o zakazie przyjmowania", null),
            actor.getId());

        assertThat(ban.getCreatedBy().getId()).isEqualTo(actor.getId());
    }

    @Test
    void everyDefectIsAnExplicitRule() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);
        String wrongChecksum = pesel.substring(0, 10) + (pesel.charAt(10) == '9' ? '0' : '9');

        NewAdmissionBan valid = new NewAdmissionBan(
            pesel, today, today.plusDays(30), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null);

        assertThat(admissionBans.check(new NewAdmissionBan(
            wrongChecksum, today, today.plusDays(30), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.PERSONAL_ID_INVALID);

        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today, today.plusDays(30), "ZK/BAN/41/26", "", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_REASON_REQUIRED);

        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today, today.plusDays(30), "", "uchwała ZK o zakazie przyjmowania", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_DECISION_SIGNATURE_REQUIRED);

        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today, null, "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_PERIOD_REQUIRED);

        // the period is inclusive: today is already over, not a single day backwards
        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today, today, "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_PERIOD_INVALID);

        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today, today.plusDays(366), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_PERIOD_OVER_365_DAYS);

        // the period fits 365 days exactly but starts in the future, so it would run past today + 365
        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today.plusDays(10), today.plusDays(375), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null))
        ).extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_EXPIRY_OVER_365_DAYS);

        // creation reports the first offended rule with its statutory code
        assertThatThrownBy(() -> admissionBans.create(new NewAdmissionBan(
            wrongChecksum, today, today.plusDays(30), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", null), null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("PERSONAL_ID_INVALID");

        assertThatThrownBy(() -> admissionBans.create(new NewAdmissionBan(
            pesel, today, today.plusDays(30), "ZK/BAN/41/26", "  ", null), null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("BAN_REASON_REQUIRED");

        assertThat(banCount()).isZero();
        assertThat(admissionBans.check(valid)).matches(AdmissionBanCheck::allowed);
    }

    @Test
    void anOverlappingUchwałaIsAConflictAndTheRecordedOneStaysUntouched() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);

        TAdmissionBan recorded = admissionBans.create(new NewAdmissionBan(
            pesel, today, today.plusDays(200), "ZK/BAN/41/26", "pierwsza uchwała", null), null);

        NewAdmissionBan overlapping = new NewAdmissionBan(
            pesel, today.plusDays(100), today.plusDays(150), "ZK/BAN/42/26", "druga uchwała", null);

        assertThat(admissionBans.check(overlapping))
            .extracting(AdmissionBanCheck::violations).asList()
            .containsExactly(AdmissionBanRule.BAN_OVERLAPS_EXISTING);

        assertThatThrownBy(() -> admissionBans.create(overlapping, null))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("BAN_OVERLAPS_EXISTING");

        // a ban touching the recorded one from the outside is fine — history is never closed early,
        // and the period still fits the creation year (the recording-day bound)
        assertThat(admissionBans.check(new NewAdmissionBan(
            pesel, today.plusDays(201), today.plusDays(365), "ZK/BAN/43/26", "kolejna uchwała", null))
        ).matches(AdmissionBanCheck::allowed);

        // §0.6: nothing was rewritten or closed silently — the recorded row stays as it was written
        assertThat(banCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT effective_until FROM admission_bans WHERE id = ?", java.sql.Date.class, recorded.getId())
            .toLocalDate()).isEqualTo(today.plusDays(200));
        assertThat(jdbc.queryForObject(
            "SELECT decision_signature FROM admission_bans WHERE id = ?", String.class, recorded.getId()))
            .isEqualTo("ZK/BAN/41/26");
    }

    @Test
    void theAnchorCaseMustNameTheSamePerson() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);

        jdbc.update("INSERT INTO recruitment_cases (district_body_id, candidate_first_name, "
            + "candidate_last_name, candidate_email, candidate_formal_identifier) "
            + "VALUES (?, 'Kolendra', 'Kowalska', 'kotwica@example.org', ?)",
            district("mazowieckie").getId(), pesel);
        Long caseId = jdbc.queryForObject(
            "SELECT id FROM recruitment_cases WHERE candidate_formal_identifier = ?", Long.class, pesel);

        TAdmissionBan anchored = admissionBans.create(new NewAdmissionBan(
            pesel, today, today.plusDays(30), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", caseId), null);

        assertThat(anchored.getRecruitmentCase().getId()).isEqualTo(caseId);

        String other = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 62);

        assertThatThrownBy(() -> admissionBans.create(new NewAdmissionBan(
            other, today, today.plusDays(30), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", caseId), null))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("BAN_CASE_IDENTIFIER_MISMATCH");

        assertThatThrownBy(() -> admissionBans.create(new NewAdmissionBan(
            other, today, today.plusDays(30), "ZK/BAN/41/26", "uchwała ZK o zakazie przyjmowania", -1L), null))
            .hasMessageContaining("not found");
    }

    private int banCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admission_bans", Integer.class);
        return count == null ? 0 : count;
    }
}

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

package org.mlodzirazem.panel.services.panelapi.importer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.MemberDistricts;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.main.web-application-type=none",
    "panel.import.file=unused.csv",
    "panel.import.autorun=false"
})
@PostgresTest
class SpreadsheetImporterTest extends DomainTestSupport {
    private static final ImportOptions APPLY = new ImportOptions(true, false);

    @Autowired
    SpreadsheetImporter importer;

    @Autowired
    AffiliationService affiliationService;

    @Autowired
    Clock clock;

    @TempDir
    Path dir;

    private Map<String, Object> sequenceBefore;

    @BeforeEach
    void rememberNumberSequence() {
        sequenceBefore = jdbc.queryForMap("SELECT last_value, is_called FROM seq_member_number_allocation");
    }

    @AfterEach
    void restoreNumberSequence() {
        // setval is not transactional: put the shared sequence back for the other test classes
        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', ?, ?)", Long.class,
            sequenceBefore.get("last_value"), sequenceBefore.get("is_called"));
    }

    private ImportReport dryRun(Sheet sheet) throws IOException {
        return importer.run(sheet.write(dir), ImportOptions.dryRun());
    }

    private ImportReport apply(Sheet sheet) throws IOException {
        return importer.run(sheet.write(dir), APPLY);
    }

    private TMember byMemberId(String memberId) {
        return memberRepository.findByMemberId(memberId).orElseThrow();
    }

    private static List<String> lines(ImportReport report) {
        return report.problems().stream().map(RowProblem::render).toList();
    }

    @Test
    void readsBomAndEmptyColumnAHeader() throws IOException {
        ImportReport report = dryRun(new Sheet().bom().add("101", "Aga", "Testowa", "Mazowiecki"));

        assertThat(report.rows()).isEqualTo(1);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void readsSemicolonAndTabDelimitedFiles() throws IOException {
        for (char delimiter : new char[] {';', '\t', ','}) {
            ImportReport report = dryRun(new Sheet().delimiter(delimiter)
                                                    .add("101", "Aga", "Testowa", "Mazowiecki")
                                                    .add("102", "Bartek", "Przykładowy", "Śląskie"));

            assertThat(report.created()).as("delimiter %d", (int) delimiter).isEqualTo(2);
            assertThat(report.errors()).isEmpty();
        }
    }

    @Test
    void matchesHeadersIgnoringCaseAndWhitespaceAndWarnsAboutUnknownColumns() throws IOException {
        List<String> header = List.of("", "  IMIĘ ", "nazwisko", "okręg  Stowarzyszenia", "DATA URODZENIA", "Dziwna");
        Sheet sheet = new Sheet().header(header).add(Map.of(
            "", "101", "  IMIĘ ", "Aga", "nazwisko", "Testowa", "okręg  Stowarzyszenia", "Zagranica",
            "DATA URODZENIA", "05.05.2005", "Dziwna", "x"));

        ImportReport report = dryRun(sheet);

        assertThat(report.created()).isEqualTo(1);
        assertThat(lines(report)).containsExactly("file: Dziwna → UNKNOWN_COLUMN");
    }

    @Test
    void missingRequiredColumnIsFatal() throws IOException {
        Sheet sheet = new Sheet().header(List.of("", "Imię", "Nazwisko")).add(Map.of("", "1", "Imię", "A"));

        assertThatThrownBy(() -> dryRun(sheet))
            .isInstanceOf(ImportFatalException.class)
            .satisfies(e -> assertThat(((ImportFatalException) e).code()).isEqualTo("MISSING_COLUMNS"))
            .hasMessageContaining("Data urodzenia")
            .hasMessageContaining("Okręg stowarzyszenia");
    }

    @Test
    void mapsDistrictNameVariants() throws IOException {
        apply(new Sheet().add("1", "A", "Pierwsza", "Mazowiecki")
                         .add("2", "B", "Druga", "mazowieckie")
                         .add("3", "C", "Trzecia", "Okręg Mazowiecki")
                         .add("4", "D", "Czwarta", "Zagranica")
                         .add("5", "E", "Piąta", "Warmińsko-Mazurskie"));

        assertThat(affiliationService.primaryDistrict(byMemberId("1").getId()).orElseThrow().getCode())
            .isEqualTo("district-mazowieckie");
        assertThat(affiliationService.primaryDistrict(byMemberId("2").getId()).orElseThrow().getCode())
            .isEqualTo("district-mazowieckie");
        assertThat(affiliationService.primaryDistrict(byMemberId("3").getId()).orElseThrow().getCode())
            .isEqualTo("district-mazowieckie");
        assertThat(affiliationService.primaryDistrict(byMemberId("4").getId()).orElseThrow().getCode())
            .isEqualTo("district-zagranica");
        assertThat(affiliationService.primaryDistrict(byMemberId("5").getId()).orElseThrow().getCode())
            .isEqualTo("district-warminsko-mazurskie");
    }

    @Test
    void reportsUnknownDistrictAndUnparseableDate() throws IOException {
        Map<String, String> badDate = Sheet.row("124", "Bez", "Daty", "Mazowiecki");
        badDate.put("Data urodzenia", "wczoraj");

        ImportReport report = dryRun(new Sheet().add("123", "Nie", "Tutaj", "Narnia").add(badDate));

        assertThat(lines(report)).containsExactly(
            "123: Okręg stowarzyszenia → UNKNOWN_DISTRICT",
            "124: Data urodzenia → UNPARSEABLE_DATE");
        assertThat(report.skipped()).isEqualTo(2);
        assertThat(report.exitCode()).isEqualTo(1);
    }

    @Test
    void splitsPhonesAndEmailsAndFallsBackToFormalNames() throws IOException {
        Map<String, String> row = Sheet.row("1", "Formalna", "Nazwiskowa", "Mazowiecki");
        row.put("nr tel", "+48 111 222 333; +48 444 555 666, +48 777");
        row.put("Prywatny adres e-mail", "a@example.org, b@example.org;c@example.org");
        row.put("Preferowane imię", "Prefka");

        apply(new Sheet().add(row));

        TMember member = byMemberId("1");
        assertThat(member.getPhoneNumbers()).containsExactly("+48 111 222 333", "+48 444 555 666", "+48 777");
        assertThat(member.getPrivateEmails()).containsExactly("a@example.org", "b@example.org", "c@example.org");
        assertThat(member.getPreferredNameFirst()).isEqualTo("Prefka");
        assertThat(member.getPreferredNameLast()).isEqualTo("Nazwiskowa");
        assertThat(member.getFormalNameFirst()).isEqualTo("Formalna");
    }

    @Test
    void validatesPeselChecksumAndComparesBirthDate() throws IOException {
        String valid = Sheet.pesel(LocalDate.of(2005, 5, 5), 1111);
        String otherDay = Sheet.pesel(LocalDate.of(2005, 5, 6), 1111);
        String broken = valid.substring(0, 10) + (valid.charAt(10) == '0' ? '1' : '0');

        Map<String, String> ok = Sheet.row("1", "Ok", "Dobry", "Mazowiecki");
        ok.put("pesel", valid);
        Map<String, String> mismatch = Sheet.row("2", "Nie", "Zgodny", "Mazowiecki");
        mismatch.put("pesel", otherDay);
        Map<String, String> checksum = Sheet.row("3", "Zly", "Skrot", "Mazowiecki");
        checksum.put("pesel", broken);
        Map<String, String> format = Sheet.row("4", "Zly", "Format", "Mazowiecki");
        format.put("pesel", "12345");

        ImportReport report = apply(new Sheet().add(ok).add(mismatch).add(checksum).add(format));

        assertThat(report.aborted()).isTrue();

        ImportReport skipping = importer.run(
            new Sheet().add(ok).add(mismatch).add(checksum).add(format).write(dir), new ImportOptions(true, true));

        assertThat(lines(skipping)).containsExactly(
            "2: pesel → BIRTH_DATE_MISMATCH", "3: pesel → CHECKSUM", "4: pesel → FORMAT");
        assertThat(skipping.created()).isEqualTo(2);
        assertThat(skipping.skipped()).isEqualTo(2);
        assertThat(byMemberId("2").getBirthDate()).isEqualTo(LocalDate.of(2005, 5, 5));
        assertThat(memberRepository.findByMemberId("3")).isEmpty();
    }

    @Test
    void dryRunWritesNothing() throws IOException {
        long membersBefore = memberRepository.count();

        ImportReport report = dryRun(new Sheet().add("1", "A", "Pierwsza", "Mazowiecki"));

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.written()).isFalse();
        assertThat(memberRepository.count()).isEqualTo(membersBefore);
        assertThat(auditCount("importer :: apply")).isZero();
        assertThat(auditCount("members :: create")).isZero();
    }

    @Test
    void applyCreatesAndUpdatesWithAudit() throws IOException {
        apply(new Sheet().add("1", "Pierwsza", "Osoba", "Mazowiecki").add("2", "Druga", "Osoba", "Śląskie"));

        Map<String, String> changed = Sheet.row("1", "Pierwsza", "Osoba", "Mazowiecki");
        changed.put("zaimki", "ona/jej");
        changed.put("Okręg stowarzyszenia", "Pomorskie");
        ImportReport report = apply(new Sheet().add(changed)
                                               .add("2", "Druga", "Osoba", "Śląskie")
                                               .add("3", "Trzecia", "Osoba", "Zagranica"));

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.unchanged()).isEqualTo(1);
        assertThat(byMemberId("1").getPronouns()).isEqualTo("ona/jej");
        assertThat(affiliationService.primaryDistrict(byMemberId("1").getId()).orElseThrow().getCode())
            .isEqualTo("district-pomorskie");
        assertThat(auditCount("importer :: apply")).isEqualTo(2);
        assertThat(auditCount("members :: create")).isEqualTo(3);
        assertThat(auditCount("members :: update")).isEqualTo(1);
        assertThat(auditPayloads("importer :: apply")).contains("\"created\": 1").contains("\"updated\": 1");
    }

    @Test
    void rerunProducesNoChanges() throws IOException {
        Map<String, String> full = Sheet.row("1", "Pierwsza", "Osoba", "Mazowiecki");
        full.put("nr tel", "111;222");
        full.put("Prywatny adres e-mail", "x@example.org");
        full.put("Okręg wyborczy", "19");
        full.put("pesel", Sheet.pesel(LocalDate.of(2005, 5, 5), 4321));
        full.put("data przyjęcia", "01/02/2024");
        full.put("Adres konta w organizacji", "pierwsza.osoba@example.org");
        Sheet sheet = new Sheet().add(full).add("2", "Druga", "Osoba", "Śląskie");

        apply(sheet);
        int creates = auditCount("members :: create");
        int updates = auditCount("members :: update");
        int affiliationAudits = auditCount("members :: affiliation-supporting");

        ImportReport second = apply(sheet);
        ImportReport dry = dryRun(sheet);

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(2);
        assertThat(dry.unchanged()).isEqualTo(2);
        assertThat(auditCount("members :: create")).isEqualTo(creates);
        assertThat(auditCount("members :: update")).isEqualTo(updates);
        assertThat(auditCount("members :: affiliation-supporting")).isEqualTo(affiliationAudits);
    }

    @Test
    void sejmConstituencyColumnIsRecognisedButNotImported() throws IOException {
        Map<String, String> numbered = Sheet.row("1", "Pierwsza", "Osoba", "Mazowiecki");
        numbered.put("Okręg wyborczy", "19");
        Map<String, String> abroad = Sheet.row("2", "Druga", "Osoba", "Zagranica");
        abroad.put("Okręg wyborczy", "nie dotyczy");
        Map<String, String> ambiguous = Sheet.row("3", "Trzecia", "Osoba", "Śląskie");
        ambiguous.put("Okręg wyborczy", "34/35");

        ImportReport report = apply(new Sheet().add(numbered).add(abroad).add(ambiguous));

        assertThat(report.problems()).isEmpty();
        assertThat(report.created()).isEqualTo(3);

        long first = byMemberId("1").getId();
        MemberDistricts districts = affiliationService.districtsOf(first);
        assertThat(districts.primaryDistrictId()).isEqualTo(district("mazowieckie").getId());
        assertThat(districts.supportingDistrictIds()).isEmpty();
        assertThat(affiliationService.votingDistrict(first).orElseThrow().getCode())
            .isEqualTo("district-mazowieckie");
    }

    @Test
    void matchesMemberNumbersIgnoringLeadingZerosAndKeepsTheStoredSpelling() throws IOException {
        TMember padded = newMember("Ala", "Alfa", "mazowieckie");
        padded.setMemberId("000205");
        memberRepository.saveAndFlush(padded);

        ImportReport report = apply(new Sheet().add("205", "Ala", "Alfa", "Mazowiecki"));

        assertThat(report.problems()).isEmpty();
        assertThat(report.created()).isZero();
        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(byMemberId("000205").getId()).isEqualTo(padded.getId());
        assertThat(memberRepository.findByMemberId("205")).isEmpty();

        ImportReport duplicate = dryRun(new Sheet().add("205", "A", "Pierwsza", "Mazowiecki")
                                                   .add("0205", "B", "Druga", "Mazowiecki"));

        assertThat(lines(duplicate)).containsExactly("0205: member_id → DUPLICATE_IN_FILE");
    }

    @Test
    void applyMovesTheNumberSequencePastTheRegister() throws IOException {
        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', 1, false)", Long.class);

        ImportReport report = apply(new Sheet().add("7", "A", "Pierwsza", "Mazowiecki")
                                               .add("1548", "B", "Druga", "Mazowiecki"));

        assertThat(report.nextMemberNumber()).isEqualTo(1549);
        assertThat(report.render()).contains("Next member number for admissions: 1549");
        assertThat(auditPayloads("importer :: apply")).contains("\"nextMemberNumber\": 1549");
        assertThat(jdbc.queryForObject("SELECT nextval('seq_member_number_allocation')", Long.class))
            .isEqualTo(1549);
    }

    @Test
    void dryRunReportsTheNextNumberWithoutMovingTheSequence() throws IOException {
        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', 1, false)", Long.class);

        ImportReport report = dryRun(new Sheet().add("7", "A", "Pierwsza", "Mazowiecki")
                                                .add("1548", "B", "Druga", "Mazowiecki")
                                                .add("960001", "C", "Testowa", "Mazowiecki"));

        assertThat(report.nextMemberNumber()).isEqualTo(1549);
        assertThat(jdbc.queryForObject("SELECT nextval('seq_member_number_allocation')", Long.class))
            .isEqualTo(1);
    }

    @Test
    void sequenceCoversTheHighestIssuedNumberAndNeverMovesBack() throws IOException {
        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', 1, false)", Long.class);

        ImportReport former = importer.run(new Sheet().add("7", "A", "Pierwsza", "Mazowiecki").write(dir),
            new ImportOptions(true, false, 2000L));

        assertThat(former.nextMemberNumber()).isEqualTo(2001);

        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', 3000)", Long.class);

        ImportReport later = apply(new Sheet().add("1548", "B", "Druga", "Mazowiecki"));

        assertThat(later.nextMemberNumber()).isEqualTo(3001);
        assertThat(jdbc.queryForObject("SELECT nextval('seq_member_number_allocation')", Long.class))
            .isEqualTo(3001);

        assertThatThrownBy(() -> importer.run(new Sheet().add("8", "C", "Trzecia", "Mazowiecki").write(dir),
            new ImportOptions(false, false, 950_000L)))
            .isInstanceOf(ImportFatalException.class)
            .satisfies(e -> assertThat(((ImportFatalException) e).code()).isEqualTo("HIGHEST_ISSUED_NUMBER"));
    }

    @Test
    void matchingPrefersMemberIdThenDomainEmailThenIdentifierAndFlagsAmbiguity() throws IOException {
        TMember a = newMember("Ala", "Alfa", "mazowieckie");
        a.setMemberId("10");
        a.setDomainEmail("alfa@example.org");
        memberRepository.saveAndFlush(a);
        TMember b = newMember("Bob", "Beta", "mazowieckie");
        b.setMemberId("20");
        b.setDomainEmail("beta@example.org");
        memberRepository.saveAndFlush(b);
        TMember c = newMember("Cec", "Gamma", "mazowieckie");
        c.setFormalIdentifier(Sheet.pesel(LocalDate.of(2005, 5, 5), 7777));
        memberRepository.saveAndFlush(c);

        Map<String, String> both = Sheet.row("10", "Ala", "Alfa", "Mazowiecki");
        both.put("Adres konta w organizacji", "BETA@example.org");
        Map<String, String> viaEmail = Sheet.row("30", "Dan", "Delta", "Mazowiecki");
        viaEmail.put("Adres konta w organizacji", "ALFA@example.org");
        Map<String, String> viaId = Sheet.row("20", "Bob", "Beta", "Mazowiecki");
        viaId.put("Adres konta w organizacji", "beta.new@example.org");
        Map<String, String> viaPesel = Sheet.row("40", "Cec", "Gamma", "Mazowiecki");
        viaPesel.put("pesel", Sheet.pesel(LocalDate.of(2005, 5, 5), 7777));

        ImportReport report = importer.run(new Sheet().add(both).add(viaEmail).add(viaId).add(viaPesel).write(dir),
            new ImportOptions(true, true));

        assertThat(lines(report)).containsExactly(
            "10: — → MATCHES_MULTIPLE_MEMBERS",
            "30: member_id → MEMBER_ID_CONFLICT");
        assertThat(memberRepository.findByMemberId("20").orElseThrow().getDomainEmail())
            .isEqualTo("beta.new@example.org");
        assertThat(memberRepository.findByMemberId("40").orElseThrow().getId()).isEqualTo(c.getId());
        assertThat(memberRepository.count()).isEqualTo(3);
        assertThat(report.updated()).isEqualTo(2);
    }

    @Test
    void reportsDuplicatesInsideTheFile() throws IOException {
        ImportReport report = dryRun(new Sheet().add("1", "A", "Pierwsza", "Mazowiecki")
                                                .add("1", "B", "Druga", "Mazowiecki"));

        assertThat(lines(report)).containsExactly("1: member_id → DUPLICATE_IN_FILE");
    }

    @Test
    void checksAgeVotingRightAndFollowUpColumnsWithoutImportingThem() throws IOException {
        Map<String, String> row = Sheet.row("1", "Pierwsza", "Osoba", "Mazowiecki");
        row.put("Wiek", "99");
        row.put("Czy może głosować", "NIE");
        row.put("Głosowanie nad usunieciem", "tak");
        row.put("Zeus CSV", "cokolwiek");
        Map<String, String> old = Sheet.row("2", "Druga", "Osoba", "Mazowiecki");
        old.put("Data urodzenia", "01.01.1990");
        old.put("Czy może głosować", "TAK");

        ImportReport report = dryRun(new Sheet().add(row).add(old));

        assertThat(lines(report)).containsExactly(
            "1: Wiek → AGE_MISMATCH",
            "1: Głosowanie nad usunieciem → REMOVAL_VOTE_FOLLOW_UP",
            "1: Czy może głosować → VOTING_RIGHT_MISMATCH",
            "2: Data urodzenia → MEMBERSHIP_AGE_LIMIT_EXCEEDED");
        assertThat(report.exitCode()).isZero();
    }

    @Test
    void underSixteenIsATransitionalSupportingMemberWithoutAVote() throws IOException {
        Map<String, String> young = Sheet.row("1", "Mloda", "Osoba", "Mazowiecki");
        young.put("Data urodzenia", LocalDate.now(clock).minusYears(15).toString());
        young.put("Czy może głosować", "nie");
        Map<String, String> adult = Sheet.row("2", "Dorosla", "Osoba", "Mazowiecki");
        adult.put("Czy może głosować", "tak");
        Sheet sheet = new Sheet().add(young).add(adult);

        assertThat(dryRun(sheet).problems()).isEmpty();

        ImportReport applied = apply(sheet);

        assertThat(applied.problems()).isEmpty();
        assertThat(byMemberId("1").getMembershipType()).isEqualTo(MembershipType.SUPPORTING);
        assertThat(byMemberId("2").getMembershipType()).isEqualTo(MembershipType.ORDINARY);
        assertThat(apply(sheet).unchanged()).isEqualTo(2);
    }

    @Test
    void votingRightOfExistingMemberIsComputedFromCurrentState() throws IOException {
        TMember member = newMember("Eli", "Gible", "mazowieckie");
        member.setMemberId("1");
        memberRepository.saveAndFlush(member);

        Map<String, String> row = Sheet.row("1", "Eli", "Gible", "Mazowiecki");
        row.put("Czy może głosować", "tak");

        assertThat(dryRun(new Sheet().add(row)).problems()).isEmpty();
    }

    @Test
    void reportNeverContainsPersonalData() throws IOException {
        String pesel = Sheet.pesel(LocalDate.of(2005, 5, 6), 2468);
        Map<String, String> bad = Sheet.row("900", "Sekretna", "Nazwiskowska", "Narnia");
        bad.put("pesel", pesel);
        bad.put("Adres konta w organizacji", "sekret@example.org");
        bad.put("Prywatny adres e-mail", "prywatny@example.org");
        bad.put("nr tel", "+48 600 700 800");
        bad.put("Data urodzenia", "nie-data");
        Map<String, String> warn = Sheet.row("901", "Ostrzezona", "Osobowska", "Mazowiecki");
        warn.put("pesel", pesel.substring(0, 10) + (pesel.charAt(10) == '0' ? '1' : '0'));
        warn.put("Wiek", "77");
        warn.put("Głosowanie nad usunieciem", "Tajny powód");

        ImportReport report = dryRun(new Sheet().add(bad).add(warn));
        String rendered = report.render();

        assertThat(rendered).contains("900: Okręg stowarzyszenia → UNKNOWN_DISTRICT");
        assertThat(rendered).doesNotContain("Sekretna", "Nazwiskowska", "Ostrzezona", "Osobowska", "sekret@",
            "prywatny@", "600 700", pesel, "Tajny", "nie-data", "Narnia");
    }
}

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

package org.mlodzirazem.panel.services.panelapi.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.SyntheticPesel;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.main.web-application-type=none",
    "panel.bans.file=unused.csv",
    "panel.bans.autorun=false"
})
@PostgresTest
class BanImporterTest extends DomainTestSupport {
    private static final String BAN_CREATE_OPERATION = "recruitment :: ban-create";

    @Autowired
    BanImporter importer;

    @TempDir
    Path dir;

    private final LocalDate today = LocalDate.now();

    @Test
    void dryRunThenApplyThenRerunOfAFreshBanFile() throws IOException {
        String first = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);
        String second = SyntheticPesel.of(LocalDate.of(2000, 2, 29), 7);

        Path file = BanFile.file()
                           .row(first, today.toString(), today.plusDays(90).toString(),
                               "Uchwała ZK 41/2026", "negatywna ocena ZK w okresie wstępnym")
                           .row(second, null, today.plusDays(300).toString(),
                               "Uchwała ZK 42/2026", "działania przeciwko celom stowarzyszenia")
                           .write(dir);

        BanImportReport dry = importer.run(file, false);

        assertThat(dry.rows()).isEqualTo(2);
        assertThat(dry.created()).isEqualTo(2);
        assertThat(dry.errors()).isEmpty();
        assertThat(dry.written()).isFalse();
        assertThat(dry.render()).contains("Result: dry run, nothing was written");
        assertThat(dry.exitCode()).isZero();
        assertThat(banCount()).isZero();
        assertThat(auditCount(BanImporter.AUDIT_OPERATION)).isZero();

        BanImportReport applied = importer.run(file, true);

        assertThat(applied.created()).isEqualTo(2);
        assertThat(applied.unchanged()).isZero();
        assertThat(applied.exitCode()).isZero();
        assertThat(applied.render()).contains("Result: applied");
        assertThat(banCount()).isEqualTo(2);

        // one ban apiece: the uchwała of the first row — read by jdbc, the entity stays detached
        Map<String, Object> stored = jdbc.queryForMap("""
            SELECT formal_identifier, decision_signature, reason, effective_from, effective_until
            FROM admission_bans
            WHERE formal_identifier = ?
            """, first);

        assertThat(stored.get("decision_signature")).isEqualTo("Uchwała ZK 41/2026");
        assertThat(stored.get("reason")).isEqualTo("negatywna ocena ZK w okresie wstępnym");
        assertThat(((java.sql.Date) stored.get("effective_from")).toLocalDate()).isEqualTo(today);
        assertThat(((java.sql.Date) stored.get("effective_until")).toLocalDate()).isEqualTo(today.plusDays(90));

        // the start date defaults to the recording day when the file leaves it blank
        Map<String, Object> secondBan = jdbc.queryForMap("""
            SELECT effective_from, effective_until
            FROM admission_bans
            WHERE formal_identifier = ?
            """, second);

        assertThat(((java.sql.Date) secondBan.get("effective_from")).toLocalDate()).isEqualTo(today);
        assertThat(((java.sql.Date) secondBan.get("effective_until")).toLocalDate()).isEqualTo(today.plusDays(300));

        // audit: names of the fields only, per ban, and the summary with the counts — zero values (§0.5)
        String created = auditPayloads(BAN_CREATE_OPERATION);

        assertThat(created).contains("formalIdentifier", "reason", "decisionSignature",
            "effectiveFrom", "effectiveUntil", "banId", "CREATED");
        assertThat(created).doesNotContain(first, second, "Uchwała", "negatywna ocena");
        assertThat(auditCount(BAN_CREATE_OPERATION)).isEqualTo(2);

        String summary = auditPayloads(BanImporter.AUDIT_OPERATION);

        assertThat(summary).contains("\"rows\": 2", "\"created\": 2");
        assertThat(summary).doesNotContain(first, second, "Uch");

        // rerun: nothing changes, the identical uchwała is reported as unchanged
        int banAuditsBefore = auditCount(BAN_CREATE_OPERATION);

        BanImportReport rerun = importer.run(file, true);

        assertThat(rerun.created()).isZero();
        assertThat(rerun.unchanged()).isEqualTo(2);
        assertThat(rerun.skipped()).isZero();
        assertThat(rerun.errors()).isEmpty();
        assertThat(rerun.exitCode()).isZero();
        assertThat(rerun.render()).contains("Unchanged: 2");
        assertThat(banCount()).isEqualTo(2); // history untouched
        assertThat(auditCount(BAN_CREATE_OPERATION)).isEqualTo(banAuditsBefore);
    }

    @Test
    void rowProblemsAreReportedAsCodesOfLineNumbersWithoutValues() throws IOException {
        String badChecksum = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61).substring(0, 10)
            + (SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61).charAt(10) == '9' ? '0' : '9');

        BanImportReport dry = importer.run(
            BanFile.file()
                   .raw(badChecksum, today.toString(), today.plusDays(30).toString(),
                       "Uchwała ZK 41/2026", "powód jawny")
                   .raw(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 62), "31AAA", today.plusDays(30).toString(),
                       "Uchwała ZK 42/2026", "powód jawny")
                   .raw(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 63), today.toString(), null,
                       "Uchwała ZK 43/2026", "powód jawny")
                   .raw(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 64), today.toString(),
                       today.plusDays(30).toString(), "", "powód jawny")
                   .raw(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 65), today.toString(),
                       today.plusDays(30).toString(), "Uchwała ZK 45/2026", "")
                   .write(dir),
            false);

        List<BanImportProblem> errors = dry.errors();

        assertThat(errors).hasSize(5);
        assertThat(errors).extracting(BanImportProblem::code)
            .containsExactly("PERSONAL_ID_INVALID", "UNPARSEABLE_DATE", "REQUIRED", "REQUIRED", "REQUIRED");

        // every problem names the line and the column — never a value of the file (§0.5)
        assertThat(errors).extracting(BanImportProblem::rowLabel)
            .containsExactly("row 2", "row 3", "row 4", "row 5", "row 6");
        assertThat(errors).extracting(BanImportProblem::column)
            .containsExactly("pesel", "from", "until", "resolution", "reason");
        assertThat(dry.exitCode()).isEqualTo(BanImportMode.EXIT_PROBLEMS);
        assertThat(dry.render())
            .doesNotContain(badChecksum, "Uchwała", "powód jawny", "row 2: " + badChecksum);
    }

    @Test
    void theStoredUchwałaIsNeverRewrittenByARerun() throws IOException {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);

        importer.run(BanFile.file()
                          .row(pesel, today.toString(), today.plusDays(90).toString(),
                              "Uchwała ZK 41/2026", "powód pierwotny")
                          .write(dir), true);

        BanImportReport otherSignature = importer.run(
            BanFile.file()
                   .row(pesel, today.toString(), today.plusDays(90).toString(),
                       "Uchwała ZK 99/2027", "powód pierwotny")
                   .write(dir), true);

        assertThat(otherSignature.errors())
            .extracting(BanImportProblem::code)
            .containsExactly("DECISION_REFERENCE_DIFFERS");
        assertThat(otherSignature.created()).isZero();
        assertThat(otherSignature.aborted()).isTrue();
        assertThat(otherSignature.render()).contains("ABORTED, errors found, nothing was written");

        BanImportReport otherReason = importer.run(
            BanFile.file()
                   .row(pesel, today.toString(), today.plusDays(90).toString(),
                       "Uchwała ZK 41/2026", "powód poprawiony")
                   .write(dir), true);

        assertThat(otherReason.errors())
            .extracting(BanImportProblem::code)
            .containsExactly("BAN_REASON_DIFFERS");

        String stored = jdbc.queryForObject(
            "SELECT decision_signature || '|' || reason FROM admission_bans WHERE formal_identifier = ?",
            String.class, pesel);

        // history is untouchable (§0.6): the first uchwała of the period stays exactly as recorded
        assertThat(stored).isEqualTo("Uchwała ZK 41/2026|powód pierwotny");
        assertThat(banCount()).isEqualTo(1);
    }

    @Test
    void overlappingBansConflictAndApplyIsAllOrNothing() throws IOException {
        String banned = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 71);
        String fresh = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 72);

        // a recorded uchwała covering [today, today + 200]
        importer.run(BanFile.file()
                          .row(banned, today.toString(), today.plusDays(200).toString(),
                              "Uchwała ZK 41/2026", "pierwszy zakaz")
                          .write(dir), true);

        Path file = BanFile.file()
                           // a clean row first: proving all-or-nothing means it is rolled back too
                           .row(fresh, today.toString(), today.plusDays(30).toString(),
                               "Uchwała ZK 42/2026", "drugą uchwała")
                           .row(banned, today.plusDays(100).toString(), today.plusDays(300).toString(),
                               "Uchwała ZK 43/2026", "drugą uchwała")
                           .write(dir);

        BanImportReport dry = importer.run(file, false);

        assertThat(dry.errors())
            .extracting(BanImportProblem::code)
            .containsExactly("BAN_OVERLAPS_EXISTING");
        assertThat(dry.errors()).extracting(BanImportProblem::column).containsExactly("pesel");

        BanImportReport applied = importer.run(file, true);

        assertThat(applied.aborted()).isTrue();
        assertThat(applied.render()).contains("ABORTED, errors found, nothing was written");
        assertThat(banCount()).isEqualTo(1); // only the pre-existing ban — the clean row was rolled back
        assertThat(auditCount(BanImporter.AUDIT_OPERATION)).isEqualTo(1); // only the first apply summary
    }

    @Test
    void duplicateRowsInsideOneFileAreRejected() throws IOException {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);

        BanImportReport dry = importer.run(
            BanFile.file()
                   .row(pesel, today.toString(), today.plusDays(90).toString(),
                       "Uchwała ZK 41/2026", "powód jawny")
                   .row(pesel, today.toString(), today.plusDays(90).toString(),
                       "Uchwała ZK 42/2026", "powód jawny")
                   .write(dir),
            false);

        assertThat(dry.errors())
            .extracting(BanImportProblem::code)
            .containsExactly("DUPLICATE_IN_FILE");
        assertThat(banCount()).isZero();
    }

    @Test
    void statutoryPeriodsAreRowErrorsToo() throws IOException {
        String tooLong = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61);
        String futureStart = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 62);

        BanImportReport dry = importer.run(
            BanFile.file()
                   // a 366-day period of the uchwała (art. 9 ust. 5: at most 365 days)
                   .row(tooLong, today.toString(), today.plusDays(366).toString(),
                       "Uchwała ZK 41/2026", "powód jawny")
                   // the period itself fits, but the ban would still run past today + 365
                   .row(futureStart, today.plusDays(10).toString(), today.plusDays(375).toString(),
                       "Uchwała ZK 42/2026", "powód jawny")
                   .write(dir),
            false);

        assertThat(dry.errors())
            .extracting(BanImportProblem::code)
            .containsExactly("BAN_PERIOD_OVER_365_DAYS", "BAN_EXPIRY_OVER_365_DAYS");
        assertThat(dry.errors()).extracting(BanImportProblem::column)
            .containsExactly("until", "until");
    }

    @Test
    void formattedPeskelsAndEnglishHeadersImportLikeTheCanonicalForm() throws IOException {
        // a checksum-valid PESEL of a 1907 birth, written the way humans format numbers
        String spaced = "0704 - 0100 611";
        String digits = "07040100611";
        String header = "personal id;from;until;decision reference;reason";

        BanImportReport report = importer.run(
            BanFile.file()
                   .header(header)
                   .raw(spaced, today.plusDays(1).toString(), today.plusDays(365).toString(),
                       "Uchwała ZK 41/2026", "powód jawny")
                   .write(dir),
            false);

        assertThat(report.errors()).isEmpty();
        assertThat(report.created()).isEqualTo(1);

        importer.run(
            BanFile.file()
                   .header(header)
                   .raw(spaced, today.plusDays(1).toString(), today.plusDays(365).toString(),
                       "Uchwała ZK 41/2026", "powód jawny")
                   .write(dir),
            true);

        assertThat(jdbc.queryForObject(
            "SELECT formal_identifier FROM admission_bans", String.class)).isEqualTo(digits);
    }

    @Test
    void unknownHeadersAreWarningsAndMissingColumnsAreFatal() throws IOException {
        BanImportReport report = importer.run(
            BanFile.file()
                   .header("pesel;od;do;uchwała;powód;komentarz operatora")
                   .raw(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61), "", today.plusDays(30).toString(),
                       "Uchwała ZK 41/2026", "powód jawny", "niepotrzebne")
                   .write(dir),
            true);

        assertThat(report.warnings())
            .extracting(BanImportProblem::code)
            .containsExactly("UNKNOWN_COLUMN");
        assertThat(report.errors()).isEmpty();
        assertThat(report.created()).isEqualTo(1);

        assertThatThrownBy(() -> importer.run(
            BanFile.file()
                   .header("pesel;od;uchwała;powód")
                   .raw(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 61), today.toString(),
                       "Uchwała ZK 41/2026", "powód jawny")
                   .write(dir),
            false))
            .isInstanceOfSatisfying(BanImportFatalException.class,
                ex -> assertThat(ex.code()).isEqualTo("MISSING_COLUMNS"));
    }

    private int banCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admission_bans", Integer.class);
        return count == null ? 0 : count;
    }
}

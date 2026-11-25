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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipChanged;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Package A1 — the mandate CSV importer. Acceptance: a fresh database (migrations + seed) takes a
 * 3–5 row file of invented data through dry run and apply; a rerun changes nothing; a forbidden
 * art. 18 pair and a missing uchwała are row errors, never silent closures; the audit carries
 * field names and counts only.
 */
@SpringBootTest(properties = {
    "spring.main.web-application-type=none",
    "panel.mandates.file=unused.csv",
    "panel.mandates.autorun=false"
})
@PostgresTest
@Import(MandateImporterTest.EventCollector.class)
class MandateImporterTest extends DomainTestSupport {
    @Autowired
    MandateImporter importer;

    @Autowired
    EventCollector events;

    @TempDir
    Path dir;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void clearEvents() {
        events.events.clear();
    }

    // ------------------------------------------------------------------ happy path + idempotency

    @Test
    void dryRunThenApplyThenRerunOfAFreshRegisterFile() throws IOException {
        memberWithNumber("205");
        memberWithNumber("206");
        memberWithNumber("207");

        Path file = MandateFile.file()
                               .row("205", "district-mazowieckie-board", "COORDINATOR", "15.01.2026",
                                   "Uchwała WZO MZ 2/2026")
                               .row("206", "national-board", "CO_CHAIR", "2026-01-20", "Uchwała ZK 1/2026")
                               .row("207", "peer-court", "CHAIR", "2026-02-01", "Uchwała KSK 3/2026")
                               .write(dir);

        MandateImportReport dry = importer.run(file, false, false);

        assertThat(dry.rows()).isEqualTo(3);
        assertThat(dry.created()).isEqualTo(3);
        assertThat(dry.errors()).isEmpty();
        assertThat(dry.written()).isFalse();
        assertThat(dry.render()).contains("Result: dry run, nothing was written");
        assertThat(dry.exitCode()).isZero();
        assertThat(membershipCount()).isZero();
        assertThat(auditCount(MandateImporter.AUDIT_OPERATION)).isZero();

        MandateImportReport applied = importer.run(file, true, false);

        assertThat(applied.created()).isEqualTo(3);
        assertThat(applied.unchanged()).isZero();
        assertThat(applied.exitCode()).isZero();
        assertThat(applied.render()).contains("Result: applied");
        assertThat(membershipCount()).isEqualTo(3);
        assertThat(events.events).hasSize(3);
        assertThat(events.events).allMatch(e -> e.change() == BodyMembershipChanged.Change.CREATED);

        // the ZK mandate of member 206 — read by jdbc: the entity graph stays detached outside the tx
        Map<String, Object> zk = jdbc.queryForMap("""
            SELECT b.code AS body_code, mt.code AS type_code, bm.valid_from, bm.valid_to,
                   bm.decision_reference, m.member_id
            FROM body_memberships bm
              JOIN bodies b ON b.id = bm.body_id
              JOIN body_template_member_types mt ON mt.id = bm.member_type_id
              JOIN members m ON m.id = bm.member_id
            WHERE m.member_id = '206'
            """);

        assertThat(zk.get("body_code")).isEqualTo("national-board");
        assertThat(zk.get("type_code")).isEqualTo("CO_CHAIR");
        assertThat(((java.sql.Date) zk.get("valid_from")).toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(zk.get("valid_to")).isNull();
        assertThat(zk.get("decision_reference")).isEqualTo("Uchwała ZK 1/2026");

        // audit: one row per created mandate (field names only) + the summary with counts
        String payloads = auditPayloads(MandateImporter.AUDIT_OPERATION);
        assertThat(payloads).contains("decisionReference", "validFrom", "\"created\": 3", "\"rows\": 3");
        assertThat(payloads).doesNotContain("Uchwała ZK 1/2026", "Aga", "Testowa", "example.org");
        assertThat(auditCount(MandateImporter.AUDIT_OPERATION)).isEqualTo(4);

        // rerun: nothing changes, nothing new is audited per mandate
        int perMandateAuditsBefore = auditRowsMentioning("membershipId");
        int membershipsBefore = membershipCount();

        MandateImportReport rerun = importer.run(file, true, false);

        assertThat(rerun.created()).isZero();
        assertThat(rerun.unchanged()).isEqualTo(3);
        assertThat(rerun.skipped()).isZero();
        assertThat(rerun.errors()).isEmpty();
        assertThat(rerun.exitCode()).isZero();
        assertThat(rerun.render()).contains("Unchanged: 3");
        assertThat(membershipCount()).isEqualTo(membershipsBefore);
        assertThat(auditRowsMentioning("membershipId")).isEqualTo(perMandateAuditsBefore);
        assertThat(events.events).hasSize(3);
    }

    @Test
    void memberNumbersMatchNumericallyRegardlessOfPadding() throws IOException {
        memberWithNumber("000205");
        memberWithNumber("206");

        MandateFile mandates = MandateFile.file()
                                          .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 2/2026")
                                          .row("000206", "national-board", "TREASURER", "2026-01-01",
                                              "Uchwała ZK 3/2026");

        MandateImportReport report = importer.run(mandates.write(dir), true, false);

        assertThat(report.created()).isEqualTo(2);
        assertThat(report.errors()).isEmpty();
        assertThat(membershipCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ statutory rejections

    @Test
    void forbiddenPairWithAnExistingMandateAbortsApplyAndEndsNothing() throws IOException {
        TMember coordinator = memberWithNumber("205");

        // an open Zarząd Okręgu mandate — art. 18 ust. 1 pkt 6 group
        grant(coordinator, body("district-mazowieckie-board"), "COORDINATOR", today.minusMonths(6), null);
        memberWithNumber("206");

        Path file = MandateFile.file()
                               // a clean row first: proving all-or-nothing means it is rolled back too
                               .row("206", "audit-commission", "CHAIR", "2026-01-01", "Uchwała KKR 1/2026")
                               .row("205", "national-board", "CO_CHAIR", "2026-01-02", "Uchwała ZK 1/2026")
                               .write(dir);

        MandateImportReport dry = importer.run(file, false, false);

        assertThat(dry.errors())
            .extracting(MandateImportProblem::code)
            .containsExactly("INCOMPATIBLE_MEMBERSHIP");
        assertThat(dry.exitCode()).isEqualTo(MandateImportMode.EXIT_PROBLEMS);
        assertThat(dry.written()).isFalse();

        MandateImportReport applied = importer.run(file, true, false);

        assertThat(applied.aborted()).isTrue();
        assertThat(applied.skipped()).isEqualTo(1);
        assertThat(applied.render()).contains("ABORTED, errors found, nothing was written");
        assertThat(membershipCount()).isEqualTo(1); // only the pre-existing ZO grant
        assertThat(auditCount(MandateImporter.AUDIT_OPERATION)).isZero(); // rolled back with the transaction
        assertThat(events.events).isEmpty();
    }

    @Test
    void foreignPairInsideOneFileIsCaughtAtApply() throws IOException {
        memberWithNumber("205");

        Path file = MandateFile.file()
                               .row("205", "district-mazowieckie-board", "COORDINATOR", "2026-01-01",
                                   "Uchwała WZO MZ 1/2026")
                               .row("205", "national-board", "CO_CHAIR", "2026-01-15", "Uchwała ZK 1/2026")
                               .write(dir);

        // dry run looks at the register row by row, so the intra-file pair only surfaces at apply
        MandateImportReport dry = importer.run(file, false, false);

        assertThat(dry.created()).isEqualTo(2);
        assertThat(dry.errors()).isEmpty();

        MandateImportReport aborted = importer.run(file, true, false);

        assertThat(aborted.aborted()).isTrue();
        assertThat(aborted.errors())
            .extracting(MandateImportProblem::code)
            .containsExactly("INCOMPATIBLE_MEMBERSHIP");
        assertThat(membershipCount()).isZero();

        MandateImportReport skipped = importer.run(file, true, true);

        assertThat(skipped.created()).isEqualTo(1);
        assertThat(skipped.skipped()).isEqualTo(1);
        assertThat(skipped.exitCode()).isEqualTo(MandateImportMode.EXIT_PROBLEMS);
        assertThat(membershipCount()).isEqualTo(1);
    }

    @Test
    void missingResolutionIsARequiredColumnAndValue() throws IOException {
        memberWithNumber("205");

        MandateImportReport dry = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "")
                       .write(dir),
            false, false);

        assertThat(dry.errors())
            .singleElement()
            .satisfies(p -> {
                assertThat(p.code()).isEqualTo("REQUIRED");
                assertThat(p.column()).isEqualTo("resolution");
            });

        MandateImportReport applied = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "")
                       .write(dir),
            true, false);

        assertThat(applied.aborted()).isTrue();
        assertThat(membershipCount()).isZero();

        assertThatThrownBy(() -> importer.run(
            MandateFile.file()
                       .header("numer członka,kod organu,kod funkcji,od")
                       .raw("205", "national-board", "SECRETARY", "2026-01-01")
                       .write(dir),
            false, false))
            .isInstanceOfSatisfying(MandateImportFatalException.class,
                ex -> assertThat(ex.code()).isEqualTo("MISSING_COLUMNS"));
    }

    @Test
    void existingMandatesDecisionReferenceIsNeverRewritten() throws IOException {
        memberWithNumber("205");
        Path file = MandateFile.file()
                               .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 1/2026")
                               .write(dir);

        importer.run(file, true, false);

        MandateImportReport second = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 9/2027")
                       .write(dir),
            true, false);

        assertThat(second.errors())
            .extracting(MandateImportProblem::code)
            .containsExactly("DECISION_REFERENCE_DIFFERS");
        assertThat(second.created()).isZero();
        assertThat(second.exitCode()).isEqualTo(MandateImportMode.EXIT_PROBLEMS);

        TBodyMembership stored = activeMandates().getFirst();

        assertThat(stored.getDecisionReference()).isEqualTo("Uchwała ZK 1/2026");
    }

    @Test
    void skipInvalidRowsWritesTheCleanOnesOnly() throws IOException {
        memberWithNumber("205");
        memberWithNumber("206");

        Path file = MandateFile.file()
                               .row("205", "audit-commission", "CHAIR", "2026-01-01", "Uchwała KKR 1/2026")
                               .raw("206", "national-board", "CO_CHAIR", "2026-01-01", null)
                               .write(dir);

        MandateImportReport report = importer.run(file, true, true);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.unchanged()).isZero();
        assertThat(report.exitCode()).isEqualTo(MandateImportMode.EXIT_PROBLEMS);
        assertThat(membershipCount()).isEqualTo(1);
    }

    @Test
    void removedMemberIsIneligible() throws IOException {
        memberWithNumber("205");
        jdbc.update("UPDATE members SET status = 'REMOVED' WHERE id = ?",
            memberRepository.findByMemberId("205").orElseThrow().getId());

        MandateImportReport dry = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 1/2026")
                       .write(dir),
            false, false);

        assertThat(dry.errors())
            .singleElement()
            .satisfies(p -> {
                assertThat(p.code()).isEqualTo("MEMBER_NOT_ELIGIBLE");
                assertThat(p.column()).isEqualTo("member");
            });
    }

    // ------------------------------------------------------------------ warnings, unknowns, PII

    @Test
    void statutoryWarningsDoNotBlockTheImport() throws IOException {
        memberWithNumber("205");

        // two consecutive terms in the body leave the candidate past the term limit (art. 43);
        // the seeded ZK template has no plain MEMBER function — SECRETARY does
        grant(memberByNumber("205"), body("national-board"), "SECRETARY", today.minusMonths(26),
            today.minusDays(10));
        grant(memberByNumber("205"), body("national-board"), "SECRETARY", today.minusMonths(50),
            today.minusMonths(27));

        MandateImportReport report = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "CO_CHAIR", today.toString(), "Uchwała ZK 1/2026")
                       .write(dir),
            true, false);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings())
            .extracting(MandateImportProblem::code)
            .contains("TERM_LIMIT_EXCEEDED");
        assertThat(report.exitCode()).isZero();
        assertThat(membershipCount()).isEqualTo(3);
    }

    @Test
    void unknownMemberBodyAndFunctionAreRowErrors() throws IOException {
        memberWithNumber("205");

        MandateImportReport report = importer.run(
            MandateFile.file()
                       .row("205", "no-such-body", "CHAIR", "2026-01-01", "Uchwała 1/2026")
                       .row("205", "national-board", "NO_SUCH_FUNCTION", "2026-01-01", "Uchwała 1/2026")
                       .row("299", "national-board", "SECRETARY", "2026-01-01", "Uchwała 1/2026")
                       .row(null, "national-board", "SECRETARY", "2026-01-01", "Uchwała 1/2026")
                       .write(dir),
            false, false);

        assertThat(report.errors())
            .extracting(MandateImportProblem::code)
            .containsExactly("UNKNOWN_BODY", "UNKNOWN_FUNCTION", "UNKNOWN_MEMBER", "REQUIRED");
        assertThat(report.created()).isZero();
        assertThat(report.exitCode()).isEqualTo(MandateImportMode.EXIT_PROBLEMS);
    }

    @Test
    void duplicateMandateKeyInsideOneFileIsRejected() throws IOException {
        memberWithNumber("205");

        MandateImportReport report = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 1/2026")
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 1/2026")
                       .write(dir),
            true, false);

        assertThat(report.aborted()).isTrue();
        assertThat(report.errors())
            .extracting(MandateImportProblem::code)
            .containsExactly("DUPLICATE_IN_FILE");
        assertThat(report.created()).isEqualTo(1); // would-be, rolled back — nothing was written
        assertThat(membershipCount()).isZero();
    }

    @Test
    void closedMandateWithTheSameKeyStaysClosedAndCountsAsUnchanged() throws IOException {
        memberWithNumber("205");
        Path file = MandateFile.file()
                               .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 1/2026")
                               .write(dir);

        importer.run(file, true, false);
        jdbc.update("UPDATE body_memberships SET valid_to = ? WHERE decision_reference = 'Uchwała ZK 1/2026'",
            LocalDate.of(2026, 12, 31));

        MandateImportReport rerun = importer.run(file, true, false);

        assertThat(rerun.created()).isZero();
        assertThat(rerun.unchanged()).isEqualTo(1);
        assertThat(rerun.errors()).isEmpty();

        TBodyMembership stored = bodyMembershipRepository.findAll().getFirst();

        assertThat(stored.getValidTo()).isEqualTo(LocalDate.of(2026, 12, 31)); // never reopened
    }

    @Test
    void renderCarriesIdsAndCodesOnly() throws IOException {
        memberWithNumber("205");

        MandateImportReport report = importer.run(
            MandateFile.file()
                       .row("205", "national-board", "SECRETARY", "2026-01-01", "Uchwała ZK 1/2026")
                       .write(dir),
            false, false);

        String rendered = report.render();

        assertThat(rendered).contains("Mandate import:", "Mode: dry run", "Rows: 1", "Created: 1");
        assertThat(rendered).doesNotContain("Aga", "Testowa", "example.org", "Uchwała ZK 1/2026");
    }

    // ------------------------------------------------------------------ helpers

    private TMember memberWithNumber(String number) {
        TMember member = newMember("Aga", "Testowa", "mazowieckie");
        jdbc.update("UPDATE members SET member_id = ? WHERE id = ?", number, member.getId());
        return member;
    }

    private TMember memberByNumber(String number) {
        return memberRepository.findByMemberId(number).orElseThrow();
    }

    private List<TBodyMembership> activeMandates() {
        return bodyMembershipRepository.findAll();
    }

    private int membershipCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships", Integer.class);
        return count == null ? 0 : count;
    }

    private int auditRowsMentioning(String token) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audited_operations WHERE operation_id = ? AND operation_payload::text LIKE ?",
            Integer.class, MandateImporter.AUDIT_OPERATION, "%\"" + token + "\"%");
        return count == null ? 0 : count;
    }

    @TestComponent
    static class EventCollector {
        final List<BodyMembershipChanged> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(BodyMembershipChanged event) {
            events.add(event);
        }
    }
}

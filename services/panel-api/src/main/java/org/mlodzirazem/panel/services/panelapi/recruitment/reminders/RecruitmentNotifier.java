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

package org.mlodzirazem.panel.services.panelapi.recruitment.reminders;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailDispatch;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.mlodzirazem.panel.services.panelapi.mail.api.TransactionalMail;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentNotification;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentNotificationRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.ZoRecommendationRecorded;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mails of the recruitment procedure to the boards: the daily reminders to every ZO (clocks of its cases) and
 * the notifications to the ZK (candidates admitted to the initial period - statute 2026 art. 9 ust. 4 pkt 3,
 * and ZO recommendations). A mail never carries candidate data: case numbers, the due date and a link to the
 * Panel, which does its own permission check. Every mail is idempotent through {@code recruitment_notifications}
 * (the log row is written in the same transaction as the hand-over to the mail module; nothing is logged when
 * mail is switched off, so the same day is picked up once mail is on). Nothing here throws into the caller.
 */
@Slf4j
@Service
@EnableConfigurationProperties(RecruitmentReminderProperties.class)
public class RecruitmentNotifier {
    static final String ZO_DAILY = "ZO_DAILY_REMINDERS";
    static final String ZK_DIGEST = "ZK_PROBATION_DIGEST";
    static final String ZK_RECOMMENDATION = "ZK_RECOMMENDATION";

    /** What a run did, for the log line and the tests. */
    public record Report(int bodiesWithDue, int scheduled, int noAddress, int alreadySent, int notSent) {
    }

    private record Due(long caseId, long districtId, String kind, LocalDate due) {
    }

    private final JdbcClient jdbc;
    private final TBodyRepository bodies;
    private final TRecruitmentNotificationRepository notifications;
    private final TransactionalMail mail;
    private final RecruitmentReminderProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    RecruitmentNotifier(
        JdbcClient jdbc,
        TBodyRepository bodies,
        TRecruitmentNotificationRepository notifications,
        TransactionalMail mail,
        RecruitmentReminderProperties properties,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbc = jdbc;
        this.bodies = bodies;
        this.notifications = notifications;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // --- ZO: one digest per district board per day -------------------------------------------------------------

    public Report sendZoDailyReminders(LocalDate today) {
        Map<Long, List<Due>> byDistrict = new LinkedHashMap<>();

        for (Due due : dueItems(today)) {
            byDistrict.computeIfAbsent(due.districtId(), id -> new ArrayList<>()).add(due);
        }

        Counters counters = new Counters();
        counters.bodiesWithDue = byDistrict.size();

        byDistrict.forEach((districtId, items) -> {
            TBody board = bodies.findAllByParentBodyIdAndKind(districtId, BodyKind.DISTRICT_BOARD)
                .stream().findFirst().orElse(null);

            if (board == null) {
                counters.noAddress++;
                return;
            }

            deliver(counters, ZO_DAILY, board, today.toString(), null,
                "Panel MR: terminy w rekrutacji (" + items.size() + ")", zoBody(items, today),
                "recruitment-zo-reminders", "body:" + board.getId());
        });

        log.info("recruitment ZO reminders day={} districtsWithDue={} scheduled={} noBoardAddress={} alreadySent={}"
                + " notSent={}", today, counters.bodiesWithDue, counters.scheduled, counters.noAddress,
            counters.alreadySent, counters.notSent);

        return counters.report();
    }

    private List<Due> dueItems(LocalDate today) {
        LocalDate windowLimit = today.plusDays(properties.getWindowWarningDays());
        LocalDate resolutionLimit = today.plusDays(properties.getResolutionWarningDays());
        String open = " FROM recruitment_cases c WHERE c.closed_at IS NULL AND c.anonymized_at IS NULL";

        return jdbc.sql(
                "SELECT c.id, c.district_body_id, 'WINDOW_OPENS' AS kind, c.assessment_not_before_on AS due" + open
                    + " AND c.status = 'PROBATION' AND c.assessment_not_before_on = :today"
                    + " UNION ALL SELECT c.id, c.district_body_id, 'WINDOW_CLOSING', c.assessment_not_later_on" + open
                    + " AND c.status = 'PROBATION' AND c.assessment_not_later_on <= :windowLimit"
                    + " UNION ALL SELECT c.id, c.district_body_id, 'SECOND_ASSESSMENT',"
                    + " c.second_assessment_not_later_on" + open
                    + " AND c.status = 'PROBATION_EXTENDED' AND c.second_assessment_not_later_on <= :windowLimit"
                    + " UNION ALL SELECT c.id, c.district_body_id, 'ADMISSION_RESOLUTION',"
                    + " c.admission_resolution_not_later_on" + open
                    + " AND c.status IN ('ZO_RECOMMENDED', 'ZK_VOTING')"
                    + " AND c.admission_resolution_not_later_on <= :resolutionLimit"
                    + " AND NOT EXISTS (SELECT 1 FROM recruitment_decisions d WHERE d.case_id = c.id"
                    + " AND d.decision_kind = 'ZO_ADMISSION_RESOLUTION')"
                    + " ORDER BY 2, 3, 4, 1")
            .param("today", today)
            .param("windowLimit", windowLimit)
            .param("resolutionLimit", resolutionLimit)
            .query((rs, row) -> new Due(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getObject(4, LocalDate.class)))
            .list();
    }

    private String zoBody(List<Due> items, LocalDate today) {
        StringBuilder text = new StringBuilder("""
            Dzień dobry,

            to automatyczne przypomnienie z Panelu MR o terminach w rekrutacji w Waszym okręgu (stan na %s).
            """.formatted(today));

        appendSection(text, items, today, "WINDOW_OPENS",
            "Dziś otwiera się okno oceny okresu wstępnego (najwcześniejszy dzień oceny):");
        appendSection(text, items, today, "WINDOW_CLOSING",
            "Zbliża się koniec okna oceny (76. dzień od deklaracji):");
        appendSection(text, items, today, "SECOND_ASSESSMENT",
            "Zbliża się termin drugiej oceny po przedłużeniu okresu wstępnego (31 dni):");
        appendSection(text, items, today, "ADMISSION_RESOLUTION",
            "Zbliża się termin uchwały ZO o przyjęciu (7 dni od rekomendacji):");

        text.append("""

            Terminy są wskazówką dla organu; Panel nie blokuje z ich powodu żadnych czynności.
            Wiadomość nie zawiera danych kandydatów - szczegóły zobaczysz w Panelu po zalogowaniu.
            """);

        return text.toString();
    }

    private void appendSection(StringBuilder text, List<Due> items, LocalDate today, String kind, String heading) {
        List<Due> section = items.stream().filter(due -> due.kind().equals(kind)).toList();

        if (section.isEmpty()) {
            return;
        }

        text.append('\n').append(heading).append('\n');

        for (Due due : section) {
            text.append("- sprawa nr ").append(due.caseId());

            if (!kind.equals("WINDOW_OPENS")) {
                text.append(", termin ").append(due.due()).append(" (").append(relative(today, due.due())).append(')');
            }

            text.append(": ").append(link(due.caseId())).append('\n');
        }
    }

    static String relative(LocalDate today, LocalDate due) {
        long days = ChronoUnit.DAYS.between(today, due);

        if (days == 0) {
            return "dziś";
        }

        if (days > 0) {
            return days == 1 ? "jutro" : "za " + days + " dni";
        }

        return "po terminie o " + (-days) + (days == -1 ? " dzień" : " dni");
    }

    private String link(long caseId) {
        String base = properties.getPanelBaseUrl().replaceAll("/+$", "");
        return base + "/rekrutacja/" + caseId;
    }

    // --- ZK: digest of candidates admitted to the initial period (art. 9 ust. 4 pkt 3) -----------------------

    /**
     * One digest per day to the ZK about every candidate whose initial period has started since the previous
     * digest (the {@code ZK_NOTIFIED_PROBATION_START} timeline events). Digest rather than one mail per candidate:
     * the statute only requires the information, and a day's batch is easier to follow.
     */
    public Report sendZkProbationDigest(Instant now) {
        Counters counters = new Counters();
        TBody zk = nationalBoard();

        if (zk == null) {
            counters.noAddress++;
            return counters.report();
        }

        Instant since = notifications
            .findFirstByKindAndBodyIdAndCoveredUntilNotNullOrderByCoveredUntilDesc(ZK_DIGEST, zk.getId())
            .map(row -> row.getCoveredUntil().toInstant())
            .orElse(now.minus(properties.getFirstDigestLookbackDays(), ChronoUnit.DAYS));

        List<Long> caseIds = jdbc.sql("SELECT e.case_id FROM recruitment_case_events e"
                + " WHERE e.event_type = 'ZK_NOTIFIED_PROBATION_START' AND e.created_at > :since AND e.created_at <= :now"
                + " ORDER BY e.created_at, e.id")
            .param("since", Timestamp.from(since))
            .param("now", Timestamp.from(now))
            .query(Long.class)
            .list();

        counters.bodiesWithDue = caseIds.isEmpty() ? 0 : 1;

        if (!caseIds.isEmpty()) {
            StringBuilder text = new StringBuilder("""
                Dzień dobry,

                od poprzedniego zestawienia do okresu wstępnego przyjęto %d kandydatów (statut, art. 9 ust. 4 pkt 3).
                Sprawy:
                """.formatted(caseIds.size()));
            caseIds.forEach(id -> text.append("- sprawa nr ").append(id).append(": ").append(link(id)).append('\n'));
            text.append("""

                Wiadomość nie zawiera danych kandydatów - szczegóły zobaczysz w Panelu po zalogowaniu.
                """);

            String day = now.atZone(ZoneOffset.UTC).toLocalDate().toString();
            deliver(counters, ZK_DIGEST, zk, day, now,
                "Panel MR: kandydaci przyjęci do okresu wstępnego (" + caseIds.size() + ")", text.toString(),
                "recruitment-zk-probation-digest", "body:" + zk.getId());
        }

        log.info("recruitment ZK probation digest since={} cases={} scheduled={} alreadySent={} notSent={}",
            since, caseIds.size(), counters.scheduled, counters.alreadySent, counters.notSent);

        return counters.report();
    }

    // --- ZK: immediate notification about a ZO recommendation -----------------------------------------------

    /**
     * Runs synchronously in the transaction that records the recommendation: the log row and the mail
     * hand-over commit or roll back together with it, and a failure here never fails the workflow step.
     */
    @EventListener
    void onZoRecommendation(ZoRecommendationRecorded event) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            TBody zk = nationalBoard();

            if (zk == null) {
                log.warn("recruitment ZK recommendation notice for case {}: no national board", event.caseId());
                return;
            }

            String text = """
                Dzień dobry,

                ZO przekazał rekomendację w sprawie nr %d. Sprawa czeka na głosowanie ZK: %s

                Wiadomość nie zawiera danych kandydata - szczegóły zobaczysz w Panelu po zalogowaniu.
                """.formatted(event.caseId(), link(event.caseId()));

            deliver(new Counters(), ZK_RECOMMENDATION, zk, "case:" + event.caseId(), null,
                "Panel MR: rekomendacja ZO w sprawie nr " + event.caseId(), text,
                "recruitment-zk-recommendation", "recruitment-case:" + event.caseId(), false);
        } catch (RuntimeException e) {
            log.warn("recruitment ZK recommendation notice for case {} failed: {}", event.caseId(),
                e.getClass().getSimpleName());
        }
    }

    // --- shared ----------------------------------------------------------------------------------------------

    private @Nullable TBody nationalBoard() {
        return bodies.findAllByKind(BodyKind.NATIONAL_BOARD).stream().findFirst().orElse(null);
    }

    private void deliver(Counters counters, String kind, TBody body, String referenceKey,
                         @Nullable Instant coveredUntil, String subject, String text, String purpose,
                         String mailReference) {
        deliver(counters, kind, body, referenceKey, coveredUntil, subject, text, purpose, mailReference, true);
    }

    private void deliver(Counters counters, String kind, TBody body, String referenceKey,
                         @Nullable Instant coveredUntil, String subject, String text, String purpose,
                         String mailReference, boolean ownTransaction) {
        String address = body.getEmail();

        if (address == null || address.isBlank()) {
            counters.noAddress++;
            log.warn("recruitment notification kind={} body={}: the board has no e-mail address", kind, body.getId());
            return;
        }

        if (notifications.existsByKindAndBodyIdAndReferenceKey(kind, body.getId(), referenceKey)) {
            counters.alreadySent++;
            return;
        }

        Runnable work = () -> {
            MailDispatch dispatch = mail.sendAfterCommit(new MailMessage(address.strip(), subject, text, purpose,
                mailReference));

            if (dispatch != MailDispatch.SCHEDULED) {
                counters.notSent++;
                log.info("recruitment notification kind={} body={} not sent: {}", kind, body.getId(), dispatch);
                return;
            }

            TRecruitmentNotification row = new TRecruitmentNotification();
            row.setKind(kind);
            row.setBodyId(body.getId());
            row.setReferenceKey(referenceKey);
            row.setCoveredUntil(coveredUntil == null ? null : coveredUntil.atZone(ZoneOffset.UTC));
            notifications.saveAndFlush(row);
            counters.scheduled++;
        };

        if (ownTransaction) {
            tx.executeWithoutResult(status -> work.run());
        } else {
            work.run();
        }
    }

    private static final class Counters {
        int bodiesWithDue;
        int scheduled;
        int noAddress;
        int alreadySent;
        int notSent;

        Report report() {
            return new Report(bodiesWithDue, scheduled, noAddress, alreadySent, notSent);
        }
    }
}

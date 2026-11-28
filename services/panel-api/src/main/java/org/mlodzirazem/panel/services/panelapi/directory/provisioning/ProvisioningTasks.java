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

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.springframework.context.ApplicationEventPublisher;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningOperation;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskState;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL of the outbox ({@code directory_provisioning_tasks}). It stores references only: no names, addresses, user
 * principal names, object ids of Graph, Graph messages or passes.
 */
@Slf4j
@Component
class ProvisioningTasks {
    /** Serialises the claim across replicas (the hourly limit is counted under it). */
    private static final long CLAIM_LOCK = 0x50524f56L;
    private static final String DESTRUCTIVE = "('DISABLE_ACCOUNT', 'DELETE_ACCOUNT', 'REMOVE_GROUP_MEMBER')";

    /**
     * @param readySince the creation, or the last retry / release by a person
     * @param released   released from HELD by a person: skips the age and hourly-limit holds
     */
    record Task(long id, ProvisioningOperation operation, AccountSubject subject, @Nullable Long relatedCaseId,
                @Nullable Long mappingId, @Nullable Long occurrenceId, ProvisioningTaskState state, int attempts,
                Instant nextAttemptAt, @Nullable ProvisioningErrorCode errorCode, Instant createdAt,
                @Nullable Instant finishedAt, Instant readySince, boolean released) {
        ProvisioningTaskView view() {
            return new ProvisioningTaskView(id, operation, subject, mappingId, occurrenceId, state, attempts, nextAttemptAt,
                errorCode, createdAt, finishedAt);
        }
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;

    ProvisioningTasks(JdbcClient jdbc, PlatformTransactionManager transactionManager, AuditLog auditLog,
                      ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.auditLog = auditLog;
        this.events = events;
    }

    /**
     * Idempotency key: {@code operation:subjectType:subjectId[:mappingId][:occurrenceId]}.
     */
    static String key(ProvisioningRequest request) {
        AccountSubject subject = request.subject();
        StringBuilder key = new StringBuilder(request.operation().name()).append(':').append(kind(subject)).append(':').append(subject.id());

        if (request.mappingIdOrNull() != null) {
            key.append(':').append(request.mappingIdOrNull());
        }

        if (request.occurrenceIdOrNull() != null) {
            key.append(':').append(request.occurrenceIdOrNull());
        }

        return key.toString();
    }

    /**
     * Inserts the task in the caller's transaction; empty when the key exists already.
     */
    Optional<Long> insert(ProvisioningRequest request, @Nullable Long relatedCaseId, @Nullable Long actorMemberId) {
        return jdbc.sql("INSERT INTO directory_provisioning_tasks (operation, subject_kind, subject_id, related_case_id, mapping_id, "
                + "occurrence_id, idempotency_key, created_by_member_id) "
                + "VALUES (:operation, :kind, :subject, :related, :mapping, :occurrence, :key, :actor) "
                + "ON CONFLICT (idempotency_key) DO NOTHING RETURNING id")
            .param("operation", request.operation().name())
            .param("kind", kind(request.subject()))
            .param("subject", request.subject().id())
            .param("related", relatedCaseId)
            .param("mapping", request.mappingIdOrNull())
            .param("occurrence", request.occurrenceIdOrNull())
            .param("key", key(request))
            .param("actor", actorMemberId)
            .query(Long.class).optional();
    }

    Optional<Task> find(long id) {
        return jdbc.sql("SELECT * FROM directory_provisioning_tasks WHERE id = :id").param("id", id)
            .query(ProvisioningTasks::map).optional();
    }

    Task byKey(String key) {
        return jdbc.sql("SELECT * FROM directory_provisioning_tasks WHERE idempotency_key = :key").param("key", key)
            .query(ProvisioningTasks::map).single();
    }

    /**
     * An opposite task of the same subject (and mapping) that has not started yet gives way to the newer intent.
     */
    int supersede(ProvisioningOperation opposite, AccountSubject subject, @Nullable Long mappingId, long newTaskId, Instant now) {
        return jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'SUPERSEDED', finished_at = :now, superseded_by = :new "
                + "WHERE subject_kind = :kind AND subject_id = :subject AND operation = :operation AND id < :new "
                + "AND state IN ('PENDING', 'HELD') AND mapping_id IS NOT DISTINCT FROM :mapping")
            .param("now", ts(now)).param("new", newTaskId).param("kind", kind(subject)).param("subject", subject.id())
            .param("operation", opposite.name()).param("mapping", mappingId).update();
    }

    // ------------------------------------------------------------------------------------------------- the claim

    /**
     * Claims due tasks for one worker: {@code FOR UPDATE SKIP LOCKED}, one task per subject at a time and in order, the
     * lease written and committed before any Graph call. Tasks that must not run now (older than {@code maxJobAge},
     * destructive while that is switched off, over the hourly limit) go to HELD with the reason as error code.
     */
    List<Task> claim(String owner, Instant now, Duration lease, int batchSize, ProvisioningProperties properties) {
        List<Task> claimed = transaction.execute(status -> {
            jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", CLAIM_LOCK).query((rs, i) -> 1).list();

            List<Task> due = jdbc.sql("SELECT t.* FROM directory_provisioning_tasks t "
                    + "WHERE ((t.state = 'PENDING' AND t.next_attempt_at <= :now) OR (t.state = 'RUNNING' AND t.lease_until < :now)) "
                    + "AND NOT EXISTS (SELECT 1 FROM directory_provisioning_tasks o WHERE o.subject_kind = t.subject_kind "
                    + "AND o.subject_id = t.subject_id AND o.id < t.id AND o.state IN ('PENDING', 'RUNNING', 'HELD')) "
                    + "ORDER BY t.id LIMIT :limit FOR UPDATE OF t SKIP LOCKED")
                .param("now", ts(now)).param("limit", batchSize).query(ProvisioningTasks::map).list();

            long usedThisHour = jdbc.sql("SELECT count(*) FROM directory_provisioning_tasks WHERE operation IN " + DESTRUCTIVE
                    + " AND state IN ('RUNNING', 'DONE', 'FAILED') AND started_at >= :since")
                .param("since", ts(now.minus(Duration.ofHours(1)))).query(Long.class).single();

            List<Task> running = new ArrayList<>();

            for (Task task : due) {
                ProvisioningErrorCode hold = null;

                if (task.state() == ProvisioningTaskState.RUNNING && task.attempts() >= properties.maxAttempts()) {
                    // D5: a task whose worker died (or was killed) on every allowed attempt is not claimed again
                    jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'FAILED', lease_owner = NULL, lease_until = NULL, "
                            + "finished_at = :now, last_error_code = 'ATTEMPTS_EXHAUSTED' WHERE id = :id")
                        .param("now", ts(now)).param("id", task.id()).update();
                    log.warn("provisioning task {} ({}) failed: ATTEMPTS_EXHAUSTED after an expired lease", task.id(), task.operation());
                    // the same trace as any other outcome: audit row and event, in this transaction
                    ProvisioningOutcomes.announce(auditLog, events, task, false, ProvisioningErrorCode.ATTEMPTS_EXHAUSTED, false, null);
                    continue;
                }

                if (!task.released() && task.readySince().plus(properties.maxJobAge()).isBefore(now)) {
                    hold = ProvisioningErrorCode.HOLD_STALE;
                } else if (task.operation().destructive()) {
                    if (!properties.destructiveEnabled()) {
                        hold = ProvisioningErrorCode.HOLD_DESTRUCTIVE_DISABLED;
                    } else if (!task.released() && usedThisHour >= properties.maxDestructivePerHour()) {
                        hold = ProvisioningErrorCode.HOLD_DESTRUCTIVE_LIMIT;
                    }
                }

                if (hold != null) {
                    jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'HELD', lease_owner = NULL, lease_until = NULL, "
                            + "last_error_code = :code WHERE id = :id")
                        .param("code", hold.name()).param("id", task.id()).update();
                    // W2: a held task blocks the later tasks of its subject, so it must be visible; only the id and the code
                    log.warn("provisioning task {} held: {}", task.id(), hold);
                    continue;
                }

                jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'RUNNING', attempts = attempts + 1, lease_owner = :owner, "
                        + "lease_until = :until, started_at = :now WHERE id = :id")
                    .param("owner", owner).param("until", ts(now.plus(lease))).param("now", ts(now)).param("id", task.id()).update();

                if (task.operation().destructive()) {
                    usedThisHour++;
                }

                running.add(find(task.id()).orElseThrow());
            }

            return running;
        });

        return claimed == null ? List.of() : claimed;
    }

    // ------------------------------------------------------------------------------------------------- outcomes

    /** DONE or FAILED; false when the lease was lost in the meantime. */
    boolean finish(long id, String owner, ProvisioningTaskState state, @Nullable ProvisioningErrorCode code, Instant now) {
        return jdbc.sql("UPDATE directory_provisioning_tasks SET state = :state, lease_owner = NULL, lease_until = NULL, finished_at = :now, "
                + "last_error_code = :code WHERE id = :id AND lease_owner = :owner AND state = 'RUNNING'")
            .param("state", state.name()).param("now", ts(now)).param("code", code == null ? null : code.name())
            .param("id", id).param("owner", owner).update() == 1;
    }

    /**
     * W1: renews the lease of a task this worker still holds; false when the lease was taken over (or the task is no longer
     * RUNNING), in which case the task must not be executed.
     */
    boolean extendLease(long id, String owner, Instant until) {
        return jdbc.sql("UPDATE directory_provisioning_tasks SET lease_until = :until WHERE id = :id AND lease_owner = :owner "
                + "AND state = 'RUNNING'")
            .param("until", ts(until)).param("id", id).param("owner", owner).update() == 1;
    }

    /** Back to PENDING for a later attempt; {@code countAttempt=false} gives the attempt back (a deferral is not a failure). */
    boolean reschedule(long id, String owner, Instant next, @Nullable ProvisioningErrorCode code, boolean countAttempt) {
        return jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'PENDING', lease_owner = NULL, lease_until = NULL, "
                + "next_attempt_at = :next, last_error_code = :code, attempts = CASE WHEN :count THEN attempts ELSE GREATEST(attempts - 1, 0) END "
                + "WHERE id = :id AND lease_owner = :owner AND state = 'RUNNING'")
            .param("next", ts(next)).param("code", code == null ? null : code.name()).param("count", countAttempt)
            .param("id", id).param("owner", owner).update() == 1;
    }

    /** FAILED to PENDING, attempts reset; false when the task is not FAILED. */
    boolean retry(long id, Instant now) {
        return jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'PENDING', attempts = 0, next_attempt_at = :now, ready_since = :now, "
                + "released = FALSE, finished_at = NULL, last_error_code = NULL WHERE id = :id AND state = 'FAILED'")
            .param("now", ts(now)).param("id", id).update() == 1;
    }

    /** HELD to PENDING, flagged as released; false when the task is not HELD. */
    boolean release(long id, long actorMemberId, Instant now) {
        return jdbc.sql("UPDATE directory_provisioning_tasks SET state = 'PENDING', next_attempt_at = :now, ready_since = :now, released = TRUE, "
                + "released_by_member_id = :actor, last_error_code = NULL WHERE id = :id AND state = 'HELD'")
            .param("now", ts(now)).param("actor", actorMemberId).param("id", id).update() == 1;
    }

    // ------------------------------------------------------------------------------------------------- status

    /** The oldest open account task (anything but a group operation) of the subject, if there is one. */
    Optional<Task> openAccountTask(AccountSubject subject) {
        return jdbc.sql("SELECT * FROM directory_provisioning_tasks WHERE subject_kind = :kind AND subject_id = :id "
                + "AND operation NOT IN ('ADD_GROUP_MEMBER', 'REMOVE_GROUP_MEMBER') AND state IN ('PENDING', 'RUNNING', 'HELD') "
                + "ORDER BY id LIMIT 1")
            .param("kind", kind(subject)).param("id", subject.id()).query(ProvisioningTasks::map).optional();
    }

    /** The latest account task of the subject that reached DONE or FAILED. */
    Optional<Task> latestFinishedAccountTask(AccountSubject subject) {
        return jdbc.sql("SELECT * FROM directory_provisioning_tasks WHERE subject_kind = :kind AND subject_id = :id "
                + "AND operation NOT IN ('ADD_GROUP_MEMBER', 'REMOVE_GROUP_MEMBER') AND state IN ('DONE', 'FAILED') "
                + "ORDER BY finished_at DESC, id DESC LIMIT 1")
            .param("kind", kind(subject)).param("id", subject.id()).query(ProvisioningTasks::map).optional();
    }

    static String kind(AccountSubject subject) {
        return subject instanceof AccountSubject.Member ? "MEMBER" : "CANDIDATE";
    }

    static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Task map(ResultSet rs, int row) throws SQLException {
        AccountSubject subject = "MEMBER".equals(rs.getString("subject_kind"))
            ? new AccountSubject.Member(rs.getLong("subject_id"))
            : new AccountSubject.Candidate(rs.getLong("subject_id"));
        String error = rs.getString("last_error_code");
        Timestamp finished = rs.getTimestamp("finished_at");

        return new Task(
            rs.getLong("id"),
            ProvisioningOperation.valueOf(rs.getString("operation")),
            subject,
            (Long) rs.getObject("related_case_id"),
            (Long) rs.getObject("mapping_id"),
            (Long) rs.getObject("occurrence_id"),
            ProvisioningTaskState.valueOf(rs.getString("state")),
            rs.getInt("attempts"),
            rs.getTimestamp("next_attempt_at").toInstant(),
            error == null ? null : ProvisioningErrorCode.valueOf(error),
            rs.getTimestamp("created_at").toInstant(),
            finished == null ? null : finished.toInstant(),
            rs.getTimestamp("ready_since").toInstant(),
            rs.getBoolean("released"));
    }
}

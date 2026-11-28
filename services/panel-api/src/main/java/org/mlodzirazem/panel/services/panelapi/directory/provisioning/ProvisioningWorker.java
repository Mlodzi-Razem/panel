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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProvisioningFinished;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningOperation;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskState;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.SubjectKind;
import org.mlodzirazem.panel.services.panelapi.directory.provisioning.ProvisioningTasks.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the outbox. It exists only when {@code panel.directory.provisioning.enabled=true}, and every round asks
 * {@link ProvisioningGate} again, so a token that gains a forbidden permission stops it at the next poll.
 *
 * <p>A round claims due tasks under a database lock ({@code FOR UPDATE SKIP LOCKED}, one task per subject, in order),
 * commits the lease, and only then calls Graph outside any transaction; the outcome is written conditionally on the lease
 * ({@code WHERE lease_owner = ?}), together with the audit row and the {@link AccountProvisioningFinished} event. Rules of
 * a failure (SEC-33): 429 waits for {@code Retry-After}; 5xx and network errors back off exponentially with jitter; a 403 is
 * retried only while the account is younger than {@code forbidden-grace} (the administrative-unit rule is not in force
 * yet); every other code is final, and so is running out of attempts ({@code ATTEMPTS_EXHAUSTED}). A failed task never
 * blocks other subjects.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "panel.directory.provisioning", name = "enabled", havingValue = "true")
class ProvisioningWorker {
    /** Longer than the slowest task (a conversion makes about ten calls of at most twenty seconds). */
    static final Duration LEASE = Duration.ofMinutes(5);

    private final ProvisioningGate gate;
    private final ProvisioningTasks tasks;
    private final ProvisioningExecutor executor;
    private final AccountRegistry registry;
    private final ProvisioningProperties properties;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();
    /** D6: set after a 429 of this replica; until then the replica claims nothing and runs nothing. */
    private volatile @Nullable Instant throttledUntil;

    ProvisioningWorker(ProvisioningGate gate, ProvisioningTasks tasks, ProvisioningExecutor executor, AccountRegistry registry,
                       ProvisioningProperties properties, AuditLog auditLog, ApplicationEventPublisher events,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.gate = gate;
        this.tasks = tasks;
        this.executor = executor;
        this.registry = registry;
        this.properties = properties;
        this.auditLog = auditLog;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${panel.directory.provisioning.poll-interval:15s}",
        fixedDelayString = "${panel.directory.provisioning.poll-interval:15s}")
    void poll() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            // only the type: a database or Graph message may quote personal data
            log.error("provisioning poll failed: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * One round: claims and executes a batch. Returns how many tasks it ran. Nothing is claimed while a Graph throttling
     * pause of this replica lasts.
     */
    int runOnce() {
        if (!gate.enabled() || paused()) {
            return 0;
        }

        return runClaimed(tasks.claim(owner, clock.instant(), LEASE, properties.batchSize(), properties));
    }

    /**
     * Runs a claimed batch. A task whose lease was taken over in the meantime is skipped (W1); after a 429 the rest of the
     * batch goes back to PENDING without an attempt (D6); a failure of one task never stops the batch (D11).
     */
    int runClaimed(List<Task> claimed) {
        for (Task task : claimed) {
            if (paused()) {
                tasks.reschedule(task.id(), owner, throttledUntil, null, false);
                continue;
            }

            try {
                process(task);
            } catch (RuntimeException e) {
                log.error("provisioning task {} could not be processed: {}", task.id(), e.getClass().getSimpleName());
            }
        }

        return claimed.size();
    }

    private boolean paused() {
        return throttledUntil != null && clock.instant().isBefore(throttledUntil);
    }

    private void process(Task task) {
        // W1: the lease of a batch is short compared with a slow batch, so every task renews its own before the first call
        if (!tasks.extendLease(task.id(), owner, clock.instant().plus(LEASE))) {
            log.warn("provisioning task {} lost its lease before it started; another worker runs it", task.id());
            return;
        }

        try {
            ProvisioningExecutor.Result result = executor.run(task);

            if (result.deferUntil() != null) {
                tasks.reschedule(task.id(), owner, result.deferUntil(), null, false);
                return;
            }

            complete(task, null, result.alreadyDone(), result.accountId());
        } catch (ProvisioningException e) {
            fail(task, e);
        } catch (RuntimeException e) {
            log.error("provisioning task {} ({}) failed unexpectedly: {}", task.id(), task.operation(), e.getClass().getSimpleName());
            finishQuietly(task, ProvisioningErrorCode.INTERNAL);
        }
    }

    private void fail(Task task, ProvisioningException e) {
        ProvisioningErrorCode code = e.code();

        if (!retryable(task, code)) {
            log.warn("provisioning task {} ({}) failed: {}", task.id(), task.operation(), code);
            finishQuietly(task, code);
            return;
        }

        if (task.attempts() >= properties.maxAttempts()) {
            log.warn("provisioning task {} ({}) failed: ATTEMPTS_EXHAUSTED after {}", task.id(), task.operation(), code);
            finishQuietly(task, ProvisioningErrorCode.ATTEMPTS_EXHAUSTED);
            return;
        }

        Instant next = clock.instant().plus(delay(e, task.attempts()));
        log.info("provisioning task {} ({}) will be retried after {} (attempt {})", task.id(), task.operation(), code, task.attempts());

        if (code == ProvisioningErrorCode.GRAPH_THROTTLED && (throttledUntil == null || next.isAfter(throttledUntil))) {
            throttledUntil = next;
        }

        tasks.reschedule(task.id(), owner, next, code, true);
    }

    /**
     * Transient codes only (SEC-33): throttling and unavailability, and a 403 while the account is still inside the
     * administrative-unit rule's replication delay.
     */
    private boolean retryable(Task task, ProvisioningErrorCode code) {
        return switch (code) {
            case GRAPH_THROTTLED, GRAPH_UNAVAILABLE -> true;
            case GRAPH_FORBIDDEN -> accountOf(task) != null
                && accountOf(task).createdAt().plus(properties.forbiddenGrace()).isAfter(clock.instant());
            default -> false;
        };
    }

    /**
     * {@code Retry-After} when Graph gave one, otherwise exponential backoff with +-20 % jitter, never above the ceiling.
     */
    Duration delay(ProvisioningException e, int attempts) {
        Duration retryAfter = e.retryAfter();

        if (retryAfter != null) {
            return retryAfter.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : retryAfter;
        }

        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration backoff = properties.backoffInitial().multipliedBy(factor);

        if (backoff.compareTo(properties.backoffMax()) > 0) {
            backoff = properties.backoffMax();
        }

        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        Duration jittered = Duration.ofMillis((long) (backoff.toMillis() * jitter));

        return jittered.compareTo(properties.backoffMax()) > 0 ? properties.backoffMax() : jittered;
    }

    private AccountRegistry.@Nullable Account accountOf(Task task) {
        return registry.find(task.subject())
            .or(() -> task.relatedCaseId() == null ? java.util.Optional.empty()
                : registry.find(new AccountSubject.Candidate(task.relatedCaseId())))
            .orElse(null);
    }

    private void finishQuietly(Task task, ProvisioningErrorCode code) {
        try {
            complete(task, code, false, null);
        } catch (RuntimeException e) {
            log.error("provisioning task {} could not be finished: {}", task.id(), e.getClass().getSimpleName());
        }
    }

    /**
     * Records DONE or FAILED conditionally on the lease, with the audit row and the event, in one transaction.
     */
    private void complete(Task task, @Nullable ProvisioningErrorCode failure, boolean alreadyDone, @Nullable Long knownAccountId) {
        boolean succeeded = failure == null;
        Long accountId = knownAccountId != null ? knownAccountId : succeeded ? null : accountOf(task) == null ? null : accountOf(task).id();

        transaction.executeWithoutResult(status -> {
            boolean written = tasks.finish(task.id(), owner, succeeded ? ProvisioningTaskState.DONE : ProvisioningTaskState.FAILED,
                failure, clock.instant());

            if (!written) {
                log.warn("provisioning task {} lost its lease; the outcome is not recorded by this worker", task.id());
                return;
            }

            ProvisioningOutcomes.announce(auditLog, events, task, succeeded, failure, alreadyDone, accountId);
        });
    }
}

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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProvisioningFinished;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningOperation;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.SubjectKind;
import org.mlodzirazem.panel.services.panelapi.directory.provisioning.ProvisioningTasks.Task;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The trace every finished task leaves: one audit row (names and ids only) and the {@link AccountProvisioningFinished}
 * event. Called inside the transaction that records the outcome, from the worker and from the claim (expired attempts).
 */
final class ProvisioningOutcomes {
    private ProvisioningOutcomes() {
    }

    static void announce(AuditLog auditLog, ApplicationEventPublisher events, Task task, boolean succeeded,
                         @Nullable ProvisioningErrorCode failure, boolean alreadyDone, @Nullable Long accountId) {
        String outcome = succeeded ? (alreadyDone ? "ALREADY_DONE" : "SUCCESS") : failure.name();
        auditLog.record("directory :: " + auditName(task.operation()),
            AuditPayload.of("taskId", task.id()).withId("subjectId", task.subject().id()).withOutcome(outcome));

        events.publishEvent(new AccountProvisioningFinished(task.id(), task.operation(), SubjectKind.of(task.subject()),
            task.subject().id(), succeeded, failure, accountId, task.mappingId(), task.occurrenceId()));
    }

    static String auditName(ProvisioningOperation operation) {
        return switch (operation) {
            case CREATE_TRIAL_ACCOUNT, CREATE_MEMBER_ACCOUNT -> "account-create";
            case CONVERT_TRIAL_TO_MEMBER -> "account-convert";
            case ADOPT_MEMBER_ACCOUNT -> "account-adopt";
            case DISABLE_ACCOUNT -> "account-disable";
            case ENABLE_ACCOUNT -> "account-enable";
            case DELETE_ACCOUNT -> "account-delete";
            case ADD_GROUP_MEMBER -> "group-add";
            case REMOVE_GROUP_MEMBER -> "group-remove";
        };
    }
}

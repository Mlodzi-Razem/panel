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
package org.mlodzirazem.panel.services.panelapi.recruitment.eform.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Writes the DECLARATION_BLOCKED timeline event of a refused declaration. It is a separate bean so that
 * the write runs in its own transaction (REQUIRES_NEW applies only across a bean boundary), and the
 * caller invokes it only after the refusing transaction rolled back. The payload carries codes only.
 */
@Service
@RequiredArgsConstructor
class DeclarationBlockRecorder {
    /** A candidate retrying the form must not flood the timeline with the same refusal. */
    static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    private final TRecruitmentCaseRepository cases;
    private final TRecruitmentCaseEventRepository caseEvents;
    private final TMemberRepository members;
    private final AuditLog auditLog;
    private final JsonMapper jsonMapper;

    /** The block rule that refused the declaration; the name is the code stored in the payload. */
    enum BlockReason {
        EXISTING_MEMBER("existing-member"),
        ADMISSION_BAN("admission-ban"),
        DUPLICATE_OPEN_CASE("duplicate-open-case");

        /** Key of the server log line, kept as it was before the event existed. */
        final String logKey;

        BlockReason(String logKey) {
            this.logKey = logKey;
        }
    }

    /** Where the refused declaration came from. */
    enum BlockChannel {
        EFORM,
        CORRECTION,
        SCAN
    }

    /**
     * Records the event unless the newest DECLARATION_BLOCKED event of the case has the same reason and
     * channel and is younger than {@link #DEDUP_WINDOW}.
     *
     * @return true when the event was written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(long caseId, @Nullable Long actorMemberId, BlockReason reason, BlockChannel channel) {
        if (isRepeat(caseId, reason, channel)) {
            return false;
        }

        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("reason", reason.name());
        payload.put("channel", channel.name());

        TRecruitmentCaseEvent event = new TRecruitmentCaseEvent();
        event.setRecruitmentCase(cases.getReferenceById(caseId));
        event.setEventType(RecruitmentCaseEventType.DECLARATION_BLOCKED);
        event.setPayload(jsonMapper.writeValueAsString(payload));

        if (actorMemberId != null) {
            event.setCreatedBy(members.getReferenceById(actorMemberId));
        }

        caseEvents.saveAndFlush(event);

        // the audit trail carries the codes and ids only
        AuditPayload audit = AuditPayload.of("caseId", caseId);

        if (actorMemberId != null) {
            audit = audit.withId("actorId", actorMemberId);
        }

        auditLog.record("recruitment :: declaration-blocked", audit.withOutcome(reason.name()));
        return true;
    }

    private boolean isRepeat(long caseId, BlockReason reason, BlockChannel channel) {
        ZonedDateTime since = ZonedDateTime.now(ZoneOffset.UTC).minus(DEDUP_WINDOW);

        return caseEvents.findFirstByRecruitmentCaseIdAndEventTypeOrderByCreatedAtDescIdDesc(
                caseId, RecruitmentCaseEventType.DECLARATION_BLOCKED)
            .filter(newest -> newest.getCreatedAt() != null && newest.getCreatedAt().isAfter(since))
            .map(newest -> sameCodes(newest.getPayload(), reason, channel))
            .orElse(false);
    }

    private boolean sameCodes(@Nullable String payload, BlockReason reason, BlockChannel channel) {
        if (payload == null) {
            return false;
        }

        var node = jsonMapper.readTree(payload);
        return reason.name().equals(node.path("reason").asString())
            && channel.name().equals(node.path("channel").asString());
    }
}

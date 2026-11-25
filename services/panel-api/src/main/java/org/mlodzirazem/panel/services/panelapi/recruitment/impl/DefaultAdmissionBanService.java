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

package org.mlodzirazem.panel.services.panelapi.recruitment.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBan;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBanRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionBanCheck;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionBanRule;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionBanService;
import org.mlodzirazem.panel.services.panelapi.recruitment.NewAdmissionBan;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultAdmissionBanService implements AdmissionBanService {
    /** art. 9 ust. 5: "na okres maksymalnie 365 dni". */
    static final int MAX_DAYS = 365;

    private final TAdmissionBanRepository bans;
    private final TRecruitmentCaseRepository cases;
    private final TMemberRepository members;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdmissionBanCheck check(NewAdmissionBan ban) {
        List<AdmissionBanRule> violations = new ArrayList<>();

        String identifier = null;

        if (isBlank(ban.personalId())) {
            violations.add(AdmissionBanRule.BAN_PERSONAL_ID_REQUIRED);
        } else {
            try {
                identifier = RecruitmentEformRules.normalizePersonalId(ban.personalId(), null);
            } catch (RuleViolationException invalid) {
                violations.add(AdmissionBanRule.PERSONAL_ID_INVALID);
            }
        }

        if (isBlank(ban.reason())) {
            violations.add(AdmissionBanRule.BAN_REASON_REQUIRED);
        }

        if (isBlank(ban.decisionSignature())) {
            violations.add(AdmissionBanRule.BAN_DECISION_SIGNATURE_REQUIRED);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveFrom = ban.effectiveFrom() == null ? today : ban.effectiveFrom();
        LocalDate effectiveUntil = ban.effectiveUntil();

        if (effectiveUntil == null) {
            violations.add(AdmissionBanRule.BAN_PERIOD_REQUIRED);
        }

        if (effectiveUntil != null) {
            if (!effectiveUntil.isAfter(effectiveFrom)) {
                violations.add(AdmissionBanRule.BAN_PERIOD_INVALID);
            } else {
                long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveUntil);

                if (days > MAX_DAYS) {
                    // the period of the uchwała itself (art. 9 ust. 5: at most 365 days)
                    violations.add(AdmissionBanRule.BAN_PERIOD_OVER_365_DAYS);
                } else if (effectiveUntil.isAfter(today.plusDays(MAX_DAYS))) {
                    // the recording closes the uchwała cycle: a ban entered today may not run past
                    // today + 365 days, so a future start date shortens the remaining window
                    violations.add(AdmissionBanRule.BAN_EXPIRY_OVER_365_DAYS);
                }

                if (identifier != null && overlapsRecorded(identifier, effectiveFrom, effectiveUntil)) {
                    violations.add(AdmissionBanRule.BAN_OVERLAPS_EXISTING);
                }
            }
        }

        return new AdmissionBanCheck(violations);
    }

    @Override
    @Transactional
    public TAdmissionBan create(NewAdmissionBan ban, @Nullable Long actorMemberId) {
        AdmissionBanCheck check = check(ban);

        // history is untouchable (§0.6): the conflict never closes or shifts the recorded ban
        if (check.violations().contains(AdmissionBanRule.BAN_OVERLAPS_EXISTING)) {
            throw new ConflictException("BAN_OVERLAPS_EXISTING",
                "A recorded admission ban of this person already covers the requested period",
                List.of("formalIdentifier", "effectiveFrom", "effectiveUntil"));
        }

        if (!check.allowed()) {
            throw new RuleViolationException(check.violations().getFirst().name(),
                "The admission ban does not satisfy the statutory rules of art. 9 ust. 5");
        }

        String identifier = RecruitmentEformRules.normalizePersonalId(ban.personalId(), null);
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveFrom = ban.effectiveFrom() == null ? today : ban.effectiveFrom();

        TAdmissionBan admissionBan = new TAdmissionBan();
        admissionBan.setFormalIdentifier(identifier);
        admissionBan.setDecisionSignature(ban.decisionSignature().strip());
        admissionBan.setReason(ban.reason().strip());
        admissionBan.setEffectiveFrom(effectiveFrom);
        admissionBan.setEffectiveUntil(ban.effectiveUntil());
        admissionBan.setRecruitmentCase(anchorCase(ban.caseId(), identifier));

        if (actorMemberId != null) {
            members.findById(actorMemberId).ifPresent(admissionBan::setCreatedBy);
        }

        bans.saveAndFlush(admissionBan);

        // field names only — the identifier and the reason never reach the audit trail (§0.5)
        auditLog.record("recruitment :: ban-create",
            AuditPayload.of("banId", admissionBan.getId())
                .withFields(List.of("formalIdentifier", "reason", "decisionSignature",
                    "effectiveFrom", "effectiveUntil"))
                .withOutcome("CREATED"));

        return admissionBan;
    }

    private boolean overlapsRecorded(String identifier, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        // the e-declaration window check reads the same rows: inclusive end dates, so touching counts
        return bans.findAllByFormalIdentifier(identifier)
            .stream()
            .anyMatch(ban -> !ban.getEffectiveFrom().isAfter(effectiveUntil)
                && !ban.getEffectiveUntil().isBefore(effectiveFrom));
    }

    private @Nullable TRecruitmentCase anchorCase(@Nullable Long caseId, String identifier) {
        if (caseId == null) {
            return null;
        }

        TRecruitmentCase candidateCase = cases.findById(caseId)
            .orElseThrow(() -> new NotFoundException("Case", caseId));

        // art. 9 ust. 5 bans the person, not the case — the two anchors must name the same person
        if (!identifier.equals(candidateCase.getCandidateFormalIdentifier())) {
            throw new ConflictException("BAN_CASE_IDENTIFIER_MISMATCH",
                "The anchor case belongs to another person than the banned identifier",
                List.of("caseId", "formalIdentifier"));
        }

        return candidateCase;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}

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

package org.mlodzirazem.panel.services.panelapi.fees.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.fees.ConfirmedIban;
import org.mlodzirazem.panel.services.panelapi.bankimport.Ibans;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.SenderIbans;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TSenderIban;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TSenderIbanRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class DefaultSenderIbans implements SenderIbans {
    private final TSenderIbanRepository ibans;
    private final TMemberRepository members;
    private final AuditLog auditLog;

    @Override
    @Transactional
    public ConfirmedIban confirm(long actorMemberId, String iban, long memberId) {
        OwnFeesGuard.reject(actorMemberId, memberId);
        String normalised = normalise(iban);
        TMember member = members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));

        Optional<TSenderIban> existing = ibans.findByIban(normalised);

        if (existing.isPresent()) {
            if (existing.get().getMember().getId() != memberId) {
                throw new ConflictException("IBAN_ALREADY_CONFIRMED", "The IBAN is confirmed for another member");
            }

            return view(existing.get());
        }

        TSenderIban entity = new TSenderIban();
        entity.setIban(normalised);
        entity.setMember(member);
        entity.setCreatedBy(members.getReferenceById(actorMemberId));
        ibans.saveAndFlush(entity);

        auditLog.record("fees :: iban-confirm", AuditPayload.of("memberId", memberId)
                                                            .withId("ibanId", entity.getId())
                                                            .withFields(List.of("iban")));

        return view(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> memberOf(String iban) {
        if (!Ibans.isValid(iban)) {
            return Optional.empty();
        }

        return ibans.findByIban(normalise(iban)).map(i -> i.getMember().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfirmedIban> of(long memberId) {
        return ibans.findByMemberIdOrderByIbanAsc(memberId).stream().map(DefaultSenderIbans::view).toList();
    }

    @Override
    @Transactional
    public void forget(long actorMemberId, String iban) {
        TSenderIban entity = ibans.findByIban(normalise(iban))
                                  .orElseThrow(() -> new NotFoundException("Sender IBAN", "?"));

        auditLog.record("fees :: iban-forget", AuditPayload.of("memberId", entity.getMember().getId())
                                                           .withId("ibanId", entity.getId()));
        ibans.delete(entity);
    }

    private static String normalise(String raw) {
        return Ibans.tryNormalise(raw).orElseThrow(() -> new RuleViolationException("IBAN_INVALID", "Not a valid IBAN"));
    }

    private static ConfirmedIban view(TSenderIban i) {
        return new ConfirmedIban(i.getId(), i.getIban(), i.getMember().getId());
    }
}

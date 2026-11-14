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

package org.mlodzirazem.panel.services.panelapi.members.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfoResolver;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class DefaultCurrentMemberResolver implements CurrentMemberResolver {
    private final TMemberRepository members;
    private final UserAuthInfoResolver userAuthInfoResolver;
    private final AuditLog auditLog;

    @Override
    @Transactional
    public Optional<TMember> resolve(UserAuthInfo userAuthInfo) {
        Optional<TMember> bySub = members.findByDirectoryUserId(userAuthInfo.userId());

        if (bySub.isPresent()) {
            return bySub;
        }

        String email = userAuthInfo.email();

        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        Optional<TMember> byEmail = members.findByDomainEmailIgnoreCase(email.strip());

        if (byEmail.isEmpty() || byEmail.get().getDirectoryUserId() != null) {
            return Optional.empty();
        }

        TMember member = byEmail.get();
        member.setDirectoryUserId(userAuthInfo.userId());
        members.saveAndFlush(member);

        auditLog.record("members :: link-user", AuditPayload.of("memberId", member.getId())
                                                            .withFields(List.of("directoryUserId")));

        return Optional.of(member);
    }

    @Override
    @Transactional
    public Optional<TMember> current() {
        return userAuthInfoResolver.resolve().flatMap(this::resolve);
    }
}

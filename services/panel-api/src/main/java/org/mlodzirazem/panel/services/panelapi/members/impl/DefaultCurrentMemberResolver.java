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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.user.IdentityProvider;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfoResolver;
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup;
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup.EntraUser;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberIdentity;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberIdentityRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
class DefaultCurrentMemberResolver implements CurrentMemberResolver {
    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");

    private final TMemberRepository members;
    private final TMemberIdentityRepository identities;
    private final EntraUserLookup entraUsers;
    private final UserAuthInfoResolver userAuthInfoResolver;
    private final AuditLog auditLog;

    @Override
    @Transactional
    public Optional<TMember> resolve(UserAuthInfo userAuthInfo) {
        Optional<TMember> linked = members.findByIdentity(userAuthInfo.provider(), userAuthInfo.userId());

        if (linked.isPresent()) {
            return linked;
        }

        return switch (userAuthInfo.provider()) {
            case GOOGLE -> linkByDomainEmail(userAuthInfo);
            case ENTRA -> linkByEmployeeId(userAuthInfo);
        };
    }

    @Override
    @Transactional
    public Optional<TMember> current() {
        return userAuthInfoResolver.resolve().flatMap(this::resolve);
    }

    private Optional<TMember> linkByDomainEmail(UserAuthInfo userAuthInfo) {
        String email = userAuthInfo.email();

        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return members.findByDomainEmailIgnoreCase(email.strip())
            .filter(member -> !identities.existsByMemberIdAndProvider(member.getId(), IdentityProvider.GOOGLE))
            .map(member -> link(member, IdentityProvider.GOOGLE, userAuthInfo.userId()));
    }

    private Optional<TMember> linkByEmployeeId(UserAuthInfo userAuthInfo) {
        Optional<EntraUser> account = entraUsers.find(userAuthInfo.userId());

        if (account.isEmpty() || !account.get().accountEnabled()) {
            return Optional.empty();
        }

        String number = memberNumber(account.get().employeeId());

        if (number == null) {
            return Optional.empty();
        }

        List<TMember> candidates = members.findByMemberNumber(number);

        if (candidates.size() > 1) {
            log.warn("Member number {} is not unique; Entra account {} stays unlinked", number, userAuthInfo.userId());
        }

        if (candidates.size() != 1) {
            return Optional.empty();
        }

        TMember member = candidates.getFirst();

        if (identities.existsByMemberIdAndProvider(member.getId(), IdentityProvider.ENTRA)) {
            return Optional.empty();
        }

        return Optional.of(link(member, IdentityProvider.ENTRA, userAuthInfo.userId()));
    }

    private TMember link(TMember member, IdentityProvider provider, String subject) {
        TMemberIdentity identity = new TMemberIdentity();
        identity.setMember(member);
        identity.setProvider(provider);
        identity.setSubject(subject);
        identities.saveAndFlush(identity);

        auditLog.record("members :: link-user", AuditPayload.of("memberId", member.getId())
                                                            .withFields(List.of("identity"))
                                                            .withOutcome(provider.name()));

        return member;
    }

    /**
     * The member number from an Entra {@code employeeId}, without leading zeros; null when it is not a positive number.
     */
    static @Nullable String memberNumber(@Nullable String employeeId) {
        if (employeeId == null) {
            return null;
        }

        String digits = employeeId.strip();

        if (!DIGITS.matcher(digits).matches()) {
            return null;
        }

        String number = digits.replaceFirst("^0+", "");
        return number.isEmpty() ? null : number;
    }
}

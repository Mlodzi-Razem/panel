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
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentReferences;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
class DefaultPaymentReferences implements PaymentReferences {
    private static final String NUMERIC = "[0-9]{1,9}";
    private static final String MEMBER_NUMBER = "[0-9]{6}";

    private final JdbcClient jdbc;

    @Override
    @Transactional(readOnly = true)
    public String referenceOf(long memberId) {
        Optional<Optional<String>> row = jdbc.sql("SELECT member_id FROM members WHERE id = :id")
                                             .param("id", memberId)
                                             .query((rs, n) -> Optional.ofNullable(rs.getString(1)))
                                             .optional();

        String number = row.orElseThrow(() -> new NotFoundException("Member", memberId)).orElse(null);

        if (number != null && number.matches(MEMBER_NUMBER)) {
            return number;
        }

        return ReferenceCode.format(number != null && number.matches(NUMERIC) ? number : Long.toString(memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> parse(String transferTitle) {
        Set<Long> found = new HashSet<>();

        for (String number : ReferenceCode.validNumbers(transferTitle)) {
            resolve(number).ifPresent(found::add);
        }

        TransferTitle.templateNumber(transferTitle).flatMap(this::byMemberNumber).ifPresent(found::add);

        return found.size() == 1 ? Optional.of(found.iterator().next()) : Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> membersNamed(String transferTitle) {
        Set<Long> found = new HashSet<>();

        ReferenceCode.validNumbers(transferTitle).forEach(number -> resolve(number).ifPresent(found::add));
        TransferTitle.numbersNamed(transferTitle).forEach(number -> byMemberNumber(number).ifPresent(found::add));

        return found;
    }

    private Optional<Long> byMemberNumber(String number) {
        return jdbc.sql("SELECT id FROM members WHERE member_id = :number")
                   .param("number", number)
                   .query(Long.class)
                   .optional();
    }

    private Optional<Long> resolve(String number) {
        Optional<Long> byNumber = byMemberNumber(number);

        if (byNumber.isPresent()) {
            return byNumber;
        }

        return jdbc.sql("SELECT id FROM members WHERE id = :id AND (member_id IS NULL OR member_id !~ '^[0-9]{1,9}$')")
                   .param("id", Long.parseLong(number))
                   .query(Long.class)
                   .optional();
    }
}

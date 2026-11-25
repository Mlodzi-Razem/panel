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

package org.mlodzirazem.panel.services.panelapi.fees;

import org.mlodzirazem.panel.services.panelapi.fees.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class PaymentReferencesTest extends FeesTestSupport {
    @Autowired
    PaymentReferences references;

    private TMember numbered(String first, String number) {
        TMember member = newMember(first, "Ref", "mazowieckie");
        jdbc.update("UPDATE members SET member_id = ? WHERE id = ?", number, member.getId());
        return member;
    }

    @Test
    void numericMemberNumbersAreUsedAndParsedBack() {
        TMember member = numbered("Num", "7001");
        String reference = references.referenceOf(member.getId());

        assertThat(reference).startsWith("MR-7001-").hasSize(9);
        assertThat(references.parse("Skladka " + reference.toLowerCase().replace("-", " ") + " maj")).contains(member.getId());
        assertThat(references.parse(reference)).contains(member.getId());
    }

    @Test
    void nonNumericMemberNumbersFallBackToTheDatabaseId() {
        TMember member = numbered("Alpha", "A-12");
        String reference = references.referenceOf(member.getId());

        assertThat(reference).startsWith("MR-" + member.getId() + "-");
        assertThat(references.parse(reference)).contains(member.getId());
    }

    @Test
    void wrongCheckDigitUnknownMembersAndAmbiguousTitlesAreRejected() {
        TMember a = numbered("A", "7001");
        TMember b = numbered("B", "7002");
        String ra = references.referenceOf(a.getId());
        String rb = references.referenceOf(b.getId());
        String wrong = ra.substring(0, ra.length() - 1) + ((ra.charAt(ra.length() - 1) - '0' + 1) % 10);

        assertThat(references.parse(wrong)).isEmpty();
        assertThat(references.parse(ReferenceCodeFixture.unknown())).isEmpty();
        assertThat(references.parse(ra + " " + rb)).isEmpty();
        assertThat(references.parse(ra + " " + ra)).contains(a.getId());
        assertThat(codeOf(() -> references.referenceOf(-5))).isEqualTo("NOT_FOUND");
    }

    static final class ReferenceCodeFixture {
        static String unknown() {
            return "MR-999999-0";
        }
    }
}

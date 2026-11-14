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

package org.mlodzirazem.panel.services.panelapi.audit;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.audit.impl.AuditPersister;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserScopedValues;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class AuditPersisterTest extends DomainTestSupport {
    @Autowired
    AuditPersister persister;

    @Autowired
    AuditLog auditLog;

    @Test
    void persistsIdsAndFieldNamesButNeverValues() {
        TMember member = newMember("Ala", "Audit", "mazowieckie");

        memberService.update(member.getId(), Map.of(
            MemberField.PRONOUNS, "very-secret-pronouns",
            MemberField.PHONE_NUMBERS, List.of("+48 600 700 800")
        ));

        String payload = auditPayloads("members :: update");

        assertThat(payload).contains("\"memberId\"", "pronouns", "phoneNumbers")
                           .doesNotContain("very-secret-pronouns")
                           .doesNotContain("600 700 800");
        assertThat(auditPayloads("members :: create")).doesNotContain("Audit").doesNotContain("Ala");
    }

    @Test
    void unchangedUpdateIsNotAudited() {
        TMember member = newMember("Una", "Same", "mazowieckie");

        memberService.update(member.getId(), Map.of(MemberField.PREFERRED_NAME_FIRST, "Una"));

        assertThat(auditCount("members :: update")).isZero();
    }

    @Test
    void rowCarriesTraceIdTimestampAndTriggeringMember() {
        TMember member = newMember("Tim", "Trigger", "mazowieckie");
        jdbc.update("UPDATE members SET directory_user_id = 'sub-audit' WHERE id = ?", member.getId());

        ScopedValue.where(UserScopedValues.USER_AUTH_INFO, new UserAuthInfo("sub-audit", null, null))
                   .run(() -> auditLog.record("test :: op", AuditPayload.of("memberId", member.getId())
                                                                         .withFields(List.of("b", "a"))));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT operation_trace_id, operation_timestamp, triggered_by_id, operation_payload::text AS payload "
                + "FROM audited_operations WHERE operation_id = 'test :: op'");

        assertThat((String) row.get("operation_trace_id")).isNotBlank();
        assertThat(row.get("operation_timestamp")).isNotNull();
        assertThat(((Number) row.get("triggered_by_id")).longValue()).isEqualTo(member.getId());
        assertThat((String) row.get("payload")).contains("\"fields\": [\"a\", \"b\"]");
    }

    @Test
    void persistWithoutPayloadWritesEmptyObjectAndNoTriggeringMember() {
        persister.persist("test :: bare", null);

        assertThat(auditPayloads("test :: bare")).isEqualTo("{}");
        assertThat(jdbc.queryForObject("SELECT triggered_by_id FROM audited_operations WHERE operation_id = 'test :: bare'",
            Long.class)).isNull();
    }
}

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

package org.mlodzirazem.panel.services.panelapi.audit.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.audit.Audited;
import org.mlodzirazem.panel.services.panelapi.audit.AuditedPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.verify;

@SpringBootTest(
    classes = {
        AuditAspectTestService.class,
        AuditAspect.class
    }
)
@EnableAspectJAutoProxy
class AuditAspectTest {
    @MockitoBean
    AuditPersister auditPersister;

    @Autowired
    AuditAspectTestService testService;

    @Test
    void withoutPayload() {
        testService.auditedWithoutPayload();

        verify(auditPersister).persist("test :: auditedWithoutPayload", null);
    }

    @Test
    void withPayload() {
        testService.auditedWithPayload("PAYLOAD");

        verify(auditPersister).persist("test :: auditedWithPayload", "PAYLOAD");
    }

    @Test
    void withPayloadAndOtherArgs() {
        testService.auditedWithPayloadAndOtherArgs(1, "PAYLOAD", 2);

        verify(auditPersister).persist("test :: auditedWithPayloadAndOtherArgs", "PAYLOAD");
    }

    @Test
    void throwsWhenMultiplePayloads() {
        Assertions.assertThatThrownBy(() -> testService.auditedWithMultiplePayloads("PAYLOAD1", "PAYLOAD2"))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("@AuditedPayload")
                  .hasMessageContaining(AuditAspectTestService.class.getName())
                  .hasMessageContaining("auditedWithMultiplePayloads");
    }
}

class AuditAspectTestService {
    @Audited(id = "test :: auditedWithoutPayload")
    public void auditedWithoutPayload() {
    }

    @Audited(id = "test :: auditedWithPayload")
    public void auditedWithPayload(@AuditedPayload String payload) {
    }

    @Audited(id = "test :: auditedWithPayloadAndOtherArgs")
    public void auditedWithPayloadAndOtherArgs(Object arg1, @AuditedPayload String payload, Object arg2) {
    }

    @Audited(id = "test :: auditedWithMultiplePayloads")
    public void auditedWithMultiplePayloads(@AuditedPayload String payload1, @AuditedPayload String payload2) {
    }
}
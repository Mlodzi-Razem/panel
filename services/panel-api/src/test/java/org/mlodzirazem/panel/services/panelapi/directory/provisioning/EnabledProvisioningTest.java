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

import org.junit.jupiter.api.BeforeEach;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfileSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupMappingSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningActorSource;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Provisioning switched ON against the fake Graph, with the profile, group-mapping and actor sources replaced by mocks
 * (their real implementations belong to other packages). Every subclass shares one Spring context.
 */
@SpringBootTest(properties = {
    "panel.directory.provisioning.enabled=true",
    "panel.directory.provisioning.destructive-enabled=true",
    "panel.directory.provisioning.poll-interval=1h",
    "panel.directory.provisioning.max-attempts=3",
    "panel.directory.provisioning.member-license-sku=" + ProvisioningTestSupport.SKU_MEMBER,
    "panel.directory.provisioning.trial-license-sku=" + ProvisioningTestSupport.SKU_TRIAL,
    "panel.directory.provisioning.members-administrative-unit-id=" + ProvisioningTestSupport.AU_MEMBERS,
    "panel.directory.provisioning.group-administrative-unit-id=" + ProvisioningTestSupport.AU_GROUPS,
    "panel.directory.provisioning.protected-object-ids=" + ProvisioningTestSupport.PROTECTED_OID,
    "panel.entra.tenant-id=tenant-dummy",
    "panel.entra.client-id=client-dummy",
    "panel.entra.client-secret=secret-dummy",
    "logging.level.org.mlodzirazem=TRACE",
    "logging.level.org.springframework.web.client=TRACE",
    "logging.level.org.springframework.jdbc=TRACE"
})
@PostgresTest
abstract class EnabledProvisioningTest extends ProvisioningTestSupport {
    static final long ACTOR_MEMBER = 1;

    @MockitoBean
    AccountProfileSource profiles;

    @MockitoBean
    GroupMappingSource mappings;

    @MockitoBean
    ProvisioningActorSource actors;

    @Autowired
    ProvisioningWorker worker;

    @Autowired
    FinishedEventCapture events;

    /**
     * The worker is one bean for the whole context. A Graph throttling pause (D6) of one test must not stop the next one,
     * so every test starts without it.
     */
    @BeforeEach
    void clearThrottlingPause() throws ReflectiveOperationException {
        java.lang.reflect.Field field = ProvisioningWorker.class.getDeclaredField("throttledUntil");
        field.setAccessible(true);
        field.set(worker, null);
    }

    @BeforeEach
    void stubSources() {
        events.committed.clear();
        when(profiles.supports(any())).thenReturn(true);
        when(profiles.profile(any())).thenAnswer(invocation -> Optional.of(profileOf(invocation.getArgument(0))));
        when(mappings.mapping(anyLong())).thenReturn(Optional.empty());
        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(ACTOR_MEMBER, true, true)));
    }
}

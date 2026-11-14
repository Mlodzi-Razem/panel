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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfoResolver;
import org.mlodzirazem.panel.services.panelapi.core.uuid.UuidGenerator;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.audit.TAuditedOperation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.audit.TAuditedOperationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuditPersister {
    private final TAuditedOperationRepository repository;
    private final TMemberRepository members;
    private final UserAuthInfoResolver userAuthInfoResolver;
    private final ObjectProvider<Tracer> tracer;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(String id, @Nullable Object payload) {
        write(id, payload);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void persistInCurrentTransaction(String id, @Nullable Object payload) {
        write(id, payload);
    }

    private void write(String id, @Nullable Object payload) {
        TAuditedOperation operation = new TAuditedOperation();
        operation.setOperationId(id);
        operation.setTraceId(traceId());
        operation.setTimestamp(ZonedDateTime.now(clock));
        operation.setPayload(toJson(payload));
        operation.setTriggeredBy(currentMember());

        repository.save(operation);
    }

    private @Nullable TMember currentMember() {
        Optional<UserAuthInfo> authInfo = userAuthInfoResolver.resolve();

        if (authInfo.isEmpty()) {
            return null;
        }

        return members.findByDirectoryUserId(authInfo.get().userId()).orElse(null);
    }

    private String traceId() {
        Tracer currentTracer = tracer.getIfAvailable();

        if (currentTracer != null) {
            Span span = currentTracer.currentSpan();

            if (span != null) {
                return span.context().traceId();
            }
        }

        return uuidGenerator.uuidv7().toString();
    }

    private String toJson(@Nullable Object payload) {
        if (payload == null) {
            return "{}";
        }

        if (payload instanceof AuditPayload auditPayload) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("ids", auditPayload.ids());
            json.put("fields", auditPayload.fields());

            if (auditPayload.outcome() != null) {
                json.put("outcome", auditPayload.outcome());
            }

            return jsonMapper.writeValueAsString(json);
        }

        return jsonMapper.writeValueAsString(payload);
    }
}

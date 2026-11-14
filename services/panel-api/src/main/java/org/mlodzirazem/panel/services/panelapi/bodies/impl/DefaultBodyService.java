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

package org.mlodzirazem.panel.services.panelapi.bodies.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.bodies.NewBody;
import org.mlodzirazem.panel.services.panelapi.bodies.UpdateBody;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberTypeRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
class DefaultBodyService implements BodyService {
    private final TBodyRepository bodies;
    private final TBodyTemplateRepository templates;
    private final TBodyTemplateMemberTypeRepository memberTypes;
    private final CurrentMemberResolver currentMemberResolver;
    private final AuditLog auditLog;

    @Override
    @Transactional(readOnly = true)
    public TBody get(long bodyId) {
        return bodies.findById(bodyId).orElseThrow(() -> new NotFoundException("Body", bodyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBody> all() {
        return bodies.findAllWithTemplate();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBody> districts() {
        return bodies.findAllByKind(BodyKind.DISTRICT);
    }

    @Override
    @Transactional
    public TBody create(NewBody newBody) {
        if (newBody.name().isBlank()) {
            throw new RuleViolationException("FIELD_REQUIRED", "name is required");
        }

        TBodyTemplate template = templates.findById(newBody.templateId())
                                          .orElseThrow(() -> new NotFoundException("Body template",
                                              newBody.templateId()));

        TBody parent = null;

        if (template.getParentTemplate() != null) {
            if (newBody.parentBodyId() == null) {
                throw new RuleViolationException("PARENT_REQUIRED", "The body needs a parent body");
            }

            parent = get(newBody.parentBodyId());

            if (parent.getBodyTemplate() == null
                || !Objects.equals(parent.getBodyTemplate().getId(), template.getParentTemplate().getId())) {
                throw new RuleViolationException("PARENT_MISMATCH", "The parent body has a wrong template");
            }
        } else if (newBody.parentBodyId() != null) {
            throw new RuleViolationException("PARENT_NOT_ALLOWED", "The body cannot have a parent body");
        }

        TBody body = new TBody();
        body.setBodyTemplate(template);
        body.setParentBody(parent);
        body.setName(newBody.name().strip());
        body.setEmail(newBody.email());
        body.setCode(newBody.code());
        body.setCreatedBy(currentMemberResolver.current().orElse(null));
        bodies.saveAndFlush(body);

        List<String> fields = new ArrayList<>(List.of("name", "bodyTemplate"));

        if (parent != null) {
            fields.add("parentBody");
        }

        if (newBody.email() != null) {
            fields.add("email");
        }

        auditLog.record("bodies :: create", AuditPayload.of("bodyId", body.getId()).withFields(fields));

        return body;
    }

    @Override
    @Transactional
    public TBody update(long bodyId, UpdateBody update) {
        TBody body = get(bodyId);
        List<String> changed = new ArrayList<>();

        if (update.name() != null && !update.name().isBlank() && !update.name().strip().equals(body.getName())) {
            body.setName(update.name().strip());
            changed.add("name");
        }

        if (update.email() != null) {
            String email = update.email().isBlank() ? null : update.email().strip();

            if (!Objects.equals(email, body.getEmail())) {
                body.setEmail(email);
                changed.add("email");
            }
        }

        if (!changed.isEmpty()) {
            body.setUpdatedBy(currentMemberResolver.current().orElse(null));
            bodies.saveAndFlush(body);

            auditLog.record("bodies :: update", AuditPayload.of("bodyId", bodyId).withFields(changed));
        }

        return body;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBodyTemplate> templates() {
        return templates.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBodyTemplateMemberType> memberTypes(long templateId) {
        return memberTypes.findByBodyTemplateId(templateId);
    }
}

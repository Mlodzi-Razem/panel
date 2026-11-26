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
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.bodies.CreationOptions;
import org.mlodzirazem.panel.services.panelapi.bodies.NewBody;
import org.mlodzirazem.panel.services.panelapi.bodies.UpdateBody;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.BodiesManagerGuard;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberTypeRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
class DefaultBodyService implements BodyService {
    /**
     * Kinds that exist at most once in the whole country (statute 2026: national organs and
     * one-person offices; PLAN-WORKFLOW.md, A3): the Walne Zgranie, ZK, the Rada Krajowa, KSK, KKR,
     * KKW, the press spokesperson and the ZK IT proxy.
     */
    private static final Set<BodyKind> ONE_IN_THE_COUNTRY = Set.of(
        BodyKind.GENERAL_ASSEMBLY,
        BodyKind.NATIONAL_BOARD,
        BodyKind.NATIONAL_COUNCIL,
        BodyKind.PEER_COURT,
        BodyKind.AUDIT_COMMISSION,
        BodyKind.ELECTORAL_COMMISSION,
        BodyKind.PRESS_SPOKESPERSON,
        BodyKind.NATIONAL_IT_PROXY
    );

    /**
     * Kinds that exist at most once per district (parent body): WZO and ZO. A commissioner board is
     * different — each one is a body of its own, see {@code hasOpenCommissionerBoard}. Local
     * circles, teams and the districts themselves are not limited by the statute.
     */
    private static final Set<BodyKind> ONE_PER_DISTRICT = Set.of(
        BodyKind.DISTRICT_ASSEMBLY,
        BodyKind.DISTRICT_BOARD
    );

    private final TBodyRepository bodies;
    private final TBodyTemplateRepository templates;
    private final TBodyTemplateMemberTypeRepository memberTypes;
    private final TBodyMembershipRepository memberships;
    private final CurrentMemberResolver currentMemberResolver;
    private final BodiesManagerGuard bodiesManagerGuard;
    private final AuditLog auditLog;
    private final Clock clock;

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

        bodiesManagerGuard.lock();

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

        checkDuplicateKind(template, parent);

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

    /**
     * Statute 2026 keeps one organ of a kind: in the country for the national kinds, in the
     * district for the district kinds (see the kind sets above). The second organ of the same kind
     * is the mistaken-entry case the stage once produced (a duplicate Zarząd Okręgu), so the
     * guard lives in the code, not in convention. Advisory lock in {@code create} serialises two
     * racing creations, but the database has no unique constraint over the template kind —
     * denormalising the kind onto {@code bodies} would allow one if the senior ever needs it.
     */
    private void checkDuplicateKind(TBodyTemplate template, @Nullable TBody parent) {
        BodyKind kind = template.getKind();

        if (kind == null) {
            return;
        }

        if (takenInTheCountry(kind)) {
            throw new ConflictException("BODY_ALREADY_EXISTS",
                "The association already has a body of this kind (statute 2026: one organ of a kind"
                    + " in the country)", List.of("kind"));
        }

        if (parent != null && takenInTheDistrict(kind, parent)) {
            throw new ConflictException("BODY_ALREADY_EXISTS",
                "This district already has a body of this kind (statute 2026: one organ of a kind"
                    + " in the district)", List.of("kind", "parentBodyId"));
        }

        if (parent != null && kind == BodyKind.DISTRICT_COMMISSIONER && hasOpenCommissionerBoard(parent)) {
            throw new ConflictException("COMMISSIONER_BOARD_STILL_OPEN",
                "The district's commissioner board is not closed yet: a new one can be created once the earlier"
                    + " one has had its mandates and all of them have ended", List.of("kind", "parentBodyId"));
        }
    }

    private boolean takenInTheCountry(BodyKind kind) {
        return ONE_IN_THE_COUNTRY.contains(kind) && bodies.existsByKind(kind);
    }

    private boolean takenInTheDistrict(BodyKind kind, TBody district) {
        return ONE_PER_DISTRICT.contains(kind) && bodies.existsByKindAndParent(kind, district.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CreationOptions creationOptions(long templateId) {
        TBodyTemplate template = templates.findById(templateId)
                                          .orElseThrow(() -> new NotFoundException("Body template", templateId));
        BodyKind kind = template.getKind();
        TBodyTemplate parentTemplate = template.getParentTemplate();

        if (kind == null) {
            return new CreationOptions(true, List.of());
        }

        if (takenInTheCountry(kind)) {
            return new CreationOptions(false, List.of());
        }

        if (parentTemplate == null) {
            return new CreationOptions(true, List.of());
        }

        List<TBody> parents = bodies.findAllWithTemplate()
                                    .stream()
                                    .filter(b -> b.getBodyTemplate() != null
                                        && Objects.equals(b.getBodyTemplate().getId(), parentTemplate.getId()))
                                    .toList();
        List<Long> unavailable = parents.stream()
                                        .filter(p -> takenInTheDistrict(kind, p)
                                            || kind == BodyKind.DISTRICT_COMMISSIONER && hasOpenCommissionerBoard(p))
                                        .map(TBody::getId)
                                        .toList();

        return new CreationOptions(unavailable.size() < parents.size(), unavailable);
    }

    /**
     * A later commissioner board is a new body, not a second life of the old one (its 186 days are over for
     * good). It can be created once every earlier board of the district is closed: it had mandates and none is
     * current or planned. An empty board is still waiting for its mandates. Whether the new board is more than
     * a disguised extension is checked when its mandates are assigned, because only then its start is known.
     */
    private boolean hasOpenCommissionerBoard(TBody district) {
        LocalDate today = LocalDate.now(clock);

        return bodies.findByKindAndParent(BodyKind.DISTRICT_COMMISSIONER, district.getId())
                     .stream()
                     .map(board -> memberships.findAllByBodyId(board.getId()))
                     .anyMatch(mandates -> mandates.isEmpty() || mandates.stream().anyMatch(
                         m -> m.getValidTo() == null || m.getValidTo().isAfter(today)));
    }

    @Override
    @Transactional
    public void delete(long bodyId) {
        bodiesManagerGuard.lock();

        TBody body = get(bodyId);

        if (body.isSeeded()) {
            throw new ConflictException("BODY_IS_SEEDED",
                "The body was seeded by the panel's migrations and cannot be deleted — correcting"
                    + " the seed catalogue is a migration, not an edit");
        }

        if (bodies.countByParentBodyId(bodyId) > 0) {
            throw new ConflictException("BODY_HAS_CHILD_BODIES",
                "The body has subordinate bodies; move or delete them first", List.of("children"));
        }

        // Any row counts, VOIDED included: a voided mandate keeps its void reason and decision
        // references, and deleting the body would erase them (statute §0.6, mandate history).
        if (memberships.countByBodyId(bodyId) > 0) {
            throw new ConflictException("BODY_HAS_MEMBERSHIPS",
                "The body has mandates in its history (also voided ones); it cannot be deleted",
                List.of("memberships"));
        }

        bodies.delete(body);
        bodies.flush();

        List<String> fields = new ArrayList<>(List.of("name"));

        if (body.getCode() != null) {
            fields.add("code");
        }

        if (body.getEmail() != null) {
            fields.add("email");
        }

        auditLog.record("bodies :: delete", AuditPayload.of("bodyId", bodyId).withFields(fields));
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

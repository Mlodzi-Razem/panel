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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * A submission of the public "I want to join" form, waiting to become a recruitment case or to be discarded.
 * Personal data is nulled by retention ({@code dataErasedAt}); the row itself stays.
 */
@Getter
@Setter
@Entity
@Table(name = "recruitment_intakes")
@SequenceGenerator(
    name = "seq_recruitment_intakes_id",
    sequenceName = "seq_recruitment_intakes_id",
    allocationSize = 1
)
public class TRecruitmentIntake extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_recruitment_intakes_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_body_id")
    private @Nullable TBody district;

    @Column(name = "first_name")
    private @Nullable String firstName;

    @Column(name = "last_name")
    private @Nullable String lastName;

    @Column(name = "email")
    private @Nullable String email;

    @Column(name = "phone")
    private @Nullable String phone;

    @Column(name = "birth_date")
    private @Nullable LocalDate birthDate;

    @Column(name = "locality")
    private @Nullable String locality;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "consented_at", nullable = false)
    private ZonedDateTime consentedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecruitmentIntakeStatus status = RecruitmentIntakeStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "discard_reason")
    private @Nullable RecruitmentIntakeDiscardReason discardReason;

    @Column(name = "closed_at")
    private @Nullable ZonedDateTime closedAt;

    @Column(name = "case_id")
    private @Nullable Long caseId;

    @Column(name = "data_erased_at")
    private @Nullable ZonedDateTime dataErasedAt;

    /** Drops every piece of personal data; the history of what happened to the submission stays. */
    public void erasePersonalData(ZonedDateTime when) {
        firstName = null;
        lastName = null;
        email = null;
        phone = null;
        birthDate = null;
        locality = null;
        dataErasedAt = when;
    }
}

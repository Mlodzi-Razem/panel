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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "members")
@SequenceGenerator(name = "seq_members_id", sequenceName = "seq_members_id", allocationSize = 1)
public class TMember extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_members_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "member_status")
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "membership_type", nullable = false, columnDefinition = "membership_type")
    private MembershipType membershipType = MembershipType.ORDINARY;

    @Column(name = "member_id")
    private @Nullable String memberId;

    @Column(name = "preferred_name_first", nullable = false)
    private String preferredNameFirst;

    @Column(name = "preferred_name_middle")
    private @Nullable String preferredNameMiddle;

    @Column(name = "preferred_name_last", nullable = false)
    private String preferredNameLast;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "preferred_name", insertable = false, updatable = false)
    private @Nullable String preferredName;

    @Column(name = "formal_name_first", nullable = false)
    private String formalNameFirst;

    @Column(name = "formal_name_last", nullable = false)
    private String formalNameLast;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "formal_name", insertable = false, updatable = false)
    private @Nullable String formalName;

    @Column(name = "pseudonym")
    private @Nullable String pseudonym;

    @Column(name = "pronouns")
    private @Nullable String pronouns;

    @Column(name = "institution")
    private @Nullable String institution;

    @Column(name = "social_profile_url")
    private @Nullable String socialProfileUrl;

    @Column(name = "domain_email")
    private @Nullable String domainEmail;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "domain_email_aliases", nullable = false)
    private List<String> domainEmailAliases = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "private_emails", nullable = false)
    private List<String> privateEmails = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "phone_numbers", nullable = false)
    private List<String> phoneNumbers = new ArrayList<>();

    @Column(name = "probation_period_start_date")
    private @Nullable LocalDate probationPeriodStartDate;

    @Column(name = "probation_period_end_date")
    private @Nullable LocalDate probationPeriodEndDate;

    @Column(name = "request_date")
    private @Nullable LocalDate requestDate;

    @Column(name = "member_since")
    private @Nullable LocalDate memberSince;

    @Column(name = "removed_at")
    private @Nullable LocalDate removedAt;

    @Column(name = "declaration_reference")
    private @Nullable String declarationReference;

    @Column(name = "formal_identifier")
    private @Nullable String formalIdentifier;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "address_country_code", length = 3)
    private @Nullable String addressCountryCode;

    @Column(name = "address_line")
    private @Nullable String addressLine;

    @Column(name = "address_postal_code")
    private @Nullable String addressPostalCode;

    @Column(name = "address_state")
    private @Nullable String addressState;

    @Column(name = "address_province")
    private @Nullable String addressProvince;

    @Column(name = "address_city")
    private @Nullable String addressCity;

    @Column(name = "address_street")
    private @Nullable String addressStreet;

    @Column(name = "address_building")
    private @Nullable String addressBuilding;

    @Column(name = "address_apartment")
    private @Nullable String addressApartment;
}

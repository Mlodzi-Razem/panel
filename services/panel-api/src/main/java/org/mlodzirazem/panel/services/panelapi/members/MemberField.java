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

package org.mlodzirazem.panel.services.panelapi.members;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MemberField {
    PRIVATE_EMAILS(MemberFieldGroup.CONTACT, List.class, TMember::getPrivateEmails,
        (m, v) -> m.setPrivateEmails(strings(v))),
    PHONE_NUMBERS(MemberFieldGroup.CONTACT, List.class, TMember::getPhoneNumbers,
        (m, v) -> m.setPhoneNumbers(strings(v))),
    SOCIAL_PROFILE_URL(MemberFieldGroup.CONTACT, String.class, TMember::getSocialProfileUrl,
        (m, v) -> m.setSocialProfileUrl((String) v)),

    PREFERRED_NAME_FIRST(MemberFieldGroup.NAMING, String.class, TMember::getPreferredNameFirst,
        (m, v) -> m.setPreferredNameFirst((String) v)),
    PREFERRED_NAME_MIDDLE(MemberFieldGroup.NAMING, String.class, TMember::getPreferredNameMiddle,
        (m, v) -> m.setPreferredNameMiddle((String) v)),
    PREFERRED_NAME_LAST(MemberFieldGroup.NAMING, String.class, TMember::getPreferredNameLast,
        (m, v) -> m.setPreferredNameLast((String) v)),
    PSEUDONYM(MemberFieldGroup.NAMING, String.class, TMember::getPseudonym,
        (m, v) -> m.setPseudonym((String) v)),
    PRONOUNS(MemberFieldGroup.NAMING, String.class, TMember::getPronouns,
        (m, v) -> m.setPronouns((String) v)),
    INSTITUTION(MemberFieldGroup.NAMING, String.class, TMember::getInstitution,
        (m, v) -> m.setInstitution((String) v)),

    FORMAL_NAME_FIRST(MemberFieldGroup.FORMAL, String.class, TMember::getFormalNameFirst,
        (m, v) -> m.setFormalNameFirst((String) v)),
    FORMAL_NAME_LAST(MemberFieldGroup.FORMAL, String.class, TMember::getFormalNameLast,
        (m, v) -> m.setFormalNameLast((String) v)),
    FORMAL_IDENTIFIER(MemberFieldGroup.FORMAL, String.class, TMember::getFormalIdentifier,
        (m, v) -> m.setFormalIdentifier((String) v)),
    BIRTH_DATE(MemberFieldGroup.FORMAL, LocalDate.class, TMember::getBirthDate,
        (m, v) -> m.setBirthDate((LocalDate) v)),
    ADDRESS_LINE(MemberFieldGroup.FORMAL, String.class, TMember::getAddressLine,
        (m, v) -> m.setAddressLine((String) v)),
    ADDRESS_COUNTRY_CODE(MemberFieldGroup.FORMAL, String.class, TMember::getAddressCountryCode,
        (m, v) -> m.setAddressCountryCode((String) v)),
    ADDRESS_POSTAL_CODE(MemberFieldGroup.FORMAL, String.class, TMember::getAddressPostalCode,
        (m, v) -> m.setAddressPostalCode((String) v)),
    ADDRESS_STATE(MemberFieldGroup.FORMAL, String.class, TMember::getAddressState,
        (m, v) -> m.setAddressState((String) v)),
    ADDRESS_PROVINCE(MemberFieldGroup.FORMAL, String.class, TMember::getAddressProvince,
        (m, v) -> m.setAddressProvince((String) v)),
    ADDRESS_CITY(MemberFieldGroup.FORMAL, String.class, TMember::getAddressCity,
        (m, v) -> m.setAddressCity((String) v)),
    ADDRESS_STREET(MemberFieldGroup.FORMAL, String.class, TMember::getAddressStreet,
        (m, v) -> m.setAddressStreet((String) v)),
    ADDRESS_BUILDING(MemberFieldGroup.FORMAL, String.class, TMember::getAddressBuilding,
        (m, v) -> m.setAddressBuilding((String) v)),
    ADDRESS_APARTMENT(MemberFieldGroup.FORMAL, String.class, TMember::getAddressApartment,
        (m, v) -> m.setAddressApartment((String) v)),

    STATUS(MemberFieldGroup.ORGANISATIONAL, MemberStatus.class, TMember::getStatus,
        (m, v) -> m.setStatus((MemberStatus) v)),
    MEMBERSHIP_TYPE(MemberFieldGroup.ORGANISATIONAL, MembershipType.class, TMember::getMembershipType,
        (m, v) -> m.setMembershipType((MembershipType) v)),
    MEMBER_ID(MemberFieldGroup.ORGANISATIONAL, String.class, TMember::getMemberId,
        (m, v) -> m.setMemberId((String) v)),
    MEMBER_SINCE(MemberFieldGroup.ORGANISATIONAL, LocalDate.class, TMember::getMemberSince,
        (m, v) -> m.setMemberSince((LocalDate) v)),
    PROBATION_PERIOD_START_DATE(MemberFieldGroup.ORGANISATIONAL, LocalDate.class, TMember::getProbationPeriodStartDate,
        (m, v) -> m.setProbationPeriodStartDate((LocalDate) v)),
    PROBATION_PERIOD_END_DATE(MemberFieldGroup.ORGANISATIONAL, LocalDate.class, TMember::getProbationPeriodEndDate,
        (m, v) -> m.setProbationPeriodEndDate((LocalDate) v)),
    REQUEST_DATE(MemberFieldGroup.ORGANISATIONAL, LocalDate.class, TMember::getRequestDate,
        (m, v) -> m.setRequestDate((LocalDate) v)),
    REMOVED_AT(MemberFieldGroup.ORGANISATIONAL, LocalDate.class, TMember::getRemovedAt,
        (m, v) -> m.setRemovedAt((LocalDate) v)),
    DOMAIN_EMAIL(MemberFieldGroup.ORGANISATIONAL, String.class, TMember::getDomainEmail,
        (m, v) -> m.setDomainEmail((String) v)),
    DOMAIN_EMAIL_ALIASES(MemberFieldGroup.ORGANISATIONAL, List.class, TMember::getDomainEmailAliases,
        (m, v) -> m.setDomainEmailAliases(strings(v))),
    DECLARATION_REFERENCE(MemberFieldGroup.ORGANISATIONAL, String.class, TMember::getDeclarationReference,
        (m, v) -> m.setDeclarationReference((String) v));

    private final MemberFieldGroup group;
    private final Class<?> type;
    private final Function<TMember, ?> getter;
    private final BiConsumer<TMember, @Nullable Object> setter;
    private final String propertyName;

    MemberField(
        MemberFieldGroup group,
        Class<?> type,
        Function<TMember, ?> getter,
        BiConsumer<TMember, @Nullable Object> setter
    ) {
        this.group = group;
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.propertyName = camelCase(name());
    }

    public MemberFieldGroup group() {
        return group;
    }

    public Class<?> type() {
        return type;
    }

    public String propertyName() {
        return propertyName;
    }

    public @Nullable Object get(TMember member) {
        return getter.apply(member);
    }

    public void set(TMember member, @Nullable Object value) {
        setter.accept(member, value);
    }

    public static Optional<MemberField> byPropertyName(String propertyName) {
        return Arrays.stream(values()).filter(f -> f.propertyName.equals(propertyName)).findFirst();
    }

    public static Set<MemberField> inGroup(MemberFieldGroup group) {
        return Arrays.stream(values())
                     .filter(f -> f.group == group)
                     .collect(Collectors.toCollection(() -> EnumSet.noneOf(MemberField.class)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(@Nullable Object value) {
        return value == null ? new ArrayList<>() : new ArrayList<>((List<String>) value);
    }

    private static String camelCase(String constant) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;

        for (char c : constant.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }

        return result.toString();
    }
}

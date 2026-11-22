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

package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;

import java.time.LocalDate;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Candidate data fields editable in the intake milestone (M2). Mirrors {@code MemberField}:
 * the payload maps hold only these fields, blank strings mean "clear" for optional ones,
 * and the audit/timeline events carry property names, never values.
 */
public enum RecruitmentCandidateField {
    FIRST_NAME(String.class, TRecruitmentCase::getCandidateFirstName,
        (c, v) -> c.setCandidateFirstName((String) v), "candidateFirstName"),
    LAST_NAME(String.class, TRecruitmentCase::getCandidateLastName,
        (c, v) -> c.setCandidateLastName((String) v), "candidateLastName"),
    EMAIL(String.class, TRecruitmentCase::getCandidateEmail,
        (c, v) -> c.setCandidateEmail((String) v), "candidateEmail"),
    PHONE(String.class, TRecruitmentCase::getCandidatePhone,
        (c, v) -> c.setCandidatePhone((String) v), "candidatePhone"),
    SOCIAL_PROFILE_URL(String.class, TRecruitmentCase::getCandidateSocialProfileUrl,
        (c, v) -> c.setCandidateSocialProfileUrl((String) v), "candidateSocialProfileUrl"),
    BIRTH_DATE(LocalDate.class, TRecruitmentCase::getCandidateBirthDate,
        (c, v) -> c.setCandidateBirthDate((LocalDate) v), "candidateBirthDate");

    private final Class<?> type;
    private final Function<TRecruitmentCase, @Nullable Object> getter;
    private final BiConsumer<TRecruitmentCase, @Nullable Object> setter;
    private final String propertyName;

    RecruitmentCandidateField(
        Class<?> type,
        Function<TRecruitmentCase, @Nullable Object> getter,
        BiConsumer<TRecruitmentCase, @Nullable Object> setter,
        String propertyName
    ) {
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.propertyName = propertyName;
    }

    public Class<?> type() {
        return type;
    }

    public String propertyName() {
        return propertyName;
    }

    public @Nullable Object get(TRecruitmentCase recruitmentCase) {
        return getter.apply(recruitmentCase);
    }

    public void set(TRecruitmentCase recruitmentCase, @Nullable Object value) {
        setter.accept(recruitmentCase, value);
    }
}

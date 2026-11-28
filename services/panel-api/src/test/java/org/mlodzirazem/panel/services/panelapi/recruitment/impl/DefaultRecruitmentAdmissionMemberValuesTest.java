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
package org.mlodzirazem.panel.services.panelapi.recruitment.impl;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the member row written at admission: the preferred name precedence (declared, intake,
 * formal) must stay as it was before the display rule moved to RecruitmentCandidateNames.
 */
class DefaultRecruitmentAdmissionMemberValuesTest {
    private static final LocalDate ACCEPTED = LocalDate.of(2026, 11, 1);

    private static TRecruitmentCase caseWith(String intakeFirst, String intakeLast,
                                             String preferredFirst, String preferredLast,
                                             String formalFirst, String formalLast) {
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setCandidateFirstName(intakeFirst);
        candidateCase.setCandidateLastName(intakeLast);
        candidateCase.setCandidatePreferredFirstName(preferredFirst);
        candidateCase.setCandidatePreferredLastName(preferredLast);
        candidateCase.setCandidateFormalFirstName(formalFirst);
        candidateCase.setCandidateFormalLastName(formalLast);
        return candidateCase;
    }

    private static Map<MemberField, Object> values(TRecruitmentCase candidateCase) {
        return DefaultRecruitmentAdmissionService.memberValues(candidateCase, ACCEPTED);
    }

    @Test
    void declaredPreferredNameIsTheMemberPreferredName() {
        Map<MemberField, Object> values = values(caseWith("Alfa", "Nowak", "Beta", "Kowal", "Gamma", "Zieliński"));

        assertThat(values).containsEntry(MemberField.PREFERRED_NAME_FIRST, "Beta")
            .containsEntry(MemberField.PREFERRED_NAME_LAST, "Kowal")
            .containsEntry(MemberField.FORMAL_NAME_FIRST, "Gamma")
            .containsEntry(MemberField.FORMAL_NAME_LAST, "Zieliński");
    }

    @Test
    void intakeNameIsUsedWhenNoCompletePreferredPairExists() {
        Map<MemberField, Object> values = values(caseWith("Alfa", "Nowak", "Beta", null, "Gamma", "Zieliński"));

        assertThat(values).containsEntry(MemberField.PREFERRED_NAME_FIRST, "Alfa")
            .containsEntry(MemberField.PREFERRED_NAME_LAST, "Nowak");
    }

    @Test
    void formalNameIsUsedWhenNeitherPreferredNorIntakeIsComplete() {
        Map<MemberField, Object> values = values(caseWith(null, "Nowak", null, null, "Gamma", "Zieliński"));

        assertThat(values).containsEntry(MemberField.PREFERRED_NAME_FIRST, "Gamma")
            .containsEntry(MemberField.PREFERRED_NAME_LAST, "Zieliński");
    }

    @Test
    void partialFormalNameStillGoesThroughAsBeforeWithBlanksDropped() {
        Map<MemberField, Object> values = values(caseWith(null, null, null, null, "Gamma", " "));

        assertThat(values).containsEntry(MemberField.PREFERRED_NAME_FIRST, "Gamma")
            .doesNotContainKey(MemberField.PREFERRED_NAME_LAST);
    }

    @Test
    void noNameAtAllLeavesThePreferredNameOut() {
        Map<MemberField, Object> values = values(caseWith(null, null, null, null, null, null));

        assertThat(values).doesNotContainKeys(MemberField.PREFERRED_NAME_FIRST, MemberField.PREFERRED_NAME_LAST);
    }
}

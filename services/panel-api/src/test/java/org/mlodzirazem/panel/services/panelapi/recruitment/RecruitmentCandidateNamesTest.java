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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCandidateNames.Match;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCandidateNames.Source;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentCandidateNamesTest {

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

    @Test
    void declaredPreferredNameWinsOverIntakeAndFormal() {
        TRecruitmentCase candidateCase = caseWith("Alfa", "Nowak", "  Beta ", "Kowal", "Gamma", "Zieliński");

        RecruitmentCandidateNames.Display display = RecruitmentCandidateNames.display(candidateCase);

        assertThat(display.source()).isEqualTo(Source.DECLARATION_PREFERRED);
        assertThat(display.first()).isEqualTo("Beta");
        assertThat(display.last()).isEqualTo("Kowal");
    }

    @Test
    void incompletePreferredPairFallsBackToTheCaseName() {
        TRecruitmentCase candidateCase = caseWith("Alfa", "Nowak", "Beta", " ", "Gamma", "Zieliński");

        RecruitmentCandidateNames.Display display = RecruitmentCandidateNames.display(candidateCase);

        assertThat(display.source()).isEqualTo(Source.CASE);
        assertThat(display.first()).isEqualTo("Alfa");
        assertThat(display.last()).isEqualTo("Nowak");
    }

    @Test
    void formalPairIsTheLastResortAndNothingIsNoneWithNullNames() {
        RecruitmentCandidateNames.Display formal = RecruitmentCandidateNames.display(
            caseWith(null, "Nowak", null, null, "Gamma", "Zieliński"));

        assertThat(formal.source()).isEqualTo(Source.DECLARATION_FORMAL);
        assertThat(formal.first()).isEqualTo("Gamma");

        RecruitmentCandidateNames.Display none = RecruitmentCandidateNames.display(
            caseWith("", "  ", "Beta", null, "Gamma", null));

        assertThat(none.source()).isEqualTo(Source.NONE);
        assertThat(none.first()).isNull();
        assertThat(none.last()).isNull();
    }

    @Test
    void anonymizedCaseHasNoName() {
        RecruitmentCandidateNames.Display display = RecruitmentCandidateNames.display(
            caseWith(null, null, null, null, null, null));

        assertThat(display.source()).isEqualTo(Source.NONE);
    }

    @Test
    void comparisonIgnoresCaseWhitespaceAndUnicodeForm() {
        // "ą" as a precomposed character versus a base letter followed by a combining ogonek
        String precomposed = "Zażółć";
        String decomposed = Normalizer.normalize(precomposed, Normalizer.Form.NFD);
        RecruitmentCandidateNames.Comparison same = RecruitmentCandidateNames.compare(
            caseWith("Ola", precomposed, "  ola ", "ZAŻÓŁĆ", null, null));

        assertThat(same.match()).isEqualTo(Match.SAME);
        assertThat(RecruitmentCandidateNames.compare(
            caseWith("Ola", precomposed, "Ola", decomposed, null, null)).match()).isEqualTo(Match.SAME);
    }

    @Test
    void comparisonReportsDifferenceAndMissingSides() {
        RecruitmentCandidateNames.Comparison different = RecruitmentCandidateNames.compare(
            caseWith("Alfa", "Nowak", "Beta", "Kowal", null, null));

        assertThat(different.match()).isEqualTo(Match.DIFFERENT);
        assertThat(different.caseName().first()).isEqualTo("Alfa");
        assertThat(different.declarationPreferred().first()).isEqualTo("Beta");

        RecruitmentCandidateNames.Comparison notDeclared = RecruitmentCandidateNames.compare(
            caseWith("Alfa", "Nowak", null, null, "Gamma", "Zieliński"));

        assertThat(notDeclared.match()).isEqualTo(Match.INCOMPLETE);
        assertThat(notDeclared.declarationPreferred().first()).isNull();
        assertThat(notDeclared.declarationPreferred().last()).isNull();
    }
}

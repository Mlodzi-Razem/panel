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

import java.text.Normalizer;
import java.util.Locale;

/**
 * Which first and last name the Panel uses for a candidate, and how the name captured at intake compares
 * with the one from the declaration (PN-1..PN-4).
 *
 * <p>Precedence for display and for the member row: the declared preferred name, then the case name (the
 * intake name, editable on the case), then the formal name. A name counts only as a complete pair: both
 * parts non-blank. Formal data is never part of the comparison card and is never shown there.
 *
 * <p>Pure: no persistence, no logging. Values are returned stripped; comparison ignores case and
 * surrounding whitespace and normalizes to NFC, without removing diacritics.
 */
public final class RecruitmentCandidateNames {

    private RecruitmentCandidateNames() {
    }

    /** Where the displayed name comes from. {@code NONE} means no complete pair exists on the case. */
    public enum Source {
        DECLARATION_PREFERRED,
        CASE,
        DECLARATION_FORMAL,
        NONE
    }

    /** Stage of the recruitment chain a name was captured at. */
    public enum Stage {
        CASE,
        DECLARATION_PREFERRED,
        DECLARATION_FORMAL
    }

    /** Result of comparing the intake (case) name with the declared preferred name. */
    public enum Match {
        SAME,
        DIFFERENT,
        /** One side is missing (not declared yet, or the case has no complete name). */
        INCOMPLETE
    }

    /** Displayed name; first and last are null when the source is {@link Source#NONE}. */
    public record Display(@Nullable String first, @Nullable String last, Source source) {
    }

    /** A name as captured at one stage; first and last are null when that stage has no complete pair. */
    public record NameAtStage(Stage stage, @Nullable String first, @Nullable String last) {
    }

    public record Comparison(NameAtStage caseName, NameAtStage declarationPreferred, Match match) {
    }

    /** Display rule: preferred declared pair, then case pair, then formal pair, else nothing. */
    public static Display display(TRecruitmentCase candidateCase) {
        Pair preferred = complete(candidateCase.getCandidatePreferredFirstName(),
            candidateCase.getCandidatePreferredLastName());

        if (preferred != null) {
            return new Display(preferred.first(), preferred.last(), Source.DECLARATION_PREFERRED);
        }

        Pair intake = complete(candidateCase.getCandidateFirstName(), candidateCase.getCandidateLastName());

        if (intake != null) {
            return new Display(intake.first(), intake.last(), Source.CASE);
        }

        Pair formal = complete(candidateCase.getCandidateFormalFirstName(),
            candidateCase.getCandidateFormalLastName());

        if (formal != null) {
            return new Display(formal.first(), formal.last(), Source.DECLARATION_FORMAL);
        }

        return new Display(null, null, Source.NONE);
    }

    /**
     * Compares the case name (the one the candidate gave at intake, as it stands now) with the declared
     * preferred name. Match is {@link Match#INCOMPLETE} when either side has no complete pair.
     */
    public static Comparison compare(TRecruitmentCase candidateCase) {
        Pair intake = complete(candidateCase.getCandidateFirstName(), candidateCase.getCandidateLastName());
        Pair preferred = complete(candidateCase.getCandidatePreferredFirstName(),
            candidateCase.getCandidatePreferredLastName());

        NameAtStage caseName = stageOf(Stage.CASE, intake);
        NameAtStage declaration = stageOf(Stage.DECLARATION_PREFERRED, preferred);

        Match match;

        if (intake == null || preferred == null) {
            match = Match.INCOMPLETE;
        } else {
            boolean same = normalized(intake.first()).equals(normalized(preferred.first()))
                && normalized(intake.last()).equals(normalized(preferred.last()));
            match = same ? Match.SAME : Match.DIFFERENT;
        }

        return new Comparison(caseName, declaration, match);
    }

    private static NameAtStage stageOf(Stage stage, @Nullable Pair pair) {
        return pair == null
            ? new NameAtStage(stage, null, null)
            : new NameAtStage(stage, pair.first(), pair.last());
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static @Nullable Pair complete(@Nullable String first, @Nullable String last) {
        String strippedFirst = clean(first);
        String strippedLast = clean(last);

        return strippedFirst == null || strippedLast == null ? null : new Pair(strippedFirst, strippedLast);
    }

    private static @Nullable String clean(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }

    private record Pair(String first, String last) {
    }
}

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

package org.mlodzirazem.panel.services.panelapi.api.v1.recruitment;

/**
 * The predefined list views of the recruitment procedure ("widoki filtrów"): each is a fixed filter over the
 * cases the caller may read. The names are the API contract of {@code ?view=}; the front end keeps no copy of the
 * criteria.
 */
public enum RecruitmentCaseView {
    /** "Rekrutacje, które trzeba przeprowadzić": every open case. */
    TO_CONDUCT,
    /** "Osoby, które prawdopodobnie nie wysłały deklaracji": waiting for the declaration for more than N days. */
    PROBABLY_NO_DECLARATION,
    /** "Osoby rekomendowane przez ZO". */
    RECOMMENDED,
    /** "Osoby, co do których trwa głosowanie w ZK". */
    ZK_VOTING,
    /** "Wpisy, które ZK powinno usunąć": rejected, not anonymised yet, past the waiting period. */
    TO_DELETE
}

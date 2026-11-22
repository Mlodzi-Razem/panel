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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;

/**
 * Server-side rendering of the declaration PDF, paper-faithful layout of the versioned template
 * {@link RecruitmentEformRules#TEMPLATE_VERSION} ("2026" family: no address block, canonical server
 * timestamp, no under-16 parent block). The supporting-membership variant stays a template marker
 * outside of the workflow engine.
 */
public interface DeclarationPdfRenderer {
    byte[] render(TRecruitmentCase candidateCase, String templateVariant);
}

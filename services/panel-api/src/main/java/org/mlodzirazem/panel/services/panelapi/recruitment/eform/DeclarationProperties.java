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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Wiring of the e-declaration flow. Storage dir is replaceable by the staging volume. */
@Getter
@Setter
@ConfigurationProperties("panel.api.recruitment.declaration")
public class DeclarationProperties {
    /** Public base of the form URL handed to the candidate; token is appended. */
    private String publicBaseUrl = "/public/declaration-form/";

    /** Artifact store root (filesystem facade today, staging volume later). */
    private String storageDir = "./data/declarations";

    /** mObywatel accepts up to 5 MB; the e-flow allows twice that. */
    private int maxUploadMb = 10;
}

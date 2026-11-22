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

import org.mlodzirazem.panel.services.panelapi.core.error.DomainException;

/**
 * Access outcome of the anonymous declaration surface (magic link). The codes carry the API
 * contract of the public endpoints ({@code TOKEN_INVALID} 404, {@code TOKEN_EXPIRED} 410,
 * {@code CASE_CLOSED} 409) without leaking why a token is invalid — phishing hygiene; the HTTP
 * mapping lives with the API layer, the workflow layer stays free of HTTP concerns.
 */
public final class DeclarationFormAccessException extends DomainException {
    public DeclarationFormAccessException(String code, String message) {
        super(code, message);
    }
}

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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Server-side PAdES signature validation (EU DSS). The verdict carries the signer name needed for
 * the artifact row and, when the certificate reveals a serial number, the candidate-identity match
 * input of the PESEL-versus-case policy. {@code performedChecks} keeps check names only — no
 * certificate dumps, no raw payloads (GDPR discipline of the module).
 */
public interface DeclarationSignatureValidator {
    ValidatedSignature validate(byte[] pdfBytes);

    record ValidatedSignature(
        boolean valid,
        @Nullable String signerName,
        /** serialNumber attribute of the signing certificate when present (candidate PESEL policy). */
        @Nullable String signerSerialNumber,
        List<String> performedChecks,
        List<String> failedChecks
    ) {
    }
}

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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake;

import lombok.Getter;
import org.mlodzirazem.panel.services.panelapi.core.error.DomainException;

import java.util.List;

/**
 * A refusal of the anonymous form. It depends only on the form being switched off, on global or per-client
 * traffic and on the submitted values themselves, never on anything the registry knows about a person.
 */
@Getter
public class IntakeFormException extends DomainException {
    public enum Kind {
        DISABLED, BUSY, INVALID
    }

    private final Kind kind;
    private final transient List<String> fields;

    public IntakeFormException(Kind kind, String code, String message, List<String> fields) {
        super(code, message);
        this.kind = kind;
        this.fields = List.copyOf(fields);
    }
}

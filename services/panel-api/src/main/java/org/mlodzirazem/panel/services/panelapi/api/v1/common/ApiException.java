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

package org.mlodzirazem.panel.services.panelapi.api.v1.common;

import lombok.Getter;
import org.mlodzirazem.panel.services.panelapi.core.error.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ApiException extends DomainException {
    private final transient HttpStatus status;
    private final List<String> fields;

    public ApiException(HttpStatus status, String code, String message, List<String> fields) {
        super(code, message);
        this.status = status;
        this.fields = List.copyOf(fields);
    }

    public static ApiException notLinked() {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_LINKED", "The account is not linked to a member", List.of());
    }

    public static ApiException badRequest(String code, String message, List<String> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, fields);
    }

    public static ApiException forbiddenFields(List<String> fields) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Missing permission to change the listed fields",
            fields);
    }
}

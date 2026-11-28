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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentBlockedException;
import org.mlodzirazem.panel.services.panelapi.bodies.ConfirmationRequiredException;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.DomainException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionConfirmationRequiredException;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationFormAccessException;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeFormException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@Slf4j
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return respond(e.getStatus(), ErrorResponse.of(e.getCode(), e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ErrorResponse> forbidden(ForbiddenException e) {
        return respond(HttpStatus.FORBIDDEN, ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ConfirmationRequiredException.class)
    ResponseEntity<ErrorResponse> confirmation(ConfirmationRequiredException e) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(e.getCode(), e.getMessage(), null, e.getCheck()));
    }

    @ExceptionHandler(SuspensionConfirmationRequiredException.class)
    ResponseEntity<ErrorResponse> suspensionConfirmation(SuspensionConfirmationRequiredException e) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(e.getCode(), e.getMessage(), null, e.getCheck()));
    }

    @ExceptionHandler(AssignmentBlockedException.class)
    ResponseEntity<ErrorResponse> blocked(AssignmentBlockedException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY,
            new ErrorResponse(e.getCode(), e.getMessage(), null, e.getCheck()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return respond(HttpStatus.CONFLICT, ErrorResponse.of(e.getCode(), e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(RuleViolationException.class)
    ResponseEntity<ErrorResponse> ruleViolation(RuleViolationException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BankStatementParseException.class)
    ResponseEntity<ErrorResponse> statement(BankStatementParseException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ErrorResponse.of(e.getCode().name(), "The statement file cannot be read"));
    }

    @ExceptionHandler(DeclarationFormAccessException.class)
    ResponseEntity<ErrorResponse> declarationForm(DeclarationFormAccessException e) {
        HttpStatus status = switch (e.getCode()) {
            case "TOKEN_EXPIRED" -> HttpStatus.GONE;
            case "CASE_CLOSED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.NOT_FOUND;
        };

        return respond(status, ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(IntakeFormException.class)
    ResponseEntity<ErrorResponse> intakeForm(IntakeFormException e) {
        HttpStatus status = switch (e.getKind()) {
            case DISABLED -> HttpStatus.NOT_FOUND;
            case BUSY -> HttpStatus.TOO_MANY_REQUESTS;
            case INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
        };

        return respond(status, ErrorResponse.of(e.getCode(), e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> domain(DomainException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return respond(HttpStatus.CONFLICT, ErrorResponse.of("DATA_CONFLICT", "The data conflicts with existing records"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of("INTERNAL_ERROR", "Unexpected error"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        @Nullable Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status == null ? "Error" : status.getReasonPhrase();
        String code = reason.toUpperCase().replace(' ', '_');

        if (statusCode.value() == 400) {
            code = "BAD_REQUEST";
        }

        if (statusCode.value() == 413) {
            code = "FILE_TOO_LARGE";
            reason = "The file exceeds the upload limit";
        }

        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(code, reason));
    }

    /**
     * The error body is always JSON. The content type is preset so that Spring does not negotiate it
     * against the request's Accept header: on the PDF endpoint the client asks for
     * {@code application/pdf}, the JSON body then has no acceptable representation, the handler
     * fails and the error falls through to the authenticated /error dispatch (401 instead of 404/409/410).
     */
    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}

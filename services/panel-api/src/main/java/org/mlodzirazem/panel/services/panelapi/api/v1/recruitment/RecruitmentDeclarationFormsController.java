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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * E-declaration endpoints (M4a). The back office links a case to the magic-link form
 * ({@code POST /recruitment-cases/{caseId}/declaration-link}, RECRUITMENT_MANAGE enforced by the
 * service); the candidate walks the anonymous {@code /public/declaration-form/{token}} surface:
 * landing, submit, template PDF and the PAdES-signed upload. The paper-scan fallback stays in the
 * back office. Status codes of the anonymous surface: 404 TOKEN_INVALID (no detail why), 410
 * TOKEN_EXPIRED, 409 CASE_CLOSED, 422 for the rule blocks (one generic block message).
 */
@RestController
@RequiredArgsConstructor
public class RecruitmentDeclarationFormsController {
    private final CallerResolver callers;
    private final RecruitmentEformService eform;

    public record DeclarationLinkResponse(String token, String formUrl, ZonedDateTime expiresAt,
                                          String emailDispatch) {
    }

    public record DeclarationFormRequest(
        @Nullable String formalFirstName,
        @Nullable String formalLastName,
        @Nullable String preferredFirstName,
        @Nullable String preferredLastName,
        @Nullable String pronouns,
        @Nullable String personalId,
        @Nullable String phone,
        @Nullable String email,
        @Nullable Boolean imageConsent,
        @Nullable Boolean specialCategoryConsent
    ) {
    }

    @PostMapping("/recruitment-cases/{caseId}/declaration-link")
    @ResponseStatus(HttpStatus.CREATED)
    public DeclarationLinkResponse createDeclarationLink(@PathVariable("caseId") long caseId) {
        long caller = callers.require().id();
        RecruitmentEformService.DeclarationLink link = eform.createDeclarationLink(caseId, caller);

        return new DeclarationLinkResponse(link.token(), link.formUrl(), link.expiresAt(), link.emailDispatch());
    }

    @GetMapping("/public/declaration-form/{token}")
    public RecruitmentEformService.DeclarationForm landing(@PathVariable("token") String token) {
        return eform.getForm(token);
    }

    @PostMapping("/public/declaration-form/{token}")
    public RecruitmentEformService.DeclarationForm submit(
        @PathVariable("token") String token,
        @RequestBody DeclarationFormRequest request
    ) {
        return eform.submitForm(token, new RecruitmentEformService.DeclarationFormSubmission(
            request.formalFirstName(), request.formalLastName(), request.preferredFirstName(),
            request.preferredLastName(), request.pronouns(), request.personalId(),
            request.phone(), request.email(),
            Boolean.TRUE.equals(request.imageConsent()),
            Boolean.TRUE.equals(request.specialCategoryConsent())));
    }

    @GetMapping(value = "/public/declaration-form/{token}/pdf",
        produces = MediaType.APPLICATION_PDF_VALUE)
    public byte[] declarationPdf(@PathVariable("token") String token) {
        return eform.declarationPdf(token);
    }

    @PostMapping("/public/declaration-form/{token}/declaration")
    public RecruitmentEformService.DeclarationForm attachSignedDeclaration(
        @PathVariable("token") String token,
        @RequestParam("file") MultipartFile file
    ) {
        return eform.attachSignedDeclaration(token, read(file));
    }

    @PostMapping("/recruitment-cases/{caseId}/declaration-scan")
    public RecruitmentEformService.DeclarationForm attachScan(
        @PathVariable("caseId") long caseId,
        @RequestParam("file") MultipartFile file
    ) {
        long caller = callers.require().id();

        return eform.attachScan(caseId, caller, read(file));
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException failure) {
            throw ApiException.badRequest("FILE_UNREADABLE", "The upload cannot be read", List.of());
        }
    }
}

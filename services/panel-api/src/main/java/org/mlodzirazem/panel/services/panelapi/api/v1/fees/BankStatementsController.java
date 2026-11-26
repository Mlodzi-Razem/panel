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

package org.mlodzirazem.panel.services.panelapi.api.v1.fees;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PageResponse;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.BankImportService;
import org.mlodzirazem.panel.services.panelapi.fees.BankTransactionView;
import org.mlodzirazem.panel.services.panelapi.fees.Split;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankTransactionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Statement import and the unmatched queue. Statements hold names, accounts and titles of third parties, so reading
 * them needs global FEES_MANAGE as well.
 */
@RestController
@RequestMapping("/fees")
@RequiredArgsConstructor
@Transactional
public class BankStatementsController {
    private final FeeAccess access;
    private final FeeViews views;
    private final BankImportService bankImport;

    public record SplitRequest(@Nullable Long memberId, @Nullable String amount) {
    }

    public record AssignRequest(@Nullable List<SplitRequest> splits, @Nullable Boolean rememberIban) {
    }

    public record NoteRequest(@Nullable String note) {
    }

    public record ReopenRequest(@Nullable String reason) {
    }

    @PostMapping(path = "/statements/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FeeResponses.StatementReportResponse preview(@RequestPart("file") MultipartFile file) {
        Caller caller = access.manager();

        try (InputStream in = file.getInputStream()) {
            return views.report(bankImport.preview(caller.id(), in, name(file)));
        } catch (IOException e) {
            throw unreadable();
        }
    }

    @PostMapping(path = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FeeResponses.StatementReportResponse apply(@RequestPart("file") MultipartFile file) {
        Caller caller = access.manager();

        try (InputStream in = file.getInputStream()) {
            return views.report(bankImport.apply(caller.id(), in, name(file)));
        } catch (IOException e) {
            throw unreadable();
        }
    }

    @GetMapping("/statements")
    public PageResponse<FeeResponses.StatementImportResponse> imports(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size
    ) {
        access.manager();
        var result = bankImport.imports(Parse.page(page), Parse.size(size));

        return PageResponse.of(result.items().stream().map(FeeViews::statementImport).toList(), Parse.page(page),
            Parse.size(size), result.total());
    }

    @GetMapping("/transactions")
    public PageResponse<FeeResponses.TransactionResponse> transactions(
        @RequestParam(name = "status", defaultValue = "UNMATCHED") String status,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size
    ) {
        access.manager();
        var result = bankImport.transactions(Parse.enumOf("status", status, BankTransactionStatus.class),
            Parse.page(page), Parse.size(size));

        return PageResponse.of(views.transactions(result.items()), Parse.page(page), Parse.size(size), result.total());
    }

    @GetMapping("/transactions/{transactionId}")
    public FeeResponses.TransactionResponse transaction(@PathVariable("transactionId") long transactionId) {
        access.manager();
        return one(bankImport.transaction(transactionId));
    }

    @PostMapping("/transactions/{transactionId}/assign")
    public FeeResponses.TransactionResponse assign(
        @PathVariable("transactionId") long transactionId,
        @RequestBody AssignRequest request
    ) {
        Caller caller = access.manager();
        List<SplitRequest> requested = request.splits() == null ? List.of() : request.splits();
        List<Split> splits = requested.stream()
                                      .map(s -> new Split(Parse.requiredId("memberId", s.memberId()),
                                          Parse.money("amount", s.amount())))
                                      .toList();

        return one(bankImport.assign(caller.id(), transactionId, splits,
            Boolean.TRUE.equals(request.rememberIban())));
    }

    @PostMapping("/transactions/{transactionId}/ignore")
    public FeeResponses.TransactionResponse ignore(
        @PathVariable("transactionId") long transactionId,
        @RequestBody NoteRequest request
    ) {
        Caller caller = access.manager();
        String note = request.note() == null ? "" : request.note();

        return one(bankImport.ignore(caller.id(), transactionId, note));
    }

    @PostMapping("/transactions/{transactionId}/reopen")
    public FeeResponses.TransactionResponse reopen(
        @PathVariable("transactionId") long transactionId,
        @RequestBody ReopenRequest request
    ) {
        Caller caller = access.manager();
        String reason = request.reason() == null ? "" : request.reason();

        return one(bankImport.reopen(caller.id(), transactionId, reason));
    }

    private FeeResponses.TransactionResponse one(BankTransactionView view) {
        return views.transactions(List.of(view)).getFirst();
    }

    private static String name(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null ? "statement" : name;
    }

    private static RuleViolationException unreadable() {
        return new RuleViolationException("FILE_UNREADABLE", "The statement file could not be read");
    }
}

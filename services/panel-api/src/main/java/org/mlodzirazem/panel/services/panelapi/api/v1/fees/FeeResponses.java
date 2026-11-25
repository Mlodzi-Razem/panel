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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankMatchMethod;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankTransactionStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.PaymentSource;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public final class FeeResponses {
    private FeeResponses() {
    }

    public record MemberRef(long id, String displayName, @Nullable String memberNumber) {
    }

    public record Balance(String owed, String overdue, String credit) {
    }

    public record PayTo(@Nullable String accountIban, String recipientName, String title) {
    }

    public record NextDueResponse(String month, String amount) {
    }

    public record DueResponse(long id, String month, String amount, String paid, String outstanding, DueStatus status,
                              long tierId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentResponse(
        long id,
        @Nullable Long memberId,
        String amount,
        @Nullable String allocated,
        LocalDate paidOn,
        PaymentSource source,
        boolean reversed,
        @Nullable Long bankTransactionId,
        @Nullable String note,
        @Nullable Long recordedBy,
        @Nullable ZonedDateTime reversedAt,
        @Nullable String reversalReason
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdjustmentResponse(
        long id,
        long memberId,
        AdjustmentKind kind,
        String from,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String to,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String discountAmount,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String resolutionReference,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String revokedFrom,
        boolean revoked,
        @Nullable AdjustmentReason reasonCategory,
        @Nullable String justification
    ) {
    }

    public record HistoryResponse(List<DueResponse> dues, List<PaymentResponse> payments,
                                  List<AdjustmentResponse> adjustments) {
    }

    public record SelfArrears(@Nullable LocalDate since, int months, boolean suspendsElectoralRights) {
    }

    public record MeFeesResponse(
        String reference,
        PayTo payTo,
        Balance balance,
        @Nullable NextDueResponse nextDue,
        List<DueResponse> openDues,
        HistoryResponse history,
        SelfArrears arrears
    ) {
    }

    public record CardArrears(
        @Nullable LocalDate since,
        int months,
        String overdueAmount,
        int overdueDues,
        boolean suspendsElectoralRights,
        boolean groundsForExclusion
    ) {
    }

    public record AssignmentResponse(long id, long memberId, long tierId, String validFrom) {
    }

    public record FeeCardResponse(
        long memberId,
        String reference,
        Balance balance,
        CardArrears arrears,
        List<AssignmentResponse> tierAssignments,
        List<DueResponse> dues,
        List<PaymentResponse> payments,
        List<AdjustmentResponse> adjustments,
        boolean reasonsIncluded
    ) {
    }

    public record RateResponse(long id, long tierId, String monthlyAmount, String validFrom, String resolutionReference) {
    }

    public record TierResponse(long id, String code, String name, boolean isDefault, boolean active,
                               @Nullable RateResponse currentRate) {
    }

    public record MonthOverviewResponse(String month, String expected, String collected, String outstanding,
                                        int members, int membersPaid, int membersOutstanding) {
    }

    public record OverviewResponse(@Nullable Long districtId, List<MonthOverviewResponse> months) {
    }

    public record ArrearsRow(
        long memberId,
        @Nullable String memberNumber,
        String displayName,
        @Nullable Long districtId,
        @Nullable String districtName,
        @Nullable LocalDate since,
        int months,
        String overdueAmount,
        int overdueDues,
        boolean suspendsElectoralRights,
        boolean groundsForExclusion
    ) {
    }

    public record IbanResponse(long id, String iban, long memberId) {
    }

    public record OutcomeRow(
        LocalDate bookingDate,
        String amount,
        String currency,
        @Nullable String counterpartyName,
        @Nullable String counterpartyIban,
        String title,
        String outcome,
        @Nullable MemberRef member
    ) {
    }

    public record TotalsResponse(
        int transactions,
        int newTransactions,
        int duplicates,
        int matchedByReference,
        int matchedByIban,
        int unmatched,
        int foreignCurrency,
        int outgoing,
        String matchedAmount,
        String unmatchedAmount
    ) {
    }

    public record StatementReportResponse(
        @Nullable Long importId,
        String fileName,
        String format,
        String accountIban,
        String currency,
        @Nullable String statementId,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        boolean alreadyImported,
        TotalsResponse totals,
        List<OutcomeRow> transactions
    ) {
    }

    public record StatementImportResponse(
        long id,
        String fileName,
        String format,
        String accountIban,
        String currency,
        @Nullable String statementId,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        int transactions,
        int newTransactions,
        int duplicates,
        int matched,
        int unmatched,
        int outgoing,
        long importedBy,
        @Nullable ZonedDateTime importedAt
    ) {
    }

    public record TransactionPaymentResponse(long paymentId, MemberRef member, String amount, boolean reversed) {
    }

    public record TransactionResponse(
        long id,
        long importId,
        String accountIban,
        LocalDate bookingDate,
        @Nullable LocalDate valueDate,
        String amount,
        String currency,
        @Nullable String counterpartyName,
        @Nullable String counterpartyIban,
        String title,
        @Nullable String bankReference,
        BankTransactionStatus status,
        @Nullable BankMatchMethod matchMethod,
        @Nullable String ignoredNote,
        @Nullable Long decidedBy,
        @Nullable ZonedDateTime decidedAt,
        List<TransactionPaymentResponse> payments
    ) {
    }
}

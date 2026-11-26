/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

import * as z from "zod";

const money = z.string();
const nullable = z.string().nullish();

export const balanceSchema = z.object({owed: money, overdue: money, credit: money});

export const dueSchema = z.object({
    id: z.number(),
    month: z.string(),
    amount: money,
    paid: money,
    outstanding: money,
    status: z.enum(['OPEN', 'PAID', 'CANCELLED']),
    tierId: z.number(),
});

export const paymentSchema = z.object({
    id: z.number(),
    amount: money,
    allocated: money.nullish(),
    paidOn: z.string(),
    source: z.enum(['BANK', 'CASH', 'OTHER']),
    reversed: z.boolean(),
    bankTransactionId: z.number().nullish(),
    note: nullable,
    reversalReason: nullable,
});

export const ADJUSTMENT_KINDS = ['EXEMPTION', 'DISCOUNT', 'DEFERRAL'] as const;
export const ADJUSTMENT_REASONS = ['FINANCIAL_HARDSHIP', 'HEALTH', 'OTHER'] as const;

export const adjustmentSchema = z.object({
    id: z.number(),
    kind: z.enum(ADJUSTMENT_KINDS),
    from: z.string(),
    to: nullable,
    discountAmount: nullable,
    resolutionReference: nullable,
    revokedFrom: nullable,
    revoked: z.boolean(),
    // sent by the API only to fee managers and to the member themself
    reasonCategory: z.enum(ADJUSTMENT_REASONS).nullish(),
    justification: nullable,
});

export const myFeesSchema = z.object({
    reference: z.string(),
    payTo: z.object({accountIban: nullable, recipientName: z.string(), title: z.string()}),
    balance: balanceSchema,
    nextDue: z.object({month: z.string(), amount: money}).nullish(),
    openDues: z.array(dueSchema),
    history: z.object({
        dues: z.array(dueSchema),
        payments: z.array(paymentSchema),
        adjustments: z.array(adjustmentSchema),
    }),
    arrears: z.object({since: nullable, months: z.number(), suspendsElectoralRights: z.boolean()}),
});

export const assignmentSchema = z.object({id: z.number(), tierId: z.number(), validFrom: z.string()});

export const feeCardSchema = z.object({
    memberId: z.number(),
    reference: z.string(),
    balance: balanceSchema,
    arrears: z.object({
        since: nullable,
        months: z.number(),
        overdueAmount: money,
        overdueDues: z.number(),
        suspendsElectoralRights: z.boolean(),
    }),
    tierAssignments: z.array(assignmentSchema),
    dues: z.array(dueSchema),
    payments: z.array(paymentSchema),
    adjustments: z.array(adjustmentSchema),
    reasonsIncluded: z.boolean(),
});

export const rateSchema = z.object({
    id: z.number(),
    monthlyAmount: money,
    validFrom: z.string(),
    resolutionReference: z.string(),
});

export const tierSchema = z.object({
    id: z.number(),
    code: z.string(),
    name: z.string(),
    isDefault: z.boolean(),
    active: z.boolean(),
    currentRate: rateSchema.nullish(),
});

export const overviewSchema = z.object({
    months: z.array(z.object({
        month: z.string(),
        expected: money,
        collected: money,
        outstanding: money,
        members: z.number(),
        membersPaid: z.number(),
        membersOutstanding: z.number(),
    })),
});

export const arrearsRowSchema = z.object({
    memberId: z.number(),
    memberNumber: nullable,
    displayName: z.string(),
    districtName: nullable,
    since: nullable,
    months: z.number(),
    overdueAmount: money,
    overdueDues: z.number(),
    suspendsElectoralRights: z.boolean(),
});

export const ibanSchema = z.object({id: z.number(), iban: z.string()});

export const memberRefSchema = z.object({id: z.number(), displayName: z.string(), memberNumber: nullable});

export const statementReportSchema = z.object({
    importId: z.number().nullish(),
    fileName: z.string(),
    format: z.string(),
    accountIban: z.string(),
    from: nullable,
    to: nullable,
    alreadyImported: z.boolean(),
    totals: z.object({
        transactions: z.number(),
        newTransactions: z.number(),
        duplicates: z.number(),
        matchedByReference: z.number(),
        matchedByIban: z.number(),
        unmatched: z.number(),
        foreignCurrency: z.number(),
        outgoing: z.number(),
        matchedAmount: money,
        unmatchedAmount: money,
    }),
    transactions: z.array(z.object({
        bookingDate: z.string(),
        amount: money,
        currency: z.string(),
        counterpartyName: nullable,
        title: z.string(),
        outcome: z.enum([
            'NEW_MATCHED_REFERENCE', 'NEW_MATCHED_IBAN', 'NEW_UNMATCHED', 'OUTGOING', 'DUPLICATE',
            'FOREIGN_CURRENCY',
        ]),
        member: memberRefSchema.nullish(),
    })),
});

function pageOf<T extends z.ZodType>(item: T) {
    return z.object({
        items: z.array(item),
        page: z.number(),
        size: z.number(),
        totalItems: z.number(),
        totalPages: z.number(),
    });
}

export const statementImportSchema = z.object({
    id: z.number(),
    fileName: z.string(),
    format: z.string(),
    from: nullable,
    to: nullable,
    newTransactions: z.number(),
    matched: z.number(),
    unmatched: z.number(),
    importedAt: nullable,
});

export const TRANSACTION_STATUSES = ['UNMATCHED', 'MATCHED', 'IGNORED', 'OUTGOING'] as const;

export const transactionSchema = z.object({
    id: z.number(),
    bookingDate: z.string(),
    amount: money,
    currency: z.string(),
    counterpartyName: nullable,
    counterpartyIban: nullable,
    title: z.string(),
    status: z.enum(TRANSACTION_STATUSES),
    matchMethod: z.enum(['REFERENCE', 'IBAN', 'MANUAL']).nullish(),
    ignoredNote: nullable,
    payments: z.array(z.object({paymentId: z.number(), member: memberRefSchema, amount: money, reversed: z.boolean()})),
});

export const statementImportPageSchema = pageOf(statementImportSchema);
export const transactionPageSchema = pageOf(transactionSchema);

export type Due = z.infer<typeof dueSchema>;
export type FeePayment = z.infer<typeof paymentSchema>;
export type Adjustment = z.infer<typeof adjustmentSchema>;
export type MyFees = z.infer<typeof myFeesSchema>;
export type FeeCard = z.infer<typeof feeCardSchema>;
export type Tier = z.infer<typeof tierSchema>;
export type Rate = z.infer<typeof rateSchema>;
export type FeeOverview = z.infer<typeof overviewSchema>;
export type ArrearsRow = z.infer<typeof arrearsRowSchema>;
export type StatementReport = z.infer<typeof statementReportSchema>;
export type Transaction = z.infer<typeof transactionSchema>;
export type TransactionStatus = typeof TRANSACTION_STATUSES[number];

"use server";

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

import { revalidatePath } from "next/cache";
import * as z from "zod";
import { denyUnlessSignedIn, fail, idSchema, invalid, optionalText, parseId, text } from "@/app/actions/common";
import { feeWrite, uploadStatement } from "@/panel-api/fees/fees";
import { StatementReport } from "@/panel-api/fees/types";
import { ActionResult, ApiResult } from "@/panel-api/members/types";

// Permissions and rules are enforced by the API; these actions only shape the requests.

function refresh() {
    revalidatePath('/fees', 'layout');
    revalidatePath('/members', 'layout');
    revalidatePath('/teams', 'layout');
}

async function write(path: string, body: object, method: 'POST' | 'PATCH' = 'POST'): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const result = await feeWrite(path, body, method);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true};
}

/** People type 12,50 as often as 12.50. */
function amount(formData: FormData, name: string): string | undefined {
    return optionalText(formData, name)?.replace(',', '.');
}

export async function createTierAction(formData: FormData): Promise<ActionResult> {
    return write('/tiers', {
        code: text(formData, 'code'),
        name: text(formData, 'name'),
        makeDefault: formData.get('makeDefault') === 'true',
    });
}

export async function updateTierAction(tierId: number, change: {
    active?: boolean;
    makeDefault?: boolean;
}): Promise<ActionResult> {
    const parsed = z.object({active: z.boolean().optional(), makeDefault: z.boolean().optional()}).safeParse(change);
    if (!idSchema.safeParse(tierId).success || !parsed.success) {
        return invalid('tierId');
    }
    return write(`/tiers/${tierId}`, parsed.data, 'PATCH');
}

export async function addRateAction(formData: FormData): Promise<ActionResult> {
    const tierId = parseId(formData.get('tierId'));
    if (tierId === null) {
        return invalid('tierId');
    }
    return write(`/tiers/${tierId}/rates`, {
        monthlyAmount: amount(formData, 'monthlyAmount'),
        validFrom: text(formData, 'validFrom'),
        resolutionReference: text(formData, 'resolutionReference'),
    });
}

export async function assignTierAction(formData: FormData): Promise<ActionResult> {
    const memberId = parseId(formData.get('memberId'));
    const tierId = parseId(formData.get('tierId'));
    if (memberId === null || tierId === null) {
        return invalid('tierId');
    }
    return write(`/members/${memberId}/tier`, {tierId, validFrom: text(formData, 'validFrom')});
}

export async function grantAdjustmentAction(formData: FormData): Promise<ActionResult> {
    const memberId = parseId(formData.get('memberId'));
    if (memberId === null) {
        return invalid('memberId');
    }
    return write(`/members/${memberId}/adjustments`, {
        kind: text(formData, 'kind'),
        from: text(formData, 'from'),
        to: optionalText(formData, 'to'),
        discountAmount: amount(formData, 'discountAmount'),
        reasonCategory: text(formData, 'reasonCategory'),
        justification: text(formData, 'justification'),
        resolutionReference: optionalText(formData, 'resolutionReference'),
    });
}

export async function revokeAdjustmentAction(adjustmentId: number): Promise<ActionResult> {
    if (!idSchema.safeParse(adjustmentId).success) {
        return invalid('adjustmentId');
    }
    return write(`/adjustments/${adjustmentId}/revoke`, {});
}

export async function recordPaymentAction(formData: FormData): Promise<ActionResult> {
    const memberId = parseId(formData.get('memberId'));
    if (memberId === null) {
        return invalid('memberId');
    }
    return write(`/members/${memberId}/payments`, {
        amount: amount(formData, 'amount'),
        paidOn: text(formData, 'paidOn'),
        source: text(formData, 'source'),
        note: optionalText(formData, 'note'),
    });
}

export async function reversePaymentAction(formData: FormData): Promise<ActionResult> {
    const paymentId = parseId(formData.get('paymentId'));
    if (paymentId === null) {
        return invalid('paymentId');
    }
    return write(`/payments/${paymentId}/reverse`, {reason: text(formData, 'reason')});
}

export async function confirmIbanAction(formData: FormData): Promise<ActionResult> {
    const memberId = parseId(formData.get('memberId'));
    if (memberId === null) {
        return invalid('memberId');
    }
    return write(`/members/${memberId}/ibans`, {iban: text(formData, 'iban')});
}

export async function forgetIbanAction(iban: string): Promise<ActionResult> {
    const parsed = z.string().min(5).max(64).safeParse(iban);
    if (!parsed.success) {
        return invalid('iban');
    }
    return write('/ibans/forget', {iban: parsed.data});
}

const splitsSchema = z.array(z.object({memberId: idSchema, amount: z.string().min(1).max(20)})).min(1).max(20);

export async function assignTransactionAction(
    transactionId: number,
    splits: { memberId: number; amount: string }[],
    rememberIban: boolean,
): Promise<ActionResult> {
    const parsed = splitsSchema.safeParse(splits.map(s => ({...s, amount: s.amount.trim().replace(',', '.')})));
    if (!idSchema.safeParse(transactionId).success || !parsed.success) {
        return invalid('splits');
    }
    return write(`/transactions/${transactionId}/assign`, {splits: parsed.data, rememberIban: rememberIban === true});
}

export async function ignoreTransactionAction(formData: FormData): Promise<ActionResult> {
    const id = parseId(formData.get('transactionId'));
    if (id === null) {
        return invalid('transactionId');
    }
    return write(`/transactions/${id}/ignore`, {note: optionalText(formData, 'note')});
}

export async function reopenTransactionAction(formData: FormData): Promise<ActionResult> {
    const id = parseId(formData.get('transactionId'));
    if (id === null) {
        return invalid('transactionId');
    }
    return write(`/transactions/${id}/reopen`, {reason: optionalText(formData, 'reason')});
}

async function statement(formData: FormData, preview: boolean): Promise<ActionResult<StatementReport>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const file = formData.get('file');
    if (!(file instanceof File) || file.size === 0) {
        return invalid('file');
    }
    const result: ApiResult<StatementReport> = await uploadStatement(file, preview);
    if (!result.ok) {
        return fail(result.error);
    }
    if (!preview) {
        refresh();
    }
    return {ok: true, data: result.data};
}

export async function previewStatementAction(formData: FormData): Promise<ActionResult<StatementReport>> {
    return statement(formData, true);
}

export async function importStatementAction(formData: FormData): Promise<ActionResult<StatementReport>> {
    return statement(formData, false);
}

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
import { apiCall, apiMultipartCall } from "@/panel-api/members/client";
import {
    arrearsRowSchema,
    feeCardSchema,
    ibanSchema,
    myFeesSchema,
    overviewSchema,
    rateSchema,
    statementImportPageSchema,
    statementReportSchema,
    tierSchema,
    transactionPageSchema,
    TransactionStatus,
} from "@/panel-api/fees/types";

export const getMyFees = () => apiCall({method: 'GET', path: '/me/fees'}, myFeesSchema);

export const getFeeCard = (memberId: number) =>
    apiCall({method: 'GET', path: `/fees/members/${memberId}`}, feeCardSchema);

export const getFeeOverview = (months: number, districtId?: number) =>
    apiCall({method: 'GET', path: '/fees/overview', query: {months, districtId}}, overviewSchema);

export const getArrears = (longerThan: number, districtId?: number) =>
    apiCall({method: 'GET', path: '/fees/arrears', query: {longerThan, districtId}}, z.array(arrearsRowSchema));

export const listTiers = () =>
    apiCall({method: 'GET', path: '/fees/tiers', query: {includeInactive: 'true'}}, z.array(tierSchema));

export const listRates = (tierId: number) =>
    apiCall({method: 'GET', path: `/fees/tiers/${tierId}/rates`}, z.array(rateSchema));

export const listIbans = (memberId: number) =>
    apiCall({method: 'GET', path: `/fees/members/${memberId}/ibans`}, z.array(ibanSchema));

export const listStatementImports = (page: number) =>
    apiCall({method: 'GET', path: '/fees/statements', query: {page, size: 10}}, statementImportPageSchema);

export const listTransactions = (status: TransactionStatus, page: number) =>
    apiCall({method: 'GET', path: '/fees/transactions', query: {status, page, size: 25}}, transactionPageSchema);

/** The response of a write is not used: pages re-read what they show. */
export const feeWrite = (path: string, body: object, method: 'POST' | 'PATCH' = 'POST') =>
    apiCall({method, path: `/fees${path}`, body}, z.unknown());

export const uploadStatement = (file: File, preview: boolean) => {
    const body = new FormData();
    body.set('file', file, file.name);
    return apiMultipartCall(`/fees/statements${preview ? '/preview' : ''}`, body, statementReportSchema);
};

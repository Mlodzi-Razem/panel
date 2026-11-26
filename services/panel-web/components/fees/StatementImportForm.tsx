"use client";

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

import { useRef, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { importStatementAction, previewStatementAction } from "@/app/actions/fees";
import Button from "@/components/button/Button";
import { ErrorStatus } from "@/components/form/ActionForm";
import formStyles from "@/components/form/Form.module.scss";
import { Money } from "@/components/fees/format";
import { FormattedDate } from "@/components/members/format";
import { Notice, TableWrap } from "@/components/page/Page";
import { StatementReport } from "@/panel-api/fees/types";
import { ApiError } from "@/panel-api/members/types";

/** Nothing is saved until the treasurer has seen the preview of exactly the file that gets imported. */
export default function StatementImportForm() {
    const t = useTranslations('fees');
    const router = useRouter();
    const input = useRef<HTMLInputElement>(null);
    const [file, setFile] = useState<File | null>(null);
    const [report, setReport] = useState<StatementReport | null>(null);
    const [error, setError] = useState<ApiError | null>(null);
    const [pending, startTransition] = useTransition();

    const send = (preview: boolean) => {
        if (!file) {
            return;
        }
        const data = new FormData();
        data.set('file', file);
        startTransition(async () => {
            const outcome = await (preview ? previewStatementAction : importStatementAction)(data);
            if (!outcome.ok) {
                setError(outcome.error);
                return;
            }
            setError(null);
            setReport(outcome.data ?? null);
            if (!preview) {
                setFile(null);
                if (input.current) {
                    input.current.value = '';
                }
                router.refresh();
            }
        });
    };

    const saved = report !== null && report.importId !== null && report.importId !== undefined;

    return <div className={formStyles.form} aria-busy={pending}>
        <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor="statement-file">{t('statementFile')}</label>
            <input id="statement-file" ref={input} className={formStyles.input} type="file"
                   accept=".xml,.sta,.mt940,.txt"
                   onChange={e => {
                       setFile(e.target.files?.[0] ?? null);
                       setReport(null);
                       setError(null);
                   }}/>
            <span className={formStyles.hint}>{t('statementHint')}</span>
        </div>
        <div className={formStyles.buttons}>
            <Button onClick={() => send(true)} disabled={!file || pending}>{t('preview')}</Button>
            <Button variant="outline" onClick={() => send(false)}
                    disabled={!file || pending || !report || saved || report.alreadyImported}>
                {t('importStatement')}
            </Button>
        </div>
        <div aria-live="polite">
            {error && <ErrorStatus error={error}/>}
            {saved && <Notice tone="success" role="status">{t('imported')}</Notice>}
            {report?.alreadyImported && !saved && <Notice tone="warning">{t('alreadyImported')}</Notice>}
        </div>
        {report && <>
            <p>{t('reportSummary', {
                transactions: report.totals.transactions,
                fresh: report.totals.newTransactions,
                duplicates: report.totals.duplicates,
                matched: report.totals.matchedByReference + report.totals.matchedByIban,
                unmatched: report.totals.unmatched + report.totals.foreignCurrency,
                outgoing: report.totals.outgoing,
            })}</p>
            <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('bookingDate')}</th>
                        <th scope="col">{t('amount')}</th>
                        <th scope="col">{t('counterparty')}</th>
                        <th scope="col">{t('transferTitle')}</th>
                        <th scope="col">{t('outcomeLabel')}</th>
                    </tr>
                </thead>
                <tbody>
                    {report.transactions.map((row, index) => <tr key={index}>
                        <td><FormattedDate value={row.bookingDate}/></td>
                        <td>{row.currency === 'PLN' ? <Money value={row.amount}/> : `${row.amount} ${row.currency}`}</td>
                        <td>{row.counterpartyName ?? '—'}</td>
                        <td>{row.title}</td>
                        <td>
                            {t(`outcome.${row.outcome}`)}
                            {row.member && <div>{row.member.displayName}
                                {row.member.memberNumber ? ` (${row.member.memberNumber})` : ''}</div>}
                        </td>
                    </tr>)}
                </tbody>
            </TableWrap>
        </>}
    </div>;
}

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

import { getTranslations } from "next-intl/server";
import FeeTabs, { managesFees } from "@/components/fees/FeeTabs";
import { forbidden } from "@/components/fees/guard";
import StatementImportForm from "@/components/fees/StatementImportForm";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Card, Muted, Page, TableWrap } from "@/components/page/Page";
import { Pagination } from "@/components/ui/Blocks";
import { listStatementImports } from "@/panel-api/fees/fees";
import { requireMe } from "@/panel-api/members/requireMe";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function ImportPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }
    const t = await getTranslations('fees');
    if (!managesFees(me)) {
        return forbidden(t('title'));
    }
    const page = Math.max(0, Number.parseInt(String((await searchParams)['page'] ?? '0'), 10) || 0);
    const imports = await listStatementImports(page);

    return <Page title={t('title')} subtitle={t('importSubtitle')}>
        <FeeTabs me={me} active="import"/>
        <Card title={t('newImport')} id="new-import">
            <StatementImportForm/>
        </Card>
        <Card title={t('importHistory')} id="import-history">
            {!imports.ok ? <ApiErrorNotice error={imports.error}/>
                : imports.data.items.length === 0 ? <p><Muted>{t('noImports')}</Muted></p> : <>
                    <TableWrap>
                        <thead>
                            <tr>
                                <th scope="col">{t('statementFile')}</th>
                                <th scope="col">{t('period')}</th>
                                <th scope="col">{t('newTransactions')}</th>
                                <th scope="col">{t('matchedCount')}</th>
                                <th scope="col">{t('unmatchedCount')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {imports.data.items.map(row => <tr key={row.id}>
                                <td>{row.fileName}</td>
                                <td><FormattedDate value={row.from}/> – <FormattedDate value={row.to}/></td>
                                <td>{row.newTransactions}</td>
                                <td>{row.matched}</td>
                                <td>{row.unmatched}</td>
                            </tr>)}
                        </tbody>
                    </TableWrap>
                    <Pagination page={imports.data.page} totalPages={imports.data.totalPages}
                                totalItems={imports.data.totalItems} size={10} defaultSize={10} sizeOptions={[10]}
                                hrefFor={p => p > 0 ? `/fees/import?page=${p}` : '/fees/import'}/>
                </>}
        </Card>
    </Page>;
}

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

import React from "react";
import { getTranslations } from "next-intl/server";
import { CalendarBlankIcon } from "@phosphor-icons/react/ssr";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import MembersTable from "@/components/members/MembersTable";
import { Notice, Page } from "@/components/page/Page";
import { ResultCount, Toolbar } from "@/components/ui/Blocks";
import { UrlFilterSelect } from "@/components/ui/UrlControls";
import { membersEndingThisYear } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

function currentYear(): number {
    return new Date().getUTCFullYear();
}

export default async function EndingThisYearPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('members');
    const te = await getTranslations('ending');
    const params = await searchParams;
    const yearRaw = Array.isArray(params['year']) ? params['year'][0] : params['year'];
    const thisYear = currentYear();
    const years = [thisYear, thisYear + 1];
    const year = years.find(y => String(y) === yearRaw) ?? thisYear;
    const result = await membersEndingThisYear(year);

    return <Page title={t('endingTitle')} subtitle={t('endingHint')} backHref="/members" backLabel={t('title')}>
        <Notice tone="info">{te('notice')}</Notice>
        <Toolbar end={result.ok && <ResultCount>{te('resultCount', {count: result.data.length})}</ResultCount>}>
            <UrlFilterSelect param="year"
                             label={te('year')}
                             defaultValue={String(thisYear)}
                             icon={<CalendarBlankIcon aria-hidden/>}
                             options={years.map(y => ({value: String(y), label: String(y)}))}/>
        </Toolbar>
        {result.ok
            ? <MembersTable items={result.data} showEnding/>
            : <ApiErrorNotice error={result.error}/>}
    </Page>;
}

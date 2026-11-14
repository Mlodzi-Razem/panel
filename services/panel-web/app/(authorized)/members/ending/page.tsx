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
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import MembersTable from "@/components/members/MembersTable";
import { Card, Page } from "@/components/page/Page";
import { membersEndingThisYear } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function EndingThisYearPage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('members');
    const result = await membersEndingThisYear();

    return <Page title={t('endingTitle')} subtitle={t('endingHint')} backHref="/members" backLabel={t('title')}>
        <Card>
            {result.ok
                ? <MembersTable items={result.data} showEnding/>
                : <ApiErrorNotice error={result.error}/>}
        </Card>
    </Page>;
}

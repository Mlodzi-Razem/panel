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
import { Tabs } from "@/components/ui/Blocks";
import { hasPermission } from "@/panel-api/members/me";
import { LinkedMe, PERMISSIONS } from "@/panel-api/members/types";

export type FeeTab = 'mine' | 'finance' | 'tiers' | 'import' | 'transactions';

export function managesFees(me: LinkedMe): boolean {
    return me.permissions.global.includes(PERMISSIONS.feesManage);
}

export function readsFees(me: LinkedMe): boolean {
    return hasPermission(me, PERMISSIONS.feesRead);
}

/** Only what the person may open is offered; the API decides again on every request. */
export default async function FeeTabs({me, active}: { me: LinkedMe; active: FeeTab }) {
    const t = await getTranslations('fees');
    const reader = readsFees(me);
    const manager = managesFees(me);
    if (!reader && !manager) {
        return null;
    }
    const tabs: { key: FeeTab; href: string; shown: boolean }[] = [
        {key: 'mine', href: '/fees', shown: true},
        {key: 'finance', href: '/fees/finance', shown: reader},
        {key: 'tiers', href: '/fees/tiers', shown: reader},
        {key: 'import', href: '/fees/import', shown: manager},
        {key: 'transactions', href: '/fees/transactions', shown: manager},
    ];
    return <Tabs label={t('tabsLabel')}
                 items={tabs.filter(tab => tab.shown)
                            .map(tab => ({href: tab.href, label: t(`tabs.${tab.key}`), active: tab.key === active}))}/>;
}

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
import { MoneyIcon } from "@phosphor-icons/react/ssr";
import { Card, Page } from "@/components/page/Page";
import { EmptyState } from "@/components/ui/Blocks";

export default async function FeesPage() {
    const t = await getTranslations('fees');
    return <Page title={t('title')} subtitle={t('subtitle')}>
        <Card>
            <EmptyState icon={<MoneyIcon aria-hidden/>} title={t('comingSoonTitle')}>
                <span>{t('comingSoon')}</span>
            </EmptyState>
        </Card>
    </Page>;
}

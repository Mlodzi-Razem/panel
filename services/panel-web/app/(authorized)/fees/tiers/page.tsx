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
import FeeTabs, { managesFees, readsFees } from "@/components/fees/FeeTabs";
import { Money, Month } from "@/components/fees/format";
import { forbidden } from "@/components/fees/guard";
import { AddRateForm, CreateTierForm, TierButtons } from "@/components/fees/TierForms";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { Badge, Card, Muted, Notice, Page, TableWrap } from "@/components/page/Page";
import { listRates, listTiers } from "@/panel-api/fees/fees";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function TiersPage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }
    const t = await getTranslations('fees');
    if (!readsFees(me)) {
        return forbidden(t('title'));
    }
    const manager = managesFees(me);
    const tiers = await listTiers();
    const rates = tiers.ok ? await Promise.all(tiers.data.map(tier => listRates(tier.id))) : [];
    const currentMonth = new Date().toISOString().slice(0, 7);

    return <Page title={t('title')} subtitle={t('tiersSubtitle')}>
        <FeeTabs me={me} active="tiers"/>
        {manager && <Notice tone="warning">{t('rateEffect')}</Notice>}
        {!tiers.ok ? <ApiErrorNotice error={tiers.error}/> : <>
            {tiers.data.length === 0 && <p><Muted>{t('noTiers')}</Muted></p>}
            {tiers.data.map((tier, index) => {
                const history = rates[index];
                return <Card key={tier.id} id={`tier-${tier.id}`} title={`${tier.name} (${tier.code})`}
                             actions={manager && <TierButtons tierId={tier.id} isDefault={tier.isDefault}
                                                              active={tier.active}/>}>
                    <p>
                        {tier.isDefault && <Badge tone="brand">{t('defaultTier')}</Badge>}{' '}
                        {!tier.active && <Badge tone="muted">{t('inactive')}</Badge>}{' '}
                        {t('currentRate')}: <Money value={tier.currentRate?.monthlyAmount}/>
                    </p>
                    {!history.ok ? <ApiErrorNotice error={history.error}/>
                        : history.data.length === 0 ? <p><Muted>{t('noRates')}</Muted></p>
                            : <TableWrap>
                                <thead>
                                    <tr>
                                        <th scope="col">{t('validFrom')}</th>
                                        <th scope="col">{t('monthlyAmount')}</th>
                                        <th scope="col">{t('resolution')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {history.data.map(rate => <tr key={rate.id}>
                                        <td><Month value={rate.validFrom}/></td>
                                        <td><Money value={rate.monthlyAmount}/></td>
                                        <td>{rate.resolutionReference}</td>
                                    </tr>)}
                                </tbody>
                            </TableWrap>}
                    {manager && <AddRateForm tierId={tier.id} currentMonth={currentMonth}/>}
                </Card>;
            })}
        </>}
        {manager && <Card title={t('newTier')} id="new-tier">
            <CreateTierForm/>
        </Card>}
    </Page>;
}

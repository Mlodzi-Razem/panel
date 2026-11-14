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
import { BodyCard } from "@/components/bodies/BodyViews";
import CreateBodyForm from "@/components/bodies/CreateBodyForm";
import Button from "@/components/button/Button";
import formStyles from "@/components/form/Form.module.scss";
import { Card, LinkButton, Notice, Page } from "@/components/page/Page";
import { getBody, listBodies, listBodyTemplates } from "@/panel-api/members/bodies";
import { requireMe } from "@/panel-api/members/requireMe";
import { Body } from "@/panel-api/members/types";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const NATIONAL_ORDER = ['GENERAL_ASSEMBLY', 'NATIONAL_BOARD', 'PEER_COURT', 'AUDIT_COMMISSION', 'ELECTORAL_COMMISSION',
    'PRESS_SPOKESPERSON', 'TEAM'];
const DISTRICT_ORDER = ['DISTRICT_ASSEMBLY', 'DISTRICT_BOARD', 'DISTRICT_TREASURER', 'DISTRICT_COMMISSIONER',
    'LOCAL_CIRCLE'];

function rank(order: string[], body: Body): number {
    const index = order.indexOf(body.kind ?? '');
    return index === -1 ? order.length : index;
}

function sorted(order: string[], bodies: Body[]): Body[] {
    return [...bodies].sort((a, b) => rank(order, a) - rank(order, b) || a.name.localeCompare(b.name));
}

export default async function BodiesPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('bodies');
    const params = await searchParams;
    const districtRaw = Array.isArray(params['district']) ? params['district'][0] : params['district'];
    const districtId = districtRaw && /^\d+$/.test(districtRaw) ? Number(districtRaw) : null;

    const [bodiesResult, templatesResult] = await Promise.all([
        listBodies(),
        me.canManageBodies ? listBodyTemplates() : Promise.resolve(null),
    ]);
    if (!bodiesResult.ok) {
        return <Page title={t('title')}><ApiErrorNotice error={bodiesResult.error}/></Page>;
    }

    const bodies = bodiesResult.data;
    const districts = bodies.filter(b => b.kind === 'DISTRICT').sort((a, b) => a.name.localeCompare(b.name));
    const selectedDistrict = districts.find(d => d.id === districtId) ?? null;

    const shown = selectedDistrict
        ? sorted(DISTRICT_ORDER, bodies.filter(b => b.parentBodyId === selectedDistrict.id))
        : sorted(NATIONAL_ORDER, bodies.filter(b => b.kind !== 'DISTRICT' && (b.parentBodyId ?? null) === null));
    const details = await Promise.all(shown.map(b => getBody(b.id)));

    return <Page title={t('title')} subtitle={t('subtitle')}>
        <Card>
            <form method="get" action="/bodies" className={formStyles.fieldGrid} aria-label={t('scopeLabel')}>
                <div className={formStyles.field}>
                    <label className={formStyles.label} htmlFor="bodies-district">{t('scope')}</label>
                    <select id="bodies-district"
                            name="district"
                            className={formStyles.input}
                            defaultValue={selectedDistrict ? String(selectedDistrict.id) : ''}>
                        <option value="">{t('national')}</option>
                        {districts.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                    </select>
                </div>
                <div className={formStyles.field}>
                    <span className={formStyles.label} aria-hidden="true">&nbsp;</span>
                    <Button type="submit" variant="outline">{t('show')}</Button>
                </div>
            </form>
            {selectedDistrict && me.canManageBodies && <div>
                <LinkButton href={`/bodies/${selectedDistrict.id}`}>{t('manageDistrict')}</LinkButton>
            </div>}
        </Card>

        {shown.length === 0 && <Notice>{t('noBodies')}</Notice>}
        {details.map((detail, index) => detail.ok
            ? <BodyCard key={shown[index]!.id} detail={detail.data} canManage={me.canManageBodies} linkMembers={me.canSeeMembers}/>
            : <ApiErrorNotice key={shown[index]!.id} error={detail.error}/>)}

        {me.canManageBodies && <Card id="create-body" title={t('createTitle')} hint={t('createHint')}>
            {templatesResult?.ok
                ? <CreateBodyForm templates={templatesResult.data} bodies={bodies}/>
                : templatesResult && <ApiErrorNotice error={templatesResult.error}/>}
        </Card>}
    </Page>;
}

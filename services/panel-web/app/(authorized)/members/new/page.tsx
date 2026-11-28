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

import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { createActMemberAction } from "@/app/actions/membershipActs";
import { createMemberAction } from "@/app/actions/members";
import ActionForm from "@/components/form/ActionForm";
import { SelectField, TextField } from "@/components/form/Fields";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import FieldInputs from "@/components/members/FieldInputs";
import { Card, Page } from "@/components/page/Page";
import { FIELDS } from "@/panel-api/members/fields";
import { listDistricts } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { PERMISSIONS } from "@/panel-api/members/types";

type Kind = 'ordinary' | 'supporting' | 'honorary';

function kindOf(raw: string | string[] | undefined): Kind {
    return raw === 'supporting' || raw === 'honorary' ? raw : 'ordinary';
}

export default async function NewMemberPage({searchParams}: {
    searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const kind = kindOf((await searchParams)['type']);
    const t = await getTranslations('members');
    const districts = await listDistricts();
    if (!districts.ok) {
        return <Page title={t('newMember')} backHref="/members" backLabel={t('title')}>
            <ApiErrorNotice error={districts.error}/>
        </Page>;
    }

    const targets = districts.data.filter(d => d.permissions.includes(PERMISSIONS.formalManage));
    if (targets.length === 0) {
        return <Page title={t('newMember')} backHref="/members" backLabel={t('title')}>
            <ApiErrorNotice error={{status: 403, code: 'FORBIDDEN', message: ''}}/>
        </Page>;
    }

    const sections = [
        {key: 'sectionNames', defs: FIELDS.filter(f => f.group === 'NAMING')},
        {key: 'sectionFormal', defs: FIELDS.filter(f => f.group === 'FORMAL')},
        {key: 'sectionContact', defs: FIELDS.filter(f => f.group === 'CONTACT')},
        {key: 'organisationalTitle', defs: FIELDS.filter(f => f.group === 'ORGANISATIONAL')},
    ] as const;

    // Supporting and honorary memberships are granted by a resolution: they go through a membership act
    // (statute art. 9 ust. 1 pkt 1, art. 12 ust. 3) and carry the date and reference of that resolution.
    const act = kind !== 'ordinary';
    const action = act
        ? createActMemberAction.bind(null, kind === 'supporting' ? 'SUPPORTING' : 'HONORARY')
        : createMemberAction;
    const title = t(act ? `acts.newTitle.${kind}` : 'newMember');

    return <Page title={title} subtitle={t('newMemberHint')} backHref="/members" backLabel={t('title')}>
        <nav aria-label={t('acts.kindNav')}>
            <Link href="/members/new">{t('acts.kindChoice.ordinary')}</Link>{' · '}
            <Link href="/members/new?type=supporting">{t('acts.kindChoice.supporting')}</Link>{' · '}
            <Link href="/members/new?type=honorary">{t('acts.kindChoice.honorary')}</Link>
        </nav>
        <ActionForm action={action} submitLabel={t('createMember')} ariaLabel={title}>
            <Card title={t('sectionDistrict')}>
                <SelectField name="primaryDistrictId"
                             label={t('primaryDistrict')}
                             required
                             emptyLabel={targets.length === 1 ? undefined : t('chooseDistrict')}
                             options={targets.map(d => ({value: String(d.id), label: d.name}))}/>
            </Card>
            {act && <Card title={t('acts.resolutionTitle')}>
                <TextField name="grantedOn" label={t('acts.field.grantedOn')} type="date" required/>
                <TextField name="decisionReference" label={t('acts.field.decisionReference')} required
                           maxLength={200}/>
            </Card>}
            {sections.map(section => <Card key={section.key} title={t(section.key)}>
                <FieldInputs defs={section.defs} requireMandatory showGroupTitles={false}/>
            </Card>)}
        </ActionForm>
    </Page>;
}

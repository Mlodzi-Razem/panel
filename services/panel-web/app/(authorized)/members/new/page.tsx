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
import { createMemberAction } from "@/app/actions/members";
import ActionForm from "@/components/form/ActionForm";
import { SelectField } from "@/components/form/Fields";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import FieldInputs from "@/components/members/FieldInputs";
import { Card, Page } from "@/components/page/Page";
import { FIELDS } from "@/panel-api/members/fields";
import { listDistricts } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { PERMISSIONS } from "@/panel-api/members/types";

export default async function NewMemberPage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

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

    return <Page title={t('newMember')} subtitle={t('newMemberHint')} backHref="/members" backLabel={t('title')}>
        <ActionForm action={createMemberAction} submitLabel={t('createMember')} ariaLabel={t('newMember')}>
            <Card title={t('sectionDistrict')}>
                <SelectField name="primaryDistrictId"
                             label={t('primaryDistrict')}
                             required
                             emptyLabel={targets.length === 1 ? undefined : t('chooseDistrict')}
                             options={targets.map(d => ({value: String(d.id), label: d.name}))}/>
            </Card>
            {sections.map(section => <Card key={section.key} title={t(section.key)}>
                <FieldInputs defs={section.defs} requireMandatory showGroupTitles={false}/>
            </Card>)}
        </ActionForm>
    </Page>;
}

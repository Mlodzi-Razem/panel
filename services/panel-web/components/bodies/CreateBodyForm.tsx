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

"use client";

import React, { useState } from "react";
import { useTranslations } from "next-intl";
import { createBodyAction } from "@/app/actions/bodies";
import ActionForm from "@/components/form/ActionForm";
import { SelectField, TextField } from "@/components/form/Fields";
import { Body, BodyTemplate } from "@/panel-api/members/types";

export default function CreateBodyForm({templates, bodies}: { templates: BodyTemplate[]; bodies: Body[] }) {
    const t = useTranslations('bodies');
    const tEnums = useTranslations('enums');
    const [templateId, setTemplateId] = useState('');

    const template = templates.find(x => String(x.id) === templateId);
    const parentTemplateId = template?.parentTemplateId ?? null;
    const parents = parentTemplateId === null ? [] : bodies.filter(b => b.templateId === parentTemplateId);

    return <ActionForm action={createBodyAction} submitLabel={t('createBody')} ariaLabel={t('createTitle')}>
        <SelectField name="templateId"
                     label={t('template')}
                     required
                     emptyLabel={t('chooseTemplate')}
                     onChange={setTemplateId}
                     options={templates.map(x => ({
                         value: String(x.id),
                         label: `${x.name} (${tEnums.has(`bodyKind.${x.kind}`) ? tEnums(`bodyKind.${x.kind}`) : x.kind})`,
                     }))}/>
        {parentTemplateId !== null && <SelectField name="parentBodyId"
                                                   label={t('parent')}
                                                   required
                                                   emptyLabel={t('chooseParent')}
                                                   options={parents.map(p => ({value: String(p.id), label: p.name}))}/>}
        <TextField name="name" label={t('name')} required maxLength={200}/>
        <TextField name="email" label={t('email')} type="email" maxLength={200}/>
        <TextField name="code" label={t('code')} maxLength={50} hint={t('codeHint')}/>
    </ActionForm>;
}

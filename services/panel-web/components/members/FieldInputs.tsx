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
import { useTranslations } from "next-intl";
import { ListField, SelectField, TextField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import {
    FIELD_GROUPS,
    FieldDef,
    IDENTIFIER_FIELD,
    MEMBER_STATUSES,
    MEMBERSHIP_TYPES,
} from "@/panel-api/members/fields";

export type FieldInputsProps = {
    defs: readonly FieldDef[];
    values?: Record<string, unknown>;
    requireMandatory?: boolean;
    showGroupTitles?: boolean;
};

function asString(value: unknown): string {
    return typeof value === 'string' ? value : '';
}

function asList(value: unknown): string[] {
    return Array.isArray(value) ? value.map(String) : [];
}

function Input({def, values, requireMandatory}: {
    def: FieldDef;
    values: Record<string, unknown>;
    requireMandatory: boolean;
}) {
    const t = useTranslations('fields');
    const tEnums = useTranslations('enums');
    const tMembers = useTranslations('members');
    const label = t(def.name);
    const required = requireMandatory && def.required === true;

    switch (def.kind) {
        case 'list':
            return <ListField name={def.name}
                              label={label}
                              defaultValue={asList(values[def.name])}
                              type={def.name.toLowerCase().includes('email') ? 'email'
                                  : def.name === 'phoneNumbers' ? 'tel' : 'text'}/>;
        case 'status':
            return <SelectField name={def.name}
                                label={label}
                                defaultValue={asString(values[def.name]) || (requireMandatory ? 'ACTIVE' : '')}
                                emptyLabel={requireMandatory ? undefined : tMembers('notSet')}
                                options={MEMBER_STATUSES.map(v => ({value: v, label: tEnums(`memberStatus.${v}`)}))}/>;
        case 'membershipType':
            return <SelectField name={def.name}
                                label={label}
                                defaultValue={asString(values[def.name]) || (requireMandatory ? 'ORDINARY' : '')}
                                emptyLabel={requireMandatory ? undefined : tMembers('notSet')}
                                options={MEMBERSHIP_TYPES.map(v => ({value: v, label: tEnums(`membershipType.${v}`)}))}/>;
        case 'date':
            return <TextField name={def.name} label={label} type="date" required={required}
                              defaultValue={asString(values[def.name])}/>;
        case 'url':
            return <TextField name={def.name} label={label} type="url" defaultValue={asString(values[def.name])}/>;
        case 'email':
            return <TextField name={def.name} label={label} type="email" defaultValue={asString(values[def.name])}/>;
        default:
            if (def.name === IDENTIFIER_FIELD) {
                const masked = asString(values[def.name]);
                return <TextField name={def.name}
                                  label={label}
                                  defaultValue=""
                                  placeholder={masked}
                                  hint={tMembers('identifierHint')}
                                  maxLength={32}/>;
            }
            return <TextField name={def.name} label={label} required={required}
                              defaultValue={asString(values[def.name])}/>;
    }
}

export default function FieldInputs({defs, values = {}, requireMandatory = false, showGroupTitles = true}: FieldInputsProps) {
    const tGroups = useTranslations('fieldGroups');
    return <>
        {FIELD_GROUPS.map(group => {
            const groupDefs = defs.filter(d => d.group === group);
            if (groupDefs.length === 0) {
                return null;
            }
            return <fieldset key={group} className={formStyles.field}>
                {showGroupTitles && <legend className={formStyles.label}>{tGroups(group)}</legend>}
                <div className={formStyles.fieldGrid}>
                    {groupDefs.map(def => <Input key={def.name}
                                                 def={def}
                                                 values={values}
                                                 requireMandatory={requireMandatory}/>)}
                </div>
            </fieldset>;
        })}
    </>;
}

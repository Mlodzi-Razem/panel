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
import { useFormatter, useTranslations } from "next-intl";
import { Badge, DescriptionList, Muted, Tone } from "@/components/page/Page";
import { FIELD_GROUPS, FieldDef, FieldGroup } from "@/panel-api/members/fields";

function str(fields: Record<string, unknown>, key: string): string {
    const value = fields[key];
    return typeof value === 'string' ? value.trim() : '';
}

export function memberName(fields: Record<string, unknown>): string {
    const full = [str(fields, 'preferredNameFirst'), str(fields, 'preferredNameLast')].filter(Boolean).join(' ');
    return full || str(fields, 'pseudonym');
}

export function FormattedDate({value}: { value: string | null | undefined }) {
    const format = useFormatter();
    if (!value) {
        return null;
    }
    const date = new Date(value.length === 10 ? `${value}T00:00:00Z` : value);
    if (Number.isNaN(date.getTime())) {
        return <>{value}</>;
    }
    return <time dateTime={value}>
        {format.dateTime(date, value.length === 10
            ? {dateStyle: 'medium', timeZone: 'UTC'}
            : {dateStyle: 'medium', timeStyle: 'short'})}
    </time>;
}

export function memberStatusTone(status: string | null | undefined): Tone {
    switch (status) {
        case 'ACTIVE':
            return 'success';
        case 'PROBATION':
            return 'warning';
        case 'FORM_REQUEST':
            return 'info';
        case 'SUSPENDED':
            return 'danger';
        case 'REMOVED':
            return 'muted';
        default:
            return 'neutral';
    }
}

export function changeRequestStatusTone(status: string | null | undefined): Tone {
    switch (status) {
        case 'PENDING':
            return 'info';
        case 'APPROVED':
            return 'success';
        case 'REJECTED':
            return 'danger';
        case 'CANCELLED':
            return 'muted';
        default:
            return 'neutral';
    }
}

export function Empty() {
    const t = useTranslations('common');
    return <Muted>{t('notProvided')}</Muted>;
}

function isBlank(value: unknown): boolean {
    return value === null || value === undefined || value === ''
        || (Array.isArray(value) && value.length === 0);
}

export function FieldValue({def, value}: { def: Pick<FieldDef, 'name' | 'kind'>; value: unknown }) {
    const tEnums = useTranslations('enums');
    if (isBlank(value)) {
        return <Empty/>;
    }
    if (Array.isArray(value)) {
        return <>{value.map(String).join(', ')}</>;
    }
    const text = String(value);
    switch (def.kind) {
        case 'date':
            return <FormattedDate value={text}/>;
        case 'status':
            return <Badge tone={memberStatusTone(text)}>
                {tEnums.has(`memberStatus.${text}`) ? tEnums(`memberStatus.${text}`) : text}
            </Badge>;
        case 'membershipType':
            return <>{tEnums.has(`membershipType.${text}`) ? tEnums(`membershipType.${text}`) : text}</>;
        case 'url':
            return /^https?:\/\//i.test(text)
                ? <a href={text} rel="noopener noreferrer nofollow" target="_blank">{text}</a>
                : <>{text}</>;
        default:
            return <>{text}</>;
    }
}

export type FieldListProps = {
    defs: readonly FieldDef[];
    values: Record<string, unknown>;
    slots?: Record<string, React.ReactNode>;
    showAbsent?: boolean;
};

export function FieldList({defs, values, slots, showAbsent = false}: FieldListProps) {
    const t = useTranslations('fields');
    const visible = defs.filter(def => showAbsent || def.name in values);
    return <>
        {visible.map(def => <div key={def.name}>
            <dt>{t(def.name)}</dt>
            <dd>{slots?.[def.name] ?? <FieldValue def={def} value={values[def.name]}/>}</dd>
        </div>)}
    </>;
}

export function hasAnyField(values: Record<string, unknown>, defs: readonly FieldDef[]): boolean {
    return defs.some(def => def.name in values);
}

export function GroupTitle({group}: { group: FieldGroup }) {
    const t = useTranslations('fieldGroups');
    return <>{t(group)}</>;
}

export function GroupedFieldList({defs, values, slots}: {
    defs: readonly FieldDef[];
    values: Record<string, unknown>;
    slots?: Record<string, React.ReactNode>;
}) {
    const tGroups = useTranslations('fieldGroups');
    return <>
        {FIELD_GROUPS.map(group => {
            const groupDefs = defs.filter(d => d.group === group && d.name in values);
            if (groupDefs.length === 0) {
                return null;
            }
            return <section key={group} aria-label={tGroups(group)}>
                <h3>{tGroups(group)}</h3>
                <DescriptionList>
                    <FieldList defs={groupDefs} values={values} slots={slots}/>
                </DescriptionList>
            </section>;
        })}
    </>;
}

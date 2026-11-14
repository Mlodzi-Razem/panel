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

export type FieldGroup = 'CONTACT' | 'NAMING' | 'FORMAL' | 'ORGANISATIONAL';
export type FieldKind = 'text' | 'url' | 'date' | 'list' | 'status' | 'membershipType' | 'email' | 'tel';

export type FieldDef = {
    name: string;
    group: FieldGroup;
    kind: FieldKind;
    autoComplete?: string;
    required?: boolean;
};

export const FIELD_GROUPS: readonly FieldGroup[] = ['CONTACT', 'NAMING', 'FORMAL', 'ORGANISATIONAL'];

export const MEMBER_STATUSES = ['ACTIVE', 'PROBATION', 'SUSPENDED', 'FORM_REQUEST', 'REMOVED'] as const;
export const MEMBERSHIP_TYPES = ['ORDINARY', 'SUPPORTING', 'HONORARY'] as const;

export const FIELDS: readonly FieldDef[] = [
    {name: 'privateEmails', group: 'CONTACT', kind: 'list'},
    {name: 'phoneNumbers', group: 'CONTACT', kind: 'list'},
    {name: 'socialProfileUrl', group: 'CONTACT', kind: 'url'},

    {name: 'preferredNameFirst', group: 'NAMING', kind: 'text', required: true},
    {name: 'preferredNameMiddle', group: 'NAMING', kind: 'text'},
    {name: 'preferredNameLast', group: 'NAMING', kind: 'text', required: true},
    {name: 'pseudonym', group: 'NAMING', kind: 'text'},
    {name: 'pronouns', group: 'NAMING', kind: 'text'},
    {name: 'institution', group: 'NAMING', kind: 'text'},

    {name: 'formalNameFirst', group: 'FORMAL', kind: 'text', required: true},
    {name: 'formalNameLast', group: 'FORMAL', kind: 'text', required: true},
    {name: 'formalIdentifier', group: 'FORMAL', kind: 'text'},
    {name: 'birthDate', group: 'FORMAL', kind: 'date', required: true},
    {name: 'addressLine', group: 'FORMAL', kind: 'text'},
    {name: 'addressCountryCode', group: 'FORMAL', kind: 'text'},
    {name: 'addressPostalCode', group: 'FORMAL', kind: 'text'},
    {name: 'addressState', group: 'FORMAL', kind: 'text'},
    {name: 'addressProvince', group: 'FORMAL', kind: 'text'},
    {name: 'addressCity', group: 'FORMAL', kind: 'text'},
    {name: 'addressStreet', group: 'FORMAL', kind: 'text'},
    {name: 'addressBuilding', group: 'FORMAL', kind: 'text'},
    {name: 'addressApartment', group: 'FORMAL', kind: 'text'},

    {name: 'status', group: 'ORGANISATIONAL', kind: 'status'},
    {name: 'membershipType', group: 'ORGANISATIONAL', kind: 'membershipType'},
    {name: 'memberId', group: 'ORGANISATIONAL', kind: 'text'},
    {name: 'memberSince', group: 'ORGANISATIONAL', kind: 'date'},
    {name: 'probationPeriodStartDate', group: 'ORGANISATIONAL', kind: 'date'},
    {name: 'probationPeriodEndDate', group: 'ORGANISATIONAL', kind: 'date'},
    {name: 'requestDate', group: 'ORGANISATIONAL', kind: 'date'},
    {name: 'removedAt', group: 'ORGANISATIONAL', kind: 'date'},
    {name: 'domainEmail', group: 'ORGANISATIONAL', kind: 'email'},
    {name: 'domainEmailAliases', group: 'ORGANISATIONAL', kind: 'list'},
    {name: 'declarationReference', group: 'ORGANISATIONAL', kind: 'text'},
];

export function fieldsOfGroups(groups: readonly FieldGroup[]): FieldDef[] {
    return FIELDS.filter(f => groups.includes(f.group));
}

export function fieldDef(name: string): FieldDef | undefined {
    return FIELDS.find(f => f.name === name);
}

export const IDENTIFIER_FIELD = 'formalIdentifier';

export type SubmittedValue = string | string[];

function cleanString(value: unknown): string {
    return typeof value === 'string' ? value.trim() : '';
}

export function normalizeValue(def: FieldDef, value: unknown): SubmittedValue {
    if (def.kind === 'list') {
        return Array.isArray(value) ? value.map(cleanString).filter(v => v.length > 0) : [];
    }
    return cleanString(value);
}

export function readSubmitted(formData: FormData, defs: readonly FieldDef[]): Record<string, SubmittedValue> {
    const listNames = new Set(formData.getAll('__lists').map(String));
    const result: Record<string, SubmittedValue> = {};
    for (const def of defs) {
        if (def.kind === 'list') {
            if (listNames.has(def.name)) {
                result[def.name] = normalizeValue(def, formData.getAll(def.name).map(String));
            }
        } else if (formData.has(def.name)) {
            result[def.name] = normalizeValue(def, formData.get(def.name));
        }
    }
    return result;
}

export function toPayload(value: SubmittedValue): string | string[] | null {
    return value.length === 0 ? null : value;
}

export function changedPayload(
    submitted: Record<string, SubmittedValue>,
    current: Record<string, unknown>,
    defs: readonly FieldDef[],
): Record<string, string | string[] | null> {
    const result: Record<string, string | string[] | null> = {};
    for (const def of defs) {
        const value = submitted[def.name];
        if (value === undefined) {
            continue;
        }
        if (def.name === IDENTIFIER_FIELD && value === '') {
            continue;
        }
        const before = normalizeValue(def, current[def.name]);
        if (JSON.stringify(before) !== JSON.stringify(value)) {
            result[def.name] = toPayload(value);
        }
    }
    return result;
}

export function createPayload(
    submitted: Record<string, SubmittedValue>,
): Record<string, string | string[] | null> {
    const result: Record<string, string | string[] | null> = {};
    for (const [name, value] of Object.entries(submitted)) {
        const payload = toPayload(value);
        if (payload !== null) {
            result[name] = payload;
        }
    }
    return result;
}

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

import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { updateMemberAction } from "@/app/actions/members";
import EditCard from "@/components/form/EditCard";
import AffiliationsCard from "@/components/members/AffiliationsCard";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import FieldInputs from "@/components/members/FieldInputs";
import { Empty, GroupedFieldList, hasAnyField, memberName } from "@/components/members/format";
import MemberOverview from "@/components/members/MemberOverview";
import RevealIdentifier from "@/components/members/RevealIdentifier";
import { Badge, Card, Page } from "@/components/page/Page";
import { FieldDef, fieldsOfGroups } from "@/panel-api/members/fields";
import { getMember, listDistricts } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { PERMISSIONS } from "@/panel-api/members/types";

export default async function MemberPage({params}: { params: Promise<{ id: string }> }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const {id: rawId} = await params;
    if (!/^\d+$/.test(rawId)) {
        notFound();
    }
    const id = Number(rawId);

    const t = await getTranslations('members');
    const tEnums = await getTranslations('enums');
    const result = await getMember(id);
    if (!result.ok) {
        if (result.error.status === 404) {
            notFound();
        }
        return <Page title={t('title')} backHref="/members" backLabel={t('title')}>
            <ApiErrorNotice error={result.error}/>
        </Page>;
    }

    const member = result.data;
    const {access, fields} = member;
    const districts = access.manageFormal ? await listDistricts() : null;
    const targetDistricts = districts?.ok
        ? districts.data.filter(d => d.permissions.includes(PERMISSIONS.formalManage))
        : [];

    const present = (defs: FieldDef[]) => defs.filter(d => d.name in fields);
    const selfDefs = present(fieldsOfGroups(['CONTACT', 'NAMING']));
    const formalDefs = present(fieldsOfGroups(['FORMAL']));
    const orgDefs = present(fieldsOfGroups(['ORGANISATIONAL']));
    const status = typeof fields['status'] === 'string' ? fields['status'] : null;
    const identifier = typeof fields['formalIdentifier'] === 'string' ? fields['formalIdentifier'] : null;
    const updateAction = updateMemberAction.bind(null, id);

    return <Page title={memberName(fields) || `#${id}`}
                 backHref="/members"
                 backLabel={t('title')}
                 actions={status && <Badge tone={status === 'ACTIVE' ? 'success' : 'warning'}>
                     {tEnums(`memberStatus.${status}`)}
                 </Badge>}>
        {hasAnyField(fields, selfDefs) && (access.manageContact
            ? <EditCard id="member-contact"
                        title={t('contactTitle')}
                        action={updateAction}
                        successMessage={t('memberSaved')}
                        readView={<GroupedFieldList defs={selfDefs} values={fields}/>}>
                <FieldInputs defs={selfDefs} values={fields}/>
            </EditCard>
            : <Card id="member-contact" title={t('contactTitle')}>
                <GroupedFieldList defs={selfDefs} values={fields}/>
            </Card>)}

        {hasAnyField(fields, formalDefs) && (() => {
            const slots = {
                formalIdentifier: access.manageFormal && !access.formalIdentifierRevealed
                    ? <RevealIdentifier memberId={id} masked={identifier}/>
                    : identifier ? <code>{identifier}</code> : <Empty/>,
            };
            return access.manageFormal
                ? <EditCard id="member-formal"
                            title={t('formalTitle')}
                            action={updateAction}
                            successMessage={t('memberSaved')}
                            readView={<GroupedFieldList defs={formalDefs} values={fields} slots={slots}/>}>
                    <FieldInputs defs={formalDefs} values={fields} showGroupTitles={false}/>
                </EditCard>
                : <Card id="member-formal" title={t('formalTitle')}>
                    <GroupedFieldList defs={formalDefs} values={fields} slots={slots}/>
                </Card>;
        })()}

        {hasAnyField(fields, orgDefs) && (access.manageFormal
            ? <EditCard id="member-organisational"
                        title={t('organisationalTitle')}
                        action={updateAction}
                        successMessage={t('memberSaved')}
                        readView={<GroupedFieldList defs={orgDefs} values={fields}/>}>
                <FieldInputs defs={orgDefs} values={fields} showGroupTitles={false}/>
            </EditCard>
            : <Card id="member-organisational" title={t('organisationalTitle')}>
                <GroupedFieldList defs={orgDefs} values={fields}/>
            </Card>)}

        {access.manageFormal && member.affiliations
            && <AffiliationsCard memberId={id}
                                 affiliations={member.affiliations}
                                 targetDistricts={targetDistricts.map(d => ({id: d.id, name: d.name}))}/>}

        <MemberOverview member={member} showAffiliations={!(access.manageFormal && member.affiliations)}/>
    </Page>;
}

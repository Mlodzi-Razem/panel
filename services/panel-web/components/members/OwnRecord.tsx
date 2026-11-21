/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
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
import { requestFormalChangeAction, updateOwnProfileAction } from "@/app/actions/me";
import { getPanelServerSession } from "@/auth/authOptions";
import EditCard from "@/components/form/EditCard";
import { TextAreaField } from "@/components/form/Fields";
import AffiliationsCard from "@/components/members/AffiliationsCard";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import ChangeRequestList from "@/components/members/ChangeRequestList";
import FieldInputs from "@/components/members/FieldInputs";
import { Empty, GroupedFieldList, hasAnyField } from "@/components/members/format";
import { BodyFunctionsCard, ElectoralRights, MembershipEndNotice } from "@/components/members/MemberOverview";
import AccessMatrix from "@/components/members/record/AccessMatrix";
import RecordShell, { RecordTab, resolveTab } from "@/components/members/record/RecordShell";
import { Card, LinkButton } from "@/components/page/Page";
import { fieldsOfGroups } from "@/panel-api/members/fields";
import { listOwnChangeRequests } from "@/panel-api/members/me";
import { requireMe } from "@/panel-api/members/requireMe";

/**
 * The signed-in member's own record: profile, formal data change requests, districts, functions and access.
 * Rendered at `/user` and in the Teams tab _Moje członkostwo_.
 */
export default async function OwnRecord({basePath, tab}: {
    basePath: string;
    tab: string | string[] | undefined;
}) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('user');
    const tm = await getTranslations('members');
    const session = await getPanelServerSession();
    const requests = await listOwnChangeRequests();

    const member = me.member;
    const fields = member.fields;
    const selfDefs = fieldsOfGroups(['CONTACT', 'NAMING']);
    const formalDefs = fieldsOfGroups(['FORMAL']);
    const orgDefs = fieldsOfGroups(['ORGANISATIONAL']);
    const identifier = typeof fields['formalIdentifier'] === 'string' ? fields['formalIdentifier'] : null;
    const hasFormal = hasAnyField(fields, formalDefs);

    const tabs: RecordTab[] = [{key: 'details', label: tm('tabDetails')}];
    if (member.affiliations) {
        tabs.push({key: 'districts', label: tm('tabDistricts'), count: member.affiliations.length});
    }
    tabs.push({key: 'functions', label: tm('tabFunctions'), count: member.bodyFunctions.length});
    tabs.push({key: 'requests', label: t('tabRequests'), count: requests.ok ? requests.data.length : undefined});
    tabs.push({key: 'access', label: t('tabAccess')});
    const activeTab = resolveTab(tab, tabs);

    return <RecordShell title={t('title')}
                        image={session?.user?.image}
                        member={member}
                        basePath={basePath}
                        tabs={tabs}
                        activeTab={activeTab}
                        actions={hasFormal && !me.pendingChangeRequest
                            && <LinkButton href={`${basePath}#own-formal`} primary>{t('requestChange')}</LinkButton>}>
        {activeTab === 'details' && <>
            <EditCard id="own-profile"
                      title={t('profileTitle')}
                      hint={t('profileHint')}
                      action={updateOwnProfileAction}
                      successMessage={t('profileSaved')}
                      readView={<GroupedFieldList defs={selfDefs} values={fields}/>}>
                <FieldInputs defs={selfDefs} values={fields}/>
            </EditCard>

            {hasFormal && <EditCard id="own-formal"
                                    title={t('formalTitle')}
                                    hint={t('formalHint')}
                                    editLabel={t('requestChange')}
                                    submitLabel={t('sendRequest')}
                                    successMessage={t('requestSent')}
                                    disabledReason={me.pendingChangeRequest ? t('pendingExists') : undefined}
                                    action={requestFormalChangeAction}
                                    readView={<GroupedFieldList defs={formalDefs}
                                                                values={fields}
                                                                slots={{
                                                                    formalIdentifier: identifier
                                                                        ? <code>{identifier}</code>
                                                                        : <Empty/>,
                                                                }}/>}>
                <FieldInputs defs={formalDefs} values={fields} showGroupTitles={false}/>
                <TextAreaField name="reason" label={t('reasonLabel')} hint={t('reasonHint')} maxLength={2000}/>
            </EditCard>}

            {hasAnyField(fields, orgDefs) && <Card id="own-organisational" title={t('organisationalTitle')}>
                <GroupedFieldList defs={orgDefs} values={fields}/>
            </Card>}

            <ElectoralRights member={member}/>
            <MembershipEndNotice endsOn={member.membershipEndsOn}/>
        </>}

        {activeTab === 'districts' && member.affiliations
            && <AffiliationsCard memberId={member.id} affiliations={member.affiliations} readOnly/>}

        {activeTab === 'functions' && <BodyFunctionsCard functions={member.bodyFunctions}/>}

        {activeTab === 'requests' && (requests.ok
            ? <ChangeRequestList requests={requests.data}/>
            : <ApiErrorNotice error={requests.error}/>)}

        {activeTab === 'access' && <AccessMatrix permissions={me.permissions}/>}
    </RecordShell>;
}

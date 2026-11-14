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
import EditCard from "@/components/form/EditCard";
import { TextAreaField } from "@/components/form/Fields";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import ChangeRequestList from "@/components/members/ChangeRequestList";
import FieldInputs from "@/components/members/FieldInputs";
import { Empty, GroupedFieldList, hasAnyField, memberName } from "@/components/members/format";
import MemberOverview from "@/components/members/MemberOverview";
import { Badge, Card, Page } from "@/components/page/Page";
import { fieldsOfGroups } from "@/panel-api/members/fields";
import { listOwnChangeRequests } from "@/panel-api/members/me";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function UserPage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('user');
    const tEnums = await getTranslations('enums');
    const requests = await listOwnChangeRequests();

    const member = me.member;
    const fields = member.fields;
    const selfDefs = fieldsOfGroups(['CONTACT', 'NAMING']);
    const formalDefs = fieldsOfGroups(['FORMAL']);
    const orgDefs = fieldsOfGroups(['ORGANISATIONAL']);
    const status = typeof fields['status'] === 'string' ? fields['status'] : null;
    const identifier = typeof fields['formalIdentifier'] === 'string' ? fields['formalIdentifier'] : null;

    return <Page title={memberName(fields) || t('title')}
                 subtitle={t('subtitle')}
                 actions={status && <Badge tone={status === 'ACTIVE' ? 'success' : 'warning'}>
                     {tEnums(`memberStatus.${status}`)}
                 </Badge>}>
        <EditCard id="own-profile"
                  title={t('profileTitle')}
                  hint={t('profileHint')}
                  action={updateOwnProfileAction}
                  successMessage={t('profileSaved')}
                  readView={<GroupedFieldList defs={selfDefs} values={fields}/>}>
            <FieldInputs defs={selfDefs} values={fields}/>
        </EditCard>

        {hasAnyField(fields, formalDefs) && <EditCard id="own-formal"
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

        <MemberOverview member={member}/>

        {requests.ok
            ? <ChangeRequestList requests={requests.data}/>
            : <ApiErrorNotice error={requests.error}/>}
    </Page>;
}

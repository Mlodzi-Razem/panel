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
import { UsersThreeIcon } from "@phosphor-icons/react/ssr";
import { updateBodyAction } from "@/app/actions/bodies";
import AssignFunctionForm from "@/components/bodies/AssignFunctionForm";
import { KindBadge } from "@/components/bodies/BodyViews";
import EndMembershipForm from "@/components/bodies/EndMembershipForm";
import EditCard from "@/components/form/EditCard";
import { TextField } from "@/components/form/Fields";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Badge, Card, DescriptionList, Page, TableWrap } from "@/components/page/Page";
import { EmptyState, NameCell, RecordHeader } from "@/components/ui/Blocks";
import { getBody } from "@/panel-api/members/bodies";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function BodyPage({params}: { params: Promise<{ id: string }> }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const {id: rawId} = await params;
    if (!/^\d+$/.test(rawId)) {
        notFound();
    }
    const id = Number(rawId);

    const t = await getTranslations('bodies');
    const result = await getBody(id);
    if (!result.ok) {
        if (result.error.status === 404) {
            notFound();
        }
        return <Page title={t('title')} backHref="/bodies" backLabel={t('title')}>
            <ApiErrorNotice error={result.error}/>
        </Page>;
    }

    const {body, composition, memberTypes} = result.data;
    const canManage = me.canManageBodies;

    const header = <RecordHeader name={body.name}
                                 badges={<KindBadge kind={body.kind}/>}
                                 subtitle={body.email ? <a href={`mailto:${body.email}`}>{body.email}</a> : undefined}
                                 facts={[
                                     ...(body.templateName ? [{label: t('template'), value: body.templateName}] : []),
                                     {label: t('membersCount'), value: composition.length},
                                 ]}/>;

    return <Page title={body.name}
                 backHref={body.parentBodyId ? `/bodies?district=${body.parentBodyId}` : '/bodies'}
                 backLabel={t('title')}
                 header={header}>
        {canManage
            ? <EditCard id="body-details"
                        title={t('detailsTitle')}
                        action={updateBodyAction.bind(null, id)}
                        successMessage={t('bodySaved')}
                        readView={<DescriptionList>
                            <div><dt>{t('name')}</dt><dd>{body.name}</dd></div>
                            <div><dt>{t('email')}</dt><dd>{body.email ?? '—'}</dd></div>
                            {body.templateName && <div><dt>{t('template')}</dt><dd>{body.templateName}</dd></div>}
                        </DescriptionList>}>
                <TextField name="name" label={t('name')} required defaultValue={body.name} maxLength={200}/>
                <TextField name="email" label={t('email')} type="email" defaultValue={body.email ?? ''} maxLength={200}/>
            </EditCard>
            : null}

        <Card id="composition" title={t('compositionTitle')}>
            {composition.length === 0
                ? <EmptyState icon={<UsersThreeIcon aria-hidden/>} title={t('noComposition')}/>
                : <TableWrap>
                    <thead>
                        <tr>
                            <th scope="col">{t('member')}</th>
                            <th scope="col">{t('role')}</th>
                            <th scope="col">{t('validFrom')}</th>
                            <th scope="col">{t('validTo')}</th>
                            {canManage && <th scope="col">{t('endMembership')}</th>}
                        </tr>
                    </thead>
                    <tbody>
                        {composition.map(entry => <tr key={entry.membershipId}>
                            <td>
                                <NameCell name={entry.memberName}
                                          href={me.canSeeMembers ? `/members/${entry.memberId}` : undefined}/>
                            </td>
                            <td><Badge tone="brand">{entry.memberTypeName}</Badge></td>
                            <td><FormattedDate value={entry.validFrom}/></td>
                            <td>{entry.validTo ? <FormattedDate value={entry.validTo}/> : t('ongoing')}</td>
                            {canManage && <td>
                                <EndMembershipForm bodyId={id}
                                                   membershipId={entry.membershipId}
                                                   memberName={entry.memberName}/>
                            </td>}
                        </tr>)}
                    </tbody>
                </TableWrap>}
        </Card>

        {canManage && <Card id="assign" title={t('assignTitle')} hint={t('assignHint')}>
            {memberTypes.length === 0
                ? <p>{t('noRoles')}</p>
                : <AssignFunctionForm bodyId={id} memberTypes={memberTypes}/>}
        </Card>}
    </Page>;
}

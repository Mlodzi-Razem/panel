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

import MemberTypeName from "@/components/bodies/MemberTypeName";
import React from "react";
import Link from "next/link";
import { useFormatter, useTranslations } from "next-intl";
import { BriefcaseIcon } from "@phosphor-icons/react/ssr";
import { FormattedDate } from "@/components/members/format";
import styles from "@/components/members/record/record.module.scss";
import { Badge, Card, DescriptionList, Notice, TableWrap } from "@/components/page/Page";
import { EmptyState } from "@/components/ui/Blocks";
import { BodyFunction, MemberDetail } from "@/panel-api/members/types";

export function BodyFunctionsCard({functions, linkBodies = true}: { functions: BodyFunction[]; linkBodies?: boolean }) {
    const t = useTranslations('members');
    return <Card id="functions" title={t('functionsTitle')} hint={t('functionsHint')}>
        {functions.length === 0
            ? <EmptyState icon={<BriefcaseIcon aria-hidden/>} title={t('noFunctions')}/>
            : <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('fnBody')}</th>
                        <th scope="col">{t('fnRole')}</th>
                        <th scope="col">{t('fnFrom')}</th>
                        <th scope="col">{t('fnTo')}</th>
                    </tr>
                </thead>
                <tbody>
                    {functions.map(f => <tr key={f.membershipId}>
                        <td>{linkBodies ? <Link href={`/bodies/${f.bodyId}`}>{f.bodyName}</Link> : f.bodyName}</td>
                        <td><Badge tone="brand"><MemberTypeName code={f.memberTypeCode} name={f.memberTypeName}/></Badge></td>
                        <td><FormattedDate value={f.validFrom}/></td>
                        <td>{f.validTo ? <FormattedDate value={f.validTo}/> : t('ongoing')}</td>
                    </tr>)}
                </tbody>
            </TableWrap>}
    </Card>;
}

export function ElectoralRights({member}: { member: MemberDetail }) {
    const t = useTranslations('electoral');
    const rights = member.electoralRights;
    if (!rights) {
        return null;
    }
    const votingDistrict = member.affiliations?.find(a => a.districtId === rights.votingDistrictId);
    return <Card id="electoral-rights" title={t('title')} hint={t('hint')}>
        <DescriptionList>
            <div>
                <dt>{t('eligibleLabel')}</dt>
                <dd><Badge tone={rights.eligible ? 'success' : 'warning'}>
                    {rights.eligible ? t('yes') : t('no')}
                </Badge></dd>
            </div>
            <div>
                <dt>{t('nationalLabel')}</dt>
                <dd><Badge tone={rights.canVoteInNationalElection ? 'success' : 'muted'}>
                    {rights.canVoteInNationalElection ? t('yes') : t('no')}
                </Badge></dd>
            </div>
            {votingDistrict && <div>
                <dt>{t('districtLabel')}</dt>
                <dd>{votingDistrict.districtName}</dd>
            </div>}
        </DescriptionList>
        <Notice tone={rights.eligible ? 'success' : 'warning'} role="status">
            <p>{rights.eligible ? t('eligible') : t('notEligible')}</p>
            {!rights.eligible && <ul>
                {!rights.activeOrdinaryMember && <li>{t('reasonNotOrdinary')}</li>}
                {rights.suspended && <li>{t('reasonSuspended')}</li>}
                {rights.feeArrears && <li>{t('reasonArrears')}</li>}
            </ul>}
            <p>{rights.canVoteInNationalElection ? t('nationalYes') : t('nationalNo')}</p>
            {votingDistrict && <p>{t('votingDistrict', {district: votingDistrict.districtName})}</p>}
        </Notice>
    </Card>;
}

export function MembershipEndNotice({endsOn}: { endsOn: string | null | undefined }) {
    const t = useTranslations('members');
    const format = useFormatter();
    if (!endsOn) {
        return null;
    }
    return <Notice tone="info" role="status">
        <p className={styles.rowTitle}>{t('membershipEndTitle')}</p>
        <p>{t('membershipEndText', {
            date: format.dateTime(new Date(`${endsOn}T00:00:00Z`), {dateStyle: 'long', timeZone: 'UTC'}),
        })}</p>
    </Notice>;
}

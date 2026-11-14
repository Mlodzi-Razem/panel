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
import Link from "next/link";
import { useFormatter, useTranslations } from "next-intl";
import { FormattedDate } from "@/components/members/format";
import { Badge, Card, Notice } from "@/components/page/Page";
import { Affiliation, BodyFunction, MemberDetail } from "@/panel-api/members/types";

export function AffiliationList({affiliations}: { affiliations: Affiliation[] }) {
    const t = useTranslations('members');
    const tEnums = useTranslations('enums');
    if (affiliations.length === 0) {
        return <p>{t('noAffiliations')}</p>;
    }
    return <ul>
        {affiliations.map(a => <li key={a.districtId}>
            {a.districtName}{' '}
            <Badge>{tEnums(`affiliationKind.${a.kind}`)}</Badge>{' '}
            {a.votesHere && <Badge tone="success">{t('votesHere')}</Badge>}{' '}
            <span>{t('since')} <FormattedDate value={a.validFrom}/></span>
        </li>)}
    </ul>;
}

export function BodyFunctionList({functions, linkBodies}: { functions: BodyFunction[]; linkBodies: boolean }) {
    const t = useTranslations('members');
    if (functions.length === 0) {
        return <p>{t('noFunctions')}</p>;
    }
    return <ul>
        {functions.map(f => <li key={f.membershipId}>
            {linkBodies
                ? <Link href={`/bodies/${f.bodyId}`}>{f.bodyName}</Link>
                : f.bodyName}
            {' – '}{f.memberTypeName}{' '}
            <span>
                (<FormattedDate value={f.validFrom}/>{' – '}
                {f.validTo ? <FormattedDate value={f.validTo}/> : t('ongoing')})
            </span>
        </li>)}
    </ul>;
}

export function ElectoralRights({member}: { member: MemberDetail }) {
    const t = useTranslations('electoral');
    const rights = member.electoralRights;
    if (!rights) {
        return null;
    }
    const votingDistrict = member.affiliations?.find(a => a.districtId === rights.votingDistrictId);
    return <Card id="electoral-rights" title={t('title')} hint={t('hint')}>
        <Notice tone={rights.eligible ? 'success' : 'warning'} role="status">
            {rights.eligible ? t('eligible') : t('notEligible')}
        </Notice>
        {!rights.eligible && <ul>
            {!rights.activeOrdinaryMember && <li>{t('reasonNotOrdinary')}</li>}
            {rights.suspended && <li>{t('reasonSuspended')}</li>}
            {rights.feeArrears && <li>{t('reasonArrears')}</li>}
        </ul>}
        <p>{rights.canVoteInNationalElection ? t('nationalYes') : t('nationalNo')}</p>
        {votingDistrict && <p>{t('votingDistrict', {district: votingDistrict.districtName})}</p>}
    </Card>;
}

export default function MemberOverview({member, showAffiliations = true, linkBodies = true}: {
    member: MemberDetail;
    showAffiliations?: boolean;
    linkBodies?: boolean;
}) {
    const t = useTranslations('members');
    const format = useFormatter();
    return <>
        {showAffiliations && member.affiliations && <Card id="affiliations" title={t('affiliationsTitle')}>
            <AffiliationList affiliations={member.affiliations}/>
        </Card>}
        <Card id="functions" title={t('functionsTitle')}>
            <BodyFunctionList functions={member.bodyFunctions} linkBodies={linkBodies}/>
        </Card>
        {member.membershipEndsOn && <Card id="membership-end" title={t('membershipEndTitle')}>
            <p>{t('membershipEndText', {
                date: format.dateTime(new Date(`${member.membershipEndsOn}T00:00:00Z`), {
                    dateStyle: 'long',
                    timeZone: 'UTC',
                }),
            })}</p>
        </Card>}
        <ElectoralRights member={member}/>
    </>;
}

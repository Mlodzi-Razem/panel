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

import React from "react";
import { useTranslations } from "next-intl";
import {
    changeMembershipTypeAction,
    grantHonoraryToMemberAction,
    reinstateMemberAction,
    removeMemberAction,
} from "@/app/actions/membershipActs";
import ActionForm from "@/components/form/ActionForm";
import { SelectField, TextField } from "@/components/form/Fields";
import { FormattedDate } from "@/components/members/format";
import styles from "@/components/members/record/record.module.scss";
import { Card } from "@/components/page/Page";
import { MembershipAct } from "@/panel-api/members/membershipActs";

const GROUNDS = [
    'DEATH', 'INCAPACITATION', 'LOSS_OF_PUBLIC_RIGHTS', 'RESIGNATION', 'AGE_26', 'EXCLUSION', 'HONORARY_REVOKED',
] as const;

/** Grounds that apply to the member's type: AGE_26 only for ordinary, HONORARY_REVOKED only for honorary. */
function groundsFor(membershipType: string): readonly string[] {
    return GROUNDS.filter(g => {
        if (g === 'AGE_26') {
            return membershipType === 'ORDINARY';
        }
        if (g === 'HONORARY_REVOKED') {
            return membershipType === 'HONORARY';
        }
        return true;
    });
}

/** Acts were not recorded for members whose state predates the register; they are shown as such. */
function isLegacy(acts: MembershipAct[], status: string, membershipType: string): boolean {
    const has = (kind: MembershipAct['kind']) => acts.some(a => a.kind === kind);
    return (status === 'REMOVED' && !has('REMOVAL'))
        || (membershipType === 'HONORARY' && !has('HONORARY_GRANT'))
        || (membershipType === 'SUPPORTING' && !has('SUPPORTING_GRANT'));
}

export default function MembershipActsCard({memberId, status, membershipType, acts, canGrantHonorary = false}: {
    memberId: number;
    status: string;
    membershipType: string;
    acts: MembershipAct[];
    /** Honorary membership is granted by ZK only (art. 21 ust. 2 pkt 4): the same global permission the API checks. */
    canGrantHonorary?: boolean;
}) {
    const t = useTranslations('members');
    const tEnums = useTranslations('enums');
    const tActs = (key: string) => t(`acts.${key}`);
    const removed = status === 'REMOVED';
    const legacy = isLegacy(acts, status, membershipType);
    const otherType = membershipType === 'ORDINARY' ? 'SUPPORTING' : 'ORDINARY';
    const canChangeType = !removed && (membershipType === 'ORDINARY' || membershipType === 'SUPPORTING');

    return <Card id="member-membership-acts" title={tActs('title')} hint={tActs('hint')}>
        {legacy && <p className={styles.cardHint}>{tActs('legacy')}</p>}

        {acts.length === 0
            ? <p>{tActs('empty')}</p>
            : <ul className={styles.districtRows}>
                {acts.map(act => <li key={act.id}>
                    <div>
                        <span className={styles.rowTitle}>{tActs(`kind.${act.kind}`)}</span>
                        <small>
                            <FormattedDate value={act.effectiveOn}/> · {tActs('field.decisionReference')}: {act.decisionReference}
                        </small>
                        {act.removalGround && <small>{tActs('field.ground')}: {tActs(`ground.${act.removalGround}`)}</small>}
                        {act.membershipTypeAfter && <small>{tActs('field.targetType')}: {tEnums(`membershipType.${act.membershipTypeAfter}`)}</small>}
                        {act.reason && <small>{tActs('field.reason')}: {act.reason}</small>}
                    </div>
                </li>)}
            </ul>}

        <div className={styles.manage}>
            {!removed && <ActionForm ariaLabel={tActs('action.remove')}
                                    submitLabel={tActs('action.remove')}
                                    successMessage={tActs('saved')}
                                    resetOnSuccess
                                    action={fd => removeMemberAction(memberId, {
                                        ground: String(fd.get('ground') ?? ''),
                                        removedOn: String(fd.get('removedOn') ?? ''),
                                        decisionReference: String(fd.get('decisionReference') ?? ''),
                                    })}>
                <SelectField name="ground" label={tActs('field.ground')} required
                             options={groundsFor(membershipType).map(g => ({value: g, label: tActs(`ground.${g}`)}))}/>
                <TextField name="removedOn" label={tActs('field.removedOn')} type="date" required/>
                <TextField name="decisionReference" label={tActs('field.decisionReference')} required
                           maxLength={200}/>
                <p className={styles.cardHint}>{tActs('exclusionHint')}</p>
            </ActionForm>}

            {removed && <ActionForm ariaLabel={tActs('action.reinstate')}
                                   submitLabel={tActs('action.reinstate')}
                                   successMessage={tActs('saved')}
                                   resetOnSuccess
                                   action={fd => reinstateMemberAction(memberId, {
                                       effectiveOn: String(fd.get('effectiveOn') ?? ''),
                                       decisionReference: String(fd.get('decisionReference') ?? ''),
                                       reason: String(fd.get('reason') ?? ''),
                                   })}>
                <TextField name="effectiveOn" label={tActs('field.effectiveOn')} type="date" required/>
                <TextField name="decisionReference" label={tActs('field.decisionReference')} required
                           maxLength={200}/>
                <TextField name="reason" label={tActs('field.reason')} hint={tActs('field.reasonHint')} required
                           maxLength={500}/>
            </ActionForm>}

            {canChangeType && <ActionForm ariaLabel={tActs('action.changeType')}
                                         submitLabel={tActs('action.changeType')}
                                         successMessage={tActs('saved')}
                                         resetOnSuccess
                                         action={fd => changeMembershipTypeAction(memberId, {
                                             membershipType: String(fd.get('membershipType') ?? ''),
                                             effectiveOn: String(fd.get('effectiveOn') ?? ''),
                                             decisionReference: String(fd.get('decisionReference') ?? ''),
                                             reason: String(fd.get('reason') ?? ''),
                                         })}>
                <input type="hidden" name="membershipType" value={otherType}/>
                <p>{tActs('field.targetType')}: {tEnums(`membershipType.${otherType}`)}</p>
                <TextField name="effectiveOn" label={tActs('field.effectiveOn')} type="date" required/>
                <TextField name="decisionReference" label={tActs('field.decisionReference')} required
                           maxLength={200}/>
                <TextField name="reason" label={tActs('field.reason')} hint={tActs('field.reasonHint')} required
                           maxLength={500}/>
            </ActionForm>}

            {canGrantHonorary && !removed && membershipType !== 'HONORARY' && <ActionForm ariaLabel={tActs('action.grantHonorary')}
                                                                     submitLabel={tActs('action.grantHonorary')}
                                                                     successMessage={tActs('saved')}
                                                                     resetOnSuccess
                                                                     action={fd => grantHonoraryToMemberAction(memberId, {
                                                                         grantedOn: String(fd.get('grantedOn') ?? ''),
                                                                         decisionReference: String(fd.get('decisionReference') ?? ''),
                                                                     })}>
                <TextField name="grantedOn" label={tActs('field.grantedOn')} type="date" required/>
                <TextField name="decisionReference" label={tActs('field.decisionReference')} required
                           maxLength={200}/>
            </ActionForm>}
        </div>
    </Card>;
}

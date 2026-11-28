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

import { getTranslations } from "next-intl/server";
import { FormattedDate } from "@/components/members/format";
import { AppealEndedForm, EndSuspendedMandatesForm, LiftSuspensionForm, VoidSuspensionForm } from "@/components/members/SuspensionActionForms";
import SuspendMemberForm from "@/components/members/SuspendMemberForm";
import { Badge, Card, Notice, Muted, TableWrap } from "@/components/page/Page";
import { addDays } from "@/components/members/suspensionDates";
import { Suspension, SuspensionOptions, SuspensionOverview } from "@/panel-api/members/suspensions";
import type { Tone } from "@/components/page/Page";

const OPEN_STATES = ['SCHEDULED', 'ACTIVE'];

function stateTone(state: string): Tone {
    switch (state) {
        case 'ACTIVE': return 'warning';
        case 'SCHEDULED': return 'info';
        case 'VOIDED': return 'danger';
        default: return 'muted';
    }
}

/**
 * Register of suspensions of one member (art. 10). Entries are never edited or deleted: lifting, appeal and
 * correction are separate entries (or operations) with their own resolution.
 */
export default async function SuspensionsCard({memberId, overview, options, canManage, canBodiesManage}: {
    memberId: number;
    overview: SuspensionOverview;
    options: SuspensionOptions | null;
    canManage: boolean;
    canBodiesManage: boolean;
}) {
    const t = await getTranslations('suspensions');
    const tEnums = await getTranslations('enums');
    const {effects, items} = overview;
    const hintDays = options?.appealHintDays ?? 14;

    const actions = (item: Suspension) => {
        const open = OPEN_STATES.includes(item.state);
        const lossDue = item.positionLoss.due && item.positionLoss.outstanding.length > 0;
        const bodiesFor = (kind: string) => (options?.bodies ?? []).filter(b => b.allowedFor.includes(kind));
        const canAppeal = item.scope === 'FULL' && item.kind !== 'VOLUNTARY' && !item.appealEnded && item.state !== 'VOIDED';

        if (!canManage && !(canBodiesManage && lossDue)) {
            return null;
        }

        return <details>
            <summary>{t('actions')}</summary>
            {canManage && open && item.kind && <>
                <h4>{t('lift')}</h4>
                <LiftSuspensionForm memberId={memberId}
                                    suspensionId={item.id}
                                    bodies={bodiesFor(item.kind)}
                                    mandatesNote={item.arrearsHint && !item.arrearsHint.settled
                                        ? t('arrearsHintOpen', {months: item.arrearsHint.months})
                                        : t('arrearsHintSettled')}/>
            </>}
            {canManage && canAppeal && <>
                <h4>{t('appealEnded')}</h4>
                <AppealEndedForm memberId={memberId}
                                 suspensionId={item.id}
                                 suggestedOn={addDays(item.decisionDate ?? item.start, hintDays)}
                                 hintDays={hintDays}/>
            </>}
            {canBodiesManage && lossDue && <>
                <h4>{t('endMandates')}</h4>
                <EndSuspendedMandatesForm memberId={memberId}
                                          suspensionId={item.id}
                                          outstanding={item.positionLoss.outstanding}
                                          conflicting={item.positionLoss.conflicting}/>
            </>}
            {canManage && item.state !== 'VOIDED' && <>
                <h4>{t('void')}</h4>
                <VoidSuspensionForm memberId={memberId} suspensionId={item.id}/>
            </>}
        </details>;
    };

    return <Card id="member-suspensions" title={t('title')} hint={t('hint')}>
        {effects.competencesSuspended && <Notice tone="warning">
            {t('bannerCompetences')} {t('effectCompetences')}
        </Notice>}
        {effects.votingSuspended && <Notice tone="warning">{t('effectVoting')}</Notice>}

        {items.length === 0
            ? <Muted>{t('empty')}</Muted>
            : <TableWrap>
                <table>
                    <thead>
                        <tr>
                            <th scope="col">{t('columnKind')}</th>
                            <th scope="col">{t('columnScope')}</th>
                            <th scope="col">{t('columnBody')}</th>
                            <th scope="col">{t('columnFrom')}</th>
                            <th scope="col">{t('columnTo')}</th>
                            <th scope="col">{t('columnState')}</th>
                            <th scope="col">{t('columnSignature')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {items.map(item => <tr key={item.id}>
                            <td>{tEnums(`suspensionKind.${item.kind}`)}</td>
                            <td>
                                {tEnums(`suspensionScope.${item.scope}`)}
                                {item.scopeDescription && <Muted> — {item.scopeDescription}</Muted>}
                            </td>
                            <td>{item.suspendingBodyName ?? '—'}</td>
                            <td>
                                <FormattedDate value={item.start}/>
                                {item.decisionDate && <Muted> {t('decisionDateNote', {date: item.decisionDate})}</Muted>}
                            </td>
                            <td>{item.end ? <FormattedDate value={item.end}/> : t('openEnded')}</td>
                            <td>
                                <Badge tone={stateTone(item.state)}>{tEnums(`suspensionState.${item.state}`)}</Badge>
                                {item.lifted && <Muted> {t('liftedOn', {date: item.lifted.on})}</Muted>}
                                {item.voided && <Muted> {t('voidedNote')}</Muted>}
                            </td>
                            <td>{item.decisionSignature ?? '—'}</td>
                        </tr>)}
                        {items.map(item => {
                            const loss = item.positionLoss;
                            const notes = [];
                            if (loss.applicable && loss.due && loss.dueOn) {
                                notes.push(<Notice key={`d${item.id}`} tone="danger">
                                    {t('positionLossDue', {date: loss.dueOn})} {t('positionLossCount', {
                                        count: loss.outstanding.length,
                                    })}
                                </Notice>);
                            } else if (loss.applicable && loss.dueOn === null && item.scope === 'FULL') {
                                notes.push(<Notice key={`a${item.id}`} tone="info">
                                    {t('positionLossAwaiting')}
                                </Notice>);
                            }
                            if (loss.applicable && loss.dueOn && !loss.due) {
                                notes.push(<Notice key={`p${item.id}`} tone="info">
                                    {t('positionLossPending')}
                                </Notice>);
                            }
                            if (loss.endedCount > 0) {
                                notes.push(<Muted key={`e${item.id}`}>{t('endedCount', {count: loss.endedCount})}</Muted>);
                            }
                            if (notes.length === 0 && !actions(item)) {
                                return null;
                            }
                            return <tr key={`row-${item.id}`}>
                                <td colSpan={7}>
                                    {notes}
                                    {actions(item)}
                                </td>
                            </tr>;
                        })}
                    </tbody>
                </table>
            </TableWrap>}

        {canManage && options && <details>
            <summary>{t('add')}</summary>
            <SuspendMemberForm memberId={memberId} options={options}/>
        </details>}
    </Card>;
}

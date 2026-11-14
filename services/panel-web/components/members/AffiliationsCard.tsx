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
import { changeAffiliationAction, endAffiliationAction } from "@/app/actions/members";
import ActionButton from "@/components/form/ActionButton";
import ActionForm from "@/components/form/ActionForm";
import { CheckboxField, SelectField } from "@/components/form/Fields";
import { FormattedDate } from "@/components/members/format";
import { Badge, Card, Muted, TableWrap } from "@/components/page/Page";
import { Affiliation } from "@/panel-api/members/types";

export type DistrictOption = { id: number; name: string };

export default function AffiliationsCard({memberId, affiliations, targetDistricts}: {
    memberId: number;
    affiliations: Affiliation[];
    targetDistricts: DistrictOption[];
}) {
    const t = useTranslations('members');
    const tEnums = useTranslations('enums');
    const tc = useTranslations('common');

    const affiliatedIds = new Set(affiliations.map(a => a.districtId));
    const addable = targetDistricts.filter(d => !affiliatedIds.has(d.id));
    const movable = targetDistricts.filter(d => !affiliations.some(a => a.districtId === d.id && a.kind === 'PRIMARY'));
    const options = (list: DistrictOption[]) => list.map(d => ({value: String(d.id), label: d.name}));

    return <Card id="affiliations" title={t('affiliationsTitle')} hint={t('affiliationsHint')}>
        {affiliations.length === 0 && <p>{t('noAffiliations')}</p>}
        {affiliations.length > 0 && <TableWrap>
            <thead>
                <tr>
                    <th scope="col">{t('district')}</th>
                    <th scope="col">{t('affiliationKind')}</th>
                    <th scope="col">{t('since')}</th>
                    <th scope="col">{tc('actions')}</th>
                </tr>
            </thead>
            <tbody>
                {affiliations.map(a => <tr key={a.districtId}>
                    <td>{a.districtName}</td>
                    <td>
                        <Badge>{tEnums(`affiliationKind.${a.kind}`)}</Badge>{' '}
                        {a.votesHere && <Badge tone="success">{t('votesHere')}</Badge>}
                    </td>
                    <td><FormattedDate value={a.validFrom}/></td>
                    <td>
                        {a.kind === 'SUPPORTING' ? <div style={{display: 'flex', gap: '0.5rem', flexWrap: 'wrap'}}>
                            {!a.votesHere && <ActionButton label={t('setVoting')}
                                                           ariaLabel={`${t('setVoting')}: ${a.districtName}`}
                                                           action={() => changeAffiliationAction(memberId, {
                                                               action: 'SET_VOTING',
                                                               districtId: a.districtId,
                                                           })}/>}
                            <ActionButton label={t('endAffiliation')}
                                          ariaLabel={`${t('endAffiliation')}: ${a.districtName}`}
                                          confirmMessage={t('endAffiliationConfirm', {district: a.districtName})}
                                          action={() => endAffiliationAction(memberId, a.districtId)}/>
                        </div> : <Muted>—</Muted>}
                    </td>
                </tr>)}
            </tbody>
        </TableWrap>}

        {addable.length > 0 && <ActionForm ariaLabel={t('addSupporting')}
                                          submitLabel={t('addSupporting')}
                                          successMessage={t('affiliationSaved')}
                                          resetOnSuccess
                                          action={fd => changeAffiliationAction(memberId, {
                                              action: 'ADD_SUPPORTING',
                                              districtId: Number(fd.get('districtId')),
                                              votesHere: fd.get('votesHere') === 'true',
                                          })}>
            <SelectField name="districtId" label={t('supportingDistrict')} required
                         emptyLabel={t('chooseDistrict')} options={options(addable)}/>
            <CheckboxField name="votesHere" label={t('votesHereOption')}/>
        </ActionForm>}

        {movable.length > 0 && <ActionForm ariaLabel={t('movePrimary')}
                                          submitLabel={t('movePrimary')}
                                          successMessage={t('affiliationSaved')}
                                          resetOnSuccess
                                          action={fd => changeAffiliationAction(memberId, {
                                              action: 'SET_PRIMARY',
                                              districtId: Number(fd.get('districtId')),
                                              keepPreviousAsSupporting: fd.get('keepPreviousAsSupporting') === 'true',
                                          })}>
            <SelectField name="districtId" label={t('newPrimaryDistrict')} required
                         emptyLabel={t('chooseDistrict')} options={options(movable)}/>
            <CheckboxField name="keepPreviousAsSupporting" label={t('keepPreviousOption')}/>
        </ActionForm>}
    </Card>;
}

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
import { MapPinIcon } from "@phosphor-icons/react/ssr";
import { changeAffiliationAction, endAffiliationAction } from "@/app/actions/members";
import ActionButton from "@/components/form/ActionButton";
import ActionForm from "@/components/form/ActionForm";
import { CheckboxField, SelectField } from "@/components/form/Fields";
import { FormattedDate } from "@/components/members/format";
import styles from "@/components/members/record/record.module.scss";
import { Badge, Card } from "@/components/page/Page";
import { EmptyState } from "@/components/ui/Blocks";
import { Affiliation } from "@/panel-api/members/types";

export type DistrictOption = { id: number; name: string };

export default function AffiliationsCard({memberId, affiliations, targetDistricts = [], readOnly = false}: {
    memberId: number;
    affiliations: Affiliation[];
    targetDistricts?: DistrictOption[];
    readOnly?: boolean;
}) {
    const t = useTranslations('members');
    const tEnums = useTranslations('enums');

    const primary = affiliations.find(a => a.kind === 'PRIMARY');
    const supporting = affiliations.filter(a => a.kind === 'SUPPORTING');
    const affiliatedIds = new Set(affiliations.map(a => a.districtId));
    const addable = targetDistricts.filter(d => !affiliatedIds.has(d.id));
    const movable = targetDistricts.filter(d => !affiliations.some(a => a.districtId === d.id && a.kind === 'PRIMARY'));
    const options = (list: DistrictOption[]) => list.map(d => ({value: String(d.id), label: d.name}));
    const manageable = !readOnly && (addable.length > 0 || movable.length > 0);

    return <Card id="affiliations" title={t('affiliationsTitle')} hint={t('affiliationsHint')}>
        {affiliations.length === 0 && <EmptyState icon={<MapPinIcon aria-hidden/>} title={t('noAffiliations')}/>}

        {primary && <div className={styles.primaryDistrict}>
            <div>
                <small>{tEnums('affiliationKind.PRIMARY')}</small>
                <strong>{primary.districtName}</strong>
                <small>{t('since')} <FormattedDate value={primary.validFrom}/></small>
            </div>
            {primary.votesHere && <Badge tone="success">{t('votesHere')}</Badge>}
        </div>}

        {supporting.length > 0 && <div>
            <h3>{t('supportingDistricts')}</h3>
            <ul className={styles.districtRows}>
                {supporting.map(a => <li key={a.districtId}>
                    <div>
                        <span className={styles.rowTitle}>
                            {a.districtName}
                            {a.votesHere && <Badge tone="success">{t('votesHere')}</Badge>}
                        </span>
                        <small>{t('since')} <FormattedDate value={a.validFrom}/></small>
                    </div>
                    {!readOnly && <div className={styles.rowActions}>
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
                    </div>}
                </li>)}
            </ul>
        </div>}

        {manageable && <div className={styles.manage}>
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
        </div>}
    </Card>;
}

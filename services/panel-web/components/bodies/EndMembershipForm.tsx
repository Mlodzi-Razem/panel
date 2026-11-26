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
import { endMembershipAction } from "@/app/actions/bodies";
import { useSelfRemoval } from "@/components/bodies/useSelfRemoval";
import ActionForm from "@/components/form/ActionForm";
import { TextField } from "@/components/form/Fields";

export default function EndMembershipForm({bodyId, membershipId, memberName}: {
    bodyId: number;
    membershipId: number;
    memberName: string;
}) {
    const t = useTranslations('bodies');
    const selfRemoval = useSelfRemoval();

    return <>
        <ActionForm ariaLabel={`${t('endMembership')}: ${memberName}`}
                    submitLabel={t('endMembership')}
                    submitVariant="outline"
                    action={fd => selfRemoval.run(confirmations => endMembershipAction(
                        bodyId,
                        membershipId,
                        String(fd.get('endDate') ?? ''),
                        String(fd.get('decisionReference') ?? ''),
                        confirmations,
                    ))}>
            <TextField name="endDate" label={t('endDate')} type="date" hint={t('endDateHint')}/>
            <TextField name="decisionReference"
                       label={t('endReference')}
                       maxLength={200}
                       hint={t('endReferenceHint')}/>
        </ActionForm>
        {selfRemoval.dialog}
    </>;
}

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
import { voidMembershipAction } from "@/app/actions/bodies";
import ActionForm from "@/components/form/ActionForm";
import { TextField } from "@/components/form/Fields";

/**
 * "Wpis omyłkowy" (void): the mandate never happened. Separate from ending a function — the row
 * stays in the register with status VOIDED, so the reason is required and the copy warns.
 */
export default function VoidMembershipForm({bodyId, membershipId, memberName}: {
    bodyId: number;
    membershipId: number;
    memberName: string;
}) {
    const t = useTranslations('bodies');

    const onSubmit = (formData: FormData) => voidMembershipAction(
        bodyId,
        membershipId,
        String(formData.get('reason') ?? ''),
        String(formData.get('decisionReference') ?? ''),
    );

    return <ActionForm ariaLabel={`${t('voidMembership')}: ${memberName}`}
                       submitLabel={t('voidMembership')}
                       submitVariant="danger"
                       successMessage={t('voided')}
                       action={onSubmit}>
        <TextField name="reason"
                   label={t('voidReason')}
                   required
                   maxLength={500}
                   hint={t('voidReasonHint')}/>
        <TextField name="decisionReference"
                   label={t('voidReference')}
                   maxLength={120}
                   hint={t('voidReferenceHint')}/>
    </ActionForm>;
}

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
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { deleteBodyAction } from "@/app/actions/bodies";
import ActionButton from "@/components/form/ActionButton";

/**
 * "Usuń organ": only for a body created in the panel (never a seeded one), without subordinate
 * bodies and without any mandate in its history (voided ones included). The confirmation covers all of it.
 */
export default function DeleteBodyButton({id, name}: { id: number; name: string }) {
    const t = useTranslations('bodies');
    const router = useRouter();

    return <ActionButton label={t('deleteBody')}
                         variant="danger"
                         ariaLabel={`${t('deleteBody')}: ${name}`}
                         confirmMessage={t('deleteBodyConfirm')}
                         action={() => deleteBodyAction(id)}
                         onSuccess={() => router.push('/bodies')}/>;
}

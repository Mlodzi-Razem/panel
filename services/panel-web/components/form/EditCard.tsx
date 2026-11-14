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

import React, { useState } from "react";
import { useTranslations } from "next-intl";
import Button from "@/components/button/Button";
import ActionForm from "@/components/form/ActionForm";
import { Card } from "@/components/page/Page";
import { ActionResult } from "@/panel-api/members/types";

export type EditCardProps = React.PropsWithChildren<{
    id: string;
    title: string;
    hint?: string;
    readView: React.ReactNode;
    action: (formData: FormData) => Promise<ActionResult>;
    editLabel?: string;
    submitLabel?: string;
    successMessage?: string;
    disabledReason?: string;
}>;

export default function EditCard({
    id, title, hint, readView, action, editLabel, submitLabel, successMessage, disabledReason, children,
}: EditCardProps) {
    const t = useTranslations('common');
    const [editing, setEditing] = useState(false);

    return <Card id={id}
                 title={title}
                 hint={hint}
                 actions={!editing && <Button variant="outline" onClick={() => setEditing(true)} disabled={!!disabledReason}>
                     {editLabel ?? t('edit')}
                 </Button>}>
        {!editing && disabledReason && <p>{disabledReason}</p>}
        {editing
            ? <ActionForm action={action}
                          submitLabel={submitLabel ?? t('save')}
                          successMessage={successMessage}
                          onSuccess={() => setEditing(false)}
                          extraButtons={<Button variant="outline" onClick={() => setEditing(false)}>{t('cancel')}</Button>}>
                {children}
            </ActionForm>
            : readView}
    </Card>;
}

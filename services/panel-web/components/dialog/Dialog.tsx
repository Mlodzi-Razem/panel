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

import React, { useEffect, useId, useRef } from "react";
import styles from "@/components/form/Form.module.scss";

export type DialogProps = React.PropsWithChildren<{
    open: boolean;
    title: string;
    onClose: () => void;
}>;

export default function Dialog({open, title, onClose, children}: DialogProps) {
    const ref = useRef<HTMLDialogElement>(null);
    const titleId = useId();

    useEffect(() => {
        const dialog = ref.current;
        if (!dialog) {
            return;
        }
        if (open && !dialog.open) {
            dialog.showModal();
        } else if (!open && dialog.open) {
            dialog.close();
        }
    }, [open]);

    return <dialog ref={ref} className={styles.dialog} aria-labelledby={titleId} onClose={onClose}>
        {open && <div className={styles.dialogBody}>
            <h2 id={titleId}>{title}</h2>
            {children}
        </div>}
    </dialog>;
}

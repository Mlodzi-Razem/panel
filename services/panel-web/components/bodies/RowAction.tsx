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

import React, { useId, useState } from "react";
import { useTranslations } from "next-intl";
import Button, { ButtonVariant } from "@/components/button/Button";
import styles from "./Bodies.module.scss";

/** A form that stays folded under its button until asked for; it stays mounted, so nothing typed is lost. */
export default function RowAction({label, variant = 'outline', children}: React.PropsWithChildren<{
    label: string;
    variant?: ButtonVariant;
}>) {
    const tc = useTranslations('common');
    const [open, setOpen] = useState(false);
    const id = useId();
    return <div className={styles.rowAction}>
        <Button variant={open ? 'ghost' : variant} aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}>
            {open ? tc('collapse') : label}
        </Button>
        <div id={id} hidden={!open}>{children}</div>
    </div>;
}

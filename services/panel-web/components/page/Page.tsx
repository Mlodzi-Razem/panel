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

import React from "react";
import Link from "next/link";
import { ArrowLeftIcon } from "@phosphor-icons/react/ssr";
import styles from "./Page.module.scss";

export type PageProps = React.PropsWithChildren<{
    title: string;
    subtitle?: string;
    actions?: React.ReactNode;
    backHref?: string;
    backLabel?: string;
    header?: React.ReactNode;
}>;

export function Page({title, subtitle, actions, backHref, backLabel, header, children}: PageProps) {
    return <main className={styles.page}>
        {backHref && <Link href={backHref} className={styles.backLink}>
            <ArrowLeftIcon aria-hidden/>{backLabel}
        </Link>}
        {header ?? <header className={styles.header}>
            <div className={styles.headerText}>
                <h1 className={styles.title}>{title}</h1>
                {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
            </div>
            {actions && <div className={styles.actions}>{actions}</div>}
        </header>}
        {children}
    </main>;
}

export type CardProps = React.PropsWithChildren<{
    title?: string;
    hint?: string;
    actions?: React.ReactNode;
    id?: string;
}>;

export function Card({title, hint, actions, id, children}: CardProps) {
    const headingId = id ? `${id}-title` : undefined;
    return <section className={styles.card} id={id} aria-labelledby={title ? headingId : undefined}>
        {(title || actions) && <div className={styles.cardHeader}>
            {title && <h2 className={styles.cardTitle} id={headingId}>{title}</h2>}
            {actions && <div className={styles.actions}>{actions}</div>}
        </div>}
        {hint && <p className={styles.cardHint}>{hint}</p>}
        {children}
    </section>;
}

export type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'muted' | 'brand';

export function Badge({tone = 'neutral', children}: React.PropsWithChildren<{ tone?: Tone }>) {
    return <span className={styles.badge} data-tone={tone}>{children}</span>;
}

export function Notice({tone = 'neutral', children, role}: React.PropsWithChildren<{
    tone?: Tone;
    role?: 'alert' | 'status';
}>) {
    return <div className={styles.notice} data-tone={tone} role={role}>{children}</div>;
}

export function DescriptionList({children}: React.PropsWithChildren<object>) {
    return <dl className={styles.dl}>{children}</dl>;
}

export function LinkButton({href, primary = false, children}: React.PropsWithChildren<{
    href: string;
    primary?: boolean;
}>) {
    return <Link href={href} className={styles.linkButton} data-primary={primary}>{children}</Link>;
}

export function Muted({children}: React.PropsWithChildren<object>) {
    return <span className={styles.muted}>{children}</span>;
}

export function TableWrap({children}: React.PropsWithChildren<object>) {
    return <div className={styles.tableWrap}><table className={styles.table}>{children}</table></div>;
}

export { styles as pageStyles };

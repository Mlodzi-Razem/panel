/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
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
import { useTranslations } from "next-intl";
import { Badge, Card } from "@/components/page/Page";
import styles from "@/components/teams/Teams.module.scss";

export function TeamsPage({children}: React.PropsWithChildren<object>) {
    return <main className={styles.page}>{children}</main>;
}

export function Hero({title, subtitle}: { title: string; subtitle?: React.ReactNode }) {
    return <header className={styles.hero}>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
    </header>;
}

export function CardGrid({children}: React.PropsWithChildren<object>) {
    return <div className={styles.grid}>{children}</div>;
}

/** Bottom row of two cards: a wide lead card and its narrow companion (mockup proportion 2:1). */
export function WideNarrowGrid({children}: React.PropsWithChildren<object>) {
    return <div className={styles.wideNarrowGrid}>{children}</div>;
}

/** A mockup section whose Panel module does not exist yet. It shows what will appear, never placeholder data. */
export function ComingSoon({title, children}: React.PropsWithChildren<{ title: string }>) {
    const t = useTranslations('teams');
    return <Card title={title} actions={<Badge tone="muted">{t('soon')}</Badge>}>
        <p className={styles.soon}>{children}</p>
    </Card>;
}

export function ExternalLink({href, children}: React.PropsWithChildren<{ href: string }>) {
    return <a href={href} target="_blank" rel="noopener noreferrer" className={styles.link}>{children}</a>;
}

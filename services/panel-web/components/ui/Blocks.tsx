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
import { useTranslations } from "next-intl";
import {
    CaretDoubleLeftIcon,
    CaretDoubleRightIcon,
    CaretDownIcon,
    CaretLeftIcon,
    CaretRightIcon,
    CaretUpDownIcon,
    CaretUpIcon,
} from "@phosphor-icons/react/ssr";
import Avatar from "@/components/ui/Avatar";
import { UrlFilterSelect } from "@/components/ui/UrlControls";
import styles from "./ui.module.scss";

export function Toolbar({children, end}: React.PropsWithChildren<{ end?: React.ReactNode }>) {
    return <div className={styles.toolbar} role="group">
        <div className={styles.toolbarStart}>{children}</div>
        {end && <div className={styles.toolbarEnd}>{end}</div>}
    </div>;
}

export function ResultCount({children}: React.PropsWithChildren<object>) {
    return <span className={styles.resultCount} aria-live="polite">{children}</span>;
}

export type SortState = { field: string; dir: 'asc' | 'desc' };

export function parseSort(raw: string | undefined, allowed: readonly string[], fallback: SortState): SortState {
    const [field, dir] = (raw ?? '').split(':');
    if (!allowed.includes(field)) {
        return fallback;
    }
    return {field, dir: dir === 'desc' ? 'desc' : 'asc'};
}

export function SortHeader({label, field, sort, hrefFor}: {
    label: string;
    field: string;
    sort: SortState;
    hrefFor: (sortParam: string) => string;
}) {
    const active = sort.field === field;
    const nextDir = active && sort.dir === 'asc' ? 'desc' : 'asc';
    const ariaSort = active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none';
    return <th scope="col" aria-sort={ariaSort}>
        <Link className={styles.sortLink} data-active={active} href={hrefFor(`${field}:${nextDir}`)} scroll={false}>
            {label}
            {active ? (sort.dir === 'asc' ? <CaretUpIcon aria-hidden/> : <CaretDownIcon aria-hidden/>)
                : <CaretUpDownIcon aria-hidden/>}
        </Link>
    </th>;
}

function pageWindow(page: number, totalPages: number): (number | 'gap')[] {
    const wanted = new Set([0, totalPages - 1, page - 1, page, page + 1].filter(p => p >= 0 && p < totalPages));
    const sorted = [...wanted].sort((a, b) => a - b);
    const result: (number | 'gap')[] = [];
    sorted.forEach((p, index) => {
        if (index > 0 && p - sorted[index - 1] > 1) {
            result.push('gap');
        }
        result.push(p);
    });
    return result;
}

export function Pagination({page, totalPages, totalItems, size, defaultSize, hrefFor, sizeOptions = [10, 25, 50, 100]}: {
    page: number;
    totalPages: number;
    totalItems: number;
    size: number;
    defaultSize: number;
    hrefFor: (page: number) => string;
    sizeOptions?: number[];
}) {
    const t = useTranslations('common');
    const last = Math.max(0, totalPages - 1);
    const pageLink = (target: number, label: React.ReactNode, ariaLabel: string, disabled: boolean) =>
        <li><Link className={styles.pageLink}
                  href={hrefFor(target)}
                  aria-label={ariaLabel}
                  aria-disabled={disabled}
                  tabIndex={disabled ? -1 : undefined}
                  scroll={false}>{label}</Link></li>;

    return <nav className={styles.pagination} aria-label={t('pagination')}>
        <div className={styles.rowsPerPage}>
            <span>{t('rowsPerPage')}</span>
            <UrlFilterSelect param="size"
                             label={t('rowsPerPage')}
                             defaultValue={String(defaultSize)}
                             options={sizeOptions.map(o => ({value: String(o), label: String(o)}))}/>
            <span>{t('ofRows', {count: totalItems, size})}</span>
        </div>
        {totalPages > 1 && <ul className={styles.pages}>
            {pageLink(0, <CaretDoubleLeftIcon aria-hidden/>, t('firstPage'), page <= 0)}
            {pageLink(page - 1, <CaretLeftIcon aria-hidden/>, t('previousPage'), page <= 0)}
            {pageWindow(page, totalPages).map((p, index) => p === 'gap'
                ? <li key={`gap-${index}`} className={styles.ellipsis} aria-hidden>…</li>
                : <li key={p}>
                    <Link className={styles.pageLink}
                          href={hrefFor(p)}
                          aria-current={p === page ? 'page' : undefined}
                          aria-label={t('pageNumber', {page: p + 1})}
                          scroll={false}>{p + 1}</Link>
                </li>)}
            {pageLink(page + 1, <CaretRightIcon aria-hidden/>, t('nextPage'), page >= last)}
            {pageLink(last, <CaretDoubleRightIcon aria-hidden/>, t('lastPage'), page >= last)}
        </ul>}
    </nav>;
}

export function NameCell({name, secondary, href}: { name: string; secondary?: string | null; href?: string }) {
    const text = <span className={styles.nameText}>
        <span>{name}</span>
        {secondary && <small>{secondary}</small>}
    </span>;
    return <div className={styles.nameCell}>
        <Avatar name={name} size={34}/>
        {href ? <Link href={href}>{text}</Link> : text}
    </div>;
}

export function RowActions({children}: React.PropsWithChildren<object>) {
    return <div className={styles.rowActions}>{children}</div>;
}

export function IconLink({href, label, children}: React.PropsWithChildren<{ href: string; label: string }>) {
    return <Link href={href} className={styles.iconLink} aria-label={label} title={label}>{children}</Link>;
}

export function Chips({items}: { items: string[] }) {
    return <span className={styles.chips}>
        {items.map(item => <span key={item} className={styles.chip}>{item}</span>)}
    </span>;
}

export type RecordFact = { label: string; value: React.ReactNode };

export function RecordHeader({name, image, subtitle, badges, facts, actions}: {
    name: string;
    image?: string | null;
    subtitle?: React.ReactNode;
    badges?: React.ReactNode;
    facts?: RecordFact[];
    actions?: React.ReactNode;
}) {
    return <header className={styles.recordHeader}>
        <Avatar name={name} image={image} size={72} className={styles.recordAvatar}/>
        <div className={styles.recordMain}>
            <div className={styles.recordTitleRow}>
                <h1>{name}</h1>
                {badges}
            </div>
            {subtitle && <p className={styles.recordSubtitle}>{subtitle}</p>}
            {facts && facts.length > 0 && <dl className={styles.recordFacts}>
                {facts.map(fact => <div key={fact.label}>
                    <dt>{fact.label}</dt>
                    <dd>{fact.value}</dd>
                </div>)}
            </dl>}
        </div>
        {actions && <div className={styles.recordActions}>{actions}</div>}
    </header>;
}

export type TabItem = { href: string; label: string; active: boolean; count?: number };

export function Tabs({items, label}: { items: TabItem[]; label: string }) {
    return <nav className={styles.tabs} aria-label={label}>
        {items.map(item => <Link key={item.href}
                                 href={item.href}
                                 className={styles.tab}
                                 aria-current={item.active ? 'page' : undefined}
                                 scroll={false}>
            {item.label}
            {item.count !== undefined && item.count > 0 && <span className={styles.count}>{item.count}</span>}
        </Link>)}
    </nav>;
}

export function Stats({children}: React.PropsWithChildren<object>) {
    return <div className={styles.stats}>{children}</div>;
}

export function StatCard({label, value, icon, href, tone}: {
    label: string;
    value: React.ReactNode;
    icon: React.ReactNode;
    href?: string;
    tone?: 'brand' | 'info' | 'warning' | 'success';
}) {
    const content = <>
        <span className={styles.statIcon} data-tone={tone}>{icon}</span>
        <span className={styles.statText}>
            <strong>{value}</strong>
            <span>{label}</span>
        </span>
    </>;
    return href ? <Link href={href} className={styles.stat}>{content}</Link>
        : <div className={styles.stat}>{content}</div>;
}

export function EmptyState({icon, title, children}: React.PropsWithChildren<{ icon: React.ReactNode; title: string }>) {
    return <div className={styles.empty}>
        {icon}
        <strong>{title}</strong>
        {children}
    </div>;
}

export function CountBadge({value}: { value: number }) {
    return value > 0 ? <span className={styles.count}>{value}</span> : null;
}

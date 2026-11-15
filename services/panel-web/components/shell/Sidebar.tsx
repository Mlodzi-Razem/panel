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

import React, { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { signOut } from "next-auth/react";
import { useTranslations } from "next-intl";
import {
    BuildingsIcon,
    HourglassMediumIcon,
    IdentificationCardIcon,
    ListIcon,
    MoneyIcon,
    SignOutIcon,
    SquaresFourIcon,
    TrayIcon,
    UsersThreeIcon,
    XIcon,
} from "@phosphor-icons/react/ssr";
import mrLogo from "@/assets/mr-logo.svg";
import Avatar from "@/components/ui/Avatar";
import { activeHref, NavGroup, NavIcon } from "@/components/shell/nav";
import styles from "./Shell.module.scss";

const ICONS: Record<NavIcon, React.ReactNode> = {
    dashboard: <SquaresFourIcon aria-hidden/>,
    profile: <IdentificationCardIcon aria-hidden/>,
    members: <UsersThreeIcon aria-hidden/>,
    requests: <TrayIcon aria-hidden/>,
    ending: <HourglassMediumIcon aria-hidden/>,
    bodies: <BuildingsIcon aria-hidden/>,
    fees: <MoneyIcon aria-hidden/>,
};

export type SidebarUser = { name: string; email?: string | null; image?: string | null };

export default function Sidebar({groups, user}: { groups: NavGroup[]; user: SidebarUser }) {
    const t = useTranslations('menu');
    const pathname = usePathname();
    const [openOn, setOpenOn] = useState<string | null>(null);
    const open = openOn === pathname;
    const setOpen = (value: boolean) => setOpenOn(value ? pathname : null);
    const active = activeHref(pathname, groups);

    useEffect(() => {
        if (!open) {
            return;
        }
        const onKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setOpen(false);
            }
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [open]);

    return <>
        <header className={styles.topbar}>
            <button type="button"
                    className={styles.iconButton}
                    aria-label={t('openMenu')}
                    aria-expanded={open}
                    aria-controls="panel-sidebar"
                    onClick={() => setOpen(true)}>
                <ListIcon aria-hidden/>
            </button>
            <Image src={mrLogo} alt="Młodzi Razem" unoptimized className={styles.topbarLogo}/>
        </header>
        <div className={styles.backdrop} data-open={open} onClick={() => setOpen(false)} aria-hidden/>
        <nav id="panel-sidebar" className={styles.sidebar} data-open={open} aria-label={t('navigation')}>
            <div className={styles.brand}>
                <Link href="/" className={styles.brandLink}>
                    <Image src={mrLogo} alt="Młodzi Razem" unoptimized className={styles.logo} loading="eager"/>
                    <span className={styles.brandLabel}>{t('panel')}</span>
                </Link>
                <button type="button"
                        className={`${styles.iconButton} ${styles.closeButton}`}
                        aria-label={t('closeMenu')}
                        onClick={() => setOpen(false)}>
                    <XIcon aria-hidden/>
                </button>
            </div>
            <div className={styles.groups}>
                {groups.filter(g => g.items.length > 0).map(group => <div key={group.labelKey} className={styles.group}>
                    <p className={styles.groupLabel}>{t(group.labelKey)}</p>
                    <ul>
                        {group.items.map(item => <li key={item.href}>
                            <Link href={item.href}
                                  className={styles.item}
                                  aria-current={item.href === active ? 'page' : undefined}>
                                {ICONS[item.icon]}
                                <span className={styles.itemLabel}>{t(item.labelKey)}</span>
                                {item.badge !== undefined && item.badge > 0 &&
                                    <span className={styles.itemBadge} aria-label={t('pendingCount', {count: item.badge})}>
                                        {item.badge}
                                    </span>}
                            </Link>
                        </li>)}
                    </ul>
                </div>)}
            </div>
            <div className={styles.user}>
                <Avatar name={user.name} image={user.image} size={38}/>
                <div className={styles.userText}>
                    <span className={styles.userName}>{user.name}</span>
                    {user.email && <span className={styles.userEmail}>{user.email}</span>}
                </div>
                <button type="button"
                        className={styles.iconButton}
                        aria-label={t('logout')}
                        title={t('logout')}
                        onClick={() => signOut()}>
                    <SignOutIcon aria-hidden/>
                </button>
            </div>
        </nav>
    </>;
}

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

import styles from './PSideMenu.module.scss';
import React from "react";
import { User } from "next-auth";
import css from "@/util/css";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { ListIcon } from "@phosphor-icons/react/ssr";

import mrLogo from '@/assets/mr-logo.svg';
import Image from "next/image";

export type PSideMenuRoute = {
    labelKey: string;
    icon: React.ReactNode;
    urlSegment: string;
    children: PSideMenuSubroute[];
}
export type PSideMenuSubroute = Omit<PSideMenuRoute, 'children'>;

export type PSideMenuProps = {
    user: User;
    t: ReturnType<typeof useTranslations>,
    routes: PSideMenuRoute[];
    urlSegments: string[];
    signOut: () => void;
}

export default function PSideMenu(props: PSideMenuProps) {
    return <nav className={css(styles.container, 'panel-side-menu-container')}>
        <PLogoMenuItem/>
        <PMenuRoutes {...props}/>
        <div/>
        <PUserRow user={props.user} t={props.t} signOut={props.signOut}/>
        <PToggleMenuButton/>
    </nav>
}

function PMenuRoute(props: { route: PSideMenuRoute; urlSegments: string[], t: ReturnType<typeof useTranslations> }) {
    const active = isRouteActive(props);
    return <Link className={styles.menuItem}
                 aria-selected={active}
                 aria-current={active ? 'page' : false}
                 href={`/${props.route.urlSegment}`}>
        <div className={styles.menuItemIconContainer}>
            {props.route.icon}
        </div>
        <div className={styles.menuItemLabelContainer}>
            {props.t(props.route.labelKey)}
        </div>
    </Link>
}

function isRouteActive(props: { route: PSideMenuRoute; urlSegments: string[] }) {
    return props.urlSegments?.[0] === props.route.urlSegment;
}

function PToggleMenuButton() {
    return <div className={css(styles.toggleMenuContainer, 'panel-side-menu-toggle-container')}>
        <input type="checkbox" className={css(styles.toggleMenuCheckbox, 'panel-side-menu-toggle-checkbox')}/>
        <ListIcon width="var(--size-sidemenu-item-size)" height="var(--size-sidemenu-item-size)" className={styles.toggleMenuIcon}/>
    </div>;
}

function PMenuRoutes(props: PSideMenuProps) {
    return <div>
        {props.routes.map(route => <PMenuRoute key={route.urlSegment}
                                               route={route}
                                               urlSegments={props.urlSegments}
                                               t={props.t}/>)}
    </div>;
}

function PLogoMenuItem() {
    return <div className={css(styles.logoContainer, 'panel-side-menu-logo-container')}>
        <Image src={mrLogo} alt={'logo'} unoptimized={true} className={styles.logo} loading="eager"/>
    </div>
}

function PUserRow(props: { user: User, t: ReturnType<typeof useTranslations>, signOut: () => void }) {
    return <div className={styles.userContainer}>
        <div className={styles.userAvatarContainer}>
            <Image src={props.user.image ?? ''}
                   alt="avatar"
                   width={100}
                   height={100}
                   unoptimized={true}
                   className={styles.userAvatar}/>
        </div>
        <div className={styles.userDescriptionContainer}>
            <span className={styles.userNameLabel}>{props.user.name}</span>
            <span className={styles.logoutLabel} onClick={props.signOut}>{props.t('logout')}</span>
        </div>
    </div>;
}

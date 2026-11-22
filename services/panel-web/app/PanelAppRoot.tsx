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

import React, { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import SuspenseFallback from "@/app/SuspenseFallback";
import Sidebar, { SidebarUser } from "@/components/shell/Sidebar";
import { NavGroup } from "@/components/shell/nav";
import styles from "@/components/shell/Shell.module.scss";

export type PanelAppRootProps = React.PropsWithChildren<{ user: SidebarUser; groups: NavGroup[] }>;

/**
 * The application shell. The skip link is the first focusable element of the page (C9):
 * keyboard users jump straight over the navigation to the main content region.
 */
export default async function PanelAppRoot({user, groups, children}: PanelAppRootProps) {
    const t = await getTranslations("menu");

    return <div className={styles.shell}>
        <a className={styles.skipLink} href="#main-content">{t("skipToContent")}</a>
        <Sidebar user={user} groups={groups}/>
        <div className={styles.main} id="main-content">
            <Suspense fallback={<SuspenseFallback/>}>
                {children}
            </Suspense>
        </div>
    </div>;
}

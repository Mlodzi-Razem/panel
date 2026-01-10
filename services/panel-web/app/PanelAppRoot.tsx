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

import React, { Suspense } from "react";
import styles from './PanelAppRoot.module.scss';
import SideMenu from "@/components/menu/side-menu/SideMenu";
import SuspenseFallback from "@/app/SuspenseFallback";
import { Session, User } from "next-auth";

export type PanelAppRootProps = React.PropsWithChildren<{ session: Session }>;

const PanelAppRoot = ({
    session,
    children,
}: PanelAppRootProps) => {
    return <div className={styles.container}>
        <SideMenu user={session.user as User}/>
        <div className={styles.content}>
            <Suspense fallback={<SuspenseFallback/>}>
                {children}
            </Suspense>
        </div>
    </div>;
};
export default PanelAppRoot;
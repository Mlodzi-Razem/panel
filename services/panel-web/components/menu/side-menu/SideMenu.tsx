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

"use client";

import { User } from "next-auth";
import PSideMenu, { PSideMenuRoute } from "@/components/menu/side-menu/PSideMenu";
import { useSelectedLayoutSegments } from "next/dist/client/components/navigation";
import { MoneyIcon, UserIcon, UsersThreeIcon } from "@phosphor-icons/react/ssr";
import { useTranslations } from "next-intl";
import { signOut } from "next-auth/react";

const iconProps = {width: '100%', height: '100%'};
const routes: PSideMenuRoute[] = [
    {
        urlSegment: "user",
        labelKey: "user",
        icon: <UserIcon {...iconProps}/>,
        children: [],
    },
    {
        urlSegment: "members",
        labelKey: "members",
        icon: <UsersThreeIcon {...iconProps} />,
        children: [],
    },
    {
        urlSegment: "fees",
        labelKey: "fees",
        icon: <MoneyIcon {...iconProps}/>,
        children: [],
    },
] as const;

export default function SideMenu({user}: { user: User }) {
    const urlSegments = useSelectedLayoutSegments();
    const t = useTranslations('menu');

    return <PSideMenu user={user} routes={routes} urlSegments={urlSegments} t={t} signOut={signOut}/>
}
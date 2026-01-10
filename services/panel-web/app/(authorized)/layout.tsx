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

import { getServerSession } from "next-auth";
import { redirect } from "next/navigation";
import PanelAppRoot from "@/app/PanelAppRoot";
import React from "react";

export default async function AuthorizedLayout({children}: React.PropsWithChildren<object>) {
    const session = await getServerSession();

    if (!session || !session.user) {
        redirect('/login');
    }

    return <PanelAppRoot session={session}>
        {children}
    </PanelAppRoot>;
}
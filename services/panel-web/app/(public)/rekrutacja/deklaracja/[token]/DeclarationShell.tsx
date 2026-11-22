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

import Image from "next/image";
import React from "react";
import mrLogo from "@/assets/mr-logo.svg";
import styles from "./DeclarationPage.module.scss";

/** Brand shell of the public candidate flow: slim plum band with the white MR logo above the card column. */
export default function DeclarationShell({children}: React.PropsWithChildren<object>) {
    return <div className={styles.shell}>
        <header className={styles.band}>
            <Image src={mrLogo} alt="Młodzi Razem" unoptimized priority className={styles.bandLogo}/>
        </header>
        <div className={styles.column}>{children}</div>
    </div>;
}

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

import Centered from "@/components/layout/centered/Centered";
import { isAuthenticated } from "@/auth/authOptions";
import { redirect } from "next/navigation";
import LoginPageSignInButton from "@/app/(public)/login/LoginPageSignInButton";

import styles from './LoginPage.module.scss';

export default async function Login() {
    if (await isAuthenticated()) {
        redirect('/');
    }

    return <div className={styles.page}>
        <Centered>
            <main className={styles.container}>
                <div>
                    <h3 style={{textAlign: 'center'}}>Młodzi Razem</h3>
                    <h4 style={{textAlign: 'center'}}>Panel</h4>
                </div>
                <LoginPageSignInButton/>
            </main>
        </Centered>
    </div>;
}
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

import { getTranslations } from "next-intl/server";
import { Card } from "@/components/page/Page";
import { ExternalLink, Hero, TeamsPage } from "@/components/teams/TeamsBlocks";
import styles from "@/components/teams/Teams.module.scss";

export default async function TeamsAbout() {
    const t = await getTranslations('teams');
    return <TeamsPage>
        <Hero title={t('aboutTitle')}/>
        <Card>
            <p>{t('aboutBody')}</p>
            <p>{t('privacy')}</p>
        </Card>
        <Card title={t('links')}>
            <ul className={styles.links}>
                <li><ExternalLink href="/">{t('openInBrowser')}</ExternalLink></li>
                <li><ExternalLink href="https://engage.cloud.microsoft/">{t('itHelp')}</ExternalLink></li>
                <li><a href="mailto:bezpieczenstwo@mlodzirazem.org" className={styles.link}>{t('reportIncident')}</a></li>
            </ul>
        </Card>
    </TeamsPage>;
}

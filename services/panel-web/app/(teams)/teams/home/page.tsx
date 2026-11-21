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

import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { ShieldIcon } from "@phosphor-icons/react/ssr";
import { FormattedDate, memberName } from "@/components/members/format";
import { Card, LinkButton } from "@/components/page/Page";
import { CardGrid, ComingSoon, ExternalLink, Hero, TeamsPage, WideNarrowGrid } from "@/components/teams/TeamsBlocks";
import styles from "@/components/teams/Teams.module.scss";
import { requireMe } from "@/panel-api/members/requireMe";

const SECURITY_MAILBOX = 'bezpieczenstwo@mlodzirazem.org';
const IT_HELP_URL = 'https://engage.cloud.microsoft/';

export default async function TeamsHome() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('teams');
    const types = await getTranslations('enums.membershipType');
    const fields = me.member.fields;
    const firstName = String(fields['preferredNameFirst'] ?? '').trim() || memberName(fields);
    const type = typeof fields['membershipType'] === 'string' && types.has(fields['membershipType'])
        ? types(fields['membershipType'])
        : null;
    const since = typeof fields['memberSince'] === 'string' ? fields['memberSince'] : null;
    const district = me.member.affiliations?.find(a => a.kind === 'PRIMARY')?.districtName ?? null;

    return <TeamsPage>
        <Hero title={t('hello', {name: firstName})}
              subtitle={(type || district) && <>
                  {type && <>{t('membership', {type})}{since && <> {t('since')} <FormattedDate value={since}/></>}</>}
                  {type && district && ' · '}
                  {district}
              </>}/>

        <CardGrid>
            <ComingSoon title={t('voteTitle')}>{t('voteSoon')}</ComingSoon>
            <ComingSoon title={t('feeTitle')}>{t('feeSoon')}</ComingSoon>
            <Card title={t('districtTitle')}>
                <p>{district ?? t('noDistrict')}</p>
                <Link href="/teams/district" className={styles.link}>{t('districtMore')}</Link>
            </Card>
            <Card title={t('dataTitle')}>
                <p>{t('dataHint')}</p>
                <LinkButton href="/teams/membership#own-formal">{t('dataChange')}</LinkButton>
            </Card>
        </CardGrid>

        <WideNarrowGrid>
            <ComingSoon title={t('newsTitle')}>{t('newsSoon')}</ComingSoon>
            <Card title={t('helpTitle')}>
                <p>{t('helpBody')}</p>
                <a href={`mailto:${SECURITY_MAILBOX}`} className={styles.reportButton}>
                    <ShieldIcon aria-hidden/>{t('reportIncident')}
                </a>
                <ExternalLink href={IT_HELP_URL}>{t('itHelp')}</ExternalLink>
            </Card>
        </WideNarrowGrid>
    </TeamsPage>;
}

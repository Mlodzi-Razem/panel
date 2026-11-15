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

import React from "react";
import { useTranslations } from "next-intl";
import { FieldValue, FormattedDate, memberName } from "@/components/members/format";
import styles from "@/components/members/record/record.module.scss";
import { Badge, Page } from "@/components/page/Page";
import { RecordFact, RecordHeader, Tabs } from "@/components/ui/Blocks";
import { MemberDetail } from "@/panel-api/members/types";

export type RecordTab = { key: string; label: string; count?: number };

export const DEFAULT_TAB = 'details';

export function resolveTab(raw: string | string[] | undefined, tabs: RecordTab[]): string {
    const value = Array.isArray(raw) ? raw[0] : raw;
    return tabs.some(tab => tab.key === value) ? value! : tabs[0]?.key ?? DEFAULT_TAB;
}

function text(fields: Record<string, unknown>, key: string): string {
    const value = fields[key];
    return typeof value === 'string' ? value.trim() : '';
}

export default function RecordShell({title, image, member, actions, backHref, backLabel, basePath, tabs, activeTab, children}: React.PropsWithChildren<{
    title: string;
    image?: string | null;
    member: MemberDetail;
    actions?: React.ReactNode;
    backHref?: string;
    backLabel?: string;
    basePath: string;
    tabs: RecordTab[];
    activeTab: string;
    }>) {
    const t = useTranslations('members');
    const tf = useTranslations('fields');
    const tEnums = useTranslations('enums');
    const fields = member.fields;
    const name = memberName(fields) || title;

    const status = text(fields, 'status');
    const type = text(fields, 'membershipType');
    const pronouns = text(fields, 'pronouns');
    const pseudonym = text(fields, 'pseudonym');
    const subtitle = [pronouns, pseudonym && pseudonym !== name ? pseudonym : ''].filter(Boolean).join(' · ');
    const primary = member.affiliations?.find(a => a.kind === 'PRIMARY');
    const memberId = text(fields, 'memberId');
    const memberSince = text(fields, 'memberSince');

    const facts: RecordFact[] = [];
    if (primary) {
        facts.push({label: t('primaryDistrict'), value: primary.districtName});
    }
    if (memberId) {
        facts.push({label: t('membershipNumber'), value: memberId});
    }
    if (memberSince) {
        facts.push({label: tf('memberSince'), value: <FormattedDate value={memberSince}/>});
    }
    if (member.membershipEndsOn) {
        facts.push({label: t('membershipEnds'), value: <FormattedDate value={member.membershipEndsOn}/>});
    }

    const badges = <>
        {status && <FieldValue def={{name: 'status', kind: 'status'}} value={status}/>}
        {type && <Badge tone="brand">
            {tEnums.has(`membershipType.${type}`) ? tEnums(`membershipType.${type}`) : type}
        </Badge>}
    </>;

    return <Page title={name}
                 backHref={backHref}
                 backLabel={backLabel}
                 header={<RecordHeader name={name}
                                       image={image}
                                       badges={badges}
                                       subtitle={subtitle || undefined}
                                       facts={facts}
                                       actions={actions}/>}>
        {tabs.length > 1 && <Tabs label={t('recordSections')}
                                  items={tabs.map(tab => ({
                                      href: tab.key === tabs[0].key ? basePath : `${basePath}?tab=${tab.key}`,
                                      label: tab.label,
                                      active: tab.key === activeTab,
                                      count: tab.count,
                                  }))}/>}
        <div className={styles.stack}>{children}</div>
    </Page>;
}

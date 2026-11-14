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

"use client";

import React, { useState } from "react";
import { useTranslations } from "next-intl";
import { revealFormalIdentifierAction } from "@/app/actions/members";
import ActionButton from "@/components/form/ActionButton";
import { Empty } from "@/components/members/format";

export default function RevealIdentifier({memberId, masked}: { memberId: number; masked: string | null }) {
    const t = useTranslations('members');
    const [revealed, setRevealed] = useState<string | null>(null);

    if (revealed !== null) {
        return <span>
            <code aria-live="polite">{revealed}</code>{' '}
            <ActionButton label={t('hideIdentifier')}
                          action={async () => {
                              setRevealed(null);
                              return {ok: true as const};
                          }}/>
        </span>;
    }

    return <span>
        {masked ? <code>{masked}</code> : <Empty/>}{' '}
        <ActionButton label={t('showIdentifier')}
                      action={() => revealFormalIdentifierAction(memberId)}
                      onSuccess={result => setRevealed(result.data ?? null)}/>
    </span>;
}

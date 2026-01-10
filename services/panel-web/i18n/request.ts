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

import { getRequestConfig } from 'next-intl/server';

import messagesEn from './messages/messages-en';
import messagesPl from './messages/messages-pl';

const localesMap = {
    en: messagesEn,
    pl: messagesPl
} as const;

const supportedLocales = Object.keys(localesMap);

function validateLocale(locale: string): locale is keyof typeof localesMap {
    return supportedLocales.includes(locale);
}

export default getRequestConfig(async ({requestLocale}) => {
    const locale = (await requestLocale) ?? 'en';

    if (!validateLocale(locale)) {
        throw new Error(`Unsupported locale: ${locale}`);
    }

    const messages = localesMap[locale];

    return {
        locale,
        messages
    };
});
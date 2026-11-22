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

export type NavIcon = 'dashboard' | 'profile' | 'members' | 'requests' | 'ending' | 'bodies' | 'fees' | 'recruitment';

export type NavItem = {
    href: string;
    labelKey: string;
    icon: NavIcon;
    badge?: number;
};

export type NavGroup = {
    labelKey: string;
    items: NavItem[];
};

export function activeHref(pathname: string, groups: NavGroup[]): string | undefined {
    const hrefs = groups.flatMap(g => g.items.map(i => i.href));
    return hrefs
        .filter(href => href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`))
        .sort((a, b) => b.length - a.length)[0];
}

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

type FlaggedCssClasses = Readonly<Record<string, boolean>> | Map<string, boolean>;

function classesFromEntries(obj: FlaggedCssClasses) {
    const entries = obj instanceof Map ? [...obj.entries()] : Object.entries(obj);

    return entries.filter(([, value]) => value)
                  .map(([key]) => key)
                  .join(' ');
}

export default function css(...classes: ReadonlyArray<FlaggedCssClasses | string | undefined | null | boolean>) {
    return classes.filter(x => !!x && typeof x !== 'boolean')
                  .map(classArg => {
                      if (typeof classArg === 'string') {
                          return classArg;
                      } else {
                          return classesFromEntries(classArg as unknown as FlaggedCssClasses);
                      }
                  }).join(' ');
}
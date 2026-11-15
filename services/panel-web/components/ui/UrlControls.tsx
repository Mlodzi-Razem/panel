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

import React, { useEffect, useId, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { CaretDownIcon, MagnifyingGlassIcon } from "@phosphor-icons/react/ssr";
import styles from "./ui.module.scss";

const DEBOUNCE_MS = 300;

function useUrlParams(resetParams: readonly string[]) {
    const router = useRouter();
    const pathname = usePathname();
    const searchParams = useSearchParams();

    const set = (key: string, value: string) => {
        const next = new URLSearchParams(window.location.search);
        if (value) {
            next.set(key, value);
        } else {
            next.delete(key);
        }
        for (const reset of resetParams) {
            if (reset !== key) {
                next.delete(reset);
            }
        }
        const qs = next.toString();
        router.replace(qs ? `${pathname}?${qs}` : pathname, {scroll: false});
    };

    return {searchParams, set};
}

export type UrlSearchFieldProps = {
    param?: string;
    label: string;
    placeholder: string;
    resetParams?: readonly string[];
};

export function UrlSearchField({param = 'q', label, placeholder, resetParams = ['page']}: UrlSearchFieldProps) {
    const {searchParams, set} = useUrlParams(resetParams);
    const current = searchParams.get(param) ?? '';
    const [value, setValue] = useState(current);
    const id = useId();

    useEffect(() => {
        if (value.trim() === current) {
            return;
        }
        const handle = setTimeout(() => set(param, value.trim()), DEBOUNCE_MS);
        return () => clearTimeout(handle);
    });

    return <div className={`${styles.pill} ${styles.search}`}>
        <MagnifyingGlassIcon aria-hidden/>
        <label htmlFor={id} className={styles.srOnly}>
            {label}
        </label>
        <input id={id}
               type="search"
               value={value}
               placeholder={placeholder}
               maxLength={100}
               autoComplete="off"
               onChange={event => setValue(event.target.value)}/>
    </div>;
}

export type UrlFilterSelectProps = {
    param: string;
    label: string;
    allLabel?: string;
    icon?: React.ReactNode;
    options: { value: string; label: string }[];
    defaultValue?: string;
    resetParams?: readonly string[];
};

export function UrlFilterSelect({param, label, allLabel, icon, options, defaultValue = '', resetParams = ['page']}: UrlFilterSelectProps) {
    const {searchParams, set} = useUrlParams(resetParams);
    const value = searchParams.get(param) ?? defaultValue;
    const id = useId();

    return <div className={`${styles.pill} ${styles.select}`} data-active={!!value && value !== defaultValue}>
        {icon}
        <label htmlFor={id} className={styles.srOnly}>
            {label}
        </label>
        <select id={id} value={value} onChange={event => set(param, event.target.value === defaultValue ? '' : event.target.value)}>
            {allLabel !== undefined && <option value="">{allLabel}</option>}
            {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <CaretDownIcon aria-hidden className={styles.chevron}/>
    </div>;
}

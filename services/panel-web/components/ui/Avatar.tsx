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
import styles from "./ui.module.scss";
import css from "@/util/css";

const PALETTE = ['#720546', '#1f2f6b', '#1e7a4a', '#b45309', '#5b2a86', '#0f6e6e', '#9f1239', '#3a4a5c'];

export function initialsOf(name: string): string {
    const parts = name.trim().split(/\s+/).filter(part => /\p{L}/u.test(part));
    if (parts.length === 0) {
        return '?';
    }
    const first = parts[0][0] ?? '';
    const last = parts.length > 1 ? parts[parts.length - 1][0] ?? '' : '';
    return (first + last).toLocaleUpperCase();
}

function colorOf(name: string): string {
    let hash = 0;
    for (const char of name) {
        hash = (hash * 31 + char.codePointAt(0)!) | 0;
    }
    return PALETTE[Math.abs(hash) % PALETTE.length];
}

export type AvatarProps = {
    name: string;
    image?: string | null;
    size?: number;
    className?: string;
};

export default function Avatar({name, image, size = 36, className}: AvatarProps) {
    const [failed, setFailed] = useState(false);

    if (image && !failed) {
        return <img src={image}
                    alt=""
                    width={size}
                    height={size}
                    referrerPolicy="no-referrer"
                    onError={() => setFailed(true)}
                    className={css(styles.avatar, className)}/>;
    }

    return <span className={css(styles.avatar, className)}
                 aria-hidden
                 style={{width: size, height: size, background: colorOf(name), fontSize: Math.round(size * 0.38)}}>
        {initialsOf(name)}
    </span>;
}

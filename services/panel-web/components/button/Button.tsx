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

import styles from './Button.module.scss';
import css from "@/util/css";
import React from "react";

export type ButtonVariant = 'primary' | 'secondary' | 'outline';

export type ButtonProps = React.PropsWithChildren<{
    onClick?: () => void;
    variant?: ButtonVariant;
    className?: string;
    style?: React.CSSProperties;
    iconLeft?: React.ReactNode;
    iconRight?: React.ReactNode;
    disabled?: boolean;
}>;

export default function Button({
    onClick,
    variant = 'primary',
    className,
    children,
    style,
    iconLeft,
    iconRight,
    disabled = false
}: ButtonProps) {
    return <button onClick={onClick}
                   className={css(styles.button, className)}
                   style={style}
                   data-variant={variant}
    disabled={disabled}>
        {iconLeft && <div className={styles.iconContainer}>{iconLeft}</div>}
        <div>{children}</div>
        {iconRight && <div className={styles.iconContainer}>{iconRight}</div>}
    </button>;
}
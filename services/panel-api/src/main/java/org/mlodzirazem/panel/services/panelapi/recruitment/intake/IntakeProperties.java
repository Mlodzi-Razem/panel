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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The public "I want to join" form: switch, abuse limits and retention periods. */
@Getter
@Setter
@ConfigurationProperties("panel.api.recruitment.intake")
public class IntakeProperties {
    /**
     * Whether the public form accepts submissions. Off by default: switching it on needs an approved privacy notice.
     * The queue in the back office and the retention run work regardless.
     */
    private boolean enabled = false;

    /** A form sent faster than this after it was opened is treated as automated. */
    private int minFillSeconds = 4;

    /** An opened form can be sent for this long. */
    private int formValidHours = 24;

    private int maxPerHour = 30;

    private int maxPerDay = 150;

    /** Submissions waiting in the queue (ignored repeats included until they age out). */
    private int maxPending = 500;

    /** Applied only when panel-web passes a client address, i.e. when it runs behind a trusted proxy. */
    private int maxPerClientPerHour = 5;

    /** Unhandled submissions are erased after this many days. */
    private int pendingRetentionDays = 60;

    /** Discarded submissions are erased this many days after the decision (spam and withdrawals at once). */
    private int discardedRetentionDays = 30;
}

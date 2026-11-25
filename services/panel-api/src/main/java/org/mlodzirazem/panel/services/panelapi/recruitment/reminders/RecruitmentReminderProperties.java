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

package org.mlodzirazem.panel.services.panelapi.recruitment.reminders;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code panel.api.recruitment.reminders.*}: daily reminders to the district boards and notifications to the
 * national board. Sending also needs {@code panel.mail.enabled=true}; with mail off the job does nothing but count.
 */
@Getter
@Setter
@ConfigurationProperties("panel.api.recruitment.reminders")
public class RecruitmentReminderProperties {
    /** Master switch of the scheduled job and the notifications (mail has its own switch on top). */
    private boolean enabled = true;

    /** Public address of the Panel, the base of the links in the mails (no trailing slash needed). */
    private String panelBaseUrl = "https://panel.mlodzirazem.org";

    /** How many days before the end of the assessment window (and of the second assessment) a case is listed. */
    private int windowWarningDays = 7;

    /** How many days before the ZO admission resolution deadline a case is listed. */
    private int resolutionWarningDays = 3;

    /** How far back the very first ZK digest looks. */
    private int firstDigestLookbackDays = 7;
}

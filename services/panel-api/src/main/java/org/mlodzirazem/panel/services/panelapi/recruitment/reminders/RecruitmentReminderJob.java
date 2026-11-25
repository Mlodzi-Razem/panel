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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily run (cron {@code panel.api.recruitment.reminders.cron}, 07:00 Europe/Warsaw by default). The clock is the
 * single {@link Clock} bean; the cron trigger only decides when to look.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecruitmentReminderJob {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final RecruitmentNotifier notifier;
    private final RecruitmentReminderProperties properties;
    private final Clock clock;

    @Scheduled(
        cron = "${panel.api.recruitment.reminders.cron:0 0 7 * * *}",
        zone = "${panel.api.recruitment.reminders.zone:Europe/Warsaw}"
    )
    void run() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            notifier.sendZoDailyReminders(LocalDate.now(clock.withZone(WARSAW)));
            notifier.sendZkProbationDigest(clock.instant());
        } catch (RuntimeException e) {
            log.error("recruitment reminders run failed: {}", e.getClass().getSimpleName());
        }
    }
}

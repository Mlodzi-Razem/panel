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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.RecruitmentIntakeQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily retention of intake submissions (03:30 Europe/Warsaw). It also runs while the public form is off. */
@Slf4j
@Component
@RequiredArgsConstructor
class IntakeRetentionJob {
    private final RecruitmentIntakeQueueService queue;

    @Scheduled(cron = "${panel.api.recruitment.intake.retention-cron:0 30 3 * * *}", zone = "Europe/Warsaw")
    void run() {
        try {
            queue.applyRetention();
        } catch (RuntimeException e) {
            // only the type: a database message may quote row values
            log.error("recruitment intake retention failed: {}", e.getClass().getSimpleName());
        }
    }
}

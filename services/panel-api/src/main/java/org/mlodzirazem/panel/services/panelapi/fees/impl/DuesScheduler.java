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

package org.mlodzirazem.panel.services.panelapi.fees.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mlodzirazem.panel.services.panelapi.fees.DuesRun;
import org.mlodzirazem.panel.services.panelapi.fees.DuesService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
class DuesScheduler {
    private final DuesService duesService;
    private final Clock clock;

    @Scheduled(cron = "${panel.fees.dues-cron:0 0 2 * * *}", zone = "UTC")
    void generateCurrentMonth() {
        DuesRun run = duesService.generateDues(YearMonth.now(clock));
        log.info("Dues generated: members={} created={} updated={} cancelled={}", run.members(), run.created(),
            run.updated(), run.cancelled());
    }
}

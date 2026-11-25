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

package org.mlodzirazem.panel.services.panelapi.admin;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.main.web-application-type=none",
    "panel.mandates.file=src/test/resources/admin/mandates.csv"
})
@PostgresTest
class MandateImportRunnerTest extends DomainTestSupport {
    @Autowired
    MandateImportRunner runner;

    @Test
    void printsDryRunReportAndExitsCleanWithoutWriting() {
        // the fixture member 9007 of the checked-in CSV — invented data, no real PESEL numbers
        var member = newMember("Robot", "Importowy", "mazowieckie");
        jdbc.update("UPDATE members SET member_id = '9007' WHERE id = ?", member.getId());

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        try {
            runner.run(null);
        } finally {
            System.setOut(original);
        }

        String output = captured.toString(StandardCharsets.UTF_8);

        assertThat(output).contains("Mode: dry run", "Rows: 1", "Created: 1", "Errors: 0");
        assertThat(output).doesNotContain("Uchwała", "Importowy", "example.org");
        assertThat(runner.getExitCode()).isZero();

        Integer memberships = jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships", Integer.class);

        assertThat(memberships).isZero();
    }
}

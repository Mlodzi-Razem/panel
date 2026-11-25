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

import java.util.List;

/**
 * Report of one mandate import run, rendered to stdout for the operator (codes and counts only,
 * no member names). Mirrors the register importer's report: dry run explains what an apply would
 * write; {@code aborted} means errors were found and nothing was written (all-or-nothing).
 */
public record MandateImportReport(
    String fileName,
    boolean apply,
    boolean aborted,
    int rows,
    int created,
    int unchanged,
    int skipped,
    List<MandateImportProblem> problems
) {
    public MandateImportReport {
        problems = List.copyOf(problems);
    }

    public List<MandateImportProblem> errors() {
        return problems.stream().filter(MandateImportProblem::error).toList();
    }

    public List<MandateImportProblem> warnings() {
        return problems.stream().filter(p -> !p.error()).toList();
    }

    public boolean written() {
        return apply && !aborted;
    }

    public int exitCode() {
        return errors().isEmpty() ? MandateImportMode.EXIT_OK : MandateImportMode.EXIT_PROBLEMS;
    }

    public String render() {
        StringBuilder out = new StringBuilder();

        out.append("Mandate import: ").append(fileName).append('\n');
        out.append("Mode: ").append(apply ? "apply" : "dry run").append('\n');
        out.append("Rows: ").append(rows).append('\n');
        out.append("Created: ").append(created).append('\n');
        out.append("Unchanged: ").append(unchanged).append('\n');
        out.append("Skipped (rows with errors): ").append(skipped).append('\n');
        out.append("Errors: ").append(errors().size()).append('\n');
        out.append("Warnings: ").append(warnings().size()).append('\n');

        if (aborted) {
            out.append("Result: ABORTED, errors found, nothing was written\n");
        } else if (apply) {
            out.append("Result: applied\n");
        } else {
            out.append("Result: dry run, nothing was written\n");
        }

        section(out, "Errors", errors());
        section(out, "Warnings", warnings());

        return out.toString();
    }

    private static void section(StringBuilder out, String title, List<MandateImportProblem> list) {
        if (list.isEmpty()) {
            return;
        }

        out.append('\n').append(title).append(":\n");
        list.forEach(p -> out.append(p.render()).append('\n'));
    }
}

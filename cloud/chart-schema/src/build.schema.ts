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

import * as z from 'zod/v4';
import * as fs from "node:fs";
import * as path from "node:path";
import PanelValues from "./values";

function saveFile(outFilePath: string, obj: object) {
    if (fs.existsSync(outFilePath)) {
        fs.rmSync(outFilePath);
    }

    const dir = path.dirname(outFilePath);

    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, {recursive: true})
    }

    const content = JSON.stringify(obj, null, 2) + "\n";
    fs.writeFileSync(outFilePath, content, {encoding: 'utf-8'});
}

const schemaObject = z.toJSONSchema(PanelValues,
    {
        io: "input",
        reused: "ref",
        cycles: "ref",
        unrepresentable: "throw",
        target: "draft-07",
    },
);

const schemaFilePath = "./out/values.schema.json";

saveFile(schemaFilePath, schemaObject);


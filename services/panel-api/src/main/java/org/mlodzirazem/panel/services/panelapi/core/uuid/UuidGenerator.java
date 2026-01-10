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

package org.mlodzirazem.panel.services.panelapi.core.uuid;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

@Component
public class UuidGenerator {
    private static final SecureRandom SECURE_RANDOM;
    private static final TimeBasedEpochRandomGenerator UUIDv7_GENERATOR;

    static  {
        try {
            SECURE_RANDOM = SecureRandom.getInstanceStrong();
            UUIDv7_GENERATOR = Generators.timeBasedEpochRandomGenerator(SECURE_RANDOM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not create a SecureRandom instance", e);
        }
    }
    public UUID uuidv7() {
        return UUIDv7_GENERATOR.generate();
    }
}

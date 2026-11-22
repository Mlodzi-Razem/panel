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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationArtifactStore;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Filesystem artifact store: deterministic content-addressed references (sha256), prepared dirs.
 * Replaceable by a staging volume without touching the service (the interface hides the layout).
 */
@Component
@RequiredArgsConstructor
class FilesystemDeclarationArtifactStore implements DeclarationArtifactStore {
    private final DeclarationProperties properties;

    @Override
    public String save(byte[] content) {
        String ref = RecruitmentEformRules.sha256Hex(content) + ".pdf";

        try {
            Files.createDirectories(Path.of(properties.getStorageDir()));
            Files.write(Path.of(properties.getStorageDir(), ref), content);
            return ref;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot persist a declaration artifact", failure);
        }
    }

    @Override
    public byte[] load(String storageRef) {
        try {
            return Files.readAllBytes(Path.of(properties.getStorageDir(), storageRef));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read a declaration artifact " + storageRef, failure);
        }
    }
}

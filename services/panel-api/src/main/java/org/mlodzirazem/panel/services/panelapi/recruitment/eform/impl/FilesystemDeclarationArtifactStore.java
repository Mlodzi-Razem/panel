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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationArtifactStore;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Filesystem artifact store: deterministic content-addressed references (sha256) in one flat directory.
 * Deployments mount a persistent volume there; the files hold personal data, so they are owner-only.
 * The directory is probed at startup: an unwritable one must stop the boot, not the candidate's submit.
 */
@Component
@RequiredArgsConstructor
class FilesystemDeclarationArtifactStore implements DeclarationArtifactStore {
    private final DeclarationProperties properties;

    @PostConstruct
    void requireWritableDirectory() {
        Path directory = directory();

        try {
            Files.createDirectories(directory);
            Files.delete(Files.createTempFile(directory, ".probe-", ".tmp"));
        } catch (IOException failure) {
            throw new IllegalStateException(
                ("Declaration storage directory '%s' is not writable by the user '%s'. Set "
                    + "panel.api.recruitment.declaration.storage-dir (env PANEL_DECLARATIONS_DIR) to a directory "
                    + "on a persistent volume writable by the API process (uid 10001 in the container image). "
                    + "Cause: %s").formatted(directory, System.getProperty("user.name"),
                    failure.getClass().getSimpleName()), failure);
        }
    }

    @Override
    public String save(byte[] content) {
        String ref = RecruitmentEformRules.sha256Hex(content) + ".pdf";

        try {
            // written aside and renamed, so a reader (or the backup job) never sees a partial *.pdf;
            // createTempFile makes the file owner-only (rw-------) on POSIX file systems
            Path partial = Files.createTempFile(directory(), ".upload-", ".tmp");

            try {
                try (FileChannel channel = FileChannel.open(partial, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(content);

                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }

                    channel.force(true);
                }

                Files.move(partial, directory().resolve(ref), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(partial);
            }

            return ref;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot persist a declaration artifact", failure);
        }
    }

    @Override
    public byte[] load(String storageRef) {
        try {
            return Files.readAllBytes(directory().resolve(storageRef));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read a declaration artifact " + storageRef, failure);
        }
    }

    @Override
    public void delete(String storageRef) {
        try {
            Files.deleteIfExists(directory().resolve(storageRef));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot delete a declaration artifact " + storageRef, failure);
        }
    }

    private Path directory() {
        return Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
    }
}

/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemDeclarationArtifactStoreTest {
    @TempDir
    Path temp;

    private static FilesystemDeclarationArtifactStore storeIn(Path directory) {
        DeclarationProperties properties = new DeclarationProperties();
        properties.setStorageDir(directory.toString());
        return new FilesystemDeclarationArtifactStore(properties);
    }

    @Test
    void savesContentAddressedOwnerOnlyFilesAndLeavesNothingBehind() throws IOException {
        Path directory = temp.resolve("declarations");
        FilesystemDeclarationArtifactStore store = storeIn(directory);
        store.requireWritableDirectory();
        byte[] content = "%PDF-1.4 fixture".getBytes(StandardCharsets.US_ASCII);

        String ref = store.save(content);
        assertThat(store.save(content)).isEqualTo(ref);

        assertThat(ref).isEqualTo(RecruitmentEformRules.sha256Hex(content) + ".pdf");
        assertThat(store.load(ref)).isEqualTo(content);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.resolve(ref))))
            .isEqualTo("rw-------");

        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactly(ref);
        }
    }

    @Test
    void anUnusableDirectoryStopsTheBootWithAReadableMessage() throws IOException {
        // below a regular file: unusable even for root, so the test does not depend on the build user
        Path directory = Files.createFile(temp.resolve("not-a-directory")).resolve("declarations");

        assertThatThrownBy(storeIn(directory)::requireWritableDirectory)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(directory.toString())
            .hasMessageContaining("PANEL_DECLARATIONS_DIR")
            .hasMessageContaining("10001");
    }

    @Test
    void aReadOnlyDirectoryStopsTheBoot() throws IOException {
        Path directory = Files.createDirectory(temp.resolve("read-only"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-x------")));
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(directory), "root ignores the mode");

        assertThatThrownBy(storeIn(directory)::requireWritableDirectory)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not writable");
    }
}

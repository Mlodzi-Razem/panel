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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

/**
 * Storage of declaration artifacts (PO-5, filesystem facade for the staging volume today).
 * References are opaque strings the service persists in declaration_artifacts.storage_ref.
 */
public interface DeclarationArtifactStore {
    /** Persists the artifact and returns its storage reference. */
    String save(byte[] content);

    byte[] load(String storageRef);

    /** Removes the stored artifact; an already missing artifact is not an error (idempotent). */
    void delete(String storageRef);
}

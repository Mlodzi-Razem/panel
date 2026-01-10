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

package org.mlodzirazem.panel.services.panelapi.directory.api.components;

import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.beans.impl.DirectoryImplementationComplianceValidator;

import java.util.List;

/**
 * Created to provide some compile-time safety for <code>injectService</code> methods.
 * <p>
 * It is sealed to prevent The drawback is that it forbids us from using child packages.
 *
 * @see DirectoryImplementation
 * @see DirectoryImplementationComplianceValidator
 */
public sealed interface DirectoryInjectableComponent
    permits DirectoryUserProfileFetcher {

    @SuppressWarnings("unchecked")
    static List<Class<? extends DirectoryInjectableComponent>> permittedSubclasses() {
        return List.of((Class<? extends DirectoryInjectableComponent>[]) DirectoryInjectableComponent.class.getPermittedSubclasses());
    }
}

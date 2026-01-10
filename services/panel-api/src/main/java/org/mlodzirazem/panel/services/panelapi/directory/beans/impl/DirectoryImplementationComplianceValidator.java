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

package org.mlodzirazem.panel.services.panelapi.directory.beans.impl;

import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryInjectableComponent;
import org.springframework.stereotype.Component;

@Component
public class DirectoryImplementationComplianceValidator {
    public void validate(DirectoryImplementation directoryImplementation) throws DirectoryComplianceException {
        validateAllServicesAreExposed(directoryImplementation);
    }

    private void validateAllServicesAreExposed(DirectoryImplementation directoryImplementation) throws DirectoryComplianceException {
        for (var componentClass : DirectoryInjectableComponent.permittedSubclasses()) {
            try {
                directoryImplementation.injectService(componentClass);
            } catch (Exception ex) {
                throw new DirectoryComplianceException(
                    "Directory implementation does not expose required service: " + componentClass.getName(),
                    ex
                );
            }
        }
    }
}

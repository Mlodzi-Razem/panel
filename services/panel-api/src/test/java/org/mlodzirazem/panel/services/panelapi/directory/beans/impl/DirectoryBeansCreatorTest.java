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

import lombok.SneakyThrows;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;
import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryInjectableComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {DirectoryBeansCreator.class, ReloadableDirectoryImplementation.class})
class DirectoryBeansCreatorTest {
    @MockitoBean
    DirectoryImplementationComplianceValidator complianceValidator;

    @MockitoBean
    DirectoryImplementationProvider implementationProvider;

    @Autowired
    ApplicationContext context;

    @Autowired
    ReloadableDirectoryImplementation reloadableDirectoryImplementation;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        doNothing().when(complianceValidator).validate(any());
        when(implementationProvider.createImplementation()).thenReturn(mock(DirectoryImplementation.class));

        reloadableDirectoryImplementation.reload();
    }

    @ParameterizedTest
    @ArgumentsSource(DirectoryComponentsProvider.class)
    void allServicesAreInjected(Class<? extends DirectoryInjectableComponent> componentClass) {
        Map<String, DirectoryInjectableComponent> beans = context.getBeansOfType(DirectoryInjectableComponent.class);
        Collection<DirectoryInjectableComponent> components = beans.values();

        assertThat(components)
            .as("There must be one service of type " + componentClass.getName())
            .haveExactly(1, new Condition<>(componentClass::isInstance, "element of type " + componentClass.getName()));
    }

    private static class DirectoryComponentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context)
            throws Exception {
            return DirectoryInjectableComponent.permittedSubclasses().stream().map(c -> Arguments.of(c));
        }
    }
}
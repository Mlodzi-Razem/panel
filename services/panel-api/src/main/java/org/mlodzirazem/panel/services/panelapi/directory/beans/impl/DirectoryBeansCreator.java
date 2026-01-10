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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryInjectableComponent;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class responsible for creating and registering Directory beans.
 * <ul>
 * <li>First:<br>
 * <code>postProcessBeanFactory</code> - register beans definitions. The most important part
 * is <code>.addDependsOn("reloadableDirectoryImplementation")</code>, because it informs the {@link BeanFactory} that
 * it should not try to autowire interfaces implementing {@link DirectoryInjectableComponent} before
 * {@link ReloadableDirectoryImplementation} is ready.</li>
 * <li>Second:<br>
 * <code>postProcessAfterInitialization</code> - uses
 * {@link ReloadableDirectoryImplementation}<code>::injectService</code> to provide interfaces' implementation using
 * proxy.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class DirectoryBeansCreator implements BeanFactoryPostProcessor, BeanPostProcessor, ApplicationContextAware {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

        DirectoryInjectableComponent.permittedSubclasses().forEach(subclass -> {
            registerBeanDefinition(subclass, registry);
        });
    }

    private static void registerBeanDefinition(
        Class<? extends DirectoryInjectableComponent> subclass,
        BeanDefinitionRegistry registry
    ) {
        BeanDefinition beanDefinition =
            BeanDefinitionBuilder.rootBeanDefinition(subclass)
                                 .addDependsOn("reloadableDirectoryImplementation")
                                 .setScope(BeanDefinition.SCOPE_SINGLETON)
                                 .getBeanDefinition();
        registry.registerBeanDefinition(subclass.getSimpleName(), beanDefinition);
    }

    @Override
    public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ReloadableDirectoryImplementation reloadableDirectoryImplementation) {
            DirectoryInjectableComponent.permittedSubclasses()
                                        .forEach(subclass -> {
                                            registerSingleton(reloadableDirectoryImplementation, subclass);
                                        });
        }

        return bean;
    }

    private void registerSingleton(
        ReloadableDirectoryImplementation reloadableDirectoryImplementation,
        Class<? extends DirectoryInjectableComponent> subclass
    ) {
        assert applicationContext != null : "ApplicationContextAware::setApplicationContext failed";

        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        beanFactory.registerSingleton(
            subclass.getSimpleName(),
            reloadableDirectoryImplementation.injectService(subclass)
        );
    }

    // Autowiring may not work in this case
    @Nullable
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

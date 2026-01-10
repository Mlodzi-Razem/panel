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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.api.ReloadableDirectoryService;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryInjectableComponent;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReloadableDirectoryImplementation implements DirectoryImplementation, ReloadableDirectoryService {
    private final DirectoryImplementationComplianceValidator complianceValidator;
    private final DirectoryImplementationProvider implementationProvider;

    private final AtomicReference<@Nullable DirectoryImplementation> delegate = new AtomicReference<>(null);
    private final Map<Class<? extends DirectoryInjectableComponent>, DirectoryInjectableComponent> cachedServices = new ConcurrentHashMap<>();

    /**
     * Returns a <b>cached proxy</b> which always calls <code>current().method()</code>. The proxy is cached for the
     * lifetime of current <code>delegate</code>.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends DirectoryInjectableComponent> T injectService(Class<T> serviceClass) {
        return (T) cachedServices.computeIfAbsent(serviceClass, this::createProxy);
    }

    @SuppressWarnings("unchecked")
    private <T extends DirectoryInjectableComponent> T createProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {serviceClass},
            (_, method, args) -> method.invoke(current(), args)
        );
    }

    @Override
    public void close() throws Exception {
        closeCurrent();
    }

    @Override
    public void reload() {
        try {
            DirectoryImplementation newImpl = getNew();
            setNew(newImpl);
            closeCurrent();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to close or initialize directory implementation", ex);
        }
    }

    private DirectoryImplementation getNew() throws DirectoryComplianceException {
        DirectoryImplementation newImpl = implementationProvider.createImplementation();
        complianceValidator.validate(newImpl);
        return newImpl;
    }

    private void setNew(DirectoryImplementation newImpl) {
        delegate.set(newImpl);
    }

    private void closeCurrent() throws Exception {
        try {
            DirectoryImplementation currentDirImpl = delegate.get();
            if (currentDirImpl != null) {
                currentDirImpl.close();
            }
        } finally {
            delegate.set(null);
            cachedServices.clear();
        }
    }

    private DirectoryImplementation current() {
        DirectoryImplementation dirImpl = delegate.get();
        if (dirImpl == null) {
            return THROWING_PROXY_IMPL;
        }

        return dirImpl;
    }

    private final DirectoryImplementation THROWING_PROXY_IMPL = new ThrowingProxyDirectoryImplementation();
}

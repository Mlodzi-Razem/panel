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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReloadableDirectoryImplementation implements DirectoryImplementation, ReloadableDirectoryService {
    private final DirectoryImplementationComplianceValidator complianceValidator;
    private final DirectoryImplementationProvider implementationProvider;

    private final AtomicReference<@Nullable DirectoryImplementation> delegate = new AtomicReference<>(null);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
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
            serviceClass.getClassLoader(),
            new Class<?>[] {serviceClass},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "DirectoryProxy[" + serviceClass.getSimpleName() + "]";
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }

                lock.readLock().lock();
                try {
                    T service = current().injectService(serviceClass);
                    return method.invoke(service, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                } finally {
                    lock.readLock().unlock();
                }
            }
        );
    }

    @Override
    public void close() throws Exception {
        lock.writeLock().lock();
        try {
            closeCurrent();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void reload() {
        lock.writeLock().lock();
        try {
            DirectoryImplementation newImpl = getNew();
            DirectoryImplementation oldImpl = delegate.getAndSet(newImpl);
            closeQuietly(oldImpl);
        } catch (Exception ex) {
            log.error("Failed to reload directory implementation; keeping the previous one", ex);
            throw new IllegalStateException("Failed to initialize directory implementation", ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String currentProviderName() {
        DirectoryImplementation current = delegate.get();
        return current == null ? "none" : current.getClass().getSimpleName();
    }

    private DirectoryImplementation getNew() throws DirectoryComplianceException {
        DirectoryImplementation newImpl = implementationProvider.createImplementation();
        try {
            complianceValidator.validate(newImpl);
        } catch (DirectoryComplianceException | RuntimeException ex) {
            closeQuietly(newImpl);
            throw ex;
        }
        return newImpl;
    }

    private void closeQuietly(@Nullable DirectoryImplementation impl) {
        if (impl == null) {
            return;
        }
        try {
            impl.close();
        } catch (Exception ex) {
            log.warn("Failed to close directory implementation {}", impl.getClass().getName(), ex);
        }
    }

    private void closeCurrent() throws Exception {
        DirectoryImplementation currentDirImpl = delegate.getAndSet(null);
        if (currentDirImpl != null) {
            currentDirImpl.close();
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

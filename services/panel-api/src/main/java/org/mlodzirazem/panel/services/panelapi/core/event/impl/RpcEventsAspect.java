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

package org.mlodzirazem.panel.services.panelapi.core.event.impl;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.mlodzirazem.panel.services.panelapi.core.event.RpcEvent;
import org.springframework.stereotype.Component;

@Component
@Aspect
@RequiredArgsConstructor
public class RpcEventsAspect {
    private final ApplicationEventsRpcRegistry registry;

    /**
     * Methods annotated with @EventListener OR annotations annotated with @EventListener (@TransactionalEventListener
     * etc.)
     */
    @Pointcut(
        """
            @annotation(org.springframework.context.event.EventListener) ||
            @annotation(org.springframework.transaction.event.TransactionalEventListener) ||
            @annotation(org.springframework.modulith.events.ApplicationModuleListener)
        """
    )
    public void eventListener() {
    }

    @Around(value = "eventListener() && args(event)", argNames = "joinPoint,event")
    public Object onRpcEventException(ProceedingJoinPoint joinPoint, RpcEvent<?> event) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            registry.respondError(event.correlationId(), ex);
            throw ex;
        }
    }
}

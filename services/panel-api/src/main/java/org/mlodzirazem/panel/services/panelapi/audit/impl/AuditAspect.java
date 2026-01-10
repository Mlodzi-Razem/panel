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

package org.mlodzirazem.panel.services.panelapi.audit.impl;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.Audited;
import org.mlodzirazem.panel.services.panelapi.audit.AuditedPayload;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Component
@RequiredArgsConstructor
@Aspect
public class AuditAspect {
    private final AuditPersister persister;

    @Pointcut("@annotation(org.mlodzirazem.panel.services.panelapi.audit.Audited) && execution(* *(..))")
    public void audited() {
    }

    @Before("audited()")
    public void beforeAuditedOperation(JoinPoint joinPoint) {
        String id = getAuditedOperationId(joinPoint);
        Object payload = findPayload(joinPoint);

        persister.persist(id, payload);
    }

    private static String getAuditedOperationId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(Audited.class).id();
    }

    private Object findPayload(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        return findAnnotatedPayload(signature, parameters, args);
    }

    private static @Nullable Object findAnnotatedPayload(
        MethodSignature signature,
        Parameter[] parameters,
        Object[] args
    ) {
        Object annotated = null;
        int foundAnnotated = 0;

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(AuditedPayload.class)) {
                annotated = args[i];
                foundAnnotated++;

                if (foundAnnotated > 1) {
                    throw new IllegalStateException(
                        "Only one parameter can be annotated with @AuditedPayload. Check method " +
                        signature.toLongString());
                }
            }
        }
        return annotated;
    }
}

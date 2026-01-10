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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.One;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class ApplicationEventsRpcRegistry {
    /** correlationId -> request */
    private final Map<UUID, RpcResponseRequest> responseRequests = new ConcurrentHashMap<>(1024);

    private final Scheduler reactorScheduler;
    private final Clock clock;

    public ApplicationEventsRpcRegistry(@Qualifier("applicationTaskExecutor") AsyncTaskExecutor asyncTaskExecutor, Clock clock) {
        this.reactorScheduler = Schedulers.fromExecutor(asyncTaskExecutor);
        this.clock = clock;
    }

    public Mono<Object> registerResponseExpectation(UUID correlationId, Duration timeout) {
        var sink = Sinks.one();

        addRequest(correlationId, timeout, sink);

        return sink.asMono()
                   .doFinally(_ -> removeRequest(correlationId))
                   .timeout(timeout)
                   .publishOn(reactorScheduler);
    }

    private void addRequest(UUID correlationId, Duration timeout, One<Object> sink) {
        var newRequest = new RpcResponseRequest(clock.instant(), timeout, sink);
        if (responseRequests.containsKey(correlationId)) {
            IllegalStateException ex = new IllegalStateException("Duplicate correlationId " + correlationId);
            sink.tryEmitError(ex);
            throw ex;
        }

        responseRequests.put(correlationId, newRequest);
    }

    private void removeRequest(UUID correlationId) {
        responseRequests.remove(correlationId);
    }

    public void respondSuccess(UUID correlationId, Object response) {
        One<Object> sink = findSink(correlationId);

        sink.tryEmitValue(response).orThrow();
    }

    public void respondSuccess(UUID correlationId) {
        One<Object> sink = findSink(correlationId);

        sink.tryEmitEmpty().orThrow();
    }

    public void respondError(UUID correlationId, Throwable error) {
        One<Object> sink = findSink(correlationId);

        sink.tryEmitError(error).orThrow();
    }

    private One<Object> findSink(UUID correlationId) {
        RpcResponseRequest request = responseRequests.get(correlationId);
        if (request == null) {
            throw new IllegalStateException("No RPC response request found for correlationId " + correlationId);
        }
        return request.sink();
    }

    record RpcResponseRequest(
        Instant addedAt,
        Duration timeout,
        One<Object> sink
    ) {
    }

    /**
     * Makes sure that there are no orphaned RPC response requests. This method iterates over all registered RPC
     * response requests and removes those that have timed out.
     * <p>
     * This method is scheduled to run every 5 minutes
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void vacuum() {
        responseRequests.entrySet()
                        .removeIf(entry -> {
                            RpcResponseRequest request = entry.getValue();
                            Instant deadline = request.addedAt().plus(request.timeout());
                            Instant now = clock.instant();

                            return now.isAfter(deadline);
                        });
    }
}

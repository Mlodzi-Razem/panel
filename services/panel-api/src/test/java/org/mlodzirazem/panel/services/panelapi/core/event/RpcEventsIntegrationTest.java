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

package org.mlodzirazem.panel.services.panelapi.core.event;

import lombok.SneakyThrows;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mlodzirazem.panel.services.panelapi.core.event.impl.ApplicationEventsRpcRegistry;
import org.mlodzirazem.panel.services.panelapi.core.event.impl.RpcEventsAspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = {
        RpcResponder.class,
        RpcEventPublisher.class,
        ApplicationEventsRpcRegistry.class,
        RpcEventsAspect.class,
        RpcEventsIntegrationTest.TestRpcListener.class,
        RpcEventsIntegrationTest.RpcTestConfiguration.class
    }
)
@EnableAsync
@EnableAspectJAutoProxy
@Execution(ExecutionMode.CONCURRENT)
class RpcEventsIntegrationTest {
    static final Duration TIMEOUT = Duration.ofMillis(50);
    public static final int TEST_REPETITIONS = 512;
    static final Map<UUID, AtomicInteger> VOID_RESPONSES = new ConcurrentHashMap<>(TEST_REPETITIONS);

    @Autowired
    RpcEventPublisher publisher;

    @RepeatedTest(value = TEST_REPETITIONS, failureThreshold = 1)
    void rpcEventPublishing() {
        TestRpcEvent event = new TestRpcEvent(UUID.randomUUID());
        String response = publisher.publishAndReceive(event, TIMEOUT);
        assertThat(response).isEqualTo("RESPONSE:" + event.correlationId());
    }

    @RepeatedTest(value = TEST_REPETITIONS, failureThreshold = 1)
    void rpcEventExceptionHandling() {
        ThrowingRpcEvent event = new ThrowingRpcEvent(UUID.randomUUID());
        assertThatThrownBy(() -> publisher.publishAndReceive(event, TIMEOUT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("THROWING");
    }

    @RepeatedTest(value = TEST_REPETITIONS, failureThreshold = 1)
    void rpcEventTimeoutHandling() {
        TimeoutRpcEvent event = new TimeoutRpcEvent(UUID.randomUUID());
        assertThatThrownBy(() -> publisher.publishAndReceive(event, TIMEOUT))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Timeout");
    }

    @RepeatedTest(value = TEST_REPETITIONS, failureThreshold = 1)
    void voidRpcEventHandling() {
        VoidRpcEvent event = new VoidRpcEvent(UUID.randomUUID());
        publisher.publishAndReceive(event, TIMEOUT);

        assertVoidResponsesCounterEquals(event, 1);
    }

    @SuppressWarnings("SameParameterValue")
    private static void assertVoidResponsesCounterEquals(VoidRpcEvent event, int value) {
        assertThat(VOID_RESPONSES.get(event.correlationId())).hasValue(value);
    }

    record TestRpcEvent(
        UUID correlationId
    ) implements RpcEvent<String> {
    }

    record ThrowingRpcEvent(
        UUID correlationId
    ) implements RpcEvent<String> {
    }

    record TimeoutRpcEvent(
        UUID correlationId
    ) implements RpcEvent<String> {
    }

    record VoidRpcEvent(
        UUID correlationId
    ) implements RpcEvent<Void> {
    }

    static class TestRpcListener {
        @Autowired
        RpcResponder responder;

        @EventListener
        @Async
        void react(TestRpcEvent event) {
            String response = "RESPONSE:" + event.correlationId().toString();
            responder.respond(event, response);
        }

        @EventListener
        @Async
        void react(ThrowingRpcEvent ignoredEvent) {
            throw new IllegalStateException("THROWING");
        }

        @SneakyThrows
        @EventListener
        @Async
        void react(TimeoutRpcEvent ignoredEvent) {
            Thread.sleep(TIMEOUT.plusMillis(150));
        }

        @EventListener
        @Async
        void react(VoidRpcEvent event) {
            incrementVoidResponsesCounter(event);
            responder.respond(event);
        }
    }

    private static void incrementVoidResponsesCounter(VoidRpcEvent event) {
        VOID_RESPONSES.computeIfAbsent(event.correlationId(), _ -> new AtomicInteger(0))
                      .incrementAndGet();
    }

    static class RpcTestConfiguration {
        @Bean
        public Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        @Qualifier("applicationTaskExecutor")
        public AsyncTaskExecutor asyncTaskExecutor() {
            return new SimpleAsyncTaskExecutor();
        }
    }
}
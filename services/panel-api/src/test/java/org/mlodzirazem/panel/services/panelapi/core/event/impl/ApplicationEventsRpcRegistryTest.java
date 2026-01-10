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

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationEventsRpcRegistryTest {
    static final LocalDateTime NOW = LocalDateTime.of(2025, 12, 26, 12, 0);

    @Mock
    Clock clock;

    @Spy
    AsyncTaskExecutor asyncTaskExecutor = new SimpleAsyncTaskExecutor();

    @InjectMocks
    ApplicationEventsRpcRegistry registry;

    @Nested
    class registerResponseExpectation {
        final UUID correlationId = UUID.randomUUID();

        @Test
        void success() {
            Mono<Object> expectation = register(correlationId, Duration.ofSeconds(1));

            registry.respondSuccess(correlationId, "SUCCESS");
            Object response = expectation.block();

            assertThat(response).isEqualTo("SUCCESS");
        }

        @Test
        void exception() {
            Mono<Object> expectation = register(correlationId, Duration.ofSeconds(1));

            registry.respondError(correlationId, new IllegalStateException("ERROR"));

            assertThatThrownBy(expectation::block)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ERROR");
        }

        @Test
        void voidEvent() {
            Mono<Object> expectation = register(correlationId, Duration.ofSeconds(1));

            registry.respondSuccess(correlationId);

            StepVerifier.create(expectation)
                        .expectComplete()
                        .verify(Duration.ofSeconds(1));
        }

        @AfterEach
        void afterEach() {
            // make sure that the provided AsyncTaskExecutor is used
            verify(asyncTaskExecutor).execute(any());

            // make sure that the request has been removed once the Mono completes
            assertThatRespondingIsImpossible(correlationId);
        }
    }

    private void assertThatRespondingIsImpossible(UUID correlationId) {
        assertThatThrownBy(() -> registry.respondSuccess(correlationId, "THROW"))
            .hasMessage("No RPC response request found for correlationId " + correlationId);
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    @Test
    void vacuumClearsTimedOutRequests() {
        UUID longDurationId = UUID.randomUUID();
        Set<UUID> shortDurationIds = Stream.generate(UUID::randomUUID)
                                           .limit(16)
                                           .collect(Collectors.toUnmodifiableSet());

        register(longDurationId, Duration.ofSeconds(10));
        register(shortDurationIds, Duration.ofSeconds(1));

        mockInstant(NOW.plusSeconds(5));
        registry.vacuum();

        assertSoftly(as -> {
            assertThatRequestsHaveBeenRemoved(as, shortDurationIds);
            assertThatRequestIsPreserved(as, longDurationId);
        });
    }

    private Mono<Object> register(UUID longDurationId, Duration duration) {
        mockInstant(NOW);
        return registry.registerResponseExpectation(longDurationId, duration);
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    private void register(Set<UUID> shortDurationIds, Duration duration) {
        shortDurationIds.forEach(id -> {
            register(id, duration);
        });
    }

    private void assertThatRequestIsPreserved(SoftAssertions as, UUID longDurationId) {
        as.assertThatCode(() -> registry.respondSuccess(longDurationId, "SUCCESS"))
          .as("not timed out request should be preserved")
          .doesNotThrowAnyException();
    }

    private void assertThatRequestsHaveBeenRemoved(SoftAssertions as, Set<UUID> shortDurationIds) {
        as.assertThat(shortDurationIds)
          .as("timed out requests should be removed")
          .allSatisfy(this::assertThatRespondingIsImpossible);
    }

    private void mockInstant(LocalDateTime localDateTime) {
        Instant instant = localDateTime.toInstant(ZoneOffset.UTC);

        when(clock.instant()).thenReturn(instant);
    }
}
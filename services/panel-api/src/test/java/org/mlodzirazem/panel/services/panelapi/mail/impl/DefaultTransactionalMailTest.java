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

package org.mlodzirazem.panel.services.panelapi.mail.impl;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailDispatch;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.MailDeliveryStatus;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DefaultTransactionalMailTest {
    private static final String SEND_URL = "https://graph.example/v1.0/users/panel%40mlodzirazem.org/sendMail";

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FakeDeliveries deliveries = new FakeDeliveries();
    private final GraphMailSender graph =
        new GraphMailSender(new GraphMailSenderTest.FixedToken("tok"), builder.build());

    private DefaultTransactionalMail mail(MailProperties properties) {
        return mail(properties, true);
    }

    private DefaultTransactionalMail mail(MailProperties properties, boolean graphConfigured) {
        GraphAppToken token = new GraphAppToken() {
            @Override
            public boolean configured() {
                return graphConfigured;
            }

            @Override
            public String graphUrl() {
                return "https://graph.example";
            }

            @Override
            public String bearer() {
                return "tok";
            }
        };
        return new DefaultTransactionalMail(properties, token, graph, deliveries, clock);
    }

    private static MailProperties enabled(String sender, int limit) {
        return new MailProperties(true, sender, null, null, limit);
    }

    private static MailProperties enabled() {
        return enabled("panel@mlodzirazem.org", 300);
    }

    private static MailMessage to(String recipient) {
        return new MailMessage(recipient, "Subject line", "Body text", "test-purpose", "case:7");
    }

    @Test
    void isDisabledByDefaultAndWhenGraphIsNotConfigured() {
        server.expect(never(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(mail(new MailProperties(false, "panel@mlodzirazem.org", null, null, null))
            .sendAfterCommit(to("a@example.org"))).isEqualTo(MailDispatch.DISABLED);
        assertThat(mail(enabled(), false).sendAfterCommit(to("a@example.org"))).isEqualTo(MailDispatch.DISABLED);

        assertThat(deliveries.rows).isEmpty();
        server.verify();
    }

    @Test
    void defaultsAreSafe() {
        MailProperties defaults = new MailProperties(false, null, null, null, null);

        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.sender()).isNull();
        assertThat(defaults.allowedSenderDomain()).isEqualTo("mlodzirazem.org");
        assertThat(defaults.internalDomains()).containsExactly("mlodzirazem.org");
        assertThat(defaults.dailyExternalLimit()).isEqualTo(300);
    }

    @Test
    void refusesAnOnmicrosoftSender() {
        MailProperties properties =
            new MailProperties(true, "panel@mlodzirazem.onmicrosoft.com", "mlodzirazem.onmicrosoft.com", null, 300);
        server.expect(never(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(mail(properties).sendAfterCommit(to("a@example.org"))).isEqualTo(MailDispatch.REFUSED_SENDER);

        assertThat(deliveries.rows).singleElement().satisfies(row -> {
            assertThat(row.status).isEqualTo(MailDeliveryStatus.REFUSED_SENDER);
            assertThat(row.domain).isEqualTo("example.org");
        });
        server.verify();
    }

    @Test
    void refusesASenderOutsideTheAllowedDomainOrMissing() {
        assertThat(mail(enabled("panel@example.com", 300)).sendAfterCommit(to("a@example.org")))
            .isEqualTo(MailDispatch.REFUSED_SENDER);
        assertThat(mail(enabled(null, 300)).sendAfterCommit(to("a@example.org")))
            .isEqualTo(MailDispatch.REFUSED_SENDER);

        assertThat(deliveries.rows).extracting(r -> r.status)
            .containsExactly(MailDeliveryStatus.REFUSED_SENDER, MailDeliveryStatus.REFUSED_SENDER);
    }

    @Test
    void rejectsInvalidRecipientsWithoutARow() {
        for (String bad : new String[] {"", "  ", "nobody", "a@@example.org", "a@b@example.org", "@example.org",
            "a@", "a b@example.org", "a@example.org\nBcc: x@example.net"}) {
            assertThat(mail(enabled()).sendAfterCommit(to(bad))).as(bad).isEqualTo(MailDispatch.INVALID_RECIPIENT);
        }

        assertThat(deliveries.rows).isEmpty();
    }

    @Test
    void refusesExternalRecipientsOverTheDailyLimit() {
        server.expect(never(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));
        deliveries.externalSent = 2;

        assertThat(mail(enabled("panel@mlodzirazem.org", 2)).sendAfterCommit(to("a@example.org")))
            .isEqualTo(MailDispatch.REFUSED_LIMIT);

        assertThat(deliveries.rows).singleElement().satisfies(row -> {
            assertThat(row.status).isEqualTo(MailDeliveryStatus.REFUSED_LIMIT);
            assertThat(row.external).isTrue();
        });
        assertThat(deliveries.countedSince).isEqualTo(Instant.parse("2026-11-21T12:00:00Z"));
        server.verify();
    }

    @Test
    void internalRecipientsDoNotCountAgainstTheLimit() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));
        deliveries.externalSent = 999;

        assertThat(mail(enabled("panel@mlodzirazem.org", 1)).sendAfterCommit(to("Member@MlodziRazem.org")))
            .isEqualTo(MailDispatch.SCHEDULED);

        assertThat(deliveries.rows).singleElement().satisfies(row -> {
            assertThat(row.status).isEqualTo(MailDeliveryStatus.SENT);
            assertThat(row.external).isFalse();
            assertThat(row.domain).isEqualTo("mlodzirazem.org");
        });
        assertThat(deliveries.countedSince).isNull();
        server.verify();
    }

    @Test
    void sendsImmediatelyWithoutATransactionAndRecordsOnlyNames() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(mail(enabled()).sendAfterCommit(to("a@example.org"))).isEqualTo(MailDispatch.SCHEDULED);

        assertThat(deliveries.rows).singleElement().satisfies(row -> {
            assertThat(row.status).isEqualTo(MailDeliveryStatus.SENT);
            assertThat(row.purpose).isEqualTo("test-purpose");
            assertThat(row.reference).isEqualTo("case:7");
            assertThat(row.domain).isEqualTo("example.org");
            assertThat(row.external).isTrue();
            assertThat(row.errorCode).isNull();
            assertThat(row.toString()).doesNotContain("a@example.org").doesNotContain("Subject").doesNotContain("Body");
        });
        server.verify();
    }

    @Test
    void recordsAFailureAndNeverThrows() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(mail(enabled()).sendAfterCommit(to("a@example.org"))).isEqualTo(MailDispatch.SCHEDULED);

        assertThat(deliveries.rows).singleElement().satisfies(row -> {
            assertThat(row.status).isEqualTo(MailDeliveryStatus.FAILED);
            assertThat(row.errorCode).isEqualTo("HTTP_429");
        });
    }

    @Test
    void sendsOnceAfterCommitAndNotBefore() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));
        DefaultTransactionalMail mail = mail(enabled());
        TransactionTemplate tx = new TransactionTemplate(new NoopTransactionManager());

        tx.executeWithoutResult(status -> {
            assertThat(mail.sendAfterCommit(to("a@example.org"))).isEqualTo(MailDispatch.SCHEDULED);
            assertThat(deliveries.rows).isEmpty();
        });

        assertThat(deliveries.rows).hasSize(1);
        server.verify();
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        server.expect(never(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));
        DefaultTransactionalMail mail = mail(enabled());
        TransactionTemplate tx = new TransactionTemplate(new NoopTransactionManager());

        tx.executeWithoutResult(status -> {
            mail.sendAfterCommit(to("a@example.org"));
            status.setRollbackOnly();
        });

        assertThat(deliveries.rows).isEmpty();
        server.verify();
    }

    static final class FakeDeliveries implements MailDeliveries {
        final List<Row> rows = new ArrayList<>();
        long externalSent;
        @Nullable Instant countedSince;

        @Override
        public long countExternalSentSince(Instant since) {
            countedSince = since;
            return externalSent;
        }

        @Override
        public void record(String purpose, @Nullable String reference, String recipientDomain, boolean external,
                           MailDeliveryStatus status, @Nullable String errorCode) {
            rows.add(new Row(purpose, reference, recipientDomain, external, status, errorCode));
        }

        record Row(String purpose, @Nullable String reference, String domain, boolean external,
                   MailDeliveryStatus status, @Nullable String errorCode) {
        }
    }

    /**
     * Drives transaction synchronisation (commit and rollback callbacks) without a database.
     */
    static final class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}

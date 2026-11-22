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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphMailSenderTest {
    private static final String SEND_URL = "https://graph.example/v1.0/users/panel%40mlodzirazem.org/sendMail";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private final GraphMailSender sender = new GraphMailSender(new FixedToken("tok"), builder.build());

    private static final MailMessage MESSAGE = new MailMessage(
        "candidate@example.org", "Your declaration", "Open https://example.org/x/secret-token", "test-purpose", "case:1");

    @Test
    void postsTheDocumentedSendMailRequestAndTreats202AsSent() {
        server.expect(requestTo(SEND_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer tok"))
              .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
              .andExpect(jsonPath("$.message.subject").value("Your declaration"))
              .andExpect(jsonPath("$.message.body.contentType").value("Text"))
              .andExpect(jsonPath("$.message.body.content").value("Open https://example.org/x/secret-token"))
              .andExpect(jsonPath("$.message.toRecipients.length()").value(1))
              .andExpect(jsonPath("$.message.toRecipients[0].emailAddress.address").value("candidate@example.org"))
              .andExpect(jsonPath("$.saveToSentItems").value(false))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        GraphMailSender.Result result = sender.send("panel@mlodzirazem.org", MESSAGE);

        assertThat(result.sent()).isTrue();
        assertThat(result.errorCode()).isNull();
        server.verify();
    }

    @Test
    void addsReplyToOnlyWhenSet() {
        server.expect(requestTo(SEND_URL))
              .andExpect(jsonPath("$.message.replyTo.length()").value(1))
              .andExpect(jsonPath("$.message.replyTo[0].emailAddress.address").value("district@example.org"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        MailMessage withReplyTo = new MailMessage("candidate@example.org", "S", "B", "test-purpose", "case:1",
            "district@example.org");
        assertThat(sender.send("panel@mlodzirazem.org", withReplyTo).sent()).isTrue();
        server.verify();
        server.reset();

        server.expect(requestTo(SEND_URL))
              .andExpect(jsonPath("$.message.replyTo").doesNotExist())
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(sender.send("panel@mlodzirazem.org", MESSAGE).sent()).isTrue();
        server.verify();
    }

    @Test
    void capturesTheGraphErrorCode() {
        server.expect(requestTo(SEND_URL))
              .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                  .body("{\"error\":{\"code\":\"ErrorAccessDenied\",\"message\":\"Access is denied.\"}}"));

        GraphMailSender.Result result = sender.send("panel@mlodzirazem.org", MESSAGE);

        assertThat(result.sent()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ErrorAccessDenied");
    }

    @Test
    void fallsBackToTheHttpStatusWhenTheBodyIsNoGraphError() {
        server.expect(requestTo(SEND_URL))
              .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_PLAIN).body("upstream down"));

        GraphMailSender.Result result = sender.send("panel@mlodzirazem.org", MESSAGE);

        assertThat(result).isEqualTo(new GraphMailSender.Result(false, "HTTP_502"));
    }

    @Test
    void ignoresAnUnsafeErrorCode() {
        server.expect(requestTo(SEND_URL))
              .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                  .body("{\"error\":{\"code\":\"x y@example.org\"}}"));

        assertThat(sender.send("panel@mlodzirazem.org", MESSAGE).errorCode()).isEqualTo("HTTP_400");
    }

    @Test
    void reportsTokenFailureWithoutThrowing() {
        GraphMailSender failing = new GraphMailSender(new FixedToken(null), builder.build());

        assertThat(failing.send("panel@mlodzirazem.org", MESSAGE)).isEqualTo(new GraphMailSender.Result(false, "TOKEN_FAILED"));
        server.verify();
    }

    @Test
    void messageRenderingNeverShowsAddressSubjectOrBody() {
        assertThat(MESSAGE.toString())
            .contains("test-purpose", "case:1")
            .doesNotContain("example.org", "declaration", "secret-token");
        assertThat(new MailMessage("a@example.org", "S", "B", "p", "r", "reply@example.org").toString())
            .doesNotContain("example.org");
    }

    record FixedToken(String value) implements GraphAppToken {
        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public String graphUrl() {
            return "https://graph.example";
        }

        @Override
        public String bearer() {
            if (value == null) {
                throw new IllegalStateException("no token");
            }
            return value;
        }
    }
}

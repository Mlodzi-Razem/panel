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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code POST /v1.0/users/{sender}/sendMail} with the app-only token. Graph answers 202 with no body; any
 * other outcome becomes a short error code and is never thrown.
 */
@Slf4j
@Component
class GraphMailSender {
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    /**
     * @param sent      Graph accepted the message (HTTP 202)
     * @param errorCode the Graph {@code error.code}, or {@code HTTP_<status>} / {@code UNREACHABLE} /
     *                  {@code TOKEN_FAILED}; null when sent
     */
    record Result(boolean sent, @Nullable String errorCode) {
        static final Result SENT = new Result(true, null);
    }

    private final GraphAppToken token;
    private final RestClient restClient;

    @Autowired
    GraphMailSender(GraphAppToken token) {
        this(token, RestClient.builder().requestFactory(timeouts()).build());
    }

    GraphMailSender(GraphAppToken token, RestClient restClient) {
        this.token = token;
        this.restClient = restClient;
    }

    Result send(String sender, MailMessage message) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("subject", message.subject());
        content.put("body", Map.of("contentType", "Text", "content", message.textBody()));
        content.put("toRecipients", List.of(Map.of("emailAddress", Map.of("address", message.recipient()))));
        if (message.replyTo() != null) {
            content.put("replyTo", List.of(Map.of("emailAddress", Map.of("address", message.replyTo()))));
        }
        Map<String, Object> body = Map.of("message", content, "saveToSentItems", false);

        try {
            String bearer = token.bearer();

            var response = restClient.post()
                .uri(token.graphUrl() + "/v1.0/users/{sender}/sendMail", sender)
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

            return response.getStatusCode().value() == 202
                ? Result.SENT
                : new Result(false, "HTTP_" + response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            return new Result(false, graphErrorCode(e));
        } catch (RestClientException e) {
            return new Result(false, "UNREACHABLE");
        } catch (IllegalStateException e) {
            return new Result(false, "TOKEN_FAILED");
        }
    }

    private static String graphErrorCode(RestClientResponseException e) {
        try {
            Map<?, ?> parsed = e.getResponseBodyAs(Map.class);
            if (parsed != null && parsed.get("error") instanceof Map<?, ?> error
                && error.get("code") instanceof String code && SAFE_CODE.matcher(code).matches()) {
                return code;
            }
        } catch (RuntimeException ignored) {
            // not JSON: fall back to the status
        }

        return "HTTP_" + e.getStatusCode().value();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
}

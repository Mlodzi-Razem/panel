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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake.impl;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Signed "this form was opened at" stamps. The key lives only in this instance (the API runs as one replica): after
 * a restart an open form is refused as expired and the page asks the candidate to send it again.
 */
@Component
class IntakeFormTokens {
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final Clock clock;

    IntakeFormTokens(Clock clock) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, ALGORITHM);
        this.clock = clock;
    }

    String issue() {
        String issuedAt = Long.toString(clock.instant().toEpochMilli());
        return issuedAt + "." + sign(issuedAt);
    }

    /** How long ago the form was opened, or empty for a missing or forged token. */
    Optional<Duration> age(@Nullable String token) {
        if (token == null) {
            return Optional.empty();
        }

        int dot = token.indexOf('.');

        if (dot < 1) {
            return Optional.empty();
        }

        String issuedAt = token.substring(0, dot);

        if (!MessageDigest.isEqual(sign(issuedAt).getBytes(StandardCharsets.UTF_8),
            token.substring(dot + 1).getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        return Optional.of(Duration.between(Instant.ofEpochMilli(Long.parseLong(issuedAt)), clock.instant()));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

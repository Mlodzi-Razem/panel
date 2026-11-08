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

package org.mlodzirazem.panel.services.panelapi.api.security;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(ServiceTokenProperties.class)
class ApiSecurityConfiguration {
    private static final String ERROR_BODY_TEMPLATE = "{\"status\":%d,\"error\":\"%s\"}";

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                .requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            );

        return http.build();
    }

    @Bean
    JwtDecoder serviceTokenDecoder(ServiceTokenProperties properties) {
        SecretKey key = new SecretKeySpec(properties.secretBytes(), "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
            new JwtTimestampValidator(properties.clockSkew()),
            new JwtClaimValidator<String>(JwtClaimNames.ISS, properties.issuer()::equals),
            new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(properties.audience())
            ),
            requiredClaim(JwtClaimNames.SUB),
            requiredClaim(JwtClaimNames.EXP)
        )));

        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> requiredClaim(String claim) {
        return jwt -> {
            Object value = jwt.getClaim(claim);
            if (value == null || value.toString().isBlank()) {
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing required claim: " + claim, null)
                );
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeError(response, HttpStatus.UNAUTHORIZED);
        };
    }

    private static AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, exception) -> writeError(response, HttpStatus.FORBIDDEN);
    }

    private static void writeError(
        HttpServletResponse response,
        HttpStatus status
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(ERROR_BODY_TEMPLATE.formatted(status.value(), status.getReasonPhrase()));
    }
}

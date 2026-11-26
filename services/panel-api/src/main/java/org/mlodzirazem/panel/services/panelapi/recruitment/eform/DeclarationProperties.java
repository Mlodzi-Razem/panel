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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Wiring of the e-declaration flow. */
@Getter
@Setter
@ConfigurationProperties("panel.api.recruitment.declaration")
public class DeclarationProperties {
    /** The panel-web route of the candidate form; the token is its last segment. */
    public static final String FORM_ROUTE = "/rekrutacja/deklaracja/";

    /** Public address of panel-web (env PANEL_WEB_URL); the candidate link is built on it. */
    private String webUrl = "http://localhost:3000";

    /** Artifact store root; deployments mount a persistent volume here (env PANEL_DECLARATIONS_DIR). */
    private String storageDir = "./data/declarations";

    /** mObywatel accepts up to 5 MB; the e-flow allows twice that. */
    private int maxUploadMb = 10;

    /** The absolute link handed (and e-mailed) to the candidate. */
    public String formUrl(String token) {
        return webUrl.replaceFirst("/+$", "") + FORM_ROUTE + token;
    }

    /** A relative or malformed web address would only surface as a dead link in the candidate's mailbox. */
    @PostConstruct
    void requireAbsoluteWebUrl() {
        if (!isAbsoluteHttpAddress(webUrl)) {
            throw new IllegalStateException(
                ("panel.api.recruitment.declaration.web-url (env PANEL_WEB_URL) must be the absolute http(s) address "
                    + "of panel-web without a query or fragment, got '%s'").formatted(webUrl));
        }
    }

    private static boolean isAbsoluteHttpAddress(String address) {
        try {
            URI uri = new URI(address);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (URISyntaxException | NullPointerException malformed) {
            return false;
        }
    }
}

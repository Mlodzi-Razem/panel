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

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AddressAvailability;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupCheck;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.TemporaryAccessPass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The only class that writes to Microsoft Graph for provisioning (the worker, the guard and the synchronous API call
 * it; nothing else may). It uses the shared app-only token of {@link GraphAppToken}. Rules it enforces itself:
 *
 * <ul>
 *     <li>Every method first requires {@code panel.directory.provisioning.enabled}: with the switch off it throws
 *     {@link ProvisioningErrorCode#PROVISIONING_DISABLED} before any HTTP request, token request included.</li>
 *     <li>Object ids are UUIDs and are checked before they enter a URL; the only absolute URL it follows is an
 *     {@code @odata.nextLink} on the configured Graph host.</li>
 *     <li>Removing a group member is {@code DELETE /groups/{g}/members/{u}/$ref}, asserted in code: without
 *     {@code /$ref} Graph deletes the user.</li>
 *     <li>It never logs, and a failure carries only a {@link ProvisioningErrorCode}: Graph messages quote user
 *     principal names and a Temporary Access Pass response would quote the pass, so no response body, header or
 *     exception cause ever leaves this class.</li>
 * </ul>
 *
 * Permissions are assumed to be roles scoped to administrative units (D1); a 403 is reported as
 * {@link ProvisioningErrorCode#GRAPH_FORBIDDEN} and the worker decides whether it is the unit's replication delay.
 */
@Component
class GraphDirectoryClient {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern ADDRESS = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,255}$");
    private static final String USER_SELECT = "$select=id,userPrincipalName,employeeId,accountEnabled,userType,createdDateTime,assignedLicenses";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!#$%&*+-=?@";
    private static final int MAX_PAGES = 100;

    /**
     * One Graph answer, reduced to what decisions need. Its {@code toString} deliberately omits the body.
     */
    record Reply(int status, @Nullable Map<String, Object> body, @Nullable Duration retryAfter, boolean unreadable) {
        boolean ok() {
            return status >= 200 && status < 300;
        }

        /** The Graph {@code error.code} when it is a short safe token, otherwise null. */
        @Nullable String errorCode() {
            return body != null && body.get("error") instanceof Map<?, ?> error
                && error.get("code") instanceof String code && SAFE_CODE.matcher(code).matches() ? code : null;
        }

        /** Whether the error message or one of its detail codes contains the text; used for matching only. */
        boolean mentions(String text) {
            if (body == null || !(body.get("error") instanceof Map<?, ?> error)) {
                return false;
            }

            if (error.get("message") instanceof String message && message.toLowerCase().contains(text.toLowerCase())) {
                return true;
            }

            return error.get("details") instanceof List<?> details && details.stream().anyMatch(
                d -> d instanceof Map<?, ?> m && m.get("code") instanceof String c && c.toLowerCase().contains(text.toLowerCase()));
        }

        boolean conflict() {
            return mentions("ObjectConflict") || mentions("already exists");
        }

        @Override
        public String toString() {
            return "Reply[" + status + ']';
        }
    }

    /**
     * @param department the district, when known
     * @param employeeId set for members, never for trial accounts
     */
    record NewUser(String userPrincipalName, String displayName, String givenName, String surname,
                   @Nullable String department, @Nullable String employeeId, @Nullable String mail) {
        @Override
        public String toString() {
            return "NewUser[redacted]";
        }
    }

    private final GraphAppToken token;
    private final ProvisioningProperties properties;
    private final Clock clock;
    private final RestClient restClient;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    GraphDirectoryClient(GraphAppToken token, ProvisioningProperties properties, Clock clock) {
        this(token, properties, clock, defaultRestClient());
    }

    GraphDirectoryClient(GraphAppToken token, ProvisioningProperties properties, Clock clock, RestClient restClient) {
        this.token = token;
        this.properties = properties;
        this.clock = clock;
        this.restClient = restClient;
    }

    // ---------------------------------------------------------------------------------------------------- users

    Optional<GraphUser> user(String objectId) {
        Reply reply = call(HttpMethod.GET, "/v1.0/users/{id}?" + USER_SELECT, null, null, uuid(objectId));
        return userOrEmpty(reply);
    }

    Optional<GraphUser> userByUpn(String userPrincipalName) {
        Reply reply = call(HttpMethod.GET, "/v1.0/users/{upn}?" + USER_SELECT, null, null, userPrincipalName);
        return userOrEmpty(reply);
    }

    /**
     * Object ids of the users that carry the given {@code employeeId} (at most two are read; one is enough to know
     * the number is taken).
     */
    List<String> userIdsByEmployeeId(String employeeId) {
        Reply reply = call(HttpMethod.GET, "/v1.0/users?$filter={filter}&$select=id&$top=2&$count=true",
            null, Map.of("ConsistencyLevel", "eventual"), "employeeId eq '" + employeeId.replace("'", "''") + "'");

        if (!reply.ok()) {
            throw failure(reply);
        }

        return ids(reply.body());
    }

    /**
     * Creates the user (in the members administrative unit when one is configured, otherwise at tenant level for the
     * {@code User.Create} fallback) and returns the new object id. The generated password is random, never shown and
     * never kept: sign-in goes through a Temporary Access Pass and a passkey.
     */
    String createUser(NewUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        String unit = properties.membersAdministrativeUnitId();

        if (!unit.isBlank()) {
            body.put("@odata.type", "#microsoft.graph.user");
        }

        body.put("accountEnabled", true);
        body.put("displayName", user.displayName());
        body.put("mailNickname", user.userPrincipalName().substring(0, user.userPrincipalName().indexOf('@')));
        body.put("userPrincipalName", user.userPrincipalName());
        body.put("givenName", user.givenName());
        body.put("surname", user.surname());
        body.put("usageLocation", properties.usageLocation());

        if (user.department() != null) {
            body.put("department", user.department());
        }

        if (user.employeeId() != null) {
            body.put("employeeId", user.employeeId());
        }

        if (properties.setMail() && user.mail() != null) {
            body.put("mail", user.mail());
        }

        body.put("passwordProfile", Map.of("forceChangePasswordNextSignIn", false, "password", randomPassword()));

        Reply reply = unit.isBlank()
            ? call(HttpMethod.POST, "/v1.0/users", body, null)
            : call(HttpMethod.POST, "/v1.0/directory/administrativeUnits/{unit}/members", body, null, uuid(unit));

        if (reply.status() == 400 && reply.conflict()) {
            throw new ProvisioningException(ProvisioningErrorCode.UPN_CONFLICT);
        }

        if (!reply.ok()) {
            throw failure(reply);
        }

        if (reply.body() != null && reply.body().get("id") instanceof String id && PrivateUuid.valid(id)) {
            return id.toLowerCase();
        }

        // created, but the answer is unreadable: the retry finds the account by its user principal name
        throw new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
    }

    void patchUser(String objectId, Map<String, Object> properties) {
        Reply reply = call(HttpMethod.PATCH, "/v1.0/users/{id}", properties, null, uuid(objectId));

        if (reply.status() == 400 && reply.conflict()) {
            throw new ProvisioningException(ProvisioningErrorCode.UPN_CONFLICT);
        }

        if (!reply.ok()) {
            throw failure(reply);
        }
    }

    void assignLicense(String objectId, @Nullable String addSku, Collection<String> removeSkus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addLicenses", addSku == null ? List.of() : List.of(Map.of("skuId", addSku, "disabledPlans", List.of())));
        body.put("removeLicenses", List.copyOf(removeSkus));

        Reply reply = call(HttpMethod.POST, "/v1.0/users/{id}/assignLicense", body, null, uuid(objectId));

        if (reply.status() == 400 && reply.mentions("license")) {
            throw new ProvisioningException(ProvisioningErrorCode.LICENSE_UNAVAILABLE);
        }

        if (!reply.ok()) {
            throw failure(reply);
        }
    }

    /**
     * Ends the sign-in sessions of the user (best effort: the account is already disabled, a refusal is not an error).
     */
    void revokeSignInSessions(String objectId) {
        try {
            call(HttpMethod.POST, "/v1.0/users/{id}/revokeSignInSessions", null, null, uuid(objectId));
        } catch (ProvisioningException ignored) {
            // the account is disabled; revoking sessions only shortens the window
        }
    }

    void deleteUser(String objectId) {
        Reply reply = call(HttpMethod.DELETE, "/v1.0/users/{id}", null, null, uuid(objectId));

        if (!reply.ok() && reply.status() != 404) {
            throw failure(reply);
        }
    }

    // ---------------------------------------------------------------------------------------------------- groups

    /**
     * Adds a user to a group; a member already there counts as done. Returns whether Graph had to add it.
     */
    boolean addGroupMember(String groupId, String userId) {
        String user = uuid(userId);
        Reply reply = call(HttpMethod.POST, "/v1.0/groups/{group}/members/$ref",
            Map.of("@odata.id", token.graphUrl() + "/v1.0/directoryObjects/" + user), null, uuid(groupId));

        if (reply.ok()) {
            return true;
        }

        if (reply.status() == 400 && reply.mentions("already exist")) {
            return false;
        }

        if (reply.status() == 400 && (reply.mentions("don't exist") || reply.mentions("do not exist"))) {
            // a freshly created group is not replicated everywhere yet
            throw new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
        }

        throw failure(reply);
    }

    /**
     * Removes a user from a group with {@code DELETE /groups/{g}/members/{u}/$ref}. Returns whether the user had been a
     * member (a 404 means there was nothing to remove).
     */
    boolean removeGroupMember(String groupId, String userId) {
        String path = removeMemberPath(groupId, userId);
        Reply reply = call(HttpMethod.DELETE, path, null, null);

        if (reply.ok()) {
            return true;
        }

        if (reply.status() == 404) {
            return false;
        }

        throw failure(reply);
    }

    /**
     * The only DELETE shape allowed on a group's members; the {@code /$ref} suffix is asserted because Graph deletes the
     * user object itself when it is missing.
     */
    static String removeMemberPath(String groupId, String userId) {
        String path = "/v1.0/groups/" + uuid(groupId) + "/members/" + uuid(userId) + "/$ref";

        if (!path.endsWith("/$ref")) {
            throw new IllegalStateException("A group member is removed with /$ref");
        }

        return path;
    }

    GroupCheck checkGroup(String groupId) {
        Reply group = call(HttpMethod.GET, "/v1.0/groups/{group}?$select=id,isAssignableToRole,membershipRule", null, null, uuid(groupId));

        if (group.status() == 404) {
            return GroupCheck.NOT_FOUND;
        }

        if (!group.ok() || group.body() == null) {
            throw failure(group);
        }

        if (Boolean.TRUE.equals(group.body().get("isAssignableToRole"))) {
            return GroupCheck.ROLE_ASSIGNABLE;
        }

        if (group.body().get("membershipRule") instanceof String rule && !rule.isBlank()) {
            return GroupCheck.DYNAMIC;
        }

        String unit = properties.groupAdministrativeUnitId();

        if (!unit.isBlank()) {
            Reply member = call(HttpMethod.GET, "/v1.0/directory/administrativeUnits/{unit}/members/{group}?$select=id",
                null, null, uuid(unit), uuid(groupId));

            if (member.status() == 404) {
                return GroupCheck.OUTSIDE_ADMINISTRATIVE_UNIT;
            }

            if (!member.ok()) {
                throw failure(member);
            }
        }

        return GroupCheck.MANAGEABLE;
    }

    /** Object ids of the direct user members, all pages. */
    Set<String> groupUserIds(String groupId) {
        return pagedIds("/v1.0/groups/{group}/members/microsoft.graph.user?$select=id&$top=999", uuid(groupId));
    }

    /** Object ids of the owners, all pages. */
    Set<String> groupOwnerIds(String groupId) {
        return pagedIds("/v1.0/groups/{group}/owners?$select=id&$top=999", uuid(groupId));
    }

    // ---------------------------------------------------------------------------------------------------- TAP

    /**
     * Creates a one-time Temporary Access Pass and returns it in memory only. Any failure, including an unreadable
     * success, is reported as a bare {@link ProvisioningException} without a cause.
     */
    TemporaryAccessPass issueTemporaryAccessPass(String objectId) {
        long minutes = properties.tapLifetime().toMinutes();
        Reply reply = call(HttpMethod.POST, "/v1.0/users/{id}/authentication/temporaryAccessPassMethods",
            Map.of("isUsableOnce", true, "lifetimeInMinutes", minutes), null, uuid(objectId));

        if (!reply.ok()) {
            throw failure(reply);
        }

        if (reply.body() != null && reply.body().get("temporaryAccessPass") instanceof String pass && !pass.isBlank()) {
            long lifetime = reply.body().get("lifetimeInMinutes") instanceof Number n ? n.longValue() : minutes;
            char[] chars = pass.toCharArray();

            try {
                return new TemporaryAccessPass(chars, clock.instant().plus(Duration.ofMinutes(lifetime)), true);
            } finally {
                java.util.Arrays.fill(chars, '\0');
            }
        }

        // a pass may exist that we cannot show: the caller tries again, which replaces it
        throw new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
    }

    // ---------------------------------------------------------------------------------------------------- address

    /**
     * Whether an address is used as {@code mail}, user principal name or proxy address of a user or a group. Never
     * throws: anything unexpected is {@link AddressAvailability#UNKNOWN}. The address goes to Graph only.
     */
    AddressAvailability addressAvailability(String address) {
        String value = address.strip().toLowerCase();

        if (!ADDRESS.matcher(value).matches()) {
            return AddressAvailability.UNKNOWN;
        }

        try {
            String users = "mail eq '" + value + "' or userPrincipalName eq '" + value + "' or proxyAddresses/any(p:p eq 'smtp:" + value + "')";
            String groups = "mail eq '" + value + "' or proxyAddresses/any(p:p eq 'smtp:" + value + "')";

            Reply user = call(HttpMethod.GET, "/v1.0/users?$filter={filter}&$top=1&$select=id&$count=true",
                null, Map.of("ConsistencyLevel", "eventual"), users);
            Reply group = call(HttpMethod.GET, "/v1.0/groups?$filter={filter}&$top=1&$select=id&$count=true",
                null, Map.of("ConsistencyLevel", "eventual"), groups);

            if (!user.ok() || !group.ok()) {
                return AddressAvailability.UNKNOWN;
            }

            return ids(user.body()).isEmpty() && ids(group.body()).isEmpty()
                ? AddressAvailability.FREE
                : AddressAvailability.TAKEN;
        } catch (RuntimeException e) {
            return AddressAvailability.UNKNOWN;
        }
    }

    // ---------------------------------------------------------------------------------------------------- plumbing

    /**
     * Maps a non-successful answer to a failure code. 404 is {@link ProvisioningErrorCode#GRAPH_NOT_FOUND}; callers
     * that treat it as an answer check the status before calling this.
     */
    ProvisioningException failure(Reply reply) {
        int status = reply.status();

        if (status == 429) {
            return new ProvisioningException(ProvisioningErrorCode.GRAPH_THROTTLED, reply.retryAfter());
        }

        if (status == 403) {
            return new ProvisioningException(ProvisioningErrorCode.GRAPH_FORBIDDEN);
        }

        if (status == 404) {
            return new ProvisioningException(ProvisioningErrorCode.GRAPH_NOT_FOUND);
        }

        if (status >= 500 || status == 401 || status == 408 || status == 409) {
            // 409 is Directory_ConcurrencyViolation: the same request succeeds a moment later
            return new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
        }

        return new ProvisioningException(ProvisioningErrorCode.GRAPH_REJECTED);
    }

    private Optional<GraphUser> userOrEmpty(Reply reply) {
        if (reply.status() == 404) {
            return Optional.empty();
        }

        if (!reply.ok() || reply.body() == null) {
            throw failure(reply);
        }

        Map<String, Object> body = reply.body();
        Set<String> skus = new HashSet<>();

        if (body.get("assignedLicenses") instanceof List<?> licenses) {
            for (Object license : licenses) {
                if (license instanceof Map<?, ?> m && m.get("skuId") instanceof String sku) {
                    skus.add(sku.toLowerCase());
                }
            }
        }

        Instant created = null;

        if (body.get("createdDateTime") instanceof String text) {
            try {
                created = Instant.parse(text);
            } catch (RuntimeException ignored) {
                // an unreadable creation time only disables the "created by this task" shortcut
            }
        }

        return Optional.of(new GraphUser(
            string(body.get("id")) == null ? "" : string(body.get("id")).toLowerCase(),
            string(body.get("userPrincipalName")),
            string(body.get("employeeId")),
            body.get("accountEnabled") instanceof Boolean b ? b : null,
            string(body.get("userType")),
            created,
            Set.copyOf(skus)));
    }

    private Set<String> pagedIds(String template, Object... vars) {
        Set<String> ids = new LinkedHashSet<>();
        Reply reply = call(HttpMethod.GET, template, null, null, vars);

        for (int page = 0; ; page++) {
            if (!reply.ok()) {
                throw failure(reply);
            }

            ids.addAll(ids(reply.body()));

            Object next = reply.body() == null ? null : reply.body().get("@odata.nextLink");

            if (!(next instanceof String link) || link.isBlank()) {
                return ids;
            }

            if (page >= MAX_PAGES || !link.startsWith(token.graphUrl() + "/")) {
                // never send the token to another host
                throw new ProvisioningException(ProvisioningErrorCode.GRAPH_REJECTED);
            }

            URI nextPage;

            try {
                nextPage = URI.create(link);
            } catch (IllegalArgumentException e) {
                // D15: the message of URI.create quotes the link, which may carry $skiptoken or $filter
                throw new ProvisioningException(ProvisioningErrorCode.GRAPH_REJECTED);
            }

            reply = send(restClient.method(HttpMethod.GET).uri(nextPage), null, null);
        }
    }

    private static List<String> ids(@Nullable Map<String, Object> body) {
        List<String> ids = new ArrayList<>();

        if (body != null && body.get("value") instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> m && m.get("id") instanceof String id) {
                    ids.add(id.toLowerCase());
                }
            }
        }

        return ids;
    }

    private Reply call(HttpMethod method, String pathTemplate, @Nullable Object body,
                       @Nullable Map<String, String> headers, Object... variables) {
        requireEnabled();

        try {
            return send(restClient.method(method).uri(token.graphUrl() + pathTemplate, variables), body, headers);
        } catch (ProvisioningException e) {
            throw e;
        } catch (RuntimeException e) {
            // no cause: a transport or conversion message can quote what was sent or received
            throw new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
        }
    }

    private Reply send(RestClient.RequestBodySpec request, @Nullable Object body, @Nullable Map<String, String> headers) {
        requireEnabled();

        String bearer;

        try {
            bearer = token.bearer();
        } catch (RuntimeException e) {
            throw new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
        }

        try {
            request.header("Authorization", "Bearer " + bearer).accept(MediaType.APPLICATION_JSON);

            if (headers != null) {
                headers.forEach(request::header);
            }

            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }

            return request.exchange((req, response) -> {
                int status = response.getStatusCode().value();
                Duration retryAfter = retryAfter(response.getHeaders().getFirst("Retry-After"));
                boolean noContent = status == 204 || response.getHeaders().getContentLength() == 0;

                if (noContent) {
                    return new Reply(status, null, retryAfter, false);
                }

                try {
                    return new Reply(status, response.bodyTo(JSON_OBJECT), retryAfter, false);
                } catch (RuntimeException e) {
                    return new Reply(status, null, retryAfter, true);
                }
            }, true);
        } catch (ProvisioningException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ProvisioningException(ProvisioningErrorCode.GRAPH_UNAVAILABLE);
        }
    }

    private void requireEnabled() {
        if (!properties.enabled() || !token.configured()) {
            throw new ProvisioningException(ProvisioningErrorCode.PROVISIONING_DISABLED);
        }
    }

    private static @Nullable Duration retryAfter(@Nullable String header) {
        if (header == null) {
            return null;
        }

        try {
            long seconds = Long.parseLong(header.strip());
            return seconds < 0 ? null : Duration.ofSeconds(Math.min(seconds, Duration.ofHours(1).toSeconds()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable String string(@Nullable Object value) {
        return value instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private static String uuid(String value) {
        if (!PrivateUuid.valid(value)) {
            throw new IllegalArgumentException("An object id must be a UUID");
        }

        return value.toLowerCase();
    }

    private String randomPassword() {
        StringBuilder password = new StringBuilder(40);

        for (int i = 0; i < 40; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }

        return password.toString();
    }

    private static RestClient defaultRestClient() {
        HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder().requestFactory(factory).build();
    }

    /** UUID syntax check shared by the client and its callers. */
    static final class PrivateUuid {
        private PrivateUuid() {
        }

        static boolean valid(@Nullable String value) {
            return value != null && ProvisioningProperties.UUID.matcher(value).matches();
        }
    }
}

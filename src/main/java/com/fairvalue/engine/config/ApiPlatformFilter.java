package com.fairvalue.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.api.dto.ApiEnvelope;
import com.fairvalue.engine.platform.ApiClientRecord;
import com.fairvalue.engine.platform.ApiPlatformAuthenticatedClient;
import com.fairvalue.engine.platform.ApiPlatformService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ApiPlatformFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTR = "fairvalue.api.request_id";
    public static final String REQUEST_TIMESTAMP_ATTR = "fairvalue.api.request_timestamp";
    public static final String REQUEST_CLIENT_ATTR = "fairvalue.api.client";
    public static final String REQUEST_PRESENTED_KEY_ATTR = "fairvalue.api.presented_key";
    public static final String REQUEST_ENTITLEMENT_ATTR = "fairvalue.api.entitlement";

    private final ApiPlatformProperties properties;
    private final ApiPlatformService apiPlatformService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public ApiPlatformFilter(ApiPlatformProperties properties, ApiPlatformService apiPlatformService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.apiPlatformService = apiPlatformService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Instant now = Instant.now();
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        request.setAttribute(REQUEST_TIMESTAMP_ATTR, now);
        response.setHeader("X-Request-Id", requestId);

        ApiClientRecord client = null;
        if (properties.getAuth().isEnabled()) {
            ApiPlatformAuthenticatedClient authenticatedClient = authorize(request, response, requestId, now);
            if (authenticatedClient == null) {
                return;
            }
            client = authenticatedClient.client();
            request.setAttribute(REQUEST_CLIENT_ATTR, client);
            request.setAttribute(REQUEST_PRESENTED_KEY_ATTR, authenticatedClient.presentedKey());
            response.setHeader("X-Api-Plan", client.planCode());
            if (!authorizeEntitlement(request, response, requestId, now, client)) {
                apiPlatformService.recordUsage(client, request.getRequestURI(), request.getMethod(), HttpServletResponse.SC_FORBIDDEN, now);
                return;
            }
            if (!apiPlatformService.withinDailyQuota(client, java.time.LocalDate.ofInstant(now, java.time.ZoneId.systemDefault()))) {
                writeError(response, 429, requestId, now, "quota_exceeded", "Daily quota exceeded.");
                return;
            }
        }

        Integer remaining = null;
        if (!allowThroughRateLimit(request, response, requestId, now, client)) {
            if (client != null) {
                apiPlatformService.recordUsage(client, request.getRequestURI(), request.getMethod(), 429, now);
            }
            return;
        }
        if (client != null && properties.getRateLimit().isEnabled()) {
            remaining = remainingLimit(client, now);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limitFor(client)));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(remaining, 0)));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (client != null) {
                apiPlatformService.recordUsage(client, request.getRequestURI(), request.getMethod(), response.getStatus(), now);
            }
        }
    }

    private ApiPlatformAuthenticatedClient authorize(HttpServletRequest request, HttpServletResponse response, String requestId, Instant now) throws IOException {
        String headerName = properties.getAuth().getHeaderName();
        String presentedKey = request.getHeader(headerName);
        ApiPlatformAuthenticatedClient authenticatedClient = apiPlatformService.authenticate(presentedKey).orElse(null);
        if (authenticatedClient == null) {
            Set<String> validKeys = properties.getAuth().configuredKeys();
            if (presentedKey != null && !presentedKey.isBlank() && validKeys.contains(presentedKey)) {
                ApiClientRecord fallbackClient = new ApiClientRecord(
                        0L,
                        presentedKey,
                        "Legacy Property Client",
                        "legacy_property",
                        true,
                        false,
                        properties.getRateLimit().getRequestsPerMinute(),
                        25_000L,
                        Set.of("market.read", "market.screen", "us.read", "us.valuation.run", "us.admin.read", "us.admin.write", "platform.read", "platform.write").stream().toList(),
                        java.util.Map.of("source", "properties")
                );
                authenticatedClient = new ApiPlatformAuthenticatedClient(fallbackClient, presentedKey);
            }
        }
        if (authenticatedClient == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, requestId, now, "unauthorized", "Missing or invalid API key.");
            return null;
        }
        return authenticatedClient;
    }

    private boolean authorizeEntitlement(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId,
            Instant now,
            ApiClientRecord client
    ) throws IOException {
        String entitlement = apiPlatformService.requiredEntitlement(request.getRequestURI(), request.getMethod());
        request.setAttribute(REQUEST_ENTITLEMENT_ATTR, entitlement);
        if (entitlement == null || apiPlatformService.hasEntitlement(client, entitlement)) {
            return true;
        }
        writeError(response, HttpServletResponse.SC_FORBIDDEN, requestId, now, "forbidden", "Client is not entitled to access this route.");
        return false;
    }

    private boolean allowThroughRateLimit(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId,
            Instant now,
            ApiClientRecord client
    ) throws IOException {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        String identity = client == null ? null : client.clientKey();
        String identityHeader = properties.getRateLimit().getIdentityHeader();
        if (identity == null || identity.isBlank()) {
            identity = request.getHeader(identityHeader);
        }
        if (identity == null || identity.isBlank()) {
            identity = request.getRemoteAddr();
        }
        long epochMinute = now.getEpochSecond() / 60;
        RateWindow window = rateWindows.compute(identity, (key, existing) -> {
            if (existing == null || existing.epochMinute() != epochMinute) {
                return new RateWindow(epochMinute, new AtomicInteger(0));
            }
            return existing;
        });
        int count = window.counter().incrementAndGet();
        if (count > limitFor(client)) {
            writeError(response, 429, requestId, now, "rate_limited", "Rate limit exceeded.");
            return false;
        }
        return true;
    }

    private int remainingLimit(ApiClientRecord client, Instant now) {
        String identity = client == null ? "" : client.clientKey();
        RateWindow window = rateWindows.get(identity);
        if (window == null) {
            return limitFor(client);
        }
        return limitFor(client) - window.counter().get();
    }

    private int limitFor(ApiClientRecord client) {
        if (client != null && client.requestsPerMinute() > 0) {
            return client.requestsPerMinute();
        }
        return properties.getRateLimit().getRequestsPerMinute();
    }

    private void writeError(HttpServletResponse response, int status, String requestId, Instant now, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiEnvelope.error(requestId, now, code, message));
    }

    private record RateWindow(
            long epochMinute,
            AtomicInteger counter
    ) {
    }
}

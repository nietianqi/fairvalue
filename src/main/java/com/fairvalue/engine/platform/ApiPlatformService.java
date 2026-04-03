package com.fairvalue.engine.platform;

import com.fairvalue.engine.repository.ApiClientRepository;
import com.fairvalue.engine.repository.ApiUsageDailyRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiPlatformService {
    private static final ZoneId USAGE_ZONE = ZoneId.systemDefault();
    private static final Map<String, PlanDefinition> PLAN_DEFINITIONS = Map.of(
            "developer_browser", new PlanDefinition(
                    "developer_browser",
                    "Developer Browser",
                    "Local browser development plan with broad read access and limited rate quota.",
                    180,
                    25_000L,
                    List.of("market.read", "market.screen", "us.read", "us.valuation.run", "us.admin.read", "us.admin.write", "platform.read")
            ),
            "internal_service", new PlanDefinition(
                    "internal_service",
                    "Internal Service",
                    "Operational service plan with full platform administration permissions.",
                    600,
                    200_000L,
                    List.of("market.read", "market.screen", "us.read", "us.valuation.run", "us.admin.read", "us.admin.write", "platform.read", "platform.write")
            ),
            "research_starter", new PlanDefinition(
                    "research_starter",
                    "Research Starter",
                    "Starter plan for read-heavy research workflows.",
                    120,
                    10_000L,
                    List.of("market.read", "us.read", "platform.read")
            ),
            "research_pro", new PlanDefinition(
                    "research_pro",
                    "Research Pro",
                    "Expanded research plan with screener and run access.",
                    300,
                    50_000L,
                    List.of("market.read", "market.screen", "us.read", "us.valuation.run", "platform.read")
            )
    );

    private final ApiClientRepository apiClientRepository;
    private final ApiUsageDailyRepository apiUsageDailyRepository;

    public ApiPlatformService(
            ApiClientRepository apiClientRepository,
            ApiUsageDailyRepository apiUsageDailyRepository
    ) {
        this.apiClientRepository = apiClientRepository;
        this.apiUsageDailyRepository = apiUsageDailyRepository;
    }

    public Optional<ApiPlatformAuthenticatedClient> authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        return apiClientRepository.findEnabledByKey(presentedKey.trim())
                .map(client -> new ApiPlatformAuthenticatedClient(client, presentedKey.trim()));
    }

    public Optional<ApiPlatformBootstrapResponse> bootstrap() {
        return apiClientRepository.findPublicClient()
                .map(client -> new ApiPlatformBootstrapResponse(
                        Instant.now(),
                        "X-API-Key",
                        client.clientKey(),
                        "X-Use-Envelope",
                        true,
                        client.clientName(),
                        client.planCode(),
                        client.requestsPerMinute(),
                        client.dailyQuota(),
                        client.entitlements()
                ));
    }

    public ApiPlatformClientResponse describeClient(ApiClientRecord client) {
        return new ApiPlatformClientResponse(
                Instant.now(),
                client.clientName(),
                client.planCode(),
                client.enabled(),
                client.publicClient(),
                client.requestsPerMinute(),
                client.dailyQuota(),
                client.entitlements(),
                client.metadata()
        );
    }

    public ApiUsageSummary usageSummary(ApiClientRecord client, LocalDate usageDate, int routeLimit) {
        long totalRequests = apiUsageDailyRepository.countTotalForDate(client.id(), usageDate);
        return new ApiUsageSummary(
                client.clientName(),
                client.planCode(),
                usageDate,
                totalRequests,
                client.dailyQuota(),
                Math.max(client.dailyQuota() - totalRequests, 0),
                apiUsageDailyRepository.latestSeenAt(client.id(), usageDate),
                apiUsageDailyRepository.findRoutesForDate(client.id(), usageDate, routeLimit)
        );
    }

    public List<ApiManagedClientResponse> clients() {
        return apiClientRepository.findAll().stream()
                .map(this::toManagedClient)
                .toList();
    }

    public ApiManagedClientResponse createClient(ApiCreateClientRequest request) {
        String planCode = request == null || request.planCode() == null || request.planCode().isBlank()
                ? "research_starter"
                : request.planCode().trim().toLowerCase(Locale.ROOT);
        PlanDefinition plan = planFor(planCode);
        String clientKey = generateClientKey(planCode);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("created_at", Instant.now().toString());
        metadata.put("created_via", "platform_control_plane");
        if (request != null && request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        ApiClientRecord created = apiClientRepository.insert(
                clientKey,
                request == null || request.clientName() == null || request.clientName().isBlank()
                        ? plan.displayName() + " Client"
                        : request.clientName().trim(),
                plan.code(),
                request != null && Boolean.TRUE.equals(request.publicClient()),
                request != null && request.requestsPerMinute() != null ? request.requestsPerMinute() : plan.requestsPerMinute(),
                request != null && request.dailyQuota() != null ? request.dailyQuota() : plan.dailyQuota(),
                request != null && request.entitlements() != null && !request.entitlements().isEmpty()
                        ? request.entitlements()
                        : plan.entitlements(),
                metadata
        );
        return toManagedClient(created);
    }

    public ApiManagedClientResponse rotateKey(long clientId) {
        ApiClientRecord rotated = apiClientRepository.rotateKey(clientId, generateClientKey("rotated"));
        return toManagedClient(rotated);
    }

    public ApiManagedClientResponse disable(long clientId) {
        ApiClientRecord disabled = apiClientRepository.disable(clientId);
        return toManagedClient(disabled);
    }

    public List<ApiPlanResponse> plans() {
        return PLAN_DEFINITIONS.values().stream()
                .sorted(java.util.Comparator.comparing(PlanDefinition::code))
                .map(plan -> new ApiPlanResponse(
                        plan.code(),
                        plan.displayName(),
                        plan.description(),
                        plan.requestsPerMinute(),
                        plan.dailyQuota(),
                        plan.entitlements()
                ))
                .toList();
    }

    public boolean hasEntitlement(ApiClientRecord client, String entitlement) {
        if (client == null || entitlement == null || entitlement.isBlank()) {
            return false;
        }
        return client.entitlements().stream()
                .anyMatch(scope -> scope != null && scope.equalsIgnoreCase(entitlement));
    }

    public boolean withinDailyQuota(ApiClientRecord client, LocalDate usageDate) {
        if (client == null || client.id() <= 0 || client.dailyQuota() <= 0) {
            return true;
        }
        return apiUsageDailyRepository.countTotalForDate(client.id(), usageDate) < client.dailyQuota();
    }

    public void recordUsage(ApiClientRecord client, String path, String method, int statusCode, Instant seenAt) {
        if (client == null || client.id() <= 0 || path == null || method == null || seenAt == null) {
            return;
        }
        apiUsageDailyRepository.incrementUsage(
                client.id(),
                LocalDate.ofInstant(seenAt, USAGE_ZONE),
                normalizeRouteKey(path),
                method.toUpperCase(Locale.ROOT),
                statusBucket(statusCode),
                seenAt
        );
    }

    public String requiredEntitlement(String path, String method) {
        String normalizedMethod = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        if (path == null || !path.startsWith("/v1/")) {
            return null;
        }
        if (path.startsWith("/v1/us-equities-admin/")) {
            return "GET".equals(normalizedMethod) ? "us.admin.read" : "us.admin.write";
        }
        if (path.startsWith("/v1/us-equities/")) {
            return path.endsWith("/valuation/run") && "POST".equals(normalizedMethod)
                    ? "us.valuation.run"
                    : "us.read";
        }
        if (path.startsWith("/v1/screener/valuation")) {
            return "market.screen";
        }
        if (path.startsWith("/v1/platform/clients")) {
            return "platform.write";
        }
        if (path.startsWith("/v1/platform/")) {
            return "GET".equals(normalizedMethod) ? "platform.read" : "platform.write";
        }
        return "market.read";
    }

    public String normalizeRouteKey(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }
        return path
                .replaceAll("^/v1/us-equities/[^/]+/valuation/run$", "/v1/us-equities/{ticker}/valuation/run")
                .replaceAll("^/v1/us-equities/[^/]+/valuation/summary$", "/v1/us-equities/{ticker}/valuation/summary")
                .replaceAll("^/v1/us-equities/[^/]+/valuation/report$", "/v1/us-equities/{ticker}/valuation/report")
                .replaceAll("^/v1/us-equities/[^/]+/profile$", "/v1/us-equities/{ticker}/profile")
                .replaceAll("^/v1/us-equities/[^/]+/data-quality$", "/v1/us-equities/{ticker}/data-quality")
                .replaceAll("^/v1/us-equities/[^/]+/financial-quality$", "/v1/us-equities/{ticker}/financial-quality")
                .replaceAll("^/v1/us-equities-admin/[^/]+/overview$", "/v1/us-equities-admin/{ticker}/overview")
                .replaceAll("^/v1/us-equities-admin/[^/]+/source-status$", "/v1/us-equities-admin/{ticker}/source-status")
                .replaceAll("^/v1/us-equities-admin/[^/]+/valuation-jobs$", "/v1/us-equities-admin/{ticker}/valuation-jobs")
                .replaceAll("^/v1/us-equities-admin/[^/]+/valuation-jobs/retry-failed$", "/v1/us-equities-admin/{ticker}/valuation-jobs/retry-failed")
                .replaceAll("^/v1/us-equities-admin/[^/]+/market-sync$", "/v1/us-equities-admin/{ticker}/market-sync")
                .replaceAll("^/v1/us-equities-admin/[^/]+/sec-sync$", "/v1/us-equities-admin/{ticker}/sec-sync")
                .replaceAll("^/v1/us-equities-admin/[^/]+/ir-sync$", "/v1/us-equities-admin/{ticker}/ir-sync")
                .replaceAll("^/v1/us-equities-admin/[^/]+/standardize$", "/v1/us-equities-admin/{ticker}/standardize")
                .replaceAll("^/v1/valuation/history/[^/]+/[^/]+$", "/v1/valuation/history/{market}/{symbol}")
                .replaceAll("^/v1/valuation/[^/]+/[^/]+/explain$", "/v1/valuation/{market}/{symbol}/explain")
                .replaceAll("^/v1/valuation/[^/]+/[^/]+$", "/v1/valuation/{market}/{symbol}")
                .replaceAll("^/v1/discovery/[^/]+$", "/v1/discovery/{market}")
                .replaceAll("^/v1/rankings/[^/]+/[^/]+$", "/v1/rankings/{market}/{rankingType}")
                .replaceAll("^/v1/peers/[^/]+/[^/]+$", "/v1/peers/{market}/{symbol}")
                .replaceAll("^/v1/platform/clients/\\d+/rotate-key$", "/v1/platform/clients/{id}/rotate-key")
                .replaceAll("^/v1/platform/clients/\\d+/disable$", "/v1/platform/clients/{id}/disable");
    }

    private String statusBucket(int statusCode) {
        if (statusCode >= 500) {
            return "5xx";
        }
        if (statusCode >= 400) {
            return "4xx";
        }
        if (statusCode >= 300) {
            return "3xx";
        }
        if (statusCode >= 200) {
            return "2xx";
        }
        return "other";
    }

    private PlanDefinition planFor(String planCode) {
        PlanDefinition plan = PLAN_DEFINITIONS.get(planCode);
        if (plan == null) {
            throw new NoSuchElementException("Unknown plan code: " + planCode);
        }
        return plan;
    }

    private ApiManagedClientResponse toManagedClient(ApiClientRecord client) {
        return new ApiManagedClientResponse(
                client.id(),
                client.clientKey(),
                client.clientName(),
                client.planCode(),
                client.enabled(),
                client.publicClient(),
                client.requestsPerMinute(),
                client.dailyQuota(),
                client.entitlements(),
                client.metadata()
        );
    }

    private String generateClientKey(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "client"
                : prefix.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT);
        return "fv-" + normalizedPrefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private record PlanDefinition(
            String code,
            String displayName,
            String description,
            int requestsPerMinute,
            long dailyQuota,
            List<String> entitlements
    ) {
    }
}

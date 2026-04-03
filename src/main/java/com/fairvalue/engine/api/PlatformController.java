package com.fairvalue.engine.api;

import com.fairvalue.engine.config.ApiPlatformFilter;
import com.fairvalue.engine.config.ApiPlatformProperties;
import com.fairvalue.engine.platform.ApiClientRecord;
import com.fairvalue.engine.platform.ApiPlatformBootstrapResponse;
import com.fairvalue.engine.platform.ApiPlatformClientResponse;
import com.fairvalue.engine.platform.ApiPlatformService;
import com.fairvalue.engine.platform.ApiCreateClientRequest;
import com.fairvalue.engine.platform.ApiManagedClientResponse;
import com.fairvalue.engine.platform.ApiPlanResponse;
import com.fairvalue.engine.platform.ApiUsageSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;

@RestController
public class PlatformController {
    private final ApiPlatformService apiPlatformService;
    private final ApiPlatformProperties apiPlatformProperties;

    public PlatformController(ApiPlatformService apiPlatformService, ApiPlatformProperties apiPlatformProperties) {
        this.apiPlatformService = apiPlatformService;
        this.apiPlatformProperties = apiPlatformProperties;
    }

    @GetMapping("/platform/bootstrap")
    public ApiPlatformBootstrapResponse bootstrap() {
        return apiPlatformService.bootstrap()
                .map(bootstrap -> new ApiPlatformBootstrapResponse(
                        Instant.now(),
                        apiPlatformProperties.getAuth().getHeaderName(),
                        bootstrap.apiKey(),
                        apiPlatformProperties.getEnvelopeHeader(),
                        apiPlatformProperties.isForceEnvelope(),
                        bootstrap.clientName(),
                        bootstrap.planCode(),
                        bootstrap.requestsPerMinute(),
                        bootstrap.dailyQuota(),
                        bootstrap.entitlements()
                ))
                .orElseGet(() -> new ApiPlatformBootstrapResponse(
                        Instant.now(),
                        apiPlatformProperties.getAuth().getHeaderName(),
                        "",
                        apiPlatformProperties.getEnvelopeHeader(),
                        false,
                        "unconfigured_public_client",
                        "bootstrap_unconfigured",
                        0,
                        0,
                        java.util.List.of()
                ));
    }

    @GetMapping("/v1/platform/me")
    public ApiPlatformClientResponse me(HttpServletRequest request) {
        ApiClientRecord client = currentClient(request);
        return apiPlatformService.describeClient(client);
    }

    @GetMapping("/v1/platform/usage")
    public ApiUsageSummary usage(
            HttpServletRequest request,
            @RequestParam(defaultValue = "12") int routes
    ) {
        ApiClientRecord client = currentClient(request);
        return apiPlatformService.usageSummary(
                client,
                LocalDate.now(),
                Math.max(1, Math.min(routes, 50))
        );
    }

    @GetMapping("/v1/platform/clients")
    public java.util.List<ApiManagedClientResponse> clients() {
        return apiPlatformService.clients();
    }

    @PostMapping("/v1/platform/clients")
    public ApiManagedClientResponse createClient(@Valid @RequestBody(required = false) ApiCreateClientRequest request) {
        return apiPlatformService.createClient(request);
    }

    @PostMapping("/v1/platform/clients/{id}/rotate-key")
    public ApiManagedClientResponse rotateKey(@PathVariable long id) {
        return apiPlatformService.rotateKey(id);
    }

    @PostMapping("/v1/platform/clients/{id}/disable")
    public ApiManagedClientResponse disableClient(@PathVariable long id) {
        return apiPlatformService.disable(id);
    }

    @GetMapping("/v1/platform/plans")
    public java.util.List<ApiPlanResponse> plans() {
        return apiPlatformService.plans();
    }

    private ApiClientRecord currentClient(HttpServletRequest request) {
        Object client = request.getAttribute(ApiPlatformFilter.REQUEST_CLIENT_ATTR);
        if (client instanceof ApiClientRecord record) {
            return record;
        }
        throw new NoSuchElementException("API client context is missing.");
    }
}

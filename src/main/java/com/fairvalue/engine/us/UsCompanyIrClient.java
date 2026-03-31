package com.fairvalue.engine.us;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class UsCompanyIrClient {
    private final RestClient restClient;

    public UsCompanyIrClient(RestClient.Builder restClientBuilder, UsLiveMarketDataProperties properties) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", properties.getSecUserAgent())
                .build();
    }

    public Optional<String> fetch(String url) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}

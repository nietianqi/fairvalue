package com.fairvalue.engine.us;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data.us.simfin")
public class UsSimfinProperties {
    private boolean enabled = false;
    private String baseUrl = "https://prod.simfin.com";
    private String apiKey;
    private String market = "us";
    private String companiesDataset = "companies";
    private String derivedDataset = "derived";
    private String derivedVariant = "ttm";
    private int cacheTtlSeconds = 21600;
    private long requestTimeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getCompaniesDataset() {
        return companiesDataset;
    }

    public void setCompaniesDataset(String companiesDataset) {
        this.companiesDataset = companiesDataset;
    }

    public String getDerivedDataset() {
        return derivedDataset;
    }

    public void setDerivedDataset(String derivedDataset) {
        this.derivedDataset = derivedDataset;
    }

    public String getDerivedVariant() {
        return derivedVariant;
    }

    public void setDerivedVariant(String derivedVariant) {
        this.derivedVariant = derivedVariant;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(long requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

package com.fairvalue.engine.us;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data.us.fred")
public class UsFredProperties {
    private boolean enabled = false;
    private String baseUrl = "https://api.stlouisfed.org";
    private String graphBaseUrl = "https://fred.stlouisfed.org";
    private String apiKey;
    private String riskFreeSeriesId = "DGS10";
    private String policyRateSeriesId = "FEDFUNDS";
    private int cacheTtlSeconds = 21600;
    private long requestTimeoutSeconds = 20;

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

    public String getGraphBaseUrl() {
        return graphBaseUrl;
    }

    public void setGraphBaseUrl(String graphBaseUrl) {
        this.graphBaseUrl = graphBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getRiskFreeSeriesId() {
        return riskFreeSeriesId;
    }

    public void setRiskFreeSeriesId(String riskFreeSeriesId) {
        this.riskFreeSeriesId = riskFreeSeriesId;
    }

    public String getPolicyRateSeriesId() {
        return policyRateSeriesId;
    }

    public void setPolicyRateSeriesId(String policyRateSeriesId) {
        this.policyRateSeriesId = policyRateSeriesId;
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
}

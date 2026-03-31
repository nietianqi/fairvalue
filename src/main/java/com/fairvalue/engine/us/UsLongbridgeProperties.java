package com.fairvalue.engine.us;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data.us.longbridge")
public class UsLongbridgeProperties {
    private boolean enabled = false;
    private String appKey;
    private String appSecret;
    private String accessToken;
    private String httpUrl;
    private String quoteWebsocketUrl;
    private int quoteCacheTtlSeconds = 30;
    private int historyCacheTtlSeconds = 900;
    private int historyBars = 252;
    private long requestTimeoutSeconds = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }

    public String getQuoteWebsocketUrl() {
        return quoteWebsocketUrl;
    }

    public void setQuoteWebsocketUrl(String quoteWebsocketUrl) {
        this.quoteWebsocketUrl = quoteWebsocketUrl;
    }

    public int getQuoteCacheTtlSeconds() {
        return quoteCacheTtlSeconds;
    }

    public void setQuoteCacheTtlSeconds(int quoteCacheTtlSeconds) {
        this.quoteCacheTtlSeconds = quoteCacheTtlSeconds;
    }

    public int getHistoryCacheTtlSeconds() {
        return historyCacheTtlSeconds;
    }

    public void setHistoryCacheTtlSeconds(int historyCacheTtlSeconds) {
        this.historyCacheTtlSeconds = historyCacheTtlSeconds;
    }

    public int getHistoryBars() {
        return historyBars;
    }

    public void setHistoryBars(int historyBars) {
        this.historyBars = historyBars;
    }

    public long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(long requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public boolean isConfigured() {
        return enabled
                && appKey != null && !appKey.isBlank()
                && appSecret != null && !appSecret.isBlank()
                && accessToken != null && !accessToken.isBlank();
    }
}

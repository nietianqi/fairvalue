package com.fairvalue.engine.cn;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data.cn.live")
public class CnLiveMarketDataProperties {
    private boolean enabled = true;
    private String baseUrl = "https://push2.eastmoney.com";
    private int quoteCacheTtlSeconds = 20;
    private int universeCacheTtlSeconds = 30;
    private int defaultPageSize = 30;
    private int maxPageSize = 60;

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

    public int getQuoteCacheTtlSeconds() {
        return quoteCacheTtlSeconds;
    }

    public void setQuoteCacheTtlSeconds(int quoteCacheTtlSeconds) {
        this.quoteCacheTtlSeconds = quoteCacheTtlSeconds;
    }

    public int getUniverseCacheTtlSeconds() {
        return universeCacheTtlSeconds;
    }

    public void setUniverseCacheTtlSeconds(int universeCacheTtlSeconds) {
        this.universeCacheTtlSeconds = universeCacheTtlSeconds;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}

package com.fairvalue.engine.us;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data.us.live")
public class UsLiveMarketDataProperties {
    private boolean enabled = true;
    private String secBaseUrl = "https://data.sec.gov";
    private String secTickersUrl = "https://www.sec.gov/files/company_tickers.json";
    private String stooqBaseUrl = "https://stooq.com";
    private String secUserAgent = "FairvalueEngine/0.1 fairvalue@example.com";
    private int tickerUniverseCacheTtlSeconds = 21600;
    private int profileCacheTtlSeconds = 21600;
    private int quoteCacheTtlSeconds = 900;
    private boolean universeSyncOnStartup = false;
    private int universeSyncMaxRetries = 3;
    private long universeSyncRetryBackoffMillis = 1500L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecBaseUrl() {
        return secBaseUrl;
    }

    public void setSecBaseUrl(String secBaseUrl) {
        this.secBaseUrl = secBaseUrl;
    }

    public String getSecTickersUrl() {
        return secTickersUrl;
    }

    public void setSecTickersUrl(String secTickersUrl) {
        this.secTickersUrl = secTickersUrl;
    }

    public String getStooqBaseUrl() {
        return stooqBaseUrl;
    }

    public void setStooqBaseUrl(String stooqBaseUrl) {
        this.stooqBaseUrl = stooqBaseUrl;
    }

    public String getSecUserAgent() {
        return secUserAgent;
    }

    public void setSecUserAgent(String secUserAgent) {
        this.secUserAgent = secUserAgent;
    }

    public int getTickerUniverseCacheTtlSeconds() {
        return tickerUniverseCacheTtlSeconds;
    }

    public void setTickerUniverseCacheTtlSeconds(int tickerUniverseCacheTtlSeconds) {
        this.tickerUniverseCacheTtlSeconds = tickerUniverseCacheTtlSeconds;
    }

    public int getProfileCacheTtlSeconds() {
        return profileCacheTtlSeconds;
    }

    public void setProfileCacheTtlSeconds(int profileCacheTtlSeconds) {
        this.profileCacheTtlSeconds = profileCacheTtlSeconds;
    }

    public int getQuoteCacheTtlSeconds() {
        return quoteCacheTtlSeconds;
    }

    public void setQuoteCacheTtlSeconds(int quoteCacheTtlSeconds) {
        this.quoteCacheTtlSeconds = quoteCacheTtlSeconds;
    }

    public boolean isUniverseSyncOnStartup() {
        return universeSyncOnStartup;
    }

    public void setUniverseSyncOnStartup(boolean universeSyncOnStartup) {
        this.universeSyncOnStartup = universeSyncOnStartup;
    }

    public int getUniverseSyncMaxRetries() {
        return universeSyncMaxRetries;
    }

    public void setUniverseSyncMaxRetries(int universeSyncMaxRetries) {
        this.universeSyncMaxRetries = universeSyncMaxRetries;
    }

    public long getUniverseSyncRetryBackoffMillis() {
        return universeSyncRetryBackoffMillis;
    }

    public void setUniverseSyncRetryBackoffMillis(long universeSyncRetryBackoffMillis) {
        this.universeSyncRetryBackoffMillis = universeSyncRetryBackoffMillis;
    }
}

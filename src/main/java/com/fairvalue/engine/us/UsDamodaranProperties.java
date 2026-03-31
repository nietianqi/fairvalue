package com.fairvalue.engine.us;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data.us.damodaran")
public class UsDamodaranProperties {
    private boolean enabled = true;
    private String erpUrl = "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datafile/histimpl.html";
    private String betaUrl = "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datafile/Betas.html";
    private String peUrl = "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datafile/pedata.html";
    private String evEbitdaUrl = "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datafile/vebitda.html";
    private int cacheTtlSeconds = 43200;
    private long requestTimeoutSeconds = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getErpUrl() {
        return erpUrl;
    }

    public void setErpUrl(String erpUrl) {
        this.erpUrl = erpUrl;
    }

    public String getBetaUrl() {
        return betaUrl;
    }

    public void setBetaUrl(String betaUrl) {
        this.betaUrl = betaUrl;
    }

    public String getPeUrl() {
        return peUrl;
    }

    public void setPeUrl(String peUrl) {
        this.peUrl = peUrl;
    }

    public String getEvEbitdaUrl() {
        return evEbitdaUrl;
    }

    public void setEvEbitdaUrl(String evEbitdaUrl) {
        this.evEbitdaUrl = evEbitdaUrl;
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

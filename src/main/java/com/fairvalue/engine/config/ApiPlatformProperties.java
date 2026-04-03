package com.fairvalue.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "app.api")
public class ApiPlatformProperties {
    private boolean envelopeEnabled = true;
    private boolean forceEnvelope = false;
    private String envelopeHeader = "X-Use-Envelope";
    private final Auth auth = new Auth();
    private final RateLimit rateLimit = new RateLimit();

    public boolean isEnvelopeEnabled() {
        return envelopeEnabled;
    }

    public void setEnvelopeEnabled(boolean envelopeEnabled) {
        this.envelopeEnabled = envelopeEnabled;
    }

    public boolean isForceEnvelope() {
        return forceEnvelope;
    }

    public void setForceEnvelope(boolean forceEnvelope) {
        this.forceEnvelope = forceEnvelope;
    }

    public String getEnvelopeHeader() {
        return envelopeHeader;
    }

    public void setEnvelopeHeader(String envelopeHeader) {
        this.envelopeHeader = envelopeHeader;
    }

    public Auth getAuth() {
        return auth;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public boolean shouldWrap(String requestHeaderValue) {
        if (!envelopeEnabled) {
            return false;
        }
        if (forceEnvelope) {
            return true;
        }
        return requestHeaderValue != null && "true".equalsIgnoreCase(requestHeaderValue.trim());
    }

    public static class Auth {
        private boolean enabled = false;
        private String headerName = "X-API-Key";
        private String validKeys = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getValidKeys() {
            return validKeys;
        }

        public void setValidKeys(String validKeys) {
            this.validKeys = validKeys;
        }

        public Set<String> configuredKeys() {
            if (validKeys == null || validKeys.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(validKeys.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public static class RateLimit {
        private boolean enabled = false;
        private int requestsPerMinute = 120;
        private String identityHeader = "X-API-Key";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = Math.max(1, requestsPerMinute);
        }

        public String getIdentityHeader() {
            return identityHeader;
        }

        public void setIdentityHeader(String identityHeader) {
            this.identityHeader = identityHeader;
        }
    }
}

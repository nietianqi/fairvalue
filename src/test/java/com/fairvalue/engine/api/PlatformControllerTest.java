package com.fairvalue.engine.api;

import com.fairvalue.engine.config.ApiPlatformProperties;
import com.fairvalue.engine.platform.ApiPlatformService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformControllerTest {

    @Test
    void bootstrapShouldReturnSafeDefaultsWhenNoPublicClientIsConfigured() {
        ApiPlatformService service = mock(ApiPlatformService.class);
        when(service.bootstrap()).thenReturn(Optional.empty());

        ApiPlatformProperties properties = new ApiPlatformProperties();
        properties.getAuth().setHeaderName("X-API-Key");
        properties.setEnvelopeHeader("X-Use-Envelope");
        properties.setForceEnvelope(true);

        PlatformController controller = new PlatformController(service, properties);

        var response = controller.bootstrap();

        assertEquals("X-API-Key", response.apiKeyHeader());
        assertEquals("", response.apiKey());
        assertEquals("X-Use-Envelope", response.envelopeHeader());
        assertFalse(response.forceEnvelope());
        assertEquals("bootstrap_unconfigured", response.planCode());
        assertEquals(0, response.requestsPerMinute());
        assertEquals(0, response.dailyQuota());
    }
}

package com.fairvalue.engine.us;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsExternalSourceStatusServiceTest {

    @Test
    void shouldMarkDamodaranFallbackAsWarning() {
        UsFredClient fredClient = mock(UsFredClient.class);
        when(fredClient.fetchRiskFreeRate()).thenReturn(Optional.empty());
        when(fredClient.fetchPolicyRate()).thenReturn(Optional.empty());

        UsLongbridgeClient longbridgeClient = mock(UsLongbridgeClient.class);
        when(longbridgeClient.fetchMarketData("AAPL")).thenReturn(Optional.empty());

        UsDamodaranClient damodaranClient = mock(UsDamodaranClient.class);
        when(damodaranClient.fetchErpSnapshot()).thenReturn(Optional.of(
                new UsDamodaranClient.DamodaranErpSnapshot(
                        LocalDate.of(2026, 4, 3),
                        Double.NaN,
                        0.0472,
                        "damodaran_static_fallback"
                )
        ));

        UsSimfinClient simfinClient = mock(UsSimfinClient.class);
        when(simfinClient.probe()).thenReturn(new UsSimfinClient.SimfinStatus(true, true, true, "ok", "bulk-download authenticated"));
        when(simfinClient.fetchCompanyRecord("AAPL")).thenReturn(Optional.empty());

        UsFredProperties fredProperties = new UsFredProperties();
        fredProperties.setEnabled(true);
        fredProperties.setApiKey("fred");
        UsLongbridgeProperties longbridgeProperties = new UsLongbridgeProperties();
        longbridgeProperties.setEnabled(true);
        longbridgeProperties.setAppKey("lb");
        longbridgeProperties.setAccessToken("token");
        UsDamodaranProperties damodaranProperties = new UsDamodaranProperties();
        damodaranProperties.setEnabled(true);
        UsSimfinProperties simfinProperties = new UsSimfinProperties();
        simfinProperties.setEnabled(true);
        simfinProperties.setApiKey("simfin");

        UsExternalSourceStatusService service = new UsExternalSourceStatusService(
                fredClient,
                fredProperties,
                longbridgeClient,
                longbridgeProperties,
                damodaranClient,
                damodaranProperties,
                simfinClient,
                simfinProperties
        );

        UsExternalSourceStatusService.ExternalSourceStatus status = service.status("AAPL");

        assertEquals("warning", status.damodaran().status());
        assertEquals("erp_snapshot_fallback", status.damodaran().detail());
        assertEquals("damodaran_static_fallback", status.damodaran().erpSource());
    }
}

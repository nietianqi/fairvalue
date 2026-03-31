package com.fairvalue.engine.us;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsExternalValuationParameterServiceTest {

    @Test
    void resolveAppliesFredAndDamodaranOverrides() {
        UsFredClient fredClient = mock(UsFredClient.class);
        UsDamodaranClient damodaranClient = mock(UsDamodaranClient.class);
        UsExternalValuationParameterService service = new UsExternalValuationParameterService(fredClient, damodaranClient);

        when(fredClient.fetchRiskFreeRate()).thenReturn(Optional.of(
                new UsFredClient.FredSeriesObservation("DGS10", LocalDate.of(2026, 3, 28), 0.041, "fred_api")
        ));
        when(damodaranClient.fetchErpSnapshot()).thenReturn(Optional.of(
                new UsDamodaranClient.DamodaranErpSnapshot(LocalDate.of(2026, 3, 1), 0.042, 0.045, "damodaran_histimpl")
        ));
        when(damodaranClient.fetchIndustrySnapshot("us_tech_compounder", "Consumer Electronics", "Technology")).thenReturn(Optional.of(
                new UsDamodaranClient.DamodaranIndustrySnapshot(
                        "Computers/Peripherals",
                        1.12,
                        28.0,
                        18.5,
                        java.util.List.of("damodaran_beta", "damodaran_pe", "damodaran_ev_ebitda")
                )
        ));

        StockSnapshot snapshot = new StockSnapshot(
                Market.US,
                "AAPL",
                "USD",
                "Apple Inc.",
                "Consumer Electronics",
                250.0,
                new StockFundamentals(
                        0.08, 0.24, 0.09, 0.025, 0.30, 24.0, 10.0, 16.0, 0.005, 0.02, -0.01,
                        0.10, 0.20, 0.90, 0.0, 0.02, 0.18, 10.0, 0.95, 0.95, 0.0, 0.85, true
                ),
                "seed"
        );
        UsSecurityMaster security = new UsSecurityMaster(
                1L,
                "AAPL",
                "AAPL.US",
                "Apple Inc.",
                "NASDAQ",
                "USD",
                "Technology",
                "Consumer Electronics",
                null,
                "compounder",
                "us_tech_compounder",
                "US",
                true
        );

        UsExternalValuationParameterService.ExternalParameterSnapshot result = service.resolve(snapshot, security);

        assertEquals(0.041, result.parameters().get("wacc.rf"), 1.0e-9);
        assertEquals(0.045, result.parameters().get("wacc.erp"), 1.0e-9);
        assertEquals(1.12, result.parameters().get("wacc.beta"), 1.0e-9);
        assertEquals(0.0, result.parameters().get("wacc.size_premium"), 1.0e-9);
        assertEquals(0.0914, result.parameters().get("wacc.base"), 1.0e-6);
        assertEquals(0.0287, result.parameters().get("terminal_growth.base"), 1.0e-6);
        assertEquals(28.0, result.parameters().get("relative.target_pe"), 1.0e-9);
        assertEquals(18.5, result.parameters().get("relative.target_ev_ebitda"), 1.0e-9);
        assertEquals("fred_api", result.sources().get("wacc.rf"));
        assertEquals("damodaran_histimpl", result.sources().get("wacc.erp"));
        assertEquals("damodaran_beta:Computers/Peripherals", result.sources().get("wacc.beta"));
        assertEquals("damodaran_pe:Computers/Peripherals", result.sources().get("relative.target_pe"));
        assertTrue(result.appliedSources().contains("fred_api"));
        assertTrue(result.appliedSources().contains("damodaran_histimpl"));
    }
}

package com.fairvalue.engine.us;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "market-data.us.live.enabled=true",
        "market-data.us.live.universe-sync-on-startup=false"
})
class UsUniverseSyncServiceTest {
    @Autowired
    private UsUniverseSyncService universeSyncService;

    @Autowired
    private UsSecurityMasterService securityMasterService;

    @Autowired
    private JdbcClient jdbcClient;

    @MockBean
    private UsSecClient usSecClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("""
                        DELETE FROM fairvalue.security_identifier_map
                        WHERE source_symbol IN ('META', 'META.US', 'AAPL')
                           OR cik IN ('0001326801', '0000320193')
                        """)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.security_master WHERE ticker = 'META'").update();
    }

    @Test
    void shouldSyncSecUniverseIntoSecurityMasterAndIdentifierMaps() {
        when(usSecClient.fetchTickerUniverse(true)).thenReturn(List.of(
                new UsSecClient.SecTickerInfo("AAPL", 320193L, "Apple Inc."),
                new UsSecClient.SecTickerInfo("META", 1326801L, "Meta Platforms, Inc.")
        ));

        UsUniverseSyncService.UniverseSyncSummary summary = universeSyncService.syncSecTickerUniverse();

        assertThat(summary.status()).isEqualTo("completed");
        assertThat(summary.totalFetched()).isEqualTo(2);
        assertThat(summary.inserted()).isEqualTo(1);
        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.failed()).isZero();

        Long securityCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.security_master
                        WHERE ticker IN ('AAPL', 'META')
                        """)
                .query(Long.class)
                .single();
        Long secMapCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.security_identifier_map
                        WHERE source_name = 'sec_edgar'
                          AND source_symbol IN ('AAPL', 'META')
                        """)
                .query(Long.class)
                .single();
        Long longbridgeMapCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.security_identifier_map
                        WHERE source_name = 'longbridge_api'
                          AND source_symbol = 'META.US'
                        """)
                .query(Long.class)
                .single();
        Long ingestionCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.ingestion_jobs
                        WHERE job_type = 'us_sec_ticker_universe_sync'
                          AND status = 'completed'
                        """)
                .query(Long.class)
                .single();

        assertThat(securityCount).isEqualTo(2);
        assertThat(secMapCount).isEqualTo(2);
        assertThat(longbridgeMapCount).isEqualTo(1);
        assertThat(ingestionCount).isGreaterThanOrEqualTo(1);
        assertThat(securityMasterService.resolveSecurityId("META")).isPresent();
        assertThat(securityMasterService.resolveSecurityId("META.US")).isPresent();
        assertThat(securityMasterService.resolveSecurityId("0001326801")).isPresent();
    }

    @Test
    void shouldRemainIdempotentAcrossRepeatedSyncs() {
        List<UsSecClient.SecTickerInfo> universe = List.of(
                new UsSecClient.SecTickerInfo("AAPL", 320193L, "Apple Inc."),
                new UsSecClient.SecTickerInfo("META", 1326801L, "Meta Platforms, Inc.")
        );
        when(usSecClient.fetchTickerUniverse(true)).thenReturn(universe, universe);

        UsUniverseSyncService.UniverseSyncSummary first = universeSyncService.syncSecTickerUniverse();
        UsUniverseSyncService.UniverseSyncSummary second = universeSyncService.syncSecTickerUniverse();

        Long metaCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.security_master
                        WHERE ticker = 'META'
                        """)
                .query(Long.class)
                .single();
        Long secMapCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.security_identifier_map
                        WHERE source_name = 'sec_edgar'
                          AND source_symbol = 'META'
                        """)
                .query(Long.class)
                .single();
        Long longbridgeMapCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.security_identifier_map
                        WHERE source_name = 'longbridge_api'
                          AND source_symbol = 'META.US'
                        """)
                .query(Long.class)
                .single();

        assertThat(first.inserted()).isEqualTo(1);
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(2);
        assertThat(metaCount).isEqualTo(1);
        assertThat(secMapCount).isEqualTo(1);
        assertThat(longbridgeMapCount).isEqualTo(1);
    }
}

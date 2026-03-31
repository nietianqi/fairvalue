package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "market-data.us.live.enabled=true",
        "market-data.us.live.universe-sync-on-startup=false"
})
class UsSecDocumentSyncServiceTest {
    @Autowired
    private UsSecDocumentSyncService usSecDocumentSyncService;

    @Autowired
    private UsSecurityMasterService usSecurityMasterService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsSecClient usSecClient;

    private long aaplSecurityId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.source_document_facts_raw
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                          AND source_id = (
                              SELECT id
                              FROM fairvalue.source_registry
                              WHERE source_name = 'sec_edgar'
                          )
                        """)
                .param("securityId", aaplSecurityId)
                .update();
    }

    @Test
    void shouldPersistSubmissionsAndCompanyFactsIntoPostgres() throws Exception {
        JsonNode submissions = objectMapper.readTree("""
                {
                  "cik": "320193",
                  "filings": {
                    "recent": {
                      "form": ["10-K", "10-Q"],
                      "accessionNumber": ["0000320193-25-000123", "0000320193-26-000010"],
                      "filingDate": ["2025-11-01", "2026-02-01"],
                      "acceptanceDateTime": ["2025-11-01T06:01:36.000Z", "2026-02-01T06:02:00.000Z"],
                      "reportDate": ["2025-09-27", "2025-12-27"],
                      "primaryDocument": ["aapl-20250927.htm", "aapl-20251227.htm"],
                      "primaryDocDescription": ["Annual report", "Quarterly report"]
                    }
                  }
                }
                """);
        JsonNode companyFacts = objectMapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "Revenues": {
                        "units": {
                          "USD": [
                            {
                              "accn": "0000320193-25-000123",
                              "form": "10-K",
                              "fy": 2025,
                              "fp": "FY",
                              "filed": "2025-11-01",
                              "start": "2024-09-29",
                              "end": "2025-09-27",
                              "val": 391035000000,
                              "frame": "CY2025"
                            }
                          ]
                        }
                      },
                      "NetIncomeLoss": {
                        "units": {
                          "USD": [
                            {
                              "accn": "0000320193-26-000999",
                              "form": "8-K",
                              "fy": 2026,
                              "fp": "Q1",
                              "filed": "2026-02-15",
                              "start": "2025-12-28",
                              "end": "2026-02-15",
                              "val": 36500000000,
                              "frame": "CY2026Q1"
                            }
                          ]
                        }
                      }
                    },
                    "dei": {
                      "EntityCommonStockSharesOutstanding": {
                        "units": {
                          "shares": [
                            {
                              "accn": "0000320193-26-000010",
                              "form": "10-Q",
                              "fy": 2026,
                              "fp": "Q1",
                              "filed": "2026-02-01",
                              "end": "2025-12-27",
                              "val": 15000000000
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        when(usSecClient.fetchSubmissions("AAPL")).thenReturn(java.util.Optional.of(submissions));
        when(usSecClient.fetchCompanyFacts("AAPL")).thenReturn(java.util.Optional.of(companyFacts));

        UsSecDocumentSyncService.SecDocumentSyncSummary summary = usSecDocumentSyncService.syncTicker("AAPL");

        assertThat(summary.ticker()).isEqualTo("AAPL");
        assertThat(summary.securityId()).isEqualTo(aaplSecurityId);
        assertThat(summary.submissionDocumentsUpserted()).isEqualTo(2);
        assertThat(summary.rawFactsInserted()).isEqualTo(3);
        assertThat(summary.placeholderDocumentsUpserted()).isEqualTo(1);
        assertThat(summary.skippedFacts()).isZero();
        assertThat(summary.parsedDocuments()).isEqualTo(3);

        Long documentCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        Long parsedCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                          AND parsed_status = 'parsed'
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        Long rawFactCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.source_document_facts_raw
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        String placeholderDocType = jdbcClient.sql("""
                        SELECT document_type
                        FROM fairvalue.source_documents
                        WHERE accession_no = '0000320193-26-000999'
                        """)
                .query(String.class)
                .single();
        String firstConcept = jdbcClient.sql("""
                        SELECT concept_name
                        FROM fairvalue.source_document_facts_raw
                        WHERE security_id = :securityId
                        ORDER BY concept_name
                        LIMIT 1
                        """)
                .param("securityId", aaplSecurityId)
                .query(String.class)
                .single();

        assertThat(documentCount).isEqualTo(3);
        assertThat(parsedCount).isEqualTo(3);
        assertThat(rawFactCount).isEqualTo(3);
        assertThat(placeholderDocType).isEqualTo("8-K");
        assertThat(firstConcept).isEqualTo("EntityCommonStockSharesOutstanding");
    }
}

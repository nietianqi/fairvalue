package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsSourceDocument;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class SourceDocumentRepository {
    private final JdbcClient jdbcClient;

    public SourceDocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long upsert(UsSourceDocument document) {
        return jdbcClient.sql("""
                        INSERT INTO fairvalue.source_documents (
                            security_id,
                            source_id,
                            document_type,
                            document_title,
                            filing_date,
                            accepted_at,
                            period_end_date,
                            accession_no,
                            document_url,
                            local_storage_path,
                            checksum,
                            parsed_status,
                            parser_version,
                            updated_at
                        ) VALUES (
                            :securityId,
                            :sourceId,
                            :documentType,
                            :documentTitle,
                            :filingDate,
                            :acceptedAt,
                            :periodEndDate,
                            :accessionNo,
                            :documentUrl,
                            :localStoragePath,
                            :checksum,
                            :parsedStatus,
                            :parserVersion,
                            NOW()
                        )
                        ON CONFLICT (source_id, accession_no) WHERE accession_no IS NOT NULL
                        DO UPDATE SET
                            security_id = EXCLUDED.security_id,
                            document_type = EXCLUDED.document_type,
                            document_title = COALESCE(EXCLUDED.document_title, fairvalue.source_documents.document_title),
                            filing_date = COALESCE(EXCLUDED.filing_date, fairvalue.source_documents.filing_date),
                            accepted_at = COALESCE(EXCLUDED.accepted_at, fairvalue.source_documents.accepted_at),
                            period_end_date = COALESCE(EXCLUDED.period_end_date, fairvalue.source_documents.period_end_date),
                            document_url = COALESCE(EXCLUDED.document_url, fairvalue.source_documents.document_url),
                            local_storage_path = COALESCE(EXCLUDED.local_storage_path, fairvalue.source_documents.local_storage_path),
                            checksum = COALESCE(EXCLUDED.checksum, fairvalue.source_documents.checksum),
                            parsed_status = EXCLUDED.parsed_status,
                            parser_version = EXCLUDED.parser_version,
                            updated_at = NOW()
                        RETURNING id
                        """)
                .param("securityId", document.securityId())
                .param("sourceId", document.sourceId())
                .param("documentType", document.documentType())
                .param("documentTitle", document.documentTitle())
                .param("filingDate", document.filingDate())
                .param("acceptedAt", document.acceptedAt() == null ? null : java.sql.Timestamp.from(document.acceptedAt()))
                .param("periodEndDate", document.periodEndDate())
                .param("accessionNo", document.accessionNo())
                .param("documentUrl", document.documentUrl())
                .param("localStoragePath", document.localStoragePath())
                .param("checksum", document.checksum())
                .param("parsedStatus", document.parsedStatus())
                .param("parserVersion", document.parserVersion())
                .query(Long.class)
                .single();
    }

    public void updateParsedStatus(List<Long> sourceDocumentIds, String parsedStatus, String parserVersion) {
        for (Long sourceDocumentId : sourceDocumentIds) {
            jdbcClient.sql("""
                            UPDATE fairvalue.source_documents
                            SET parsed_status = :parsedStatus,
                                parser_version = :parserVersion,
                                updated_at = NOW()
                            WHERE id = :sourceDocumentId
                            """)
                    .param("parsedStatus", parsedStatus)
                    .param("parserVersion", parserVersion)
                    .param("sourceDocumentId", sourceDocumentId)
                    .update();
        }
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsSourceDocument> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT id,
                               security_id,
                               source_id,
                               document_type,
                               document_title,
                               filing_date,
                               accepted_at,
                               period_end_date,
                               accession_no,
                               document_url,
                               local_storage_path,
                               checksum,
                               parsed_status,
                               parser_version
                        FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                        ORDER BY filing_date DESC NULLS LAST, created_at DESC
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(this::mapDocument)
                .list();
    }

    private UsSourceDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp acceptedAt = rs.getTimestamp("accepted_at");
        return new UsSourceDocument(
                rs.getLong("id"),
                rs.getLong("security_id"),
                rs.getLong("source_id"),
                rs.getString("document_type"),
                rs.getString("document_title"),
                rs.getObject("filing_date", java.time.LocalDate.class),
                acceptedAt == null ? null : acceptedAt.toInstant(),
                rs.getObject("period_end_date", java.time.LocalDate.class),
                rs.getString("accession_no"),
                rs.getString("document_url"),
                rs.getString("local_storage_path"),
                rs.getString("checksum"),
                rs.getString("parsed_status"),
                rs.getString("parser_version")
        );
    }
}

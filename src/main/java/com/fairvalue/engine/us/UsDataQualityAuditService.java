package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.DataQualityAuditRepository;
import com.fairvalue.engine.repository.FinancialStandardizedRepository;
import com.fairvalue.engine.repository.SourceDocumentFactRawRepository;
import com.fairvalue.engine.repository.SourceDocumentRepository;
import com.fairvalue.engine.repository.SourceRegistryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UsDataQualityAuditService {
    private static final List<String> TEN_K_TYPES = List.of("10-K", "20-F");
    private static final List<String> TEN_Q_TYPES = List.of("10-Q", "6-K");
    private static final List<String> EIGHT_K_TYPES = List.of("8-K", "8-K/A");
    private static final List<String> GUIDANCE_TYPES = List.of("GUIDANCE", "EARNINGS_RELEASE", "INVESTOR_DAY");
    private static final List<String> IR_TYPES = List.of("GUIDANCE", "EARNINGS_RELEASE", "INVESTOR_DAY", "INVESTOR_PRESENTATION", "IR_RELEASE");
    private static final List<String> SHARE_COUNT_CONCEPTS = List.of(
            "EntityCommonStockSharesOutstanding",
            "WeightedAverageNumberOfDilutedSharesOutstanding",
            "WeightedAverageNumberOfShareOutstandingDiluted"
    );
    private static final List<String> SBC_CONCEPTS = List.of(
            "ShareBasedCompensation",
            "AllocatedShareBasedCompensationExpense"
    );
    private static final List<String> CONVERTIBLE_KEYWORDS = List.of("convertible", "conversion");
    private static final List<String> LITIGATION_KEYWORDS = List.of("litigation", "lawsuit", "investigation", "settlement", "subpoena");

    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceDocumentFactRawRepository sourceDocumentFactRawRepository;
    private final FinancialStandardizedRepository financialStandardizedRepository;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final DataQualityAuditRepository dataQualityAuditRepository;
    private final ObjectMapper objectMapper;

    public UsDataQualityAuditService(
            UsSecurityMasterService usSecurityMasterService,
            SourceDocumentRepository sourceDocumentRepository,
            SourceDocumentFactRawRepository sourceDocumentFactRawRepository,
            FinancialStandardizedRepository financialStandardizedRepository,
            SourceRegistryRepository sourceRegistryRepository,
            DataQualityAuditRepository dataQualityAuditRepository,
            ObjectMapper objectMapper
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceDocumentFactRawRepository = sourceDocumentFactRawRepository;
        this.financialStandardizedRepository = financialStandardizedRepository;
        this.sourceRegistryRepository = sourceRegistryRepository;
        this.dataQualityAuditRepository = dataQualityAuditRepository;
        this.objectMapper = objectMapper;
    }

    public UsDataQualityAuditRecord refreshForTicker(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));

        LocalDate auditDate = LocalDate.now();
        LocalDate latest10k = sourceDocumentRepository.findLatestFilingDateBySecurityIdAndDocumentTypes(securityId, TEN_K_TYPES).orElse(null);
        LocalDate latest10q = sourceDocumentRepository.findLatestFilingDateBySecurityIdAndDocumentTypes(securityId, TEN_Q_TYPES).orElse(null);
        boolean hasRecent8k = sourceDocumentRepository.existsRecentBySecurityIdAndDocumentTypes(
                securityId,
                EIGHT_K_TYPES,
                auditDate.minusDays(90)
        );

        UsFinancialStandardizedRecord latestStandardized = financialStandardizedRepository.findLatestBySecurityId(securityId, 5).stream()
                .filter(row -> row != null)
                .findFirst()
                .orElse(null);

        boolean shareCountVerified = (latestStandardized != null
                && ((latestStandardized.dilutedShares() != null && latestStandardized.dilutedShares().compareTo(BigDecimal.ZERO) > 0)
                || (latestStandardized.basicShares() != null && latestStandardized.basicShares().compareTo(BigDecimal.ZERO) > 0)))
                || sourceDocumentFactRawRepository.existsBySecurityIdAndConceptNames(securityId, SHARE_COUNT_CONCEPTS);

        boolean sbcQuantified = (latestStandardized != null && latestStandardized.sbc() != null)
                || sourceDocumentFactRawRepository.existsBySecurityIdAndConceptNames(securityId, SBC_CONCEPTS);
        boolean netDebtUpdated = latestStandardized != null && latestStandardized.netDebt() != null;
        boolean taxRateUpdated = latestStandardized != null && latestStandardized.taxRateEffective() != null;
        boolean convertibleIdentified = sourceDocumentFactRawRepository.existsBySecurityIdAndConceptKeywords(securityId, CONVERTIBLE_KEYWORDS);

        long companyIrSourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.COMPANY_IR).orElse(-1L);
        long secSourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.SEC_EDGAR).orElse(-1L);
        boolean hasRecentGuidance = companyIrSourceId > 0 && sourceDocumentRepository.existsRecentBySecurityIdAndSourceIdAndDocumentTypes(
                securityId,
                companyIrSourceId,
                GUIDANCE_TYPES,
                auditDate.minusDays(180)
        );
        boolean hasRecentIr = companyIrSourceId > 0 && sourceDocumentRepository.existsRecentBySecurityIdAndSourceIdAndDocumentTypes(
                securityId,
                companyIrSourceId,
                IR_TYPES,
                auditDate.minusDays(180)
        );
        boolean litigationCaptured = (companyIrSourceId > 0 && sourceDocumentRepository.existsRecentBySecurityIdAndSourceIdAndTitleKeywords(
                securityId,
                companyIrSourceId,
                LITIGATION_KEYWORDS,
                auditDate.minusYears(2)
        )) || (secSourceId > 0 && sourceDocumentRepository.existsRecentBySecurityIdAndSourceIdAndTitleKeywords(
                securityId,
                secSourceId,
                LITIGATION_KEYWORDS,
                auditDate.minusYears(2)
        ));

        String guidanceStatus = hasRecentGuidance
                ? "company_ir_captured"
                : hasRecentIr
                ? "company_ir_recent"
                : latest10q != null
                ? "filings_only"
                : "guidance_blind";

        List<String> missingItems = new ArrayList<>();
        List<String> warningFlags = new ArrayList<>();

        if (latest10k == null) {
            missingItems.add("latest_10k_missing");
        }
        if (latest10q == null) {
            missingItems.add("latest_10q_missing");
        }
        if (!shareCountVerified) {
            missingItems.add("share_count_not_verified");
        }
        if (!sbcQuantified) {
            missingItems.add("sbc_not_quantified");
        }
        if (!netDebtUpdated) {
            missingItems.add("net_debt_not_updated");
        }
        if (!taxRateUpdated) {
            missingItems.add("tax_rate_not_updated");
        }
        if ("guidance_blind".equals(guidanceStatus)) {
            missingItems.add("guidance_not_captured");
        }

        if (latest10q != null && latest10q.isBefore(auditDate.minusDays(130))) {
            warningFlags.add("quarterly_filing_stale");
        }
        if (latest10k != null && latest10k.isBefore(auditDate.minusDays(420))) {
            warningFlags.add("annual_filing_stale");
        }
        if (hasRecent8k) {
            warningFlags.add("recent_8k_requires_review");
        }
        if (latestStandardized != null && latestStandardized.totalDebt() != null && latestStandardized.cash() != null
                && latestStandardized.totalDebt().compareTo(latestStandardized.cash()) > 0) {
            warningFlags.add("net_debt_positive");
        }
        if (!litigationCaptured) {
            warningFlags.add("litigation_screen_not_captured");
        }

        BigDecimal dataQualityScore = score(
                auditDate,
                latest10k,
                latest10q,
                hasRecent8k,
                shareCountVerified,
                sbcQuantified,
                guidanceStatus,
                netDebtUpdated,
                taxRateUpdated,
                litigationCaptured,
                convertibleIdentified
        );
        BigDecimal confidenceLevel = clamp(
                dataQualityScore
                        .subtract(BigDecimal.valueOf(missingItems.size()).multiply(new BigDecimal("0.03")))
                        .add(hasRecentGuidance ? new BigDecimal("0.03") : BigDecimal.ZERO)
                        .subtract(hasRecent8k ? new BigDecimal("0.01") : BigDecimal.ZERO)
        );

        UsDataQualityAuditRecord record = new UsDataQualityAuditRecord(
                securityId,
                auditDate,
                latest10k,
                latest10q,
                hasRecent8k,
                shareCountVerified,
                sbcQuantified,
                guidanceStatus,
                netDebtUpdated,
                taxRateUpdated,
                litigationCaptured,
                convertibleIdentified,
                dataQualityScore,
                confidenceLevel,
                toJson(missingItems),
                toJson(warningFlags)
        );
        dataQualityAuditRepository.upsert(record);
        return record;
    }

    public Optional<UsDataQualityAuditRecord> findLatestBySecurityId(long securityId) {
        return dataQualityAuditRepository.findLatestBySecurityId(securityId);
    }

    private BigDecimal score(
            LocalDate auditDate,
            LocalDate latest10k,
            LocalDate latest10q,
            boolean hasRecent8k,
            boolean shareCountVerified,
            boolean sbcQuantified,
            String guidanceStatus,
            boolean netDebtUpdated,
            boolean taxRateUpdated,
            boolean litigationCaptured,
            boolean convertibleIdentified
    ) {
        BigDecimal score = new BigDecimal("0.24");
        score = score.add(freshnessScore(auditDate, latest10k, 420, new BigDecimal("0.12")));
        score = score.add(freshnessScore(auditDate, latest10q, 130, new BigDecimal("0.16")));
        score = score.add(hasRecent8k ? new BigDecimal("0.04") : BigDecimal.ZERO);
        score = score.add(shareCountVerified ? new BigDecimal("0.12") : BigDecimal.ZERO);
        score = score.add(sbcQuantified ? new BigDecimal("0.08") : BigDecimal.ZERO);
        score = score.add(netDebtUpdated ? new BigDecimal("0.08") : BigDecimal.ZERO);
        score = score.add(taxRateUpdated ? new BigDecimal("0.06") : BigDecimal.ZERO);
        score = score.add(litigationCaptured ? new BigDecimal("0.03") : BigDecimal.ZERO);
        score = score.add(convertibleIdentified ? new BigDecimal("0.02") : BigDecimal.ZERO);
        score = score.add(switch (guidanceStatus) {
            case "company_ir_captured" -> new BigDecimal("0.10");
            case "company_ir_recent" -> new BigDecimal("0.07");
            case "filings_only" -> new BigDecimal("0.03");
            default -> BigDecimal.ZERO;
        });
        return clamp(score);
    }

    private BigDecimal freshnessScore(LocalDate auditDate, LocalDate filingDate, int staleDays, BigDecimal fullWeight) {
        if (filingDate == null) {
            return BigDecimal.ZERO;
        }
        long ageDays = ChronoUnit.DAYS.between(filingDate, auditDate);
        if (ageDays <= staleDays) {
            return fullWeight;
        }
        if (ageDays >= staleDays * 2L) {
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = BigDecimal.valueOf((staleDays * 2L) - ageDays)
                .divide(BigDecimal.valueOf(staleDays), 6, RoundingMode.HALF_UP);
        return fullWeight.multiply(remaining);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return new BigDecimal("0.20");
        }
        return value.max(new BigDecimal("0.20")).min(new BigDecimal("0.95")).setScale(4, RoundingMode.HALF_UP);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize data quality audit payload.", ex);
        }
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(java.util.Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }
}

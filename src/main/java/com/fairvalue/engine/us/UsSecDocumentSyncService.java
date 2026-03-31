package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.SourceDocumentFactRawRepository;
import com.fairvalue.engine.repository.SourceDocumentRepository;
import com.fairvalue.engine.repository.SourceRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UsSecDocumentSyncService {
    private static final String PARSER_VERSION = "us-sec-sync-v1";

    private final UsSecClient usSecClient;
    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceDocumentFactRawRepository sourceDocumentFactRawRepository;
    private final ObjectMapper objectMapper;

    public UsSecDocumentSyncService(
            UsSecClient usSecClient,
            UsSecurityMasterService usSecurityMasterService,
            SourceRegistryRepository sourceRegistryRepository,
            SourceDocumentRepository sourceDocumentRepository,
            SourceDocumentFactRawRepository sourceDocumentFactRawRepository,
            ObjectMapper objectMapper
    ) {
        this.usSecClient = usSecClient;
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceRegistryRepository = sourceRegistryRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceDocumentFactRawRepository = sourceDocumentFactRawRepository;
        this.objectMapper = objectMapper;
    }

    public SecDocumentSyncSummary syncTicker(String rawTicker) {
        String normalizedTicker = normalizeTicker(rawTicker);
        long securityId = resolveOrCreateSecurityId(normalizedTicker);
        long sourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.SEC_EDGAR)
                .orElseThrow(() -> new IllegalStateException("Source registry missing sec_edgar entry."));

        JsonNode submissions = usSecClient.fetchSubmissions(normalizedTicker)
                .orElseThrow(() -> new IllegalStateException("Failed to fetch SEC submissions for " + normalizedTicker));
        JsonNode companyFacts = usSecClient.fetchCompanyFacts(normalizedTicker)
                .orElseThrow(() -> new IllegalStateException("Failed to fetch SEC companyfacts for " + normalizedTicker));

        ParsedSecPayload payload = parsePayload(normalizedTicker, securityId, sourceId, submissions, companyFacts);
        return persistPayload(normalizedTicker, securityId, sourceId, payload);
    }

    private long resolveOrCreateSecurityId(String normalizedTicker) {
        Optional<Long> existing = usSecurityMasterService.resolveSecurityId(normalizedTicker);
        if (existing.isPresent()) {
            return existing.get();
        }
        UsSecClient.SecTickerInfo tickerInfo = usSecClient.fetchTickerInfo(normalizedTicker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + normalizedTicker + " was not found in SEC universe."));
        return usSecurityMasterService.upsertFromSecTicker(tickerInfo).securityId();
    }

    private ParsedSecPayload parsePayload(
            String ticker,
            long securityId,
            long sourceId,
            JsonNode submissions,
            JsonNode companyFacts
    ) {
        String cik = normalizeCik(textOrNull(submissions.path("cik")));
        List<UsSourceDocument> documents = parseSubmissionDocuments(securityId, sourceId, cik, submissions);
        List<PendingRawFact> pendingFacts = parseCompanyFacts(securityId, sourceId, companyFacts);
        return new ParsedSecPayload(ticker, cik, securityId, sourceId, documents, pendingFacts);
    }

    @Transactional
    protected SecDocumentSyncSummary persistPayload(
            String ticker,
            long securityId,
            long sourceId,
            ParsedSecPayload payload
    ) {
        Map<String, Long> documentIdsByAccession = new LinkedHashMap<>();
        int submissionDocumentsUpserted = 0;
        for (UsSourceDocument document : payload.documents()) {
            long sourceDocumentId = sourceDocumentRepository.upsert(document);
            submissionDocumentsUpserted += 1;
            documentIdsByAccession.put(document.accessionNo(), sourceDocumentId);
        }

        List<UsSourceDocumentRawFact> factsToPersist = new ArrayList<>();
        Set<Long> parsedDocumentIds = new LinkedHashSet<>();
        int placeholderDocumentsUpserted = 0;
        int skippedFacts = 0;

        for (PendingRawFact pendingFact : payload.pendingFacts()) {
            if (pendingFact.accessionNo() == null || pendingFact.accessionNo().isBlank()) {
                skippedFacts += 1;
                continue;
            }

            Long sourceDocumentId = documentIdsByAccession.get(pendingFact.accessionNo());
            if (sourceDocumentId == null) {
                UsSourceDocument placeholder = new UsSourceDocument(
                        null,
                        securityId,
                        sourceId,
                        pendingFact.documentType(),
                        pendingFact.documentTitle(),
                        pendingFact.filingDate(),
                        null,
                        pendingFact.periodEnd(),
                        pendingFact.accessionNo(),
                        null,
                        null,
                        null,
                        "pending",
                        PARSER_VERSION
                );
                sourceDocumentId = sourceDocumentRepository.upsert(placeholder);
                documentIdsByAccession.put(pendingFact.accessionNo(), sourceDocumentId);
                placeholderDocumentsUpserted += 1;
            }

            factsToPersist.add(new UsSourceDocumentRawFact(
                    sourceDocumentId,
                    securityId,
                    pendingFact.taxonomy(),
                    pendingFact.conceptName(),
                    pendingFact.unit(),
                    pendingFact.periodStart(),
                    pendingFact.periodEnd(),
                    pendingFact.fiscalYear(),
                    pendingFact.fiscalPeriod(),
                    pendingFact.valueNumeric(),
                    pendingFact.valueText(),
                    pendingFact.decimals(),
                    pendingFact.contextRef(),
                    pendingFact.segmentName(),
                    pendingFact.customTag(),
                    pendingFact.rawJson()
            ));
            parsedDocumentIds.add(sourceDocumentId);
        }

        sourceDocumentFactRawRepository.replaceBySecurityId(securityId, factsToPersist);
        sourceDocumentRepository.updateParsedStatus(new ArrayList<>(parsedDocumentIds), "parsed", PARSER_VERSION);

        return new SecDocumentSyncSummary(
                ticker,
                securityId,
                submissionDocumentsUpserted,
                factsToPersist.size(),
                placeholderDocumentsUpserted,
                skippedFacts,
                parsedDocumentIds.size(),
                "completed",
                PARSER_VERSION
        );
    }

    private List<UsSourceDocument> parseSubmissionDocuments(
            long securityId,
            long sourceId,
            String cik,
            JsonNode submissions
    ) {
        JsonNode recent = submissions.path("filings").path("recent");
        int size = recent.path("form").size();
        List<UsSourceDocument> documents = new ArrayList<>();
        for (int index = 0; index < size; index += 1) {
            String form = textOrNull(recent.path("form").path(index));
            String accessionNo = textOrNull(recent.path("accessionNumber").path(index));
            if (form == null || accessionNo == null) {
                continue;
            }

            String primaryDocDescription = textOrNull(recent.path("primaryDocDescription").path(index));
            String primaryDocument = textOrNull(recent.path("primaryDocument").path(index));
            LocalDate filingDate = parseDate(textOrNull(recent.path("filingDate").path(index)));
            Instant acceptedAt = parseInstant(textOrNull(recent.path("acceptanceDateTime").path(index)));
            LocalDate reportDate = parseDate(textOrNull(recent.path("reportDate").path(index)));
            documents.add(new UsSourceDocument(
                    null,
                    securityId,
                    sourceId,
                    form,
                    primaryDocDescription == null ? form + " filing" : primaryDocDescription,
                    filingDate,
                    acceptedAt,
                    reportDate,
                    accessionNo,
                    buildDocumentUrl(cik, accessionNo, primaryDocument),
                    null,
                    null,
                    "pending",
                    PARSER_VERSION
            ));
        }
        return documents;
    }

    private List<PendingRawFact> parseCompanyFacts(long securityId, long sourceId, JsonNode companyFacts) {
        List<PendingRawFact> facts = new ArrayList<>();
        JsonNode rootFacts = companyFacts.path("facts");
        Iterator<Map.Entry<String, JsonNode>> taxonomyIterator = rootFacts.fields();
        while (taxonomyIterator.hasNext()) {
            Map.Entry<String, JsonNode> taxonomyEntry = taxonomyIterator.next();
            String taxonomy = taxonomyEntry.getKey();
            Iterator<Map.Entry<String, JsonNode>> conceptIterator = taxonomyEntry.getValue().fields();
            while (conceptIterator.hasNext()) {
                Map.Entry<String, JsonNode> conceptEntry = conceptIterator.next();
                String conceptName = conceptEntry.getKey();
                JsonNode unitsNode = conceptEntry.getValue().path("units");
                Iterator<Map.Entry<String, JsonNode>> unitIterator = unitsNode.fields();
                while (unitIterator.hasNext()) {
                    Map.Entry<String, JsonNode> unitEntry = unitIterator.next();
                    String unit = unitEntry.getKey();
                    JsonNode items = unitEntry.getValue();
                    if (!items.isArray()) {
                        continue;
                    }
                    for (JsonNode item : items) {
                        facts.add(new PendingRawFact(
                                textOrNull(item.path("accn")),
                                securityId,
                                sourceId,
                                taxonomy,
                                conceptName,
                                unit,
                                parseDate(textOrNull(item.path("start"))),
                                parseDate(textOrNull(item.path("end"))),
                                item.hasNonNull("fy") ? item.path("fy").asInt() : null,
                                textOrNull(item.path("fp")),
                                item.path("val").isNumber() ? item.path("val").decimalValue() : null,
                                item.path("val").isTextual() ? item.path("val").asText() : null,
                                parseInteger(textOrNull(item.path("decimals"))),
                                textOrNull(item.path("frame")),
                                null,
                                !("us-gaap".equals(taxonomy) || "dei".equals(taxonomy)),
                                textOrNull(item.path("form")) == null ? "XBRL_FACT" : textOrNull(item.path("form")),
                                buildPlaceholderTitle(textOrNull(item.path("form")), conceptName),
                                parseDate(textOrNull(item.path("filed"))),
                                toJson(item)
                        ));
                    }
                }
            }
        }
        return facts;
    }

    private String buildDocumentUrl(String cik, String accessionNo, String primaryDocument) {
        if (cik == null || accessionNo == null || primaryDocument == null) {
            return null;
        }
        String cikWithoutLeadingZeros = String.valueOf(Long.parseLong(cik));
        return "https://www.sec.gov/Archives/edgar/data/"
                + cikWithoutLeadingZeros
                + "/"
                + accessionNo.replace("-", "")
                + "/"
                + primaryDocument;
    }

    private String buildPlaceholderTitle(String form, String conceptName) {
        String resolvedForm = form == null || form.isBlank() ? "companyfacts" : form;
        return resolvedForm + " placeholder from companyfacts (" + conceptName + ")";
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize companyfacts node.", ex);
        }
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    private String normalizeCik(String rawCik) {
        if (rawCik == null || rawCik.isBlank()) {
            return null;
        }
        String digits = rawCik.replaceAll("\\D", "");
        return String.format("%010d", Long.parseLong(digits));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private LocalDate parseDate(String rawDate) {
        return rawDate == null || rawDate.isBlank() ? null : LocalDate.parse(rawDate);
    }

    private Instant parseInstant(String rawInstant) {
        return rawInstant == null || rawInstant.isBlank() ? null : OffsetDateTime.parse(rawInstant).toInstant();
    }

    private Integer parseInteger(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedSecPayload(
            String ticker,
            String cik,
            long securityId,
            long sourceId,
            List<UsSourceDocument> documents,
            List<PendingRawFact> pendingFacts
    ) {
    }

    private record PendingRawFact(
            String accessionNo,
            long securityId,
            long sourceId,
            String taxonomy,
            String conceptName,
            String unit,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer fiscalYear,
            String fiscalPeriod,
            BigDecimal valueNumeric,
            String valueText,
            Integer decimals,
            String contextRef,
            String segmentName,
            boolean customTag,
            String documentType,
            String documentTitle,
            LocalDate filingDate,
            String rawJson
    ) {
    }

    public record SecDocumentSyncSummary(
            String ticker,
            long securityId,
            int submissionDocumentsUpserted,
            int rawFactsInserted,
            int placeholderDocumentsUpserted,
            int skippedFacts,
            int parsedDocuments,
            String status,
            String parserVersion
    ) {
    }
}

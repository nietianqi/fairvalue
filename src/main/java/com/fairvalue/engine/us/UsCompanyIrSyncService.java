package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.SourceDocumentRepository;
import com.fairvalue.engine.repository.SourceRegistryRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UsCompanyIrSyncService {
    private static final String PARSER_VERSION = "us-company-ir-sync-v1";

    private final UsCompanyIrClient usCompanyIrClient;
    private final UsCompanyIrSourceRegistry usCompanyIrSourceRegistry;
    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SourceDocumentRepository sourceDocumentRepository;

    public UsCompanyIrSyncService(
            UsCompanyIrClient usCompanyIrClient,
            UsCompanyIrSourceRegistry usCompanyIrSourceRegistry,
            UsSecurityMasterService usSecurityMasterService,
            SourceRegistryRepository sourceRegistryRepository,
            SourceDocumentRepository sourceDocumentRepository
    ) {
        this.usCompanyIrClient = usCompanyIrClient;
        this.usCompanyIrSourceRegistry = usCompanyIrSourceRegistry;
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceRegistryRepository = sourceRegistryRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
    }

    public CompanyIrSyncSummary syncTicker(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        long sourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.COMPANY_IR)
                .orElseThrow(() -> new IllegalStateException("Source registry missing company_ir entry."));

        UsCompanyIrSourceRegistry.TickerIrConfig config = usCompanyIrSourceRegistry.findByTicker(ticker)
                .orElseThrow(() -> new IllegalStateException("No company IR source config found for ticker " + ticker + "."));

        int sourcesVisited = 0;
        int sourceFetchFailures = 0;
        Set<String> uniqueUrls = new LinkedHashSet<>();
        List<UsSourceDocument> documents = new ArrayList<>();

        for (UsCompanyIrSourceRegistry.IrSourceConfig source : config.safeSources()) {
            sourcesVisited += 1;
            Optional<String> payload = usCompanyIrClient.fetch(source.url());
            if (payload.isEmpty()) {
                sourceFetchFailures += 1;
                continue;
            }
            List<UsSourceDocument> parsed = switch (normalizeKind(source.kind())) {
                case "ATOM_FEED", "RSS_FEED", "FEED" -> parseFeed(ticker, securityId, sourceId, source, payload.get());
                default -> List.of();
            };
            for (UsSourceDocument document : parsed) {
                if (document.documentUrl() != null && uniqueUrls.add(document.documentUrl())) {
                    documents.add(document);
                }
            }
        }

        int upserted = 0;
        Map<String, Integer> documentTypeCounts = new LinkedHashMap<>();
        for (UsSourceDocument document : documents.stream()
                .sorted(Comparator.comparing(UsSourceDocument::filingDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(UsSourceDocument::documentTitle, Comparator.nullsLast(String::compareTo)))
                .toList()) {
            sourceDocumentRepository.upsert(document);
            upserted += 1;
            documentTypeCounts.merge(document.documentType(), 1, Integer::sum);
        }

        return new CompanyIrSyncSummary(
                ticker,
                securityId,
                sourcesVisited,
                upserted,
                sourceFetchFailures,
                documentTypeCounts,
                "completed",
                PARSER_VERSION
        );
    }

    private List<UsSourceDocument> parseFeed(
            String ticker,
            long securityId,
            long sourceId,
            UsCompanyIrSourceRegistry.IrSourceConfig source,
            String payload
    ) {
        Document xml = Jsoup.parse(payload, source.url(), org.jsoup.parser.Parser.xmlParser());
        List<Element> entries = new ArrayList<>(xml.select("entry"));
        if (entries.isEmpty()) {
            entries.addAll(xml.select("item"));
        }

        List<UsSourceDocument> documents = new ArrayList<>();
        for (Element entry : entries.stream().limit(source.safeMaxItems()).toList()) {
            String title = normalizedText(firstText(entry, "title"));
            String url = extractLink(entry);
            if (title == null || url == null) {
                continue;
            }

            String category = normalizedText(extractCategory(entry));
            Instant publishedAt = extractPublishedAt(entry);
            LocalDate filingDate = publishedAt == null ? null : publishedAt.atOffset(java.time.ZoneOffset.UTC).toLocalDate();
            String documentType = inferDocumentType(title, category, source.defaultDocumentType());
            String localStoragePath = buildStoragePath(ticker, filingDate, title, url);

            documents.add(new UsSourceDocument(
                    null,
                    securityId,
                    sourceId,
                    documentType,
                    title,
                    filingDate,
                    publishedAt,
                    null,
                    null,
                    url,
                    localStoragePath,
                    null,
                    "metadata_synced",
                    PARSER_VERSION
            ));
        }
        return documents;
    }

    private String extractLink(Element entry) {
        Element atomLink = entry.selectFirst("link[href]");
        if (atomLink != null) {
            return normalizedText(atomLink.attr("abs:href"));
        }
        Element rssLink = entry.selectFirst("link");
        if (rssLink != null) {
            return normalizedText(rssLink.text());
        }
        return null;
    }

    private String extractCategory(Element entry) {
        Element atomCategory = entry.selectFirst("category[term]");
        if (atomCategory != null) {
            return atomCategory.attr("term");
        }
        Element rssCategory = entry.selectFirst("category");
        return rssCategory == null ? null : rssCategory.text();
    }

    private Instant extractPublishedAt(Element entry) {
        List<String> candidates = new ArrayList<>();
        candidates.add(firstText(entry, "updated"));
        candidates.add(firstText(entry, "published"));
        candidates.add(firstText(entry, "pubDate"));
        candidates.add(firstText(entry, "dc|date"));
        for (String candidate : candidates) {
            Instant parsed = parseInstant(candidate);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private String firstText(Element element, String selector) {
        Element selected = element.selectFirst(selector);
        return selected == null ? null : selected.text();
    }

    private Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String inferDocumentType(String title, String category, String defaultDocumentType) {
        String haystack = ((title == null ? "" : title) + " " + (category == null ? "" : category)).toLowerCase(Locale.ROOT);
        if (haystack.contains("guidance") || haystack.contains("outlook")) {
            return "GUIDANCE";
        }
        if (haystack.contains("earnings") || haystack.contains("results")) {
            return "EARNINGS_RELEASE";
        }
        if (haystack.contains("investor day") || haystack.contains("capital markets day")) {
            return "INVESTOR_DAY";
        }
        if (haystack.contains("presentation") || haystack.contains("slides") || haystack.contains("deck")) {
            return "INVESTOR_PRESENTATION";
        }
        return defaultDocumentType == null || defaultDocumentType.isBlank() ? "IR_RELEASE" : defaultDocumentType;
    }

    private String buildStoragePath(String ticker, LocalDate filingDate, String title, String url) {
        String year = filingDate == null ? "unknown" : String.valueOf(filingDate.getYear());
        String date = filingDate == null ? "undated" : filingDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String slug = slugify(title);
        String extension = extension(url);
        return "company-ir/" + ticker + "/" + year + "/" + date + "-" + slug + extension;
    }

    private String extension(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank() || !path.contains(".")) {
                return ".html";
            }
            String ext = path.substring(path.lastIndexOf('.'));
            return ext.length() > 8 ? ".html" : ext;
        } catch (URISyntaxException ex) {
            return ".html";
        }
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "document";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "document" : normalized.substring(0, Math.min(normalized.length(), 96));
    }

    private String normalizeKind(String rawKind) {
        return rawKind == null ? "ATOM_FEED" : rawKind.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizedText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.replace('\u00A0', ' ').trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    public record CompanyIrSyncSummary(
            String ticker,
            long securityId,
            int sourcesVisited,
            int documentsUpserted,
            int sourceFetchFailures,
            Map<String, Integer> documentTypeCounts,
            String status,
            String parserVersion
    ) {
    }
}

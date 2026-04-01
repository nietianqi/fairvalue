package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.SecurityMasterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class UsSecurityMasterService {
    private final SecurityMasterRepository securityMasterRepository;
    private final UsSecurityIdentifierService securityIdentifierService;

    public UsSecurityMasterService(
            SecurityMasterRepository securityMasterRepository,
            UsSecurityIdentifierService securityIdentifierService
    ) {
        this.securityMasterRepository = securityMasterRepository;
        this.securityIdentifierService = securityIdentifierService;
    }

    public Optional<Long> resolveSecurityId(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawInput.trim();
        if (isDigitsOnly(trimmed)) {
            return securityIdentifierService.findSecurityIdByCik(normalizeCik(trimmed));
        }

        if (trimmed.toUpperCase(Locale.ROOT).endsWith(".US")) {
            return securityIdentifierService.findSecurityIdBySourceSymbol(
                    UsSecurityIdentifierService.LONGBRIDGE_API,
                    normalizeSymbolFull(trimmed)
            );
        }

        String normalizedTicker = normalizeTicker(trimmed);
        return securityMasterRepository.findByTicker(normalizedTicker)
                .map(UsSecurityMaster::id)
                .or(() -> securityIdentifierService.findSecurityIdBySourceSymbol(
                        UsSecurityIdentifierService.SEC_EDGAR,
                        normalizedTicker
                ))
                .or(() -> securityIdentifierService.findSecurityIdBySourceSymbol(
                        UsSecurityIdentifierService.LONGBRIDGE_API,
                        toSymbolFull(normalizedTicker)
                ));
    }

    public Optional<UsSecurityMaster> findByTicker(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            return Optional.empty();
        }
        return securityMasterRepository.findByTicker(normalizeTicker(rawTicker));
    }

    public Optional<UsSecurityMaster> findById(long securityId) {
        return securityMasterRepository.findById(securityId);
    }

    public List<UsRelativePeerComparable> findRelativePeers(long securityId, UsSecurityMaster security, int limit) {
        if (security == null) {
            return List.of();
        }
        String companyType = security.companyType();
        boolean qualityFilter = "compounder".equals(companyType) || "hypergrowth_saas".equals(companyType);
        double minFcfMargin = qualityFilter ? 0.03 : -1.0;
        double minRoic     = qualityFilter ? 0.05 : -1.0;
        return securityMasterRepository.findRelativePeers(
                securityId,
                security.sector(),
                security.industry(),
                companyType,
                security.sectorTemplate(),
                limit,
                minFcfMargin,
                minRoic
        );
    }

    public UpsertOutcome upsertFromSecTicker(UsSecClient.SecTickerInfo tickerInfo) {
        String ticker = normalizeTicker(tickerInfo.ticker());
        String cik = normalizeCik(String.valueOf(tickerInfo.cik()));
        String symbolFull = toSymbolFull(ticker);

        Optional<Long> byCik = securityIdentifierService.findSecurityIdByCik(cik);
        Optional<Long> byTicker = securityMasterRepository.findByTicker(ticker).map(UsSecurityMaster::id);
        Optional<Long> byLongbridge = securityIdentifierService.findSecurityIdBySourceSymbol(
                UsSecurityIdentifierService.LONGBRIDGE_API,
                symbolFull
        );

        Set<Long> distinctIds = java.util.stream.Stream.of(
                        byCik.orElse(null),
                        byTicker.orElse(null),
                        byLongbridge.orElse(null)
                )
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        if (distinctIds.size() > 1) {
            throw new IllegalStateException("SEC identifier conflict detected for ticker " + ticker + " and cik " + cik);
        }

        long securityId;
        boolean created;
        UsSecurityMaster candidate = new UsSecurityMaster(
                null,
                ticker,
                symbolFull,
                cleanCompanyName(tickerInfo.title(), ticker),
                "US",
                "USD",
                null,
                null,
                null,
                null,
                null,
                "US",
                true
        );

        Optional<Long> existingId = byCik.or(() -> byTicker).or(() -> byLongbridge);
        if (existingId.isPresent()) {
            securityId = existingId.get();
            securityMasterRepository.update(securityId, candidate);
            created = false;
        } else {
            securityId = securityMasterRepository.insert(candidate);
            created = true;
        }

        securityIdentifierService.saveSecEdgarMapping(securityId, ticker, cik);
        securityIdentifierService.saveLongbridgeMapping(securityId, symbolFull, "US");
        return new UpsertOutcome(securityId, created);
    }

    private String normalizeTicker(String rawTicker) {
        String trimmed = rawTicker.trim().toUpperCase(Locale.ROOT);
        if (trimmed.endsWith(".US")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.replace(".", "-");
    }

    private String normalizeSymbolFull(String rawSymbol) {
        return toSymbolFull(normalizeTicker(rawSymbol));
    }

    private String toSymbolFull(String ticker) {
        return ticker + ".US";
    }

    private String normalizeCik(String rawCik) {
        String digits = rawCik.replaceAll("\\D", "");
        return String.format("%010d", Long.parseLong(digits));
    }

    private boolean isDigitsOnly(String rawInput) {
        return rawInput.chars().allMatch(Character::isDigit);
    }

    private String cleanCompanyName(String title, String fallbackTicker) {
        if (title == null || title.isBlank()) {
            return fallbackTicker;
        }
        return title.trim();
    }

    public record UpsertOutcome(long securityId, boolean created) {
    }
}

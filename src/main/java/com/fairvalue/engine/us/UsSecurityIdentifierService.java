package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.SecurityIdentifierMapRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsSecurityIdentifierService {
    public static final String SEC_EDGAR = "sec_edgar";
    public static final String LONGBRIDGE_API = "longbridge_api";

    private final SecurityIdentifierMapRepository repository;

    public UsSecurityIdentifierService(SecurityIdentifierMapRepository repository) {
        this.repository = repository;
    }

    public Optional<Long> findSecurityIdByCik(String cik) {
        return repository.findSecurityIdByCik(cik);
    }

    public Optional<Long> findSecurityIdBySourceSymbol(String sourceName, String sourceSymbol) {
        return repository.findSecurityIdBySourceSymbol(sourceName, sourceSymbol);
    }

    public void saveSecEdgarMapping(long securityId, String ticker, String cik) {
        repository.upsertSecEdgarMapping(securityId, ticker, cik);
    }

    public void saveLongbridgeMapping(long securityId, String symbolFull, String exchangeCode) {
        repository.upsertLongbridgeMapping(securityId, symbolFull, exchangeCode);
    }
}

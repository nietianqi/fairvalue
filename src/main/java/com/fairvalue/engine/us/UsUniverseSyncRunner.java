package com.fairvalue.engine.us;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "market-data.us.live", name = "universe-sync-on-startup", havingValue = "true")
public class UsUniverseSyncRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UsUniverseSyncRunner.class);

    private final UsUniverseSyncService universeSyncService;

    public UsUniverseSyncRunner(UsUniverseSyncService universeSyncService) {
        this.universeSyncService = universeSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        UsUniverseSyncService.UniverseSyncSummary summary = universeSyncService.syncSecTickerUniverse();
        log.info(
                "US SEC universe sync finished with status={} fetched={} inserted={} updated={} failed={}",
                summary.status(),
                summary.totalFetched(),
                summary.inserted(),
                summary.updated(),
                summary.failed()
        );
    }
}

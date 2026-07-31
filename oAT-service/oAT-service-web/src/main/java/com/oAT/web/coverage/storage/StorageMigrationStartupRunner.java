package com.oAT.web.coverage.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "oat.storage.migration.run-on-startup", havingValue = "true")
public class StorageMigrationStartupRunner implements ApplicationRunner, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(StorageMigrationStartupRunner.class);

    private final CoverageStorageMigrationService migrationService;
    private final ConfigurableApplicationContext applicationContext;
    private final int batchSize;
    private final int maxRounds;
    private final boolean exitOnComplete;

    public StorageMigrationStartupRunner(CoverageStorageMigrationService migrationService,
                                         ConfigurableApplicationContext applicationContext,
                                         @Value("${oat.storage.migration.batch-size:100}") int batchSize,
                                         @Value("${oat.storage.migration.max-rounds:10000}") int maxRounds,
                                         @Value("${oat.storage.migration.exit-on-complete:true}") boolean exitOnComplete) {
        this.migrationService = migrationService;
        this.applicationContext = applicationContext;
        this.batchSize = batchSize;
        this.maxRounds = maxRounds;
        this.exitOnComplete = exitOnComplete;
    }

    @Override
    public void run(ApplicationArguments args) {
        int effectiveBatchSize = Math.max(1, Math.min(batchSize, 1000));
        int rounds = 0;
        logger.info("Storage migration startup runner started: batchSize={}, maxRounds={}", effectiveBatchSize, maxRounds);

        while (rounds < maxRounds) {
            Map<String, Integer> pending = migrationService.pendingCounts();
            int pendingTotal = total(pending);
            logger.info("Storage migration pending before round {}: {}", rounds + 1, pending);
            if (pendingTotal <= 0) {
                logger.info("Storage migration completed: no pending rows");
                exitIfNeeded(0);
                return;
            }

            Map<String, Integer> migrated = migrationService.migrateBatch(effectiveBatchSize);
            int migratedTotal = total(migrated);
            rounds++;
            logger.info("Storage migration round {} migrated: {}", rounds, migrated);
            if (migratedTotal <= 0) {
                logger.warn("Storage migration stopped because no rows were migrated while pending rows remain: {}", pending);
                exitIfNeeded(2);
                return;
            }
        }

        logger.warn("Storage migration stopped after maxRounds={}. Pending rows: {}", maxRounds, migrationService.pendingCounts());
        exitIfNeeded(3);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private int total(Map<String, Integer> values) {
        int total = 0;
        for (Integer value : values.values()) {
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private void exitIfNeeded(int code) {
        if (exitOnComplete) {
            int exitCode = SpringApplication.exit(applicationContext, () -> code);
            System.exit(exitCode);
        }
    }
}

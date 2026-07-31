package com.oAT.web.coverage.storage;

import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.coverage.CoverageStorageProperties;
import com.oAT.web.coverage.FrontendCoverageSchemaInitializer;
import com.oAT.web.config.ElasticsearchTemplateInitializer;
import com.oAT.web.config.RestClientConfig;
import com.oAT.web.coveragecore.index.CoverageEsIndexService;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

public class StorageMigrationCliApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(StorageMigrationCliConfiguration.class)
                .profiles("storage-migration-cli")
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @SpringBootConfiguration
    @Profile("storage-migration-cli")
    @EnableAutoConfiguration(excludeName = {
            "com.oAT.ai.config.AIAutoConfiguration",
            "org.redisson.spring.starter.RedissonAutoConfigurationV2",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    })
    @Import({
            CoverageStorageProperties.class,
            CoverageStorage.class,
            CoverageStorageMigrationService.class,
            StorageMigrationStartupRunner.class,
            FrontendCoverageSchemaInitializer.class,
            RestClientConfig.class,
            ElasticsearchTemplateInitializer.class,
            CoverageEsIndexService.class,
            CoverageReportRepository.class,
            ClassCoverageRepository.class
    })
    static class StorageMigrationCliConfiguration {
    }
}

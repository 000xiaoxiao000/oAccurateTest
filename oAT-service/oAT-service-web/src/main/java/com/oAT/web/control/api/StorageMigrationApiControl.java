package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coverage.storage.CoverageStorageMigrationService;
import com.oAT.web.service.entity.UserVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Map;

@RestController
@RequestMapping("/api/storage-migration")
public class StorageMigrationApiControl {
    private final CoverageStorageMigrationService migrationService;
    private final boolean apiEnabled;
    private final String migrationToken;

    public StorageMigrationApiControl(CoverageStorageMigrationService migrationService,
                                      @Value("${oat.storage.migration.api-enabled:false}") boolean apiEnabled,
                                      @Value("${oat.storage.migration.token:}") String migrationToken) {
        this.migrationService = migrationService;
        this.apiEnabled = apiEnabled;
        this.migrationToken = migrationToken;
    }

    @GetMapping("/coverage/status")
    public ResultNotified<Map<String, Integer>> coverageStorageStatus(@SessionAttribute UserVo user,
                                                                      @RequestParam(required = false) String token) {
        ensureAllowed(user, token);
        return new ResultNotified<>(true, "pending", migrationService.pendingCounts());
    }

    @PostMapping("/coverage/batch")
    public ResultNotified<Map<String, Integer>> migrateCoverageStorage(@SessionAttribute UserVo user,
                                                                       @RequestParam(required = false) String token,
                                                                       @RequestParam(defaultValue = "100") int limit) {
        ensureAllowed(user, token);
        Assert.isTrue(limit > 0 && limit <= 1000, "limit 必须在 1 到 1000 之间");
        return new ResultNotified<>(true, "migrated", migrationService.migrateBatch(limit));
    }

    @PostMapping("/coverage/es-rebuild")
    public ResultNotified<Map<String, Integer>> rebuildCoverageEsReadModel(@SessionAttribute UserVo user,
                                                                           @RequestParam(required = false) String token,
                                                                           @RequestParam(defaultValue = "100") int limit,
                                                                           @RequestParam(defaultValue = "0") int offset) {
        ensureAllowed(user, token);
        Assert.isTrue(limit > 0 && limit <= 1000, "limit 必须在 1 到 1000 之间");
        Assert.isTrue(offset >= 0, "offset 必须大于等于 0");
        return new ResultNotified<>(true, "coverage es read model rebuilt",
                migrationService.rebuildCoverageEsReadModel(limit, offset));
    }

    private void ensureAllowed(UserVo user, String token) {
        Assert.isTrue(apiEnabled, "存储迁移接口未启用，请配置 oat.storage.migration.api-enabled=true 后再执行");
        Assert.notNull(user, "请先登录");
        Assert.hasText(user.getId(), "请先登录");
        if (StringUtils.hasText(migrationToken)) {
            Assert.isTrue(migrationToken.equals(token), "迁移 token 不正确");
        }
    }
}

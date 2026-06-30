package com.oAT.web.coveragecore.diff;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.service.GitService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.GitDiffVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CoverageDiffService {
    private final Map<String, Map<String, List<Integer>>> diffCache = new ConcurrentHashMap<>();
    private final GitService gitService;

    public CoverageDiffService(GitService gitService) {
        this.gitService = gitService;
    }

    public void clearCache() {
        diffCache.clear();
    }

    public Map<String, List<Integer>> getDiffMap(AppVo app, String oldCommit, String newCommit) {
        if (app == null) {
            return Collections.emptyMap();
        }
        String cacheKey = app.getId() + ":" + oldCommit + ":" + newCommit;
        return diffCache.computeIfAbsent(cacheKey, k -> toDiffMap(gitService.getDiffDetail(
                app.getRepoAddress(),
                app.getRepoUserName(),
                app.getRepoPassword(),
                oldCommit,
                newCommit)));
    }

    public Map<String, List<Integer>> getUncachedDiffMap(AppVo app, String oldCommit, String newCommit) {
        if (app == null) {
            return Collections.emptyMap();
        }
        return toDiffMap(gitService.getDiffDetail(
                app.getRepoAddress(),
                app.getRepoUserName(),
                app.getRepoPassword(),
                oldCommit,
                newCommit));
    }

    public List<Integer> getChangedLinesForClass(Map<String, List<Integer>> diffMap, String className) {
        if (diffMap == null || diffMap.isEmpty() || !StringUtils.hasText(className)) {
            return null;
        }
        for (String candidateClassName : CoverageSourceClassUtil.buildSourceClassCandidates(className)) {
            List<Integer> changedLines = diffMap.get(candidateClassName);
            if (changedLines != null) {
                return changedLines;
            }
        }
        return null;
    }

    public Map<String, List<Integer>> toDiffMap(List<GitDiffVo> diffs) {
        Map<String, List<Integer>> diffMap = new HashMap<>();
        if (diffs == null || diffs.isEmpty()) {
            return diffMap;
        }
        for (GitDiffVo diff : diffs) {
            if (diff == null || !StringUtils.hasText(diff.getClassName())) {
                continue;
            }
            String key = diff.getClassName();
            if ("DELETE".equals(diff.getChangeType())) {
                key += ":DELETED";
            }
            diffMap.put(key, diff.getChangedLines() == null ? Collections.emptyList() : diff.getChangedLines());
        }
        return diffMap;
    }
}

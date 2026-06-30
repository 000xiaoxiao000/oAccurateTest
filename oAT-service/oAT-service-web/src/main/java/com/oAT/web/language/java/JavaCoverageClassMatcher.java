package com.oAT.web.language.java;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class JavaCoverageClassMatcher {
    private static final Logger logger = LoggerFactory.getLogger(JavaCoverageClassMatcher.class);

    public String resolveCoverageOwnerClassName(String className) {
        return CoverageSourceClassUtil.resolveSourceOwnerClassName(className);
    }

    public ClassCoverageIndex findClassCoverage(Map<String, ClassCoverageIndex> coverageMap, String className) {
        String targetClassName = resolveCoverageOwnerClassName(className);
        if (coverageMap == null || coverageMap.isEmpty() || !StringUtils.hasText(targetClassName)) {
            return null;
        }

        ClassCoverageIndex exactMatch = coverageMap.get(targetClassName);
        if (exactMatch != null) {
            return exactMatch;
        }

        List<Map.Entry<String, ClassCoverageIndex>> suffixMatches = new ArrayList<>();
        for (Map.Entry<String, ClassCoverageIndex> entry : coverageMap.entrySet()) {
            String candidateName = entry.getKey();
            if (candidateName.endsWith(targetClassName)) {
                int endIndex = candidateName.length() - targetClassName.length();
                if (endIndex == 0 || candidateName.charAt(endIndex - 1) == '.') {
                    suffixMatches.add(entry);
                }
            }
        }

        if (suffixMatches.isEmpty()) {
            return null;
        }

        suffixMatches.sort(Comparator.comparingInt(entry -> entry.getKey().length()));
        if (suffixMatches.size() > 1) {
            logger.info("类名模糊匹配：目标={}, 找到 {} 个后缀匹配，选择最短的: {}",
                    targetClassName, suffixMatches.size(), suffixMatches.get(0).getKey());
        } else {
            logger.info("类名模糊匹配成功：目标={}, 匹配到={}", targetClassName, suffixMatches.get(0).getKey());
        }
        return suffixMatches.get(0).getValue();
    }
}

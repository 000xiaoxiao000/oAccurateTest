package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.entity.StandardDate;
import com.oAT.web.esDao.entity.SystemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class CoverageSnapshotContextSupport {
    private static final Logger logger = LoggerFactory.getLogger(CoverageSnapshotContextSupport.class);

    public CoverageSnapshotContext buildContext(List<SystemSnapshot> snapshots) {
        CoverageSnapshotContext context = new CoverageSnapshotContext();
        if (snapshots == null || snapshots.isEmpty()) {
            return context;
        }
        List<SystemSnapshot> sortedSnapshots = new ArrayList<>(snapshots);
        sortedSnapshots.sort(Comparator
                .comparing(this::getSnapshotEffectiveTime)
                .thenComparing(snapshot -> snapshot.getId() == null ? "" : snapshot.getId()));

        List<String> snapshotIds = new ArrayList<>();
        List<String> traceIds = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        String lastSnapshotTime = null;

        for (SystemSnapshot snapshot : sortedSnapshots) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getId()) || !StringUtils.hasText(snapshot.getTraceId())) {
                continue;
            }
            String effectiveTime = getSnapshotEffectiveTime(snapshot);
            snapshotIds.add(snapshot.getId());
            traceIds.add(snapshot.getTraceId());
            fingerprints.add(snapshot.getId() + "|" + effectiveTime + "|" + snapshot.getTraceId());

            if (lastSnapshotTime == null || effectiveTime.compareTo(lastSnapshotTime) > 0) {
                lastSnapshotTime = effectiveTime;
            }
        }

        context.setSnapshotIds(snapshotIds);
        context.setTraceIds(traceIds);
        context.setLastSnapshotTime(lastSnapshotTime);
        context.setSnapshotFingerprint(sha256Hex(String.join(";", fingerprints)));
        return context;
    }

    public String getSnapshotEffectiveTime(SystemSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        Date updateTime = snapshot.getUpdateTime();
        if (updateTime != null) {
            return new SimpleDateFormat(StandardDate.dateFormat).format(updateTime);
        }
        Date createTime = snapshot.getCreateTime();
        return createTime == null ? "" : new SimpleDateFormat(StandardDate.dateFormat).format(createTime);
    }

    public List<String> parseSnapshotIds(String rawSnapshotIds) {
        if (!StringUtils.hasText(rawSnapshotIds)) {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();
        for (String item : rawSnapshotIds.split(",")) {
            String id = item == null ? null : item.trim();
            if (StringUtils.hasText(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public boolean isAppendOnlySnapshotChange(List<String> oldIds, List<String> newIds) {
        if (oldIds == null || oldIds.isEmpty() || newIds == null || newIds.size() < oldIds.size()) {
            return false;
        }
        for (int i = 0; i < oldIds.size(); i++) {
            if (!Objects.equals(oldIds.get(i), newIds.get(i))) {
                return false;
            }
        }
        return true;
    }

    public String sha256Hex(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Compute snapshot fingerprint failed", e);
            return String.valueOf(text.hashCode());
        }
    }
}

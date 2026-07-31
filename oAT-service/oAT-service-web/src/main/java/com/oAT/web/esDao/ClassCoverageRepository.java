package com.oAT.web.esDao;

import com.oAT.web.coveragecore.index.CoverageEsIndexService.CoverageClassIdSearchResult;
import com.oAT.web.coveragecore.index.CoverageEsIndexService;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ClassCoverageRepository {
    private final JdbcTemplate jdbcTemplate;
    private final CoverageEsIndexService coverageEsIndexService;
    private final RowMapper<ClassCoverageIndex> summaryRowMapper = this::mapSummaryRow;
    private final RowMapper<ClassCoverageIndex> detailRowMapper = this::mapDetailRow;
    private static final String UPSERT_CLASS_SQL = """
            INSERT INTO oat_class_coverage (
                id, report_id, app_id, class_name, source_type, language, display_name, source_path, total_methods, covered_methods,
                total_branches, covered_branches, total_branch_targets, covered_branch_targets,
                total_lines, covered_lines, total_complexity, line_rate, branch_rate,
                method_rate, has_code_changes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                report_id = VALUES(report_id),
                app_id = VALUES(app_id),
                class_name = VALUES(class_name),
                source_type = VALUES(source_type),
                language = VALUES(language),
                display_name = VALUES(display_name),
                source_path = VALUES(source_path),
                total_methods = VALUES(total_methods),
                covered_methods = VALUES(covered_methods),
                total_branches = VALUES(total_branches),
                covered_branches = VALUES(covered_branches),
                total_branch_targets = VALUES(total_branch_targets),
                covered_branch_targets = VALUES(covered_branch_targets),
                total_lines = VALUES(total_lines),
                covered_lines = VALUES(covered_lines),
                total_complexity = VALUES(total_complexity),
                line_rate = VALUES(line_rate),
                branch_rate = VALUES(branch_rate),
                method_rate = VALUES(method_rate),
                has_code_changes = VALUES(has_code_changes),
                update_time = CURRENT_TIMESTAMP
            """;

    public ClassCoverageRepository(JdbcTemplate jdbcTemplate, CoverageEsIndexService coverageEsIndexService) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverageEsIndexService = coverageEsIndexService;
    }

    public Optional<ClassCoverageIndex> findById(String id) {
        List<ClassCoverageIndex> indexes = jdbcTemplate.query("SELECT * FROM oat_class_coverage WHERE id = ?",
                detailRowMapper,
                id);
        return indexes.stream().findFirst();
    }

    public Page<ClassCoverageIndex> findByReportId(String reportId, Pageable pageable) {
        return findByReportIdFiltered(reportId, null, null, null, null, null, null, null, null, null, null, pageable);
    }

    public List<ClassCoverageIndex> findByReportId(String reportId) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_class_coverage
                        WHERE report_id = ?
                        ORDER BY class_name ASC
                        """,
                detailRowMapper, reportId);
    }

    public List<ClassCoverageIndex> findBatchWithMethodsForEsRebuild(int limit) {
        return findBatchWithMethodsForEsRebuild(limit, 0);
    }

    public List<ClassCoverageIndex> findBatchWithMethodsForEsRebuild(int limit, int offset) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_class_coverage
                        ORDER BY update_time DESC, id ASC
                        LIMIT ? OFFSET ?
                        """,
                detailRowMapper,
                Math.max(1, Math.min(limit, 1000)),
                Math.max(offset, 0));
    }

    public Page<ClassCoverageIndex> findByReportIdWithMethods(String reportId, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT * FROM oat_class_coverage WHERE report_id = ? ORDER BY class_name ASC");
        List<Object> parameters = new ArrayList<>();
        parameters.add(reportId);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM oat_class_coverage WHERE report_id = ?",
                Long.class, reportId);
        if (pageable != null && pageable.isPaged()) {
            sql.append(" LIMIT ? OFFSET ?");
            parameters.add(pageable.getPageSize());
            parameters.add(pageable.getOffset());
        }
        List<ClassCoverageIndex> content = jdbcTemplate.query(sql.toString(), detailRowMapper, parameters.toArray());
        return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, total == null ? 0 : total);
    }

    public Page<ClassCoverageIndex> findByReportIdFiltered(String reportId,
                                                           String className,
                                                           String methodName,
                                                           Double minLineRate,
                                                           Double maxLineRate,
                                                           Double minBranchRate,
                                                           Double maxBranchRate,
                                                           Double minMethodRate,
                                                           Double maxMethodRate,
                                                           Integer minComplexity,
                                                           Integer maxComplexity,
                                                           Pageable pageable) {
        int page = pageable == null || pageable.isUnpaged() ? 0 : pageable.getPageNumber();
        int size = pageable == null || pageable.isUnpaged() ? 500 : pageable.getPageSize();
        CoverageClassIdSearchResult esResult = coverageEsIndexService.searchClassCoverageIds(reportId, className, methodName,
                minLineRate, maxLineRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity, null, page, size);
        if (esResult.isAvailable()) {
            List<ClassCoverageIndex> content = findSummaryByIds(esResult.getIds());
            return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, esResult.getTotal());
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM oat_class_coverage c WHERE c.report_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(reportId);
        appendClassFilters(sql, parameters, className, minLineRate, maxLineRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, null);
        if (StringUtils.hasText(methodName)) {
            throw new IllegalStateException("methodName search requires coverage_method_search ES index");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM (" + sql + ") t", Long.class, parameters.toArray());
        sql.append(" ORDER BY c.class_name ASC");
        if (pageable != null && pageable.isPaged()) {
            sql.append(" LIMIT ? OFFSET ?");
            parameters.add(pageable.getPageSize());
            parameters.add(pageable.getOffset());
        }
        List<ClassCoverageIndex> content = jdbcTemplate.query(sql.toString(), summaryRowMapper, parameters.toArray());
        return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, total == null ? 0 : total);
    }

    public List<ClassCoverageIndex> findTreeCandidates(String reportId,
                                                       String className,
                                                       String methodName,
                                                       Double minLineRate,
                                                       Double maxLineRate,
                                                       Double minBranchRate,
                                                       Double maxBranchRate,
                                                       Double minMethodRate,
                                                       Double maxMethodRate,
                                                       Integer minComplexity,
                                                       Integer maxComplexity,
                                                       String classNamePrefix) {
        CoverageClassIdSearchResult esResult = coverageEsIndexService.searchTreeClassCoverageIds(reportId, className, methodName,
                minLineRate, maxLineRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity, classNamePrefix);
        if (esResult.isAvailable()) {
            return findSummaryByIds(esResult.getIds());
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM oat_class_coverage c WHERE c.report_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(reportId);
        appendClassFilters(sql, parameters, className, minLineRate, maxLineRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, classNamePrefix);
        if (StringUtils.hasText(methodName)) {
            throw new IllegalStateException("methodName search requires coverage_method_search ES index");
        }
        sql.append(" ORDER BY c.class_name ASC");
        return jdbcTemplate.query(sql.toString(), summaryRowMapper, parameters.toArray());
    }

    @Transactional
    public List<ClassCoverageIndex> saveAll(Iterable<ClassCoverageIndex> indexes) {
        List<ClassCoverageIndex> saved = new ArrayList<>();
        for (ClassCoverageIndex index : indexes) {
            normalize(index);
            saved.add(index);
        }
        if (saved.isEmpty()) {
            return saved;
        }
        jdbcTemplate.batchUpdate(UPSERT_CLASS_SQL, saved, 500, this::bindClassCoverage);
        coverageEsIndexService.indexClassCoverage(saved);
        return saved;
    }

    @Transactional
    public ClassCoverageIndex save(ClassCoverageIndex index) {
        normalize(index);
        jdbcTemplate.update(UPSERT_CLASS_SQL, ps -> bindClassCoverage(ps, index));
        coverageEsIndexService.indexClassCoverage(List.of(index));
        return index;
    }

    private void bindClassCoverage(PreparedStatement ps, ClassCoverageIndex index) throws SQLException {
        ps.setString(1, index.getId());
        ps.setString(2, index.getReportId());
        ps.setString(3, index.getAppId());
        ps.setString(4, index.getClassName());
        ps.setString(5, index.getSourceType());
        ps.setString(6, index.getLanguage());
        ps.setString(7, index.getDisplayName());
        ps.setString(8, index.getSourcePath());
        ps.setInt(9, index.getTotalMethods());
        ps.setInt(10, index.getCoveredMethods());
        ps.setInt(11, index.getTotalBranches());
        ps.setInt(12, index.getCoveredBranches());
        ps.setInt(13, index.getTotalBranchTargets());
        ps.setInt(14, index.getCoveredBranchTargets());
        ps.setInt(15, index.getTotalLines());
        ps.setInt(16, index.getCoveredLines());
        ps.setInt(17, index.getTotalComplexity());
        ps.setObject(18, index.getLineRate());
        ps.setObject(19, index.getBranchRate());
        ps.setObject(20, index.getMethodRate());
        ps.setObject(21, index.getHasCodeChanges());
    }

    @Transactional
    public void deleteByReportId(String reportId) {
        jdbcTemplate.update("DELETE FROM oat_class_coverage WHERE report_id = ?", reportId);
        coverageEsIndexService.deleteByReportId(reportId);
    }


    private void appendClassFilters(StringBuilder sql, List<Object> parameters,
                                    String className,
                                    Double minLineRate,
                                    Double maxLineRate,
                                    Double minBranchRate,
                                    Double maxBranchRate,
                                    Double minMethodRate,
                                    Double maxMethodRate,
                                    Integer minComplexity,
                                    Integer maxComplexity,
                                    String classNamePrefix) {
        if (StringUtils.hasText(className)) {
            sql.append(" AND LOWER(c.class_name) LIKE ?");
            parameters.add("%" + className.toLowerCase() + "%");
        }
        if (StringUtils.hasText(classNamePrefix)) {
            sql.append(" AND c.class_name LIKE ?");
            parameters.add(classNamePrefix + "%");
        }
        appendRange(sql, parameters, "c.line_rate", minLineRate, maxLineRate);
        appendRange(sql, parameters, "c.branch_rate", minBranchRate, maxBranchRate);
        appendRange(sql, parameters, "c.method_rate", minMethodRate, maxMethodRate);
        appendRange(sql, parameters, "c.total_complexity", minComplexity, maxComplexity);
    }

    private void appendRange(StringBuilder sql, List<Object> parameters, String column, Object min, Object max) {
        if (min != null) {
            sql.append(" AND ").append(column).append(" >= ?");
            parameters.add(min);
        }
        if (max != null) {
            sql.append(" AND ").append(column).append(" <= ?");
            parameters.add(max);
        }
    }

    private List<ClassCoverageIndex> findSummaryByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<ClassCoverageIndex> rows = jdbcTemplate.query("""
                        SELECT * FROM oat_class_coverage
                        WHERE id IN (%s)
                        """.formatted(placeholders),
                summaryRowMapper,
                ids.toArray());
        Map<String, ClassCoverageIndex> byId = new HashMap<>();
        for (ClassCoverageIndex row : rows) {
            byId.put(row.getId(), row);
        }
        List<ClassCoverageIndex> ordered = new ArrayList<>();
        for (String id : ids) {
            ClassCoverageIndex row = byId.get(id);
            if (row != null) {
                ordered.add(row);
            }
        }
        return ordered;
    }

    private List<ClassCoverageIndex> filter(List<ClassCoverageIndex> source,
                                            String className,
                                            String methodName,
                                            Double minLineRate,
                                            Double maxLineRate,
                                            Double minBranchRate,
                                            Double maxBranchRate,
                                            Double minMethodRate,
                                            Double maxMethodRate,
                                            Integer minComplexity,
                                            Integer maxComplexity,
                                            String classNamePrefix) {
        List<ClassCoverageIndex> result = new ArrayList<>();
        for (ClassCoverageIndex index : source) {
            if (index == null) {
                continue;
            }
            if (StringUtils.hasText(className) && !contains(index.getClassName(), className)) {
                continue;
            }
            if (StringUtils.hasText(classNamePrefix) && (index.getClassName() == null || !index.getClassName().startsWith(classNamePrefix))) {
                continue;
            }
            if (StringUtils.hasText(methodName) && !hasMethodName(index, methodName)) {
                continue;
            }
            if (!inRange(index.getLineRate(), minLineRate, maxLineRate)) {
                continue;
            }
            if (!inRange(index.getBranchRate(), minBranchRate, maxBranchRate)) {
                continue;
            }
            if (!inRange(index.getMethodRate(), minMethodRate, maxMethodRate)) {
                continue;
            }
            if (!inRange(index.getTotalComplexity(), minComplexity, maxComplexity)) {
                continue;
            }
            result.add(index);
        }
        return result;
    }

    private Page<ClassCoverageIndex> page(List<ClassCoverageIndex> source, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(source);
        }
        int start = Math.toIntExact(Math.min(pageable.getOffset(), source.size()));
        int end = Math.min(start + pageable.getPageSize(), source.size());
        return new PageImpl<>(source.subList(start, end), pageable, source.size());
    }

    private boolean hasMethodName(ClassCoverageIndex index, String methodName) {
        if (index.getMethods() == null) {
            return false;
        }
        for (MethodCoverageDetail method : index.getMethods()) {
            if (method != null && contains(method.getMethodName(), methodName)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String value, String search) {
        return value != null && search != null && value.toLowerCase().contains(search.toLowerCase());
    }

    private boolean inRange(Double value, Double min, Double max) {
        if (min == null && max == null) {
            return true;
        }
        double actual = value == null ? 0D : value;
        return (min == null || actual >= min) && (max == null || actual <= max);
    }

    private boolean inRange(Integer value, Integer min, Integer max) {
        if (min == null && max == null) {
            return true;
        }
        int actual = value == null ? 0 : value;
        return (min == null || actual >= min) && (max == null || actual <= max);
    }

    private void normalize(ClassCoverageIndex index) {
        if (!StringUtils.hasText(index.getId())) {
            index.setId(UUID.randomUUID().toString());
        }
        if (!StringUtils.hasText(index.getReportId())) {
            throw new IllegalArgumentException("reportId must not be empty");
        }
        if (!StringUtils.hasText(index.getClassName())) {
            throw new IllegalArgumentException("className must not be empty");
        }
    }

    private ClassCoverageIndex mapSummaryRow(ResultSet rs, int rowNum) throws SQLException {
        ClassCoverageIndex index = new ClassCoverageIndex();
        index.setId(rs.getString("id"));
        index.setReportId(rs.getString("report_id"));
        index.setAppId(rs.getString("app_id"));
        index.setClassName(rs.getString("class_name"));
        index.setSourceType(readStringIfExists(rs, "source_type"));
        index.setLanguage(readStringIfExists(rs, "language"));
        index.setDisplayName(readStringIfExists(rs, "display_name"));
        index.setSourcePath(readStringIfExists(rs, "source_path"));
        index.setTotalMethods(rs.getInt("total_methods"));
        index.setCoveredMethods(rs.getInt("covered_methods"));
        index.setTotalBranches(rs.getInt("total_branches"));
        index.setCoveredBranches(rs.getInt("covered_branches"));
        index.setTotalBranchTargets(rs.getInt("total_branch_targets"));
        index.setCoveredBranchTargets(rs.getInt("covered_branch_targets"));
        index.setTotalLines(rs.getInt("total_lines"));
        index.setCoveredLines(rs.getInt("covered_lines"));
        index.setTotalComplexity(rs.getInt("total_complexity"));
        index.setLineRate(getDouble(rs, "line_rate"));
        index.setBranchRate(getDouble(rs, "branch_rate"));
        index.setMethodRate(getDouble(rs, "method_rate"));
        index.setHasCodeChanges(getBoolean(rs, "has_code_changes"));
        return index;
    }

    private ClassCoverageIndex mapDetailRow(ResultSet rs, int rowNum) throws SQLException {
        ClassCoverageIndex index = mapSummaryRow(rs, rowNum);
        index.setMethods(coverageEsIndexService.loadMethodDetails(index.getId()));
        return index;
    }

    private Double getDouble(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }

    private Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        boolean value = rs.getBoolean(columnName);
        return rs.wasNull() ? null : value;
    }

    private String readStringIfExists(ResultSet rs, String columnName) throws SQLException {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }
}

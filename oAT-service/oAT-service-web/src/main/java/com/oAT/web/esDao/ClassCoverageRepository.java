package com.oAT.web.esDao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.oAT.web.common.UtilJson;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ClassCoverageRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ClassCoverageIndex> rowMapper = this::mapRow;

    public ClassCoverageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ClassCoverageIndex> findById(String id) {
        List<ClassCoverageIndex> indexes = jdbcTemplate.query("SELECT * FROM oat_class_coverage WHERE id = ?",
                rowMapper,
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
                rowMapper, reportId);
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
        StringBuilder sql = new StringBuilder("SELECT * FROM oat_class_coverage c WHERE c.report_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(reportId);
        appendClassFilters(sql, parameters, className, minLineRate, maxLineRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, null);
        if (StringUtils.hasText(methodName)) {
            sql.append(" AND EXISTS (SELECT 1 FROM oat_method_coverage m WHERE m.class_coverage_id = c.id AND LOWER(m.method_name) LIKE ?)");
            parameters.add("%" + methodName.toLowerCase() + "%");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM (" + sql + ") t", Long.class, parameters.toArray());
        sql.append(" ORDER BY c.class_name ASC");
        if (pageable != null && pageable.isPaged()) {
            sql.append(" LIMIT ? OFFSET ?");
            parameters.add(pageable.getPageSize());
            parameters.add(pageable.getOffset());
        }
        List<ClassCoverageIndex> content = jdbcTemplate.query(sql.toString(), rowMapper, parameters.toArray());
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
        StringBuilder sql = new StringBuilder("SELECT * FROM oat_class_coverage c WHERE c.report_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(reportId);
        appendClassFilters(sql, parameters, className, minLineRate, maxLineRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, classNamePrefix);
        if (StringUtils.hasText(methodName)) {
            sql.append(" AND EXISTS (SELECT 1 FROM oat_method_coverage m WHERE m.class_coverage_id = c.id AND LOWER(m.method_name) LIKE ?)");
            parameters.add("%" + methodName.toLowerCase() + "%");
        }
        sql.append(" ORDER BY c.class_name ASC");
        return jdbcTemplate.query(sql.toString(), rowMapper, parameters.toArray());
    }

    @Transactional
    public List<ClassCoverageIndex> saveAll(Iterable<ClassCoverageIndex> indexes) {
        List<ClassCoverageIndex> saved = new ArrayList<>();
        for (ClassCoverageIndex index : indexes) {
            saved.add(save(index));
        }
        return saved;
    }

    @Transactional
    public ClassCoverageIndex save(ClassCoverageIndex index) {
        normalize(index);
        jdbcTemplate.update("""
                        INSERT INTO oat_class_coverage (
                            id, report_id, app_id, class_name, source_type, total_methods, covered_methods,
                            total_branches, covered_branches, total_branch_targets, covered_branch_targets,
                            total_lines, covered_lines, total_complexity, line_rate, branch_rate,
                            method_rate, has_code_changes, methods_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                        ON DUPLICATE KEY UPDATE
                            report_id = VALUES(report_id),
                            app_id = VALUES(app_id),
                            class_name = VALUES(class_name),
                            source_type = VALUES(source_type),
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
                            methods_json = VALUES(methods_json),
                            update_time = CURRENT_TIMESTAMP
                        """,
                index.getId(),
                index.getReportId(),
                index.getAppId(),
                index.getClassName(),
                index.getSourceType(),
                index.getTotalMethods(),
                index.getCoveredMethods(),
                index.getTotalBranches(),
                index.getCoveredBranches(),
                index.getTotalBranchTargets(),
                index.getCoveredBranchTargets(),
                index.getTotalLines(),
                index.getCoveredLines(),
                index.getTotalComplexity(),
                index.getLineRate(),
                index.getBranchRate(),
                index.getMethodRate(),
                index.getHasCodeChanges(),
                UtilJson.writeValueAsString(index.getMethods()));
        saveMethodCoverage(index);
        return index;
    }

    @Transactional
    public void deleteByReportId(String reportId) {
        jdbcTemplate.update("DELETE FROM oat_method_coverage WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM oat_class_coverage WHERE report_id = ?", reportId);
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

    private void saveMethodCoverage(ClassCoverageIndex index) {
        jdbcTemplate.update("DELETE FROM oat_method_coverage WHERE class_coverage_id = ?", index.getId());
        if (index.getMethods() == null) {
            return;
        }
        for (int i = 0; i < index.getMethods().size(); i++) {
            MethodCoverageDetail method = index.getMethods().get(i);
            jdbcTemplate.update("""
                            INSERT INTO oat_method_coverage (
                                id, class_coverage_id, report_id, app_id, class_name, method_name, method_desc, method_order,
                                total_lines, covered_lines, total_branches, covered_branches, total_branch_targets, covered_branch_targets,
                                complexity, covered, branch_rate, has_code_changes, detail_json
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                            ON DUPLICATE KEY UPDATE method_name=VALUES(method_name), method_desc=VALUES(method_desc), method_order=VALUES(method_order),
                                total_lines=VALUES(total_lines), covered_lines=VALUES(covered_lines), total_branches=VALUES(total_branches),
                                covered_branches=VALUES(covered_branches), total_branch_targets=VALUES(total_branch_targets),
                                covered_branch_targets=VALUES(covered_branch_targets), complexity=VALUES(complexity), covered=VALUES(covered),
                                branch_rate=VALUES(branch_rate), has_code_changes=VALUES(has_code_changes), detail_json=VALUES(detail_json), update_time=CURRENT_TIMESTAMP
                            """,
                    index.getId() + "_" + i,
                    index.getId(),
                    index.getReportId(),
                    index.getAppId(),
                    index.getClassName(),
                    method == null ? null : method.getMethodName(),
                    method == null ? null : method.getMethodDesc(),
                    i,
                    method == null ? null : method.getTotalLines(),
                    method == null ? null : method.getCoveredLines(),
                    method == null ? null : method.getTotalBranches(),
                    method == null ? null : method.getCoveredBranches(),
                    method == null ? null : method.getTotalBranchTargets(),
                    method == null ? null : method.getCoveredBranchTargets(),
                    method == null ? null : method.getComplexity(),
                    method == null ? null : method.isCovered(),
                    method == null ? null : method.getBranchRate(),
                    method == null ? null : method.isHasCodeChanges(),
                    UtilJson.writeValueAsString(method));
        }
    }

    private List<MethodCoverageDetail> loadMethodCoverage(ClassCoverageIndex index) throws SQLException {
        return jdbcTemplate.query("""
                        SELECT detail_json FROM oat_method_coverage
                        WHERE class_coverage_id = ?
                        ORDER BY method_order ASC
                        """,
                (rs, rowNum) -> {
                    try {
                        return UtilJson.getObjectMapper().readValue(rs.getString("detail_json"), MethodCoverageDetail.class);
                    } catch (Exception e) {
                        throw new SQLException("Failed to parse method detail_json", e);
                    }
                },
                index.getId());
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

    private ClassCoverageIndex mapRow(ResultSet rs, int rowNum) throws SQLException {
        ClassCoverageIndex index = new ClassCoverageIndex();
        index.setId(rs.getString("id"));
        index.setReportId(rs.getString("report_id"));
        index.setAppId(rs.getString("app_id"));
        index.setClassName(rs.getString("class_name"));
        index.setSourceType(readStringIfExists(rs, "source_type"));
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
        index.setMethods(loadMethodCoverage(index));
        if (index.getMethods() == null || index.getMethods().isEmpty()) {
            String methodsJson = rs.getString("methods_json");
            if (StringUtils.hasText(methodsJson)) {
                try {
                    index.setMethods(UtilJson.getObjectMapper().readValue(methodsJson,
                            new TypeReference<List<MethodCoverageDetail>>() {}));
                } catch (Exception e) {
                    throw new SQLException("Failed to parse methods_json", e);
                }
            }
        }
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

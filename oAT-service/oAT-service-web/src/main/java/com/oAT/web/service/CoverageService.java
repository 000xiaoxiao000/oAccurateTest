package com.oAT.web.service;

import com.oAT.web.common.Job;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.CoverageComparisonVo;
import com.oAT.web.service.entity.CoverageTreeNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface CoverageService {

    /**
     * 开启异步生成覆盖率报告任务
     */
    String startGenerateJob(String appId, String versionNumber, String branch, String commitId);

    /**
     * 开启异步生成本次 Commit 覆盖率报告任务
     */
    String startGenerateCurrentCommitJob(String appId, String versionNumber, String branch, String commitId);

    /**
     * 开启异步生成增量覆盖率报告任务
     */
    String startGenerateIncrementalJob(String appId, String versionNumber, String branch, String commitId,
                                     String baseVersionNumber, String baseCommitId);

    /**
     * 获取任务信息
     */
    Job<String> getJob(String jobId);

    /**
     * 获取指定报告
     */
    CoverageReportIndex getReport(String reportId);

    /**
     * 获取最新报告
     */
    CoverageReportIndex getLatestReport(String appId, String versionNumber);

    /**
     * 获取指定类型的最新报告 (0: 版本全量, 1: 增量, 2: 本次 Commit)
     */
    CoverageReportIndex getLatestReportByType(String appId, String versionNumber, Integer reportType);

    /**
     * 获取二级页面分页数据（支持查询）
     */
    Page<ClassCoverageIndex> getClassCoveragePage(String reportId, String className, String methodName,
                                                   Double minLineRate, Double maxLineRate,
                                                   Double minBranchRate, Double maxBranchRate,
                                                   Double minMethodRate, Double maxMethodRate,
                                                   Integer minComplexity, Integer maxComplexity,
                                                   Pageable pageable);

    /**
     * 获取树形结构节点 (支持懒加载和搜索过滤)
     */
    List<CoverageTreeNode> getTreeNodes(String reportId, String parentPackage, String classNameSearch, String methodNameSearch,
                                       Double minRate, Double maxRate,
                                       Double minBranchRate, Double maxBranchRate,
                                       Double minMethodRate, Double maxMethodRate,
                                       Integer minComplexity, Integer maxComplexity);

    /**
     * 导出覆盖率报表
     */
    void exportReport(String reportId, HttpServletResponse response) throws IOException;

    /**
     * 导出方法级覆盖率报表
     */
    void exportMethodReport(String reportId, HttpServletResponse response) throws IOException;

    /**
     * 获取指定类的覆盖率详情（用于着色）
     */
    ClassCoverageIndex getClassCoverage(String reportId, String className);

    /**
     * 获取对比数据（上一份报告对比当前）
     */
    CoverageComparisonVo getComparison(String reportId);

    /**
     * 获取趋势图数据
     */
    List<Map<String, Object>> getTrendData(String appId, String versionNumber);

    /**
     * 获取指定 commit 的最新报告
     */
    CoverageReportIndex getLatestReport(String appId, String versionNumber, String commitId);

    /**
     * 获取指定类型且匹配 commit 的最新报告
     */
    CoverageReportIndex getLatestReportByType(String appId, String versionNumber, Integer reportType, String commitId);

    /**
     * 检查是否有比给定时间更新的数据
     */
    boolean hasNewerData(String appId, String lastProcessedTime);

    /**
     * 检查指定版本的系统快照是否相对于当前报告有新增/删除/修改
     */
    boolean hasNewerData(String appId, String versionNumber, CoverageReportIndex report);

    /**
     * 获取带有着色的源码 HTML
     */
    String getColoredSource(String appId, String reportId, String className);

    /**
     * 获取指定应用的所有覆盖率报告
     */
    List<CoverageReportIndex> getReportsByAppId(String appId);

    /**
     * 对指定类的数据应用源码染色 (不依赖 reportId，自动匹配最新源码)
     */
    String getColoredSource(String appId, ClassCoverageIndex classCov);

    /**
     * 删除覆盖率报告
     */
    void deleteReport(String reportId);
}

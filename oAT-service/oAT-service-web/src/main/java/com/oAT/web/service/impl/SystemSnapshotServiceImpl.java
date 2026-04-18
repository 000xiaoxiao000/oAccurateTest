package com.oAT.web.service.impl;

import com.oAT.agent.model.*;
import com.oAT.web.common.SqlStatParse;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.service.SystemSnapshotService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemSnapshotServiceImpl implements SystemSnapshotService {

    private final Logger logger = LoggerFactory.getLogger(SystemSnapshotServiceImpl.class);

    @Autowired
    SystemSnapshotRepository repository;

    @Autowired
    TraceNodeRepository traceNodeRepository;

    @Autowired
    StaticInfoRepository staticInfoRepository;

    @Autowired
    com.oAT.web.service.SnapshotService snapshotService;

    @Override
    public SystemSnapshot getById(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return repository.findById(id).orElse(null);
    }

    @Override
    public SystemSnapshot create(String projectId, String userId, SystemSnapshot snapshot, Collection<TraceNode> nodes) {
        // 数据验证
        Assert.notNull(snapshot, "参数'snapshot'不能为空");
        Assert.hasText(snapshot.getTitle(), "标题不能为空");
        Assert.hasText(snapshot.getTraceId(), "参数'snapshot.traceId'不能为空");
        Assert.notEmpty(nodes, "参数'nodes'内容不能为空");


        saveTraceNode(nodes);
        if (snapshot.getDirectory() == null) {
            snapshot.setDirectory("root");
        }
        snapshot.setUpdateTime(new Date());
        snapshot.setCreateTime(new Date());
        snapshot.setVersion("1.0");
        snapshot.setVersionLastUpdate(new Date());
        snapshot.setPrincipals(new String[]{userId});
        snapshot.setProjectId(projectId);

        // 添加创建日志
        ChangeLog log = new ChangeLog();
        log.setTime(new Date());
        log.setType("create");
        log.setContent("创建当前快照");
        log.setUserId(userId);
        snapshot.setChangeLogs(new ChangeLog[]{log});

        snapshot.setComments(new Comment[0]);

        //解析SQL 统计
        Sql[] sqls = nodes.stream()
                .filter(a -> a instanceof SqlTraceNode) //过滤
                .map(a -> buildSql((SqlTraceNode) a))  // 转换
                .collect(Collectors.toMap(k -> k.getContent(), v -> v, (value1, value2) -> { // 基于SQL语句去重
                    value1.setCount(value1.getCount() + value2.getCount());
                    return value1;
                }))
                .values().toArray(new Sql[0]);  //  采集
        if (sqls != null && sqls.length > 0) {
            if ("mysql".equalsIgnoreCase(sqls[0].getDatabaseType())) {
                snapshot.setSqls(sqls);
            }
        }

        Sql[] cksqls = nodes.stream()
                .filter(a -> a instanceof CKSqlTraceNode) //过滤
                .map(a -> buildCKSql((CKSqlTraceNode) a))  // 转换
                .collect(Collectors.toMap(k -> k.getContent(), v -> v, (value1, value2) -> { // 基于SQL语句去重
                    value1.setCount(value1.getCount() + value2.getCount());
                    return value1;
                }))
                .values().toArray(new Sql[0]);  //  采集
        if (cksqls != null && cksqls.length > 0) {
            if ("clickhouse".equalsIgnoreCase(cksqls[0].getDatabaseType())) {
                snapshot.setSqls(cksqls);
            }
        }


        // 解析封装 执行源码
        String[] codes = nodes.stream()
                .filter(a -> a instanceof CodeNodeBean)// 过滤
                .flatMap(a -> Arrays.stream(((CodeNodeBean) a).getCodeNodes())) // 转换
                .map(a -> buildSrc(a))// 转换
                .distinct()//去重
                .collect(Collectors.toList()) // 采集
                .toArray(new String[0]);
        snapshot.setCodes(codes);

        // 解析封装 远程调用
        Remote[] remotes = nodes.stream()
                .filter(a -> a instanceof DubboTraceNode)
                .map(a -> buildRemote((DubboTraceNode) a, nodes))
                .collect(Collectors.toList())
                .toArray(new Remote[0]); // 过滤
        snapshot.setRemotes(remotes);

        return repository.save(snapshot);
    }

    @Override
    public SystemSnapshot saveBasic(String projectId, String userId, SystemSnapshot source) {
        SystemSnapshot oldSnapshot =
                repository.findById(source.getId()).orElseThrow(() -> new IllegalArgumentException("找不到要保存的快照" + source.getId()));
        if (source.getTitle() != null) {
            oldSnapshot.setTitle(source.getTitle());
        }
        if (source.getDescribe() != null) {
            oldSnapshot.setDescribe(source.getDescribe());
        }
        if (source.getVersion() != null) {
            oldSnapshot.setVersion(source.getVersion());
            oldSnapshot.setVersionLastUpdate(new Date());
        }
        if (source.getVersionCycle() != null) {
            oldSnapshot.setVersionCycle(source.getVersionCycle());
        }
        if (source.getLabels() != null) {
            oldSnapshot.setLabels(source.getLabels());
        }
        if (source.getPrincipals() != null) {
            oldSnapshot.setPrincipals(source.getPrincipals());
        }
        if (source.getTopicImage() != null) {
            oldSnapshot.setTopicImage(source.getTopicImage());
        }
        oldSnapshot.setUpdateTime(new Date());
        return repository.save(oldSnapshot);
    }

    //TODO 远程调用节点暂时只构建Dubbo
    private Remote buildRemote(DubboTraceNode dubboNode, Collection<TraceNode> nodes) {
        Remote remote = new Remote();
        remote.setType("dubbo");
        remote.setUrl(dubboNode.getRemoteUrl());
        remote.setInvokerInterface(dubboNode.getServiceInterface() + "#" + dubboNode.getServiceMethodName());
        nodes.stream().filter(a -> a.getTraceNodeId().equals(dubboNode.getTraceNodeId() + ".remote"))
                .findAny().ifPresent(
                        a -> remote.setAppId(a.getApp().getAppId())
                );
        return remote;
    }

    private String buildSrc(@NotNull StackNodeVo stackNodeVo) {
        String result = stackNodeVo.getClassName().replace('/', '.');
        String methodName = Optional.ofNullable(stackNodeVo.getMethodName()).orElse("").trim();
        if (!StringUtils.hasText(methodName)) {
            return result;
        }
        return result + " " + methodName;
    }

    private Sql buildSql(SqlTraceNode node) {
        Sql sql = new Sql();
        sql.setContent(node.getSql());
        sql.setDatabase(node.getDatabase().getName());
        sql.setDatabaseType(node.getDatabase().getType());
        sql.setCount(1);
        // 解析执行Action
        SqlStatParse parse = new SqlStatParse(node.getSql(), node.getDatabase().getType());
        Sql.Action[] actions = parse.getAll().stream()
                .map(n -> new Sql.Action(n.getModel(), n.getTableName()))
                .collect(Collectors.toList())
                .toArray(new Sql.Action[0]);
        sql.setActions(actions);
        return sql;
    }

    private Sql buildCKSql(CKSqlTraceNode node) {
        Sql sql = new Sql();
        sql.setContent(node.getSql());
        sql.setDatabase(node.getDatabase().getName());
        sql.setDatabaseType(node.getDatabase().getType());
        sql.setCount(1);
        // 解析执行Action
        SqlStatParse parse = new SqlStatParse(node.getSql(), node.getDatabase().getType());
        Sql.Action[] actions = parse.getAll().stream()
                .map(n -> new Sql.Action(n.getModel(), n.getTableName()))
                .collect(Collectors.toList())
                .toArray(new Sql.Action[0]);
        sql.setActions(actions);
        return sql;
    }

    private void saveTraceNode(Collection<TraceNode> nodes) {
        List<TraceNodeIndex> list = new ArrayList<>();
        for (TraceNode node : nodes) {
            list.add(new TraceNodeIndex(node));
        }

        // 批量保存 TraceNode。对同一个 traceId_nodeId 执行覆盖保存，避免旧节点缺失请求参数等字段。
        if (!list.isEmpty()) {
            traceNodeRepository.saveAll(list);
        }
    }

    @Override
    public List<SystemSnapshot> findBy(String projectId, String appId, String directory, String keyword) {
        Assert.notNull(projectId, "参数projectId不能为空");
        Assert.notNull(appId, "参数appId不能为空");
        Assert.notNull(directory, "参数directory不能为空");
        List<SystemSnapshot> list = repository.findByProjectIdAndAppIdAndDirectory(projectId, appId, directory);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim().toLowerCase();
            list = list.stream()
                    .filter(snapshot -> StringUtils.hasText(snapshot.getTitle()))
                    .filter(snapshot -> snapshot.getTitle().toLowerCase().contains(normalizedKeyword))
                    .collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<SystemSnapshot> findAll(String projectId, String appId) {
        Assert.notNull(projectId, "参数projectId不能为空");
        Assert.notNull(appId, "参数appId不能为空");
        List<SystemSnapshot> list = repository
                .findByProjectIdAndAppId(projectId, appId);
        return list;
    }

    @Async("coverageExecutor")
    @Override
    public void asyncCalculateCoverage(String snapshotId) {
        SystemSnapshot snapshot = getById(snapshotId);
        if (snapshot == null) return;

        try {
            snapshot.setReportStatus(1); // 生成中
            repository.save(snapshot);

            Collection<TraceNode> traceNodes = snapshotService.getTraceNodes(snapshot.getTraceId());

            // 加载全量静态数据，作为总数的数据源
            List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(snapshot.getAppId());
            // 构建 className -> (methodKey -> StaticSourceMethodInfo) 的查找表
            // methodKey = methodName + "#" + methodDesc
            Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup = new HashMap<>();
            for (StaticSourceInfo si : staticInfos) {
                if (si.getClassInfo() == null || si.getClassInfo().getMethodMaps() == null) continue;
                String className = si.getClassInfo().getClassName();
                Map<String, StaticSourceMethodInfo> methodMap = new HashMap<>();
                for (Map.Entry<String, StaticSourceMethodInfo> entry : si.getClassInfo().getMethodMaps().entrySet()) {
                    StaticSourceMethodInfo mInfo = entry.getValue();
                    String methodKey = mInfo.getMethodName() + "#" + mInfo.getMethodDesc();
                    methodMap.put(methodKey, mInfo);
                }
                staticMethodLookup.put(className, methodMap);
            }

            long totalLines = 0;
            long coveredLines = 0;
            long totalMethods = 0;
            long coveredMethods = 0;
            long totalBranches = 0;
            long coveredBranches = 0;
            int totalComplexity = 0;

            Set<String> classMethods = new HashSet<>();
            Map<String, Set<Integer>> methodTotalLinesMap = new HashMap<>();
            Map<String, Set<Integer>> methodCoveredLinesMap = new HashMap<>();
            Map<String, Set<Integer>> methodTotalBranchesMap = new HashMap<>();
            Map<String, Set<Integer>> methodCoveredBranchesMap = new HashMap<>();
            Map<String, Set<String>> methodTotalBranchTargetsMap = new HashMap<>();
            Map<String, Set<String>> methodCoveredBranchTargetsMap = new HashMap<>();
            Map<String, Integer> methodComplexityMap = new HashMap<>();

            for (TraceNode node : traceNodes) {
                if (node instanceof HttpTraceNode) {
                    StackNodeVo[] codeNodes = ((HttpTraceNode) node).getCodeNodes();
                    if (codeNodes != null) {
                        for (StackNodeVo sn : codeNodes) {
                            String methodKey = sn.getMethodName() + "#" + sn.getMethodDescriptor();
                            Map<String, StaticSourceMethodInfo> classMethodMap = staticMethodLookup.get(sn.getClassName());
                            if (classMethodMap == null) continue;

                            StaticSourceMethodInfo staticMethod = classMethodMap.get(methodKey);
                            if (staticMethod == null) continue;

                            classMethods.add(sn.getClassName());

                            // 从全量静态数据获取总数
                            methodTotalLinesMap.computeIfAbsent(methodKey, k -> new HashSet<>())
                                    .addAll(staticMethod.getMethodLineNumberMap() != null ? staticMethod.getMethodLineNumberMap() : Collections.emptyList());
                            if (sn.getDoLines() != null) {
                                methodCoveredLinesMap.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(sn.getDoLines());
                            }

                            methodComplexityMap.put(methodKey,
                                    staticMethod.getCyclomaticComplexityMap() != null ? staticMethod.getCyclomaticComplexityMap() : 0);

                            methodTotalBranchesMap.computeIfAbsent(methodKey, k -> new HashSet<>())
                                    .addAll(staticMethod.getBranchLineNumberSet() != null ? staticMethod.getBranchLineNumberSet() : Collections.emptyList());
                            addBranchTargetKeys(methodTotalBranchTargetsMap, methodKey,
                                    normalizeStaticBranchTargets(staticMethod.getBranchLineAndTargetProbeMap(),
                                            sn.getExecuteBranchTargetProbeMap()));
                            if (sn.getExecuteBranch() != null) {
                                methodCoveredBranchesMap.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(sn.getExecuteBranch());
                            }
                            addBranchTargetKeys(methodCoveredBranchTargetsMap, methodKey, sn.getExecuteBranchTargetProbeMap());
                            if (methodCoveredBranchTargetsMap.containsKey(methodKey)) {
                                Set<String> normalizedKeys = new LinkedHashSet<>();
                                Map<String, List<Integer>> normalizedStaticBranchTargets = normalizeStaticBranchTargets(
                                        staticMethod.getBranchLineAndTargetProbeMap(), sn.getExecuteBranchTargetProbeMap());
                                addBranchTargetKeysToSet(normalizedKeys, normalizedStaticBranchTargets,
                                        decodeBranchTargetKeys(methodCoveredBranchTargetsMap.get(methodKey)));
                                methodCoveredBranchTargetsMap.put(methodKey, normalizedKeys);
                            }
                        }
                    }
                }
            }

            long totalBranchTargets = 0;
            long coveredBranchTargets = 0;
            totalMethods = methodTotalLinesMap.size();
            for (String mKey : methodTotalLinesMap.keySet()) {
                totalLines += methodTotalLinesMap.get(mKey).size();
                coveredLines += methodCoveredLinesMap.getOrDefault(mKey, Collections.emptySet()).size();
                if (methodCoveredLinesMap.containsKey(mKey) && !methodCoveredLinesMap.get(mKey).isEmpty()) {
                    coveredMethods++;
                }
                totalComplexity += methodComplexityMap.getOrDefault(mKey, 0);
                totalBranches += methodTotalBranchesMap.getOrDefault(mKey, Collections.emptySet()).size();
                coveredBranches += methodCoveredBranchesMap.getOrDefault(mKey, Collections.emptySet()).size();
                totalBranchTargets += methodTotalBranchTargetsMap.getOrDefault(mKey, Collections.emptySet()).size();
                coveredBranchTargets += methodCoveredBranchTargetsMap.getOrDefault(mKey, Collections.emptySet()).size();
            }

            CoverageReportIndex report = new CoverageReportIndex();
            report.setAppId(snapshot.getAppId());
            report.setCreateTime(new Date());
            report.setTotalClasses(classMethods.size());
            report.setCoveredClasses(classMethods.size());
            report.setTotalMethods(totalMethods);
            report.setCoveredMethods(coveredMethods);
            report.setTotalLines(totalLines);
            report.setCoveredLines(coveredLines);
            report.setTotalBranches(totalBranches);
            report.setCoveredBranches(coveredBranches);
            report.setTotalBranchTargets(totalBranchTargets);
            report.setCoveredBranchTargets(coveredBranchTargets);
            report.setTotalComplexity(totalComplexity);

            snapshot.setCoverageReport(report);
            snapshot.setReportStatus(2); // 已完成
            repository.save(snapshot);
        } catch (Exception e) {
            logger.error("Error calculating coverage for snapshot: " + snapshotId, e);
            snapshot.setReportStatus(3); // 失败
            repository.save(snapshot);
        }
    }
    private void addBranchTargetKeys(Map<String, Set<String>> target,
                                     String methodKey,
                                     Map<String, List<Integer>> branchTargetProbeMap) {
        if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        for (Map.Entry<String, List<Integer>> entry : branchTargetProbeMap.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer branchTarget : entry.getValue()) {
                if (branchTarget != null) {
                    keys.add(entry.getKey() + "#" + branchTarget);
                }
            }
        }
    }

    private Map<String, List<Integer>> normalizeStaticBranchTargets(Map<String, List<Integer>> staticBranchTargets,
                                                                     Map<String, List<Integer>> executedBranchTargets) {
        if (staticBranchTargets == null || staticBranchTargets.isEmpty()) {
            return staticBranchTargets;
        }
        if (executedBranchTargets == null || executedBranchTargets.isEmpty()) {
            return staticBranchTargets;
        }

        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : staticBranchTargets.entrySet()) {
            String branchLine = entry.getKey();
            List<Integer> staticTargets = entry.getValue();
            List<Integer> executedTargets = executedBranchTargets.get(branchLine);
            if (staticTargets == null || staticTargets.isEmpty()) {
                continue;
            }
            if (executedTargets == null || executedTargets.isEmpty()) {
                normalized.put(branchLine, new ArrayList<>(new LinkedHashSet<>(staticTargets)));
                continue;
            }
            Set<Integer> staticSet = new LinkedHashSet<>(staticTargets);
            LinkedHashSet<Integer> executedSet = new LinkedHashSet<>(executedTargets);
            if (staticSet.containsAll(executedSet)) {
                normalized.put(branchLine, new ArrayList<>(executedSet));
            } else {
                normalized.put(branchLine, new ArrayList<>(staticSet));
            }
        }
        return normalized.isEmpty() ? staticBranchTargets : normalized;
    }

    private void addBranchTargetKeysToSet(Set<String> target,
                                          Map<String, List<Integer>> allowedBranchTargets,
                                          Map<String, List<Integer>> coveredBranchTargets) {
        if (allowedBranchTargets == null || allowedBranchTargets.isEmpty()
                || coveredBranchTargets == null || coveredBranchTargets.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : allowedBranchTargets.entrySet()) {
            List<Integer> allowedValues = entry.getValue();
            List<Integer> coveredValues = coveredBranchTargets.get(entry.getKey());
            if (allowedValues == null || allowedValues.isEmpty() || coveredValues == null || coveredValues.isEmpty()) {
                continue;
            }
            Set<Integer> allowed = new LinkedHashSet<>(allowedValues);
            for (Integer branchTarget : coveredValues) {
                if (branchTarget != null && allowed.contains(branchTarget)) {
                    target.add(entry.getKey() + "#" + branchTarget);
                }
            }
        }
    }

    private Map<String, List<Integer>> decodeBranchTargetKeys(Set<String> keys) {
        Map<String, List<Integer>> decoded = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return decoded;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            int split = key.lastIndexOf('#');
            if (split <= 0 || split >= key.length() - 1) {
                continue;
            }
            try {
                int branchTarget = Integer.parseInt(key.substring(split + 1));
                decoded.computeIfAbsent(key.substring(0, split), k -> new ArrayList<>()).add(branchTarget);
            } catch (NumberFormatException ignore) {
            }
        }
        return decoded;
    }
}

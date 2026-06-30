package com.oAT.web.service.impl;

import com.oAT.agent.model.*;
import com.oAT.web.common.SqlStatParse;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.domain.RemoteCallResolverService;
import com.oAT.web.domain.snapshot.SystemSnapshotCoverageCalculationService;
import com.oAT.web.esDao.SnapshotCommitMappingRepository;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.VersionCenterRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.service.AppService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.entity.AppVo;
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
    AppService appService;

    @Autowired
    RemoteCallResolverService remoteCallResolverService;

    @Autowired
    com.oAT.web.service.SnapshotService snapshotService;

    @Autowired
    VersionCenterRepository versionCenterRepository;

    @Autowired
    SnapshotCommitMappingRepository snapshotCommitMappingRepository;

    @Autowired
    com.oAT.web.coverage.CoverageStorage coverageStorage;

    @Autowired
    SystemSnapshotCoverageCalculationService systemSnapshotCoverageCalculationService;

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

        if (!StringUtils.hasText(snapshot.getDirectory())) {
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
                .filter(a -> a instanceof CodeNodeBean && ((CodeNodeBean) a).getCodeNodes() != null)
                .flatMap(a -> Arrays.stream(((CodeNodeBean) a).getCodeNodes()))
                .map(a -> buildSrc(a))
                .distinct()
                .collect(Collectors.toList())
                .toArray(new String[0]);
        snapshot.setCodes(codes);

        // 解析封装 远程调用
        RemoteCallResolver remoteCallResolver = remoteCallResolverService.build(projectId);
        Remote[] remotes = nodes.stream()
                .filter(this::isRemoteTraceNode)
                .map(a -> buildRemote(a, nodes, remoteCallResolver))
                .collect(Collectors.toList())
                .toArray(new Remote[0]); // 过滤
        snapshot.setRemotes(remotes);

        // 所有 nodes 数据已提取完毕，此时再写 ES + 对象存储，避免 codeNodes 提前清空
        saveTraceNode(nodes);

        SystemSnapshot saved = repository.save(snapshot);
        tryCreateCommitMapping(saved, "auto");
        return saved;
    }

    /**
     * 根据应用当前版本写入快照关联关系。
     * 策略：关联到应用设置的当前激活版本（App.currentVersion/currentBranch/currentCommitId）。
     * 一个快照只关联一个 Commit，若已存在则跳过。
     */
    private void tryCreateCommitMapping(SystemSnapshot snapshot, String source) {
        if (snapshot == null || !StringUtils.hasText(snapshot.getId())
                || !StringUtils.hasText(snapshot.getAppId())) {
            return;
        }
        try {
            if (snapshotCommitMappingRepository.existsBySnapshotId(snapshot.getId())) {
                return;
            }

            AppVo app = appService.getApp(snapshot.getAppId());
            if (app == null) {
                logger.info("快照 {} 对应应用不存在，跳过自动关联", snapshot.getId());
                return;
            }

            String currentCommitId = app.getCurrentCommitId();
            String currentVersion = app.getCurrentVersion();
            String currentBranch = app.getCurrentBranch();

            if (!StringUtils.hasText(currentCommitId)) {
                logger.info("快照 {} 对应应用 {} 未设置当前版本的 Commit，跳过自动关联，请在版本列表页手动补录",
                        snapshot.getId(), snapshot.getAppId());
                return;
            }

            SnapshotCommitMapping mapping = new SnapshotCommitMapping();
            mapping.setId(snapshot.getId());
            mapping.setSnapshotId(snapshot.getId());
            mapping.setAppId(snapshot.getAppId());
            mapping.setProjectId(snapshot.getProjectId());
            mapping.setVersionNumber(currentVersion);
            mapping.setRepoBranch(currentBranch);
            mapping.setRepoCommitId(currentCommitId);
            mapping.setCreateTime(new Date());
            mapping.setSnapshotCreateTime(snapshot.getCreateTime());
            mapping.setMappingSource(source);
            snapshotCommitMappingRepository.save(mapping);
            logger.info("快照 {} 已自动关联到应用当前版本 {} Commit {}（来源: {}）",
                    snapshot.getId(), currentVersion, currentCommitId, source);
        } catch (Exception e) {
            logger.warn("快照 {} 写入 Commit 关联失败，不影响快照保存", snapshot.getId(), e);
        }
    }

    /**
     * 手工补录：为当前应用下所有无关联快照，按应用当前版本补录关联关系。
     * 返回成功补录的数量。
     */
    @Override
    public int backfillCommitMapping(String appId, String versionNumber) {
        Assert.hasText(appId, "appId 不能为空");

        AppVo app = appService.getApp(appId);
        if (app == null) {
            logger.warn("补录 Commit 关联失败：应用 {} 不存在", appId);
            return 0;
        }

        String currentCommitId = app.getCurrentCommitId();
        String currentVersion = app.getCurrentVersion();
        String currentBranch = app.getCurrentBranch();

        if (!StringUtils.hasText(currentCommitId)) {
            logger.warn("补录 Commit 关联失败：应用 {} 未设置当前版本的 Commit", appId);
            return 0;
        }

        List<SystemSnapshot> snapshots = repository.findByAppId(appId);
        if (StringUtils.hasText(versionNumber)) {
            snapshots = snapshots.stream()
                    .filter(s -> versionNumber.equals(s.getVersion()))
                    .collect(java.util.stream.Collectors.toList());
        }

        int count = 0;
        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getId())) continue;
            if (snapshotCommitMappingRepository.existsBySnapshotId(snapshot.getId())) continue;

            SnapshotCommitMapping mapping = new SnapshotCommitMapping();
            mapping.setId(snapshot.getId());
            mapping.setSnapshotId(snapshot.getId());
            mapping.setAppId(snapshot.getAppId());
            mapping.setProjectId(snapshot.getProjectId());
            mapping.setVersionNumber(currentVersion);
            mapping.setRepoBranch(currentBranch);
            mapping.setRepoCommitId(currentCommitId);
            mapping.setCreateTime(new Date());
            mapping.setSnapshotCreateTime(snapshot.getCreateTime());
            mapping.setMappingSource("manual");
            snapshotCommitMappingRepository.save(mapping);
            count++;
        }
        logger.info("补录完成：appId={}, 应用当前版本={}, 补录数量={}, commitId={}",
                appId, currentVersion, count, currentCommitId);
        return count;
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

    private boolean isRemoteTraceNode(TraceNode traceNode) {
        return traceNode instanceof RemoteInvokeNode || traceNode instanceof HttpClientTraceNode;
    }

    private Remote buildRemote(TraceNode traceNode, Collection<TraceNode> nodes, RemoteCallResolver remoteCallResolver) {
        Remote remote = new Remote();
        remote.setType(traceNode.toType());
        if (traceNode instanceof DubboTraceNode) {
            DubboTraceNode dubboNode = (DubboTraceNode) traceNode;
            remote.setUrl(dubboNode.getRemoteUrl());
            remote.setInvokerInterface(dubboNode.getServiceInterface() + "#" + dubboNode.getServiceMethodName());
        } else if (traceNode instanceof FeignTraceNode) {
            FeignTraceNode feignNode = (FeignTraceNode) traceNode;
            remote.setUrl(feignNode.getRemoteUrl());
            remote.setInvokerInterface(feignNode.getRemoteFeignTargetName() + "#" + feignNode.getRemoteMethod());
        } else if (traceNode instanceof SofaRpcTraceNode) {
            SofaRpcTraceNode sofaRpcNode = (SofaRpcTraceNode) traceNode;
            remote.setUrl(sofaRpcNode.getDirectUrl());
            remote.setInvokerInterface(sofaRpcNode.getInterfaceName() + "#" + sofaRpcNode.getMethodName());
        } else if (traceNode instanceof HttpClientTraceNode) {
            HttpClientTraceNode httpClientNode = (HttpClientTraceNode) traceNode;
            remote.setUrl(httpClientNode.getServiceURL());
            remote.setInvokerInterface(httpClientNode.getServiceMethod() + " " + httpClientNode.getServiceURL());
        }
        if (traceNode instanceof RemoteInvokeNode && ((RemoteInvokeNode) traceNode).getRemoteApp() != null) {
            remote.setAppId(((RemoteInvokeNode) traceNode).getRemoteApp().getAppId());
        }
        if (!StringUtils.hasText(remote.getAppId())) {
            nodes.stream().filter(a -> a.getTraceNodeId().equals(traceNode.getTraceNodeId() + ".remote"))
                    .filter(a -> a.getApp() != null)
                    .findAny().ifPresent(a -> remote.setAppId(a.getApp().getAppId()));
        }
        if (!StringUtils.hasText(remote.getAppId())) {
            remoteCallResolver.resolve(traceNode, nodes).ifPresent(relation -> remote.setAppId(relation.getTargetAppId()));
        }
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
        // 在写入 ES 前，将所有 CodeNodeBean 的 codeNodes 提取到对象存储，
        // 然后清空字段，避免大数组写入 ES 导致存储膨胀
        for (TraceNode node : nodes) {
            if (node instanceof CodeNodeBean) {
                CodeNodeBean codeNodeBean = (CodeNodeBean) node;
                StackNodeVo[] codeNodes = codeNodeBean.getCodeNodes();
                if (codeNodes != null && codeNodes.length > 0) {
                    coverageStorage.asyncStore(node.getTraceId(), codeNodes);
                    codeNodeBean.setCodeNodes(null);
                }
            }
        }

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

    @Override
    public List<SystemSnapshot> findAll(String projectId) {
        Assert.notNull(projectId, "参数projectId不能为空");
        return repository.findByProjectId(projectId);
    }

    @Async("coverageExecutor")
    @Override
    public void asyncCalculateCoverage(String snapshotId) {
        systemSnapshotCoverageCalculationService.calculateCoverage(snapshotId);
    }
}

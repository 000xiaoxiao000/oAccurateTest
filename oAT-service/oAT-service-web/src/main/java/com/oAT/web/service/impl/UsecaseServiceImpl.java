package com.oAT.web.service.impl;

import com.oAT.agent.model.*;
import com.oAT.web.common.SqlParseInfo;
import com.oAT.web.common.SqlStatParse;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.DirtyDataException;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.entity.DirectoryDeleteResult;
import com.oAT.web.service.entity.UsecaseDetailVo;
import com.oAT.web.service.entity.UsecaseDirectoryVo;
import com.oAT.web.service.entity.UsecaseVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UsecaseServiceImpl implements UsecaseService {

    @Autowired
    CaseCenterRepository centerRepository;
    @Autowired
    TraceNodeRepository traceNodeRepository;
    @Autowired
    SystemSnapshotRepository systemSnapshotRepository;

    @Override
    public UsecaseVo doAdd(String author, UsecaseVo usecaseParam) {

        Usecase usecase = buildUsecaseRelations(usecaseParam.getSnapshots(), usecaseParam.getSystemSnapshots());
        // 设置基本信息
        BeanUtils.copyProperties(usecaseParam, usecase);
        usecase.setLastUpdateAuthor(author);
        usecase.setAuthors(new String[]{author});
        CaseCenterIndex caseCenterIndex = new CaseCenterIndex(usecase);
        CaseCenterIndex index = centerRepository.save(caseCenterIndex);
        return convertUsecase(index);
    }

    private Usecase buildUsecaseRelations(String[] snapshotIds, String[] systemSnapshotIds) {
        String[] validSnapshotIds = filterValidSnapshotIds(snapshotIds);
        String[] validSystemSnapshotIds = filterValidSystemSnapshotIds(systemSnapshotIds);
        boolean noSnapshots = ObjectUtils.isEmpty(validSnapshotIds);
        boolean noSystemSnapshots = ObjectUtils.isEmpty(validSystemSnapshotIds);
        if (noSnapshots && noSystemSnapshots) {
            return new Usecase();
        }

        Map<SqlTraceNode, String> sqlTraceNodes = new HashMap<>();
        Map<CKSqlTraceNode, String> cksqlTraceNodes = new HashMap<>();
        Map<DubboTraceNode, String> dubboTraceNodes = new HashMap<>();
        Map<RedisTraceNode, String> redisTraceNodes = new HashMap<>();
        List<StackNodeVo> codeNodes = new ArrayList<>();

        collectMySnapshotTraceNodes(validSnapshotIds, sqlTraceNodes, cksqlTraceNodes, dubboTraceNodes, redisTraceNodes, codeNodes);
        collectSystemSnapshotTraceNodes(validSystemSnapshotIds, sqlTraceNodes, cksqlTraceNodes, dubboTraceNodes, redisTraceNodes, codeNodes);

        Usecase usecase = new Usecase();
        usecase.setSql(parseSql(sqlTraceNodes));
        usecase.setSql(parseCKSql(cksqlTraceNodes));
        usecase.setRemote(parseRemote(dubboTraceNodes));
        usecase.setSrcStack(parseCoeStack(codeNodes));
        return usecase;
    }

    private String[] filterValidSnapshotIds(String[] snapshotIds) {
        if (ObjectUtils.isEmpty(snapshotIds)) {
            return new String[0];
        }
        List<String> validIds = new ArrayList<>();
        for (CaseCenterIndex snapshot : centerRepository.findAllById(Arrays.asList(snapshotIds))) {
            if (snapshot != null && snapshot.getSnapshot() != null) {
                validIds.add(snapshot.getId());
            }
        }
        return validIds.toArray(new String[0]);
    }

    private String[] filterValidSystemSnapshotIds(String[] systemSnapshotIds) {
        if (ObjectUtils.isEmpty(systemSnapshotIds)) {
            return new String[0];
        }
        List<String> validIds = new ArrayList<>();
        for (SystemSnapshot snapshot : systemSnapshotRepository.findAllById(Arrays.asList(systemSnapshotIds))) {
            if (snapshot != null && StringUtils.hasText(snapshot.getId())) {
                validIds.add(snapshot.getId());
            }
        }
        return validIds.toArray(new String[0]);
    }

    private void collectMySnapshotTraceNodes(String[] snapshotIds,
                                             Map<SqlTraceNode, String> sqlTraceNodes,
                                             Map<CKSqlTraceNode, String> cksqlTraceNodes,
                                             Map<DubboTraceNode, String> dubboTraceNodes,
                                             Map<RedisTraceNode, String> redisTraceNodes,
                                             List<StackNodeVo> codeNodes) {
        if (ObjectUtils.isEmpty(snapshotIds)) {
            return;
        }
        Iterable<CaseCenterIndex> snapshots = centerRepository.findAllById(Arrays.asList(snapshotIds));
        for (CaseCenterIndex snapshot : snapshots) {
            if (snapshot == null || snapshot.getSnapshot() == null) {
                continue;
            }
            collectTraceNodes(snapshot.getSnapshot().getTraceId(), snapshot.getId(), sqlTraceNodes, cksqlTraceNodes, dubboTraceNodes, redisTraceNodes, codeNodes);
        }
    }

    private void collectSystemSnapshotTraceNodes(String[] systemSnapshotIds,
                                                 Map<SqlTraceNode, String> sqlTraceNodes,
                                                 Map<CKSqlTraceNode, String> cksqlTraceNodes,
                                                 Map<DubboTraceNode, String> dubboTraceNodes,
                                                 Map<RedisTraceNode, String> redisTraceNodes,
                                                 List<StackNodeVo> codeNodes) {
        if (ObjectUtils.isEmpty(systemSnapshotIds)) {
            return;
        }
        Iterable<SystemSnapshot> snapshots = systemSnapshotRepository.findAllById(Arrays.asList(systemSnapshotIds));
        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            collectTraceNodes(snapshot.getTraceId(), snapshot.getId(), sqlTraceNodes, cksqlTraceNodes, dubboTraceNodes, redisTraceNodes, codeNodes);
        }
    }

    private void collectTraceNodes(String traceId,
                                   String relationId,
                                   Map<SqlTraceNode, String> sqlTraceNodes,
                                   Map<CKSqlTraceNode, String> cksqlTraceNodes,
                                   Map<DubboTraceNode, String> dubboTraceNodes,
                                   Map<RedisTraceNode, String> redisTraceNodes,
                                   List<StackNodeVo> codeNodes) {
        List<TraceNodeIndex> nodeIndexs = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 200));
        if (CollectionUtils.isEmpty(nodeIndexs)) {
            throw new DirtyDataException("找不到Trace Node traceId=" + traceId);
        }
        for (TraceNodeIndex nodeIndex : nodeIndexs) {
            TraceNode node = nodeIndex.toTraceNode();
            if (node instanceof SqlTraceNode) {
                sqlTraceNodes.put((SqlTraceNode) node, relationId);
            } else if (node instanceof CKSqlTraceNode) {
                cksqlTraceNodes.put((CKSqlTraceNode) node, relationId);
            } else if (node instanceof DubboTraceNode) {
                dubboTraceNodes.put((DubboTraceNode) node, relationId);
            } else if (node instanceof RedisTraceNode) {
                redisTraceNodes.put((RedisTraceNode) node, relationId);
            } else if (node instanceof CodeNodeBean && ((CodeNodeBean) node).getCodeNodes() != null) {
                codeNodes.addAll(Arrays.asList(((CodeNodeBean) node).getCodeNodes()));
            }
        }
    }

    private String[] parseCoeStack(List<StackNodeVo> nodeVos) {
        Set<String> result = new HashSet<>(nodeVos.size());
        for (StackNodeVo nodeVo : nodeVos) {
            if (nodeVo == null) {
                continue;
            }
            result.add(nodeVo.getClassName() + " " + nodeVo.getMethodName());
        }
        return result.toArray(new String[0]);
    }

    private UsecaseSql parseSql(Map<SqlTraceNode, String> nods) {
        UsecaseSql result = new UsecaseSql();
        List<String> sqls = new ArrayList<>();
        List<UsecaseSql.SqlAction> inserts = new ArrayList<>();
        List<UsecaseSql.SqlAction> updates = new ArrayList<>();
        List<UsecaseSql.SqlAction> deletes = new ArrayList<>();
        List<UsecaseSql.SqlAction> selects = new ArrayList<>();
        List<UsecaseSql.SqlAction> drops = new ArrayList<>();
        List<UsecaseSql.SqlAction> creates = new ArrayList<>();

        for (Map.Entry<SqlTraceNode, String> entry : nods.entrySet()) {
            SqlTraceNode sqlNode = entry.getKey();
            String snapshot = entry.getValue();
            if (sqls.contains(sqlNode.getSql())) {
                continue;
            }
            //${db_type} ${db_name}  ${sql} 格式拼装sql内容
            sqls.add(sqlNode.getDatabase().getType() + " " + sqlNode.getDatabase().getName() + " " + sqlNode.getSql());
            SqlStatParse parse = new SqlStatParse();
            parse.addSql(sqlNode.getSql(), sqlNode.getDatabase().getType());
            String databaseName = sqlNode.getDatabase().getName();
            int sqlIndex = sqls.size() - 1;
            inserts.addAll(buildSqlAction(sqlIndex, parse.getAdds(), databaseName, snapshot));
            updates.addAll(buildSqlAction(sqlIndex, parse.getUpdates(), databaseName, snapshot));
            deletes.addAll(buildSqlAction(sqlIndex, parse.getDeletes(), databaseName, snapshot));
            selects.addAll(buildSqlAction(sqlIndex, parse.getSelects(), databaseName, snapshot));
            drops.addAll(buildSqlAction(sqlIndex, parse.getDrops(), databaseName, snapshot));
            creates.addAll(buildSqlAction(sqlIndex, parse.getCreates(), databaseName, snapshot));
        }

        result.setContents(sqls.toArray(new String[0]));
        // UsecaseSql.SqlAction  可能会重复
        result.setInserts(inserts.toArray(new UsecaseSql.SqlAction[0]));
        result.setUpdates(updates.toArray(new UsecaseSql.SqlAction[0]));
        result.setSelects(selects.toArray(new UsecaseSql.SqlAction[0]));
        result.setDeletes(deletes.toArray(new UsecaseSql.SqlAction[0]));
        result.setDrops(drops.toArray(new UsecaseSql.SqlAction[0]));
        result.setCreates(creates.toArray(new UsecaseSql.SqlAction[0]));
        return result;
    }

    private UsecaseSql parseCKSql(Map<CKSqlTraceNode, String> nods) {
        UsecaseSql result = new UsecaseSql();
        List<String> sqls = new ArrayList<>();
        List<UsecaseSql.SqlAction> inserts = new ArrayList<>();
        List<UsecaseSql.SqlAction> updates = new ArrayList<>();
        List<UsecaseSql.SqlAction> deletes = new ArrayList<>();
        List<UsecaseSql.SqlAction> selects = new ArrayList<>();
        List<UsecaseSql.SqlAction> drops = new ArrayList<>();
        List<UsecaseSql.SqlAction> creates = new ArrayList<>();

        for (Map.Entry<CKSqlTraceNode, String> entry : nods.entrySet()) {
            CKSqlTraceNode sqlNode = entry.getKey();
            String snapshot = entry.getValue();
            if (sqls.contains(sqlNode.getSql())) {
                continue;
            }
            //${db_type} ${db_name}  ${sql} 格式拼装sql内容
            sqls.add(sqlNode.getDatabase().getType() + " " + sqlNode.getDatabase().getName() + " " + sqlNode.getSql());
            SqlStatParse parse = new SqlStatParse();
            parse.addSql(sqlNode.getSql(), sqlNode.getDatabase().getType());
            String databaseName = sqlNode.getDatabase().getName();
            int sqlIndex = sqls.size() - 1;
            inserts.addAll(buildSqlAction(sqlIndex, parse.getAdds(), databaseName, snapshot));
            updates.addAll(buildSqlAction(sqlIndex, parse.getUpdates(), databaseName, snapshot));
            deletes.addAll(buildSqlAction(sqlIndex, parse.getDeletes(), databaseName, snapshot));
            selects.addAll(buildSqlAction(sqlIndex, parse.getSelects(), databaseName, snapshot));
            drops.addAll(buildSqlAction(sqlIndex, parse.getDrops(), databaseName, snapshot));
            creates.addAll(buildSqlAction(sqlIndex, parse.getCreates(), databaseName, snapshot));
        }

        result.setContents(sqls.toArray(new String[0]));
        // UsecaseSql.SqlAction  可能会重复
        result.setInserts(inserts.toArray(new UsecaseSql.SqlAction[0]));
        result.setUpdates(updates.toArray(new UsecaseSql.SqlAction[0]));
        result.setSelects(selects.toArray(new UsecaseSql.SqlAction[0]));
        result.setDeletes(deletes.toArray(new UsecaseSql.SqlAction[0]));
        result.setDrops(drops.toArray(new UsecaseSql.SqlAction[0]));
        result.setCreates(creates.toArray(new UsecaseSql.SqlAction[0]));
        return result;
    }

    private List<UsecaseSql.SqlAction> buildSqlAction(int sqlIndex, List<SqlParseInfo> sqlInfos, String databaseName, String snapshot) {
        List<UsecaseSql.SqlAction> result = new ArrayList<>();
        for (SqlParseInfo sqlInfo : sqlInfos) {
            for (String column : sqlInfo.getColumns()) {
                UsecaseSql.SqlAction action = new UsecaseSql.SqlAction();
                action.setName(databaseName + "." + sqlInfo.getTableName() + "." + column);
                action.setSnapshot(snapshot);
                action.setIndex(sqlIndex);
                result.add(action);
            }
        }
        return result;
    }

    // 仅完成dubbo 的解析，还应包括其它远程调用
    public UsecaseRemote parseRemote(Map<DubboTraceNode, String> dubboNodes) {
        UsecaseRemote remote = new UsecaseRemote();
        List<String> serviceInterface = new ArrayList<>(dubboNodes.size());
        List<String> contents = new ArrayList<>(dubboNodes.size());
        DubboTraceNode node;
        String interfaceName, content;
        for (Map.Entry<DubboTraceNode, String> entry : dubboNodes.entrySet()) {
            node = entry.getKey();
            interfaceName = node.getServiceInterface() + "." + node.getServiceMethodName();
            content = node.getRemoteUrl();
            if (!serviceInterface.contains(interfaceName)) {
                serviceInterface.add(interfaceName);
            }
            if (!contents.contains(content)) {
                contents.add(content);
            }
        }
        remote.setDubbo(serviceInterface.toArray(new String[0]));
        remote.setContent(contents.toArray(new String[0]));
        return remote;
    }

    @Override
    public List<UsecaseVo> getUsecases(String projectId, String directory, String sort, String keyword) {
        Assert.notNull(projectId, "param 'projectId' must be not null");
        Assert.notNull(directory, "param 'directory' must be not null");

        if ("name".equals(sort)) {
            sort = "usecase.title.keyword";
        }

        List<CaseCenterIndex> list = centerRepository.findByUsecase_ProjectIdAndAndUsecase_Directory(projectId, directory, PageRequest.of(0, 100, Sort.Direction.DESC, sort));
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim().toLowerCase();
            list = list.stream()
                    .filter(item -> item.getUsecase() != null && StringUtils.hasText(item.getUsecase().getTitle()))
                    .filter(item -> item.getUsecase().getTitle().toLowerCase().contains(normalizedKeyword))
                    .collect(Collectors.toList());
        }
        List<UsecaseVo> result = new ArrayList<>();
        for (CaseCenterIndex caseCenterIndex : list) {
            result.add(convertUsecase(caseCenterIndex));
        }
        return result;
    }

    @Override
    public UsecaseVo getUsecase(String projectId, String id) {
        Optional<CaseCenterIndex> optional = centerRepository.findById(id);
        Assert.isTrue(optional.isPresent(), "找不到指定用例 id=" + id);
        Assert.notNull(optional.get().getUsecase(), "not found usecase by id id=" + id);
        Assert.isTrue(optional.get().getUsecase().getProjectId().equals(projectId), "the usecase not belong to project Id=" + projectId);
        return convertUsecase(optional.get());
    }

    @Override
    public List<UsecaseVo> getUsecases(String projectId, String[] ids) {
        List<UsecaseVo> result = new ArrayList<>(ids.length);
        Iterable<CaseCenterIndex> cases = centerRepository.findAllById(Arrays.asList(ids));
        for (CaseCenterIndex aCase : cases) {
            Assert.isTrue(aCase.getUsecase().getProjectId().equals(projectId), "the usecase not belong to project Id=" + projectId);
            result.add(convertUsecase(aCase));
        }
        return result;
    }

    @Override
    public UsecaseDetailVo getUsecaseDetail(String projectId, String id) {
        Optional<CaseCenterIndex> optional = centerRepository.findById(id);
        Assert.isTrue(optional.isPresent(), "not found usecase by id id=" + id);
        Assert.notNull(optional.get().getUsecase(), "not found usecase by id id=" + id);
        Assert.isTrue(optional.get().getUsecase().getProjectId().equals(projectId), "the usecase not belong to project Id=" + projectId);
        return convertUsecaseDetail(optional.get());
    }

    private UsecaseDetailVo convertUsecaseDetail(CaseCenterIndex caseCenterIndex) {
        UsecaseDetailVo detailVo = new UsecaseDetailVo();
        BeanUtils.copyProperties(caseCenterIndex.getUsecase(), detailVo);
        detailVo.setId(caseCenterIndex.getId());
        detailVo.setCreateTime(caseCenterIndex.getCreateTime());
        detailVo.setUpdateTime(caseCenterIndex.getUpdateTime());
        UsecaseSql usecaseSql = caseCenterIndex.getUsecase().getSql();
        if (usecaseSql != null) {
            detailVo.setSqls(convertUsecaseSql(usecaseSql));
        }
        return detailVo;
    }

    private UsecaseDetailVo.UsecaseDetaiSql[] convertUsecaseSql(UsecaseSql usecaseSql) {
        int firstIndex, secondIndex;
        String dbType, dbName, sql;
        List<UsecaseDetailVo.UsecaseDetaiSql> detaiSqls = new ArrayList<>(usecaseSql.getContents().length);
        // sql content 格式：${db_type} ${db_name}  ${sql}
        for (String content : usecaseSql.getContents()) {
            firstIndex = content.indexOf(" ");
            secondIndex = content.indexOf(" ", firstIndex + 1);
            dbType = content.substring(0, firstIndex);
            dbName = content.substring(firstIndex + 1, secondIndex);
            sql = content.substring(secondIndex + 1, content.length());
            detaiSqls.add(new UsecaseDetailVo.UsecaseDetaiSql(dbType, dbName, sql));
        }
        return detaiSqls.toArray(new UsecaseDetailVo.UsecaseDetaiSql[0]);
    }

    /**
     * 创建用例目录
     *
     * @return
     */
    @Override
    public UsecaseDirectoryVo createFolder(String projectId, String parentId, String name) {
        UsecaseDirectory directory = new UsecaseDirectory();
        directory.setName(name);
        directory.setParentId(parentId);
        directory.setProjectId(projectId);
        directory.setChildId(new String[0]);
        CaseCenterIndex index = new CaseCenterIndex(directory);
        index = centerRepository.save(index);
        String currentId = index.getId();
        if (!"root".equalsIgnoreCase(parentId)) {
            index = centerRepository.findById(parentId).orElseThrow(() -> new IllegalArgumentException("找不到应用 parentId=" + parentId));
            String[] s1 = index.getDirectory().getChildId();
            int length1 = s1.length;
            String[] s2 = new String[length1 + 1];
            for (int i = 0; i < length1; i++) {
                s2[i] = s1[i];
            }
            s2[length1] = currentId;
            index.getDirectory().setChildId(s2);
        }
        index = centerRepository.save(index);
        return convertDirectory(index);
    }

    /**
     * 查找指定目录下的目录
     *
     * @param projectId
     * @param parentId
     * @return
     */
    @Override
    public List<UsecaseDirectoryVo> getDirectory(String projectId, String parentId) {
        List<CaseCenterIndex> list = centerRepository.findByDirectory_ProjectIdAndDirectory_ParentId(projectId, parentId);
        List<UsecaseDirectoryVo> result = new ArrayList<>();
        for (CaseCenterIndex caseCenterIndex : list) {
            result.add(convertDirectory(caseCenterIndex));
        }
        return result;
    }

    @Override
    public List<UsecaseDirectoryVo> getDirectoryTier(String projectId, String directory) {
        Assert.hasText(projectId, "param 'project' must be not null");
        Assert.hasText(directory, "param 'directory' must be not null");
        Assert.isTrue(!directory.equalsIgnoreCase("root"), "root node not exist tier");
        // 查找当前项目下所有目录信息
        List<CaseCenterIndex> list = centerRepository.findByDirectory_ProjectId(projectId);
        Map<String, UsecaseDirectoryVo> map = new HashMap<>();
        List<UsecaseDirectoryVo> result = new ArrayList<>();
        for (CaseCenterIndex caseCenterIndex : list) {
            map.put(caseCenterIndex.getId(), convertDirectory(caseCenterIndex));
        }
        UsecaseDirectoryVo vo = map.get(directory);
        result.add(vo);
        while (!vo.getParentId().equals("root")) {
            if (!map.containsKey(vo.getParentId())) {
                throw new DirtyDataException(String.format("Usecase directory id=%s name=%s parent id id not found", vo.getId(), vo.getName()));
            }
            vo = map.get(vo.getParentId());
            result.add(vo);
        }
        return result;
    }

    /**
     * 更新用例路径
     */
    @Override
    public void updateFolder(String id, String parentId, String name) {
        Optional<CaseCenterIndex> optional = centerRepository.findById(id);
        Assert.isTrue(optional.isPresent(), "not found usecase directory by directory id=" + id);
        CaseCenterIndex index = optional.get();
        index.getDirectory().setParentId(parentId);
        index.getDirectory().setName(name);
        index.setUpdateTime(new java.util.Date());
        centerRepository.save(index);
    }

    /**
     * 删除目录
     */
    @Override
    public Boolean delFolder(String projectId, String directoryId, String parentId, String name) {
        //目录下有子目录或用例，不可删除
        String[] childId = centerRepository.findById(directoryId).get().getDirectory().getChildId();
        Usecase usecase = centerRepository.findById(directoryId).get().getUsecase();
        if (childId.length > 0) {
            return false;
        }
        if (usecase != null) {
            return false;
        }

        //删除上一级目录中关联的子目录
        if(!"root".equals(parentId)){
            String[] parentChildId = centerRepository.findById(parentId).get().getDirectory().getChildId();
            List<String> parentChildIdList = new ArrayList<>(Arrays.asList(parentChildId));
            parentChildIdList.remove(directoryId);
            String[] pChildId = parentChildIdList.toArray(new String[parentChildIdList.size()]);

            CaseCenterIndex index = centerRepository.findById(parentId).get();
            index.getDirectory().setChildId(pChildId);
            centerRepository.save(index);
        }

        centerRepository.deleteById(directoryId);
        return true;
    }

    /**
     * 更新用例
     *
     * @param author       更新作者
     * @param usecaseParam 更新的信息
     */
    @Override
    public void doUpdate(String author, UsecaseVo usecaseParam) {
        Optional<CaseCenterIndex> optional = centerRepository.findById(usecaseParam.getId());
        Assert.isTrue(optional.isPresent(), "not found usecase Id id=" + usecaseParam.getId());
        CaseCenterIndex index = optional.get();
        // 更新快照中包含的信息
        if (ObjectUtils.isEmpty(usecaseParam.getSnapshots()) && ObjectUtils.isEmpty(usecaseParam.getSystemSnapshots())) {
            index.getUsecase().setRemote(null);
            index.getUsecase().setSql(null);
            index.getUsecase().setSrcStack(null);
        } else {
            Usecase usecase = buildUsecaseRelations(usecaseParam.getSnapshots(), usecaseParam.getSystemSnapshots());
            index.getUsecase().setRemote(usecase.getRemote());
            index.getUsecase().setSql(usecase.getSql());
            index.getUsecase().setSrcStack(usecase.getSrcStack());
        }

        // 复制属性
        BeanUtils.copyProperties(usecaseParam, index.getUsecase(), "authors", "projectId", "id");
        // 添加作者
        List<String> authorList = new ArrayList<>(Arrays.asList(Optional.ofNullable(index.getUsecase().getAuthors()).orElse(new String[0])));
        if (!authorList.contains(author)) {
            authorList.add(author);
        }
        index.getUsecase().setAuthors(authorList.toArray(new String[0]));
        index.getUsecase().setLastUpdateAuthor(author);
        // 更新修改时间
        index.setUpdateTime(new java.util.Date());
        centerRepository.save(index);
    }

    /**
     * 基于ID删除指定用例
     *
     * @param projectId
     * @param id
     */
    @Override
    public void doDeleteUsecase(String projectId, String id) {
        Optional<CaseCenterIndex> op = centerRepository.findById(id);
        Assert.isTrue(op.isPresent(), "not found usecase by id=" + id);
        Assert.notNull(op.get().getUsecase(), "not found usecase by id=" + id);
        Assert.isTrue(op.get().getUsecase().getProjectId().equals(projectId), "the usecase not belong to project Id=" + projectId);
        centerRepository.deleteById(id);
    }

    @Override
    public int countUsecasesInDirectory(String projectId, String directoryId) {
        Assert.hasText(projectId, "param 'projectId' must be not null");
        Assert.hasText(directoryId, "param 'directoryId' must be not null");
        return collectDirectoryDeleteStats(projectId, directoryId).usecaseCount;
    }

    @Override
    public int countDirectoryDescendants(String projectId, String directoryId) {
        Assert.hasText(projectId, "param 'projectId' must be not null");
        Assert.hasText(directoryId, "param 'directoryId' must be not null");
        DirectoryDeleteStats stats = collectDirectoryDeleteStats(projectId, directoryId);
        return Math.max(stats.directoryIds.size() - 1, 0);
    }

    @Override
    public int deleteDirectoryWithUsecases(String projectId, String directoryId, String parentId, String name) {
        Assert.hasText(projectId, "param 'projectId' must be not null");
        Assert.hasText(directoryId, "param 'directoryId' must be not null");
        Assert.hasText(parentId, "param 'parentId' must be not null");
        Assert.hasText(name, "param 'name' must be not null");

        DirectoryDeleteStats stats = collectDirectoryDeleteStats(projectId, directoryId);
        for (String usecaseId : stats.usecaseIds) {
            centerRepository.deleteById(usecaseId);
        }
        for (int i = stats.directoryIds.size() - 1; i >= 0; i--) {
            String currentDirectoryId = stats.directoryIds.get(i);
            CaseCenterIndex currentDirectoryIndex = centerRepository.findById(currentDirectoryId).orElse(null);
            if (currentDirectoryIndex == null || currentDirectoryIndex.getDirectory() == null) {
                continue;
            }
            unlinkDirectoryFromParent(currentDirectoryIndex.getDirectory().getParentId(), currentDirectoryId);
            centerRepository.deleteById(currentDirectoryId);
        }
        return stats.usecaseCount;
    }

    @Override
    public DirectoryDeleteResult previewDeleteDirectory(String projectId, String directoryId) {
        Assert.hasText(projectId, "param 'projectId' must be not null");
        Assert.hasText(directoryId, "param 'directoryId' must be not null");
        DirectoryDeleteResult result = new DirectoryDeleteResult();
        result.setDirectoryCount(countDirectoryDescendants(projectId, directoryId));
        result.setUsecaseCount(countUsecasesInDirectory(projectId, directoryId));
        result.setRequiresCascade(result.getDirectoryCount() > 0 || result.getUsecaseCount() > 0);
        result.setDeleted(false);
        if (result.isRequiresCascade()) {
            StringBuilder message = new StringBuilder("该目录删除前需要确认级联删除");
            if (result.getDirectoryCount() > 0 || result.getUsecaseCount() > 0) {
                message.append("：");
                if (result.getDirectoryCount() > 0) {
                    message.append(result.getDirectoryCount()).append("个子目录");
                }
                if (result.getUsecaseCount() > 0) {
                    if (result.getDirectoryCount() > 0) {
                        message.append("，");
                    }
                    message.append(result.getUsecaseCount()).append("个用例");
                }
                message.append(" 将一并删除");
            }
            result.setMessage(message.toString());
        } else {
            result.setMessage("目录删除预检成功");
        }
        return result;
    }

    @Override
    public DirectoryDeleteResult deleteDirectory(String projectId, String directoryId, String parentId, String name, boolean cascade) {
        Assert.hasText(projectId, "param 'projectId' must be not null");
        Assert.hasText(directoryId, "param 'directoryId' must be not null");
        Assert.hasText(parentId, "param 'parentId' must be not null");
        Assert.hasText(name, "param 'name' must be not null");

        DirectoryDeleteResult result = previewDeleteDirectory(projectId, directoryId);
        if (result.isRequiresCascade() && !cascade) {
            return result;
        }
        if (result.isRequiresCascade()) {
            int deletedUsecaseCount = deleteDirectoryWithUsecases(projectId, directoryId, parentId, name);
            result.setUsecaseCount(deletedUsecaseCount);
            result.setDeleted(true);
            StringBuilder message = new StringBuilder("目录删除成功");
            if (result.getDirectoryCount() > 0 || deletedUsecaseCount > 0) {
                message.append("，共删除");
                if (result.getDirectoryCount() > 0) {
                    message.append(result.getDirectoryCount()).append("个子目录");
                }
                if (deletedUsecaseCount > 0) {
                    if (result.getDirectoryCount() > 0) {
                        message.append("和");
                    }
                    message.append(deletedUsecaseCount).append("个用例");
                }
            }
            result.setMessage(message.toString());
            return result;
        }

        boolean deleted = delFolder(projectId, directoryId, parentId, name);
        result.setDeleted(deleted);
        result.setMessage(deleted ? "用例目录删除成功" : "用例目录不为空，删除失败");
        return result;
    }

    private DirectoryDeleteStats collectDirectoryDeleteStats(String projectId, String directoryId) {
        DirectoryDeleteStats stats = new DirectoryDeleteStats();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(directoryId);
        while (!stack.isEmpty()) {
            String currentDirectoryId = stack.pop();
            CaseCenterIndex directoryIndex = centerRepository.findById(currentDirectoryId).orElse(null);
            if (directoryIndex == null || directoryIndex.getDirectory() == null) {
                continue;
            }
            if (!projectId.equals(directoryIndex.getDirectory().getProjectId())) {
                continue;
            }
            if (!stats.visitedDirectoryIds.add(currentDirectoryId)) {
                continue;
            }
            stats.directoryIds.add(currentDirectoryId);

            List<CaseCenterIndex> usecases = centerRepository.findByUsecase_ProjectIdAndAndUsecase_Directory(projectId, currentDirectoryId, PageRequest.of(0, 1000));
            for (CaseCenterIndex usecaseIndex : usecases) {
                if (usecaseIndex == null || usecaseIndex.getUsecase() == null) {
                    continue;
                }
                if (stats.visitedUsecaseIds.add(usecaseIndex.getId())) {
                    stats.usecaseIds.add(usecaseIndex.getId());
                    stats.usecaseCount++;
                }
            }

            String[] childIds = directoryIndex.getDirectory().getChildId();
            if (childIds == null || childIds.length == 0) {
                continue;
            }
            for (int i = childIds.length - 1; i >= 0; i--) {
                String childId = childIds[i];
                if (StringUtils.hasText(childId)) {
                    stack.push(childId);
                }
            }
        }
        return stats;
    }

    private void unlinkDirectoryFromParent(String parentId, String directoryId) {
        if (!StringUtils.hasText(parentId) || "root".equals(parentId)) {
            return;
        }
        CaseCenterIndex parentIndex = centerRepository.findById(parentId).orElse(null);
        if (parentIndex == null || parentIndex.getDirectory() == null || parentIndex.getDirectory().getChildId() == null) {
            return;
        }
        List<String> parentChildIdList = new ArrayList<>(Arrays.asList(parentIndex.getDirectory().getChildId()));
        if (!parentChildIdList.remove(directoryId)) {
            return;
        }
        parentIndex.getDirectory().setChildId(parentChildIdList.toArray(new String[0]));
        centerRepository.save(parentIndex);
    }

    private static class DirectoryDeleteStats {
        private final List<String> directoryIds = new ArrayList<>();
        private final List<String> usecaseIds = new ArrayList<>();
        private final Set<String> visitedDirectoryIds = new HashSet<>();
        private final Set<String> visitedUsecaseIds = new HashSet<>();
        private int usecaseCount;
    }

    @Override
    public int rebuildUsecaseSearchData(String projectId, String operator) {
        Assert.hasText(projectId, "projectId不能为空");
        List<CaseCenterIndex> list = centerRepository.findByUsecase_ProjectId(projectId);
        int updated = 0;
        for (CaseCenterIndex index : list) {
            if (index == null || index.getUsecase() == null) {
                continue;
            }
            Usecase current = index.getUsecase();
            if (ObjectUtils.isEmpty(current.getSnapshots()) && ObjectUtils.isEmpty(current.getSystemSnapshots())) {
                continue;
            }
            Usecase rebuilt = buildUsecaseRelations(current.getSnapshots(), current.getSystemSnapshots());
            current.setRemote(rebuilt.getRemote());
            current.setSql(rebuilt.getSql());
            current.setSrcStack(rebuilt.getSrcStack());
            if (StringUtils.hasText(operator)) {
                current.setLastUpdateAuthor(operator);
                List<String> authors = new ArrayList<>(Arrays.asList(Optional.ofNullable(current.getAuthors()).orElse(new String[0])));
                if (!authors.contains(operator)) {
                    authors.add(operator);
                }
                current.setAuthors(authors.toArray(new String[0]));
            }
            index.setUpdateTime(new Date());
            centerRepository.save(index);
            updated++;
        }
        return updated;
    }

    @Override
    public void removeSnapshotRelation(String snapshotId) {
        if (!StringUtils.hasText(snapshotId)) {
            return;
        }
        for (CaseCenterIndex index : centerRepository.findByUsecaseIsNotNull()) {
            Usecase usecase = index.getUsecase();
            if (usecase == null || ObjectUtils.isEmpty(usecase.getSnapshots())) {
                continue;
            }
            String[] original = usecase.getSnapshots();
            String[] filtered = Arrays.stream(original)
                    .filter(id -> !snapshotId.equals(id))
                    .toArray(String[]::new);
            if (filtered.length != original.length) {
                usecase.setSnapshots(filtered);
                index.setUpdateTime(new Date());
                centerRepository.save(index);
            }
        }
    }

    @Override
    public void removeSystemSnapshotRelation(String systemSnapshotId) {
        if (!StringUtils.hasText(systemSnapshotId)) {
            return;
        }
        for (CaseCenterIndex index : centerRepository.findByUsecaseIsNotNull()) {
            Usecase usecase = index.getUsecase();
            if (usecase == null || ObjectUtils.isEmpty(usecase.getSystemSnapshots())) {
                continue;
            }
            String[] original = usecase.getSystemSnapshots();
            String[] filtered = Arrays.stream(original)
                    .filter(id -> !systemSnapshotId.equals(id))
                    .toArray(String[]::new);
            if (filtered.length != original.length) {
                usecase.setSystemSnapshots(filtered);
                index.setUpdateTime(new Date());
                centerRepository.save(index);
            }
        }
    }

    private UsecaseDirectoryVo convertDirectory(CaseCenterIndex index) {
        UsecaseDirectoryVo vo = new UsecaseDirectoryVo();
        BeanUtils.copyProperties(index.getDirectory(), vo);
        vo.setId(index.getId());
        vo.setUpdateTime(index.getUpdateTime());

        return vo;
    }

    private UsecaseVo convertUsecase(CaseCenterIndex caseCenterIndex) {
        UsecaseVo usecaseVo = new UsecaseVo();
        BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
        usecaseVo.setId(caseCenterIndex.getId());
        usecaseVo.setCreateTime(caseCenterIndex.getCreateTime());
        usecaseVo.setUpdateTime(caseCenterIndex.getUpdateTime());
        usecaseVo.setSnapshotCount(countExistingSnapshots(usecaseVo.getSnapshots()));
        usecaseVo.setSystemSnapshotCount(countExistingSystemSnapshots(usecaseVo.getSystemSnapshots()));
        return usecaseVo;
    }

    private int countExistingSnapshots(String[] snapshotIds) {
        if (ObjectUtils.isEmpty(snapshotIds)) {
            return 0;
        }
        int count = 0;
        for (CaseCenterIndex index : centerRepository.findAllById(Arrays.asList(snapshotIds))) {
            if (index != null && index.getSnapshot() != null) {
                count++;
            }
        }
        return count;
    }

    private int countExistingSystemSnapshots(String[] systemSnapshotIds) {
        if (ObjectUtils.isEmpty(systemSnapshotIds)) {
            return 0;
        }
        int count = 0;
        for (SystemSnapshot snapshot : systemSnapshotRepository.findAllById(Arrays.asList(systemSnapshotIds))) {
            if (snapshot != null) {
                count++;
            }
        }
        return count;
    }

}

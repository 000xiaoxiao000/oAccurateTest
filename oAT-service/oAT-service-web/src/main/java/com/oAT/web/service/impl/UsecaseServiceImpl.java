package com.oAT.web.service.impl;

import com.oAT.agent.model.*;
import com.oAT.web.common.SqlParseInfo;
import com.oAT.web.common.SqlStatParse;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.DirtyDataException;
import com.oAT.web.service.UsecaseService;
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

import java.util.*;

@Service
public class UsecaseServiceImpl implements UsecaseService {

    @Autowired
    CaseCenterRepository centerRepository;
    @Autowired
    TraceNodeRepository traceNodeRepository;

    @Override
    public UsecaseVo doAdd(String author, UsecaseVo usecaseParam) {

        Usecase usecase;
        if (ObjectUtils.isEmpty(usecaseParam.getSnapshots())) {
            usecase = new Usecase();
        } else {
            usecase = buildUsecaseBySnapshot(usecaseParam.getSnapshots());
        }
        // 设置基本信息
        BeanUtils.copyProperties(usecaseParam, usecase);
        usecase.setLastUpdateAuthor(author);
        usecase.setAuthors(new String[]{author});
        CaseCenterIndex caseCenterIndex = new CaseCenterIndex(usecase);
        CaseCenterIndex index = centerRepository.save(caseCenterIndex);
        return convertUsecase(index);
    }

    /**
     * 基于快照构建用例中的部分信息。内容包括:
     * <ul>
     * <li>sql</li>
     * <li>remote 远程调用</li>
     * <ul/>
     *
     * @param snapshotIds
     * @return
     */
    private Usecase buildUsecaseBySnapshot(String[] snapshotIds) {
        Iterable<CaseCenterIndex> snapshots = centerRepository.findAllById(Arrays.asList(snapshotIds));

        // 找出快照中所有的Sql节点
        Map<SqlTraceNode, String> sqlTraceNodes = new HashMap<>();
        Map<CKSqlTraceNode, String> cksqlTraceNodes = new HashMap<>();
        Map<DubboTraceNode, String> dubboTraceNodes = new HashMap<>();
        Map<RedisTraceNode, String> redisTraceNodes = new HashMap<>();
        // 所堆栈节点
        List<StackNodeVo> codeNodes = new ArrayList<>();

        int count = 0;
        for (CaseCenterIndex snapshot : snapshots) {
            String traceId = snapshot.getSnapshot().getTraceId();
            List<TraceNodeIndex> nodeIndexs = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 200));
            if (CollectionUtils.isEmpty(nodeIndexs)) {
                throw new DirtyDataException("找不到Trace Node traceId=" + traceId);
            }
            for (TraceNodeIndex nodeIndex : nodeIndexs) {
                TraceNode node = nodeIndex.toTraceNode();
                if (node instanceof SqlTraceNode) {
                    sqlTraceNodes.put((SqlTraceNode) node, snapshot.getId());
                } else if (node instanceof CKSqlTraceNode) {
                    cksqlTraceNodes.put((CKSqlTraceNode) node, snapshot.getId());
                }else if (node instanceof DubboTraceNode) {
                    dubboTraceNodes.put((DubboTraceNode) node, snapshot.getId());
                }else if (node instanceof RedisTraceNode) {
                    redisTraceNodes.put((RedisTraceNode) node, snapshot.getId());
                } else if (node instanceof CodeNodeBean) {
                    if (((CodeNodeBean) node).getCodeNodes() != null) {
                        codeNodes.addAll(Arrays.asList(((CodeNodeBean) node).getCodeNodes()));
                    }
                }
            }
            count++;
        }
        Assert.isTrue(count == snapshotIds.length, "列表中存在无效的 Snapshot ID:" + Arrays.toString(snapshotIds));

        Usecase usecase = new Usecase();
        // 解析封装SQL节点
        usecase.setSql(parseSql(sqlTraceNodes));
        usecase.setSql(parseCKSql(cksqlTraceNodes));
        // 解析封装远程调用节点
        usecase.setRemote(parseRemote(dubboTraceNodes));
        // 构建执行代码堆栈
        usecase.setSrcStack(parseCoeStack(codeNodes));
        return usecase;
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
    public List<UsecaseVo> getUsecases(String projectId, String directory, String sort) {
        Assert.notNull(projectId, "param 'projectId' must be not null");
        Assert.notNull(directory, "param 'directory' must be not null");

        if ("name".equals(sort)) {
            sort = "usecase.title.keyword";
        }

        // 不分页 按单页最大值100显示，业务上限定每个目录不能超过100个用例
        List<CaseCenterIndex> list = centerRepository.findByUsecase_ProjectIdAndAndUsecase_Directory(projectId, directory, PageRequest.of(0, 100, Sort.Direction.DESC, sort));
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
        detailVo.setCreateTime(caseCenterIndex.parse(caseCenterIndex.getCreateTime()));
        detailVo.setUpdateTime(caseCenterIndex.parse(caseCenterIndex.getUpdateTime()));
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
            index = centerRepository.findById(parentId).orElseThrow(() -> new IllegalArgumentException("找不到应用 parentId=" + parentId + ""));
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
        index.setUpdateTime(index.currentTimeToString());
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
        if (ObjectUtils.isEmpty(usecaseParam.getSnapshots())) {
            index.getUsecase().setRemote(null);
            index.getUsecase().setSql(null);
        } else {
            Usecase usecase = buildUsecaseBySnapshot(usecaseParam.getSnapshots());
            index.getUsecase().setRemote(usecase.getRemote());
            index.getUsecase().setSql(usecase.getSql());
            index.getUsecase().setSrcStack(usecase.getSrcStack());
        }

        // 复制属性
        BeanUtils.copyProperties(usecaseParam, index.getUsecase(), "authors", "projectId", "id");
        // 添加作者
        List<String> authorList = Arrays.asList(index.getUsecase().getAuthors());
        if (!authorList.contains(author)) {
            authorList.add(author);
        }
        index.getUsecase().setAuthors(authorList.toArray(new String[0]));
        index.getUsecase().setLastUpdateAuthor(author);
        // 更新修改时间
        index.setUpdateTime(index.currentTimeToString());
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
        Assert.isTrue(op.get().getUsecase().getProjectId().equals(projectId), "the usecase not belong to project Id=" + projectId);
        // 不直接删除，只是标识其disable 等于true
        centerRepository.save(op.get());
    }

    private UsecaseDirectoryVo convertDirectory(CaseCenterIndex index) {
        UsecaseDirectoryVo vo = new UsecaseDirectoryVo();
        BeanUtils.copyProperties(index.getDirectory(), vo);
        vo.setId(index.getId());
        vo.setUpdateTime(index.parse(index.getUpdateTime()));
        return vo;
    }

    private UsecaseVo convertUsecase(CaseCenterIndex caseCenterIndex) {
        UsecaseVo usecaseVo = new UsecaseVo();
        BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
        usecaseVo.setId(caseCenterIndex.getId());
        usecaseVo.setCreateTime(caseCenterIndex.parse(caseCenterIndex.getCreateTime()));
        usecaseVo.setUpdateTime(caseCenterIndex.parse(caseCenterIndex.getUpdateTime()));
        return usecaseVo;
    }

}

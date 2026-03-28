package com.oAT.agent.jacoco.data;

import com.oAT.agent.common.Decompiler.ILanguageNames;
import com.oAT.agent.common.Decompiler.JavaNames;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.TypeConvert;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.StackNode;
import com.oAT.agent.jacoco.StackSession;
import com.oAT.agent.model.StackNodeVo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StackNodeVoBuilder {
    private final static Log logger = LogFactory.getLog(StackNodeVoBuilder.class);

    public StackNodeVo[] buildCodeNodes(StackSession stackSession) {
        try {
            List<StackNode> allNodes = stackSession.getAllNodes();
            int size = allNodes.size();
            logger.debug("[Agent-debug]开始构建代码采集节点数: " + size);
            StackNodeVo[] result = new StackNodeVo[size];
            StackNodeVo nodeVo;
            int i = 0;
            ILanguageNames javaNames = new JavaNames();
            for (StackNode n : allNodes) {
                String originClassName = n.getClassName();
                String originMethodName = n.getMethodName();
                String originMethodDesc = n.getMethodDesc();

                nodeVo = new StackNodeVo();
                nodeVo.setId(n.getId());
                nodeVo.setClassId(n.getClassId());
                String className = javaNames.getQualifiedClassName(originClassName);
                nodeVo.setClassName(className);
                String methodName = javaNames.getMethodName(originClassName, originMethodName, originMethodDesc, null);
                nodeVo.setMethodName(methodName);
                nodeVo.setMethodDescriptor(originMethodDesc);
                nodeVo.setUseTime(n.getUseTime());

                ArrayList<Integer> executeBranchList = TypeConvert.parseToIntList(n.getExecuteBranch());
                ArrayList<Integer> doLines = new ArrayList<>(n.getDoLines());
                if (!doLines.isEmpty()) {
                    for (Integer branch : executeBranchList) {
                        doLines.remove(branch);
                    }
                }
                nodeVo.setDoLines(doLines);
                ArrayList<Integer> lineTotalList = TypeConvert.parseToIntList(n.getLineTotal());
                nodeVo.setLineTotal(lineTotalList);
                ArrayList<Integer> executeMethodTotal = new ArrayList<>(n.getExecuteMethodTotal());
                nodeVo.setExecuteMethodTotal(executeMethodTotal);
                ArrayList<Integer> methodTotalList = TypeConvert.parseToIntList(n.getMethodTotal());
                nodeVo.setMethodTotal(methodTotalList);
                nodeVo.setExecuteBranch(executeBranchList);
                Map<String, List<String>> executeConditionMap = TypeConvert.convertStringToMap(n.getExecuteCondition());
                nodeVo.setExecuteCondition(executeConditionMap);
                nodeVo.setExecBranchConditionIsTrue(n.getExecBranchConditionIsTrue().toString());
                ArrayList<Integer> branchTotalList = TypeConvert.parseToIntList(n.getBranchTotal());
                nodeVo.setBranchTotal(branchTotalList);

                String execCyclo;
                if (executeBranchList.isEmpty()) {
                    execCyclo = "0";
                } else {
                    execCyclo = String.valueOf(executeBranchList.size());
                }
                nodeVo.setExecCyclo(execCyclo);
                nodeVo.setCyclo(n.getCyclo());
                nodeVo.setRecursive(n.isRecursive());
                nodeVo.setAsync(n.isAsync());
                nodeVo.setSize(n.getSize());
                nodeVo.setDone(n.isDone());
                result[i++] = nodeVo;
            }
            logger.debug("[Agent-debug]结束构建代码采集节点数: " + result.length);
            return result;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]buildCodeNodes 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return new StackNodeVo[0];
        }
    }
}

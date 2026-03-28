package com.oAT.web.control.entity;

import com.oAT.agent.model.Error;
import com.oAT.agent.model.RedisTraceNode;

import java.util.ArrayList;
import java.util.List;

public class RedisGraphNode extends GraphNode{

    private RedisTraceNode redisTraceNode;
    private List<RedisTraceNode> redisNodes = new ArrayList<>();
    // 异常堆栈
    private List<Error> errors = new ArrayList<>();


    public List<RedisTraceNode> getRedisNodes() {
        return redisNodes;
    }

    public void setRedisNodes(List<RedisTraceNode> redisNodes) {
        this.redisNodes = redisNodes;
    }

    public RedisGraphNode(RedisTraceNode redisTraceNode) {
        this.redisTraceNode = redisTraceNode;
    }

    public RedisTraceNode getRedisTraceNode() {
        return redisTraceNode;
    }

    public void setRedisTraceNode(RedisTraceNode redisTraceNode) {
        this.redisTraceNode = redisTraceNode;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }


    public void add(RedisTraceNode redisTraceNode) {
        redisNodes.add(redisTraceNode);
    }
}

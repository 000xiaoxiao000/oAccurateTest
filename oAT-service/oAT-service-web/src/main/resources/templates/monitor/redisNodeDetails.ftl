<#include "../commonFunction.ftl">
<div class="ui small tabular menu" style="margin: -1px 0px 0px -1px;">
    <div class="item active" data-tab="base-info">基本信息</div>
    <div class="item" data-tab="redis-invoke">Redis访问</div>
    <div class="item" data-tab="error-stack">异常堆栈</div>
</div>
<div class="ui tab active" data-tab="base-info">
    <div class="ui list content" style="padding: 10px;">
        <div class="item">
            <span class="listHeader">redis名称：</span>
            ${redis.name!}
        </div>
        <div class="ui fitted divider"></div>
        <div class="item">
            <span class="listHeader">redis执行数：</span>
            ${redis.redisNodes?size}
        </div>
        <div class="red item ">
            <span class="listHeader">异常数：</span>
            ${redis.errors?size}
        </div>
        <div class="ui fitted divider"></div>
        <div class="ui fluid accordion">
            <div class="title" style="font-weight:bold;margin-left: -5px">
                <i class="dropdown icon"></i>
                跟踪信息
            </div>
            <div class="ui content">
                跟踪ID: ${traceId!}
            </div>
        </div>
    </div>
</div>
<div class="ui tab" data-tab="redis-invoke" style="padding: 10px">
    <!--redis 列表-->
    <table class="ui compact selectable  fixed  table attached">
        <tbody>
        <#list redis.redisNodes as cmd>
            <tr>
                <td class="one wide">${cmd_index+1}</td>
                <td>
                    <div class="ui fluid accordion" style="overflow: auto">
                        <div class="title">
                            <#if cmd.type=="SET" || cmd.type=="SETEX" || cmd.type=="HSET" || cmd.type=="HPUTALL" || cmd.type=="SADD" || cmd
                            .type=="SETNX" || cmd.type=="HPUTALLEX">
                                <label class="ui green small label">
                                    增/改
                                </label>
                            <#elseif cmd.type=="DEL">
                                <label class="ui red small label">
                                    删
                                </label>
                            <#elseif cmd.type=="GET" || cmd.type=="HGET" || cmd.type=="HGETALL" || cmd.type=="SMEMBERS">
                                <label class="ui blue small label">
                                    查
                                </label>
                            <#else>
                                <label class="ui small label">
                                    ${cmd.type}
                                </label>
                            </#if>
                            ${cmd.cmd?html}
                        </div>
                    </div>
                </td>
<#--                <td class="one wide">${cmd.useTime} ms</td>-->
            </tr>
        </#list>
        </tbody>
    </table>
</div>
<div class="ui tab" data-tab="error-stack">
    <div style="padding: 10px">
        <#list redis.errors as error >
            <h3 class="ui header">${error.type!}：${error.message}</h3>
            <pre>
                <code class="basic">${error.errorStack}</code>
             </pre>
            <div class="ui divider"></div>
        </#list>
    </div>
</div>

<script>
    $('.tabular.menu .item').tab();
    $('.poping.up').popup();
    $('.ui.accordion').accordion({
        exclusive: false
    });
</script>
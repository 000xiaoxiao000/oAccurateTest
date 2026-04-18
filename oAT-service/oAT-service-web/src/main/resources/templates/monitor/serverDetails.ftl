<#include "../common.ftl">
<div class="ui small tabular menu" style="margin: -1px 0px 0px -1px;">
    <div class="item active" data-tab="base-info">基本信息</div>
    <div class="item" data-tab="sql-invoke">SQL访问</div>
    <div class="item" data-tab="remote-invoke">远程调用</div>
    <div class="item" data-tab="system-log1">系统日志</div>
</div>
<div class="ui tab active" data-tab="base-info">
    <div class="ui list content" style="padding: 10px;">
        <div class="item">
            <span class="listHeader">应用名称：</span>
            ${(appSession.application.appName)!}
        </div>
        <div class="item">
            <span class="listHeader">部署路径：</span>
            ${(appSession.clientInfo.systemDir)!}
        </div>
        <div class="item">
            <span class="listHeader"> 应用IP：</span>
            ${(appSession.clientInfo.addressIp)!}
        </div>
        <div class="ui fitted divider"></div>
        <div class="item">
            <span class="listHeader">sql数：</span>
            ${data.sqlGroups?size}
        </div>
        <div class="item">
            <span class="listHeader">远程调用数：</span>
            ${data.dubboNodes?size}
        </div>

        <div class="red item ">
            <span class="listHeader">异常数：</span>
            ${data.errors?size}
        </div>

        <div class="ui fitted divider"></div>
        <div class="ui fluid accordion">
            <div class="title" style="font-weight:bold;margin-left: -5px">
                <i class="dropdown icon"></i>
                跟踪信息
            </div>
            <div class="ui content">
                跟踪Id: ${traceId}
            </div>
        </div>
    </div>

</div>
<div class="ui tab" data-tab="sql-invoke" style="padding: 10px">
    <!--sql 列表-->
    <table class="ui selectable fixed table attached">
        <tbody>
        <thead>
        <tr>
            <th class="one wide">序号</th>
            <th>SQL语句</th>
            <th class="one wide">执行次数</th>
        </tr>
        </thead>
        <#list data.sqlGroups as sql>
            <tr>
                <td class="one wide">${sql_index+1}</td>
                <td>
                    <div class="ui fluid accordion" style="overflow: auto">
                        <div class="title">
                            <#list sql.executes as type>
                                <#if type=="insert">
                                    <label class="ui green small label">
                                        增
                                    </label>
                                <#elseif type=="update">
                                    <label class="ui yellow small label">
                                        改
                                    </label>
                                <#elseif type=="delete">
                                    <label class="ui red small label">
                                        改
                                    </label>
                                <#elseif type=="select">
                                    <label class="ui small label">
                                        查
                                    </label>
                                <#else>
                                    <label class="ui small label">
                                        ${type}
                                    </label>
                                </#if>
                            </#list>
                            ${sql.sql}
                        </div>
                        <div class="ui content segment">
                            ${sql.jdbcUrl!}
                            <div class="ui fitted divider"></div>
                            <pre>
<code class="sql">${sqlFormat(sql.sql,sql.type)}</code></pre>
                        </div>
                    </div>
                </td>
                <td class="one wide"> ${sql.count}</td>
            </tr>
        </#list>

        </tbody>
    </table>
    <!--sql 分割线-->
    <h5 class="ui horizontal divider header">
        <i class="bar chart icon"></i>
        sql 统计
    </h5>
    <!--以表格的形式展示 SQL 统计 -->
    <table class="ui compact celled structured  table">
        <thead>
        <tr>
            <th class="one wide center aligned">
                类别
            </th>
            <th class="two wide">
                表名
            </th>
            <th>
                涉及字段
            </th>
        </tr>
        </thead>
        <tbody>
        <#list data.adds as sql>
            <tr class="positive">
                <td class="center aligned">增</td>
                <td><a href="#">${sql.tableName}</a></td>
                <td>
                    <div class="ui list horizontal">
                        <#list sql.columns as column>
                            <div class="item">${column}</div>
                        </#list>

                    </div>
                </td>
            </tr>
        </#list>
        <#list data.deletes as sql>
            <tr class="negative">
                <td class="center aligned">删</td>
                <td><a href="#">${sql.tableName}</a></td>
                <td>
                    <div class="ui list horizontal">
                        <#list sql.columns as column>
                            <div class="item">${column}</div>
                        </#list>
                    </div>
                </td>
            </tr>
        </#list>
        <#list data.updates as sql>
            <tr class="warning">
                <td class="center aligned">改</td>
                <td><a href="#">${sql.tableName}</a></td>
                <td>
                    <div class="ui list horizontal">
                        <#list sql.columns as column>
                            <div class="item">${column}</div>
                        </#list>
                    </div>
                </td>
            </tr>
        </#list>
        <#list data.selects as sql>
            <tr>
                <td class="center aligned">查</td>
                <td><a href="#">${sql.tableName}</a></td>
                <td>
                    <div class="ui list horizontal">
                        <#list sql.columns as column>
                            <div class="item">${column}</div>
                        </#list>
                    </div>
                </td>
            </tr>
        </#list>
        </tbody>
    </table>
</div>
<div class="ui tab" data-tab="remote-invoke" style="overflow: scroll">
    <table class="ui selectable  single line  compact table attached">
        <thead>
        <tr>
            <th style="width: 40px">序号</th>
            <th style="width: 50px">协议</th>
            <th>服务路径</th>
            <th style="width: 40px">用时</th>
        </tr>
        </thead>
        <tbody>
        <#list data.dubboNodes as node>
            <tr <#if node.error??> class="error" </#if> >
                <td>${node_index+1}</td>
                <td>dubbo</td>
                <td>
                    <div class="ui fluid accordion ">
                        <div class="ui title">
                            ${node.remoteUrl}
                            <#if node.error??>
                                <i class="ui warning sign red icon poping up"
                                   data-content="${node.error.message}"
                                   data-variation="tiny inverted"></i>
                            </#if>
                        </div>
                        <!--远程调用详情内容-->
                        <div class="ui content segment">
                            <div class="ui tiny left floated header ">远程地址：</div>
                            ${node.remoteIp!}

                            <div class="ui divider"/>
                            <div class="ui tiny left floated header "> 输入参数：</div>
                            <pre>
<code class="json">${node.inParam!}</code></pre>
                            <div class="ui divider"/>
                            <div class="ui tiny left floated header ">返回结果：</div>
                            <pre>
<code class="json">${node.outParam!}</code></pre>
                            <div class="ui divider"/>
                        </div>
                    </div>
                </td>
                <td>${node.useTime}ms</td>
            </tr>

        </#list>
        </tbody>
    </table>
</div>
<div class="ui tab" data-tab="system-log1">
    <div style="padding: 10px;overflow-x: auto">
        <#--<#list data.errors as error >
            <h3 class="ui header">${error.type!}：${error.message}</h3>
            <pre>
                <code class="basic">${error.errorStack}</code>
             </pre>
            <div class="ui divider"></div>
        </#list>-->
        <pre><code class="basic">${data.log!}</code></pre>
    </div>
</div>
<script>
    $('.tabular.menu .item').tab();
    $('.poping.up')
        .popup()
    ;
    $('.ui.accordion').accordion({
        exclusive: false
    });
</script>

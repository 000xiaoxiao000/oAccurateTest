<#include "../commonFunction.ftl">
<div class="ui small tabular menu" style="margin: -1px 0px 0px -1px;">
    <div class="item active" data-tab="base-info">基本信息</div>
    <div class="item" data-tab="sql-invoke">SQL访问</div>
    <div class="item" data-tab="error-stack">异常堆栈</div>
</div>
<div class="ui tab active" data-tab="base-info">
    <div class="ui list content" style="padding: 10px;">
        <div class="item">
            <span class="listHeader">数据库名称：</span>
            ${database.name!}
        </div>
        <div class="item">
            <span class="listHeader">URL路径：</span>
            ${data.jdbcUrl!}
        </div>
        <div class="ui fitted divider"></div>
        <div class="item">
            <span class="listHeader">sql数：</span>
            ${data.sqlNodes?size}
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
                跟踪ID: ${traceId!}
            </div>
        </div>
    </div>

</div>
<div class="ui tab" data-tab="sql-invoke" style="padding: 10px">
    <!--sql 列表-->
    <table class="ui compact selectable fixed table attached">
        <tbody>
        <#list data.sqlNodes as sql>
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
                                        删
                                    </label>
                                <#elseif type=="select">
                                    <label class="ui blue small label">
                                        查
                                    </label>
                                <#else>
                                    <label class="ui small label">
                                        ${type}
                                    </label>
                                </#if>
                            </#list>
                            ${sql.sql?html}
                        </div>
                        <div class="ui content segment">
                            ${sql.jdbcUrl!}
                            <div class="ui fitted divider"></div>
                            <pre>
<code class="sql">${sqlFormat(sql.sql,sql.database.type)}</code>
                        </pre>
<#--                            sql结果-->
<#--                            <div class="ui content segment">-->
<#--                                <#if (sql.results)??>-->
<#--                                    <#list sql.results.contents as content>-->
<#--                                        <#if content??>${content?counter}:</#if>-->
<#--                                        <#list content as con>-->
<#--                                            <#list sql.executes as type>-->
<#--                                                <#if type=="select">-->
<#--                                                    ${con!"null"}-->
<#--                                                </#if>-->
<#--                                            </#list>-->
<#--                                        </#list>-->
<#--                                    </#list>-->
<#--                                </#if>-->
<#--                            </div>-->
                        </div>
                    </div>
                </td>
                <td class="one wide">${sql.useTime} ms</td>
            </tr>
        </#list>
        </tbody>
    </table>
    <!--sql 分割线-->
    <h5 class="ui horizontal divider header">
        <i class="bar chart icon"></i>
        SQL 统计
    </h5>
    <!--以表格的形式展示 SQL 统计 -->
    <table class="ui compact celled structured table">
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
                <td class="center aligned">
                    <label class="ui green small label">
                        增
                    </label>
                </td>
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
                <td class="center aligned">
                    <label class="ui red small label">
                        删
                    </label>
                </td>
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
                <td class="center aligned">
                    <label class="ui yellow small label">
                        改
                    </label>
                </td>
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
                <td class="center aligned">
                    <label class="ui small label">
                        查
                    </label>
                </td>
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
<div class="ui tab" data-tab="error-stack">
    <div style="padding: 10px">
        <#list data.errors as error >
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

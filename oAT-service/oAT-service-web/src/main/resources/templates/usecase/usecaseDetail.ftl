<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-用例详情</title>
     <#include "../common.ftl">
<#--语法高亮-->
    <link href="/css/monokai_sublime.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>

    <style>
        body {
            background: #f7f7f7;
        }

        .ui.text.container {
            background: #ffffff;
            min-width: 900px;
            padding: 20px;
        }
    </style>
</head>
<body>
<#assign usecaseItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">用例详情</div>
</div>
<div class="ui container" style="margin-bottom: 10px">
    <a class="ui tiny teal basic button" href="/p/${project.id}/usecase/list">
        <i class="home icon"></i>用例中心
    </a>
</div>
<div class="ui vertical menu" style="position: fixed;top: 150px;left: 50px">
    <a class="item" href="#snapshotHeader">
        快照列表
        <div class="ui label">${(snapshots?size)!'0'}</div>
    </a>
    <a class="item" href="#contentHeader">
        详情描述
    </a>
    <a class="item" href="#sqlHeader">
        SQL列表
        <div class="ui label">${(usecase.sqls?size)!'0'}</div>
    </a>
    <a class="item" href="#remoteHeader">
        远程调用
    </a>
</div>


<div class="ui text container ">
    <h1 class="ui dividing  header">${usecase.title}
        <#if labels??>
            <#list labels as label >
                  <div class="ui tiny ${label.color} label">${label.name}</div>
            </#list>
        </#if>
        <a class="ui dropdown  poping up" data-content="修改用例" data-variation="tiny inverted"
           href="edit?id=${usecase.id}">
            <i class="icon small grey link setting"></i>
        </a>
        <br>
        <div class="ui right aligned sub header  " style="margin-top: -15px;font-size: 13px">
            <span>${lastUpdateAuthor.name}</span>
            <span>最后更新于</span>
            <span>${usecase.updateTime?date} </span>
        </div>
    </h1>
    <!--主图-->
    <#if usecase.headImage??>
            <img onclick="$('#headImageDialog').modal('show');" class="ui centered medium image"
                 src="/r/${usecase.headImage}" style="cursor:pointer">

       <div id="headImageDialog" class="ui modal">
           <i class="close icon"></i>
           <img class="ui fluid image" src="/r/${usecase.headImage}">
       </div>
    </#if>

    <h3 class="ui header" id="snapshotHeader">
        快照列表
    </h3>
    <#if snapshots??>

        <table class="ui compact small table">
            <thead>
            <tr>
                <th>名称</th>
                <th>说明</th>
                <th class="right aligned">创建时间</th>
            </tr>
            </thead>
            <tbody>
           <#list snapshots as snap>
           <tr>
               <td>
                   <#if snap.appId??>
                       <a href="/p/${project.id}/${snap.appId}/snapshot/detail/${snap.id}">${snap.name}</a>
                   <#else>
                       <span>${snap.name}</span>
                   </#if>
               </td>
               <td>${snap.describe!''}</td>
               <td class="right aligned">${snap.createTime?date}</td>
           </tr>
           </#list>
            </tbody>
        </table>
    <#else >
        <p>该用例未添加任何快照</p>
    </#if>
    <h3 class="ui dividing header" id="contentHeader">
        详情描述
    </h3>
    <div id="usecaseContent">
    ${usecaseContent!"太懒了 啥也没有写!"}
    </div>
    <h3 class="ui header" id="sqlHeader">
        SQL列表
    </h3>
    <#if usecase.sqls??>
    <table class="ui selectable single line small compact fixed  table ">
        <thead>
        <tr>
            <th class="one wide"> 序号</th>
            <th class="two wide"> 数据库</th>
            <th> sql语句</th>
        </tr>
        </thead>
        <tbody>
        <#list usecase.sqls as sql>
        <tr onclick="$('#detail-${sql_index}').toggle()" style="cursor:pointer">
            <td>${sql_index+1}</td>
            <td>${sql.dbName}</td>
            <td>
                ${sql.sql}
            </td>
        </tr>
        <tr id="detail-${sql_index}" style="display: none;background-color: #f7f7f7">
            <td colspan="3">
                <pre><code class="sql">${sql.sql}</code></pre>
            </td>
        </tr>
        </#list>
        </tbody>
    </table>
    <#else >
        <p>没有任何SQL</p>
    </#if>
    <h3 class="ui header" id="remoteHeader">
        远程调用
    </h3>
    <#if usecase.remote??>
        <table class="ui selectable single  small compact fixed  table">
            <thead>
            <tr>
                <th class="two wide">协议</th>
                <th>服务路径</th>
            </tr>
            </thead>
            <tbody>
            <#if usecase.remote.dubbo??>
                <#list usecase.remote.dubbo as dubbo>
                    <tr>
                        <td>dubbo</td>
                        <td>${dubbo}</td>
                    </tr>
                </#list>
            </#if>
            <#if usecase.remote.http??>
                <#list usecase.remote.http as http>
                    <tr>
                        <td>http</td>
                        <td>${http}</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    <#else >
    <p>没有任何的远程调用信息</p>
    </#if>
</div>
<script>
    $('.ui.accordion').accordion({
        exclusive: false
    });
    $("#usecaseContent img").attr("class", "ui centered  medium image");
    $('.ui.dropdown.poping.up').popup();
</script>


</body>
</html>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-比对报告</title>
     <#include "../common.ftl">
    <style>
        /* 允许在容器内断行长字符串（如长 commit id）避免溢出 */
        .oat-wrap-word { overflow-wrap: break-word; word-break: break-all; }
        .oat-wrap-word code { white-space: normal; overflow-wrap: anywhere; }
    </style>
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">

<!--面包屑导航-->
<div class="ui small breadcrumb">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class=" section" href="/p/${project.id}/${app.id}/version/compare"> ${app.name} </a>
    <span class="divider">/</span>
    <div class="active section">比对报告</div>
</div>

<!--内容主体-->
<div class="ui text container segment " style="margin-top: 14px">
    <!-- 中间内容 -->
    <h1 class="ui dividing  header oat-wrap-word">${report.jobName}
        <br>
        <div class="ui right aligned sub header  " style="margin-top: -15px;font-size: 13px">
            <span>${report.createTime?datetime}</span>
        </div>
    </h1>
    <#-- Git 元信息 -->
    <#if report.gitBranch?has_content || report.gitOldCommit?has_content || report.gitNewCommit?has_content>
        <div class="ui small grey oat-wrap-word">
            <#if report.gitBranch?has_content>
                分支: <b>${report.gitBranch}</b>
            </#if>
            <#if report.gitOldCommit?has_content>
                &nbsp; 旧: <code>${report.gitOldCommit}</code>
            </#if>
            <#if report.gitNewCommit?has_content>
                &nbsp; 新: <code>${report.gitNewCommit}</code>
            </#if>
        </div>
        <div style="height:8px"></div>
    </#if>

    <!-- 统计摘要 -->
    <#-- show summary badges if report contains count fields -->
    <#if report.addClassCount?has_content || report.updateClassCount?has_content || report.deleteClassCount?has_content || report.addMethodCount?has_content || report.updateMethodCount?has_content || report.deleteMethodCount?has_content || report.impactCaseCount?has_content>
        <div style="margin-bottom: 12px;">
            <#if report.addClassCount?has_content>
                <span class="ui mini green label">新增类 ${report.addClassCount}</span>
            </#if>
            <#if report.updateClassCount?has_content>
                <span class="ui mini orange label">更新类 ${report.updateClassCount}</span>
            </#if>
            <#if report.deleteClassCount?has_content>
                <span class="ui mini red label">删除类 ${report.deleteClassCount}</span>
            </#if>
            <#if report.addMethodCount?has_content>
                <span class="ui mini green label">新增方法 ${report.addMethodCount}</span>
            </#if>
            <#if report.updateMethodCount?has_content>
                <span class="ui mini orange label">更新方法 ${report.updateMethodCount}</span>
            </#if>
            <#if report.deleteMethodCount?has_content>
                <span class="ui mini red label">删除方法 ${report.deleteMethodCount}</span>
            </#if>
            <#if report.impactCaseCount?has_content>
                <span class="ui mini label">影响用例 ${report.impactCaseCount}</span>
            </#if>
        </div>
    </#if>

    <!--统计信息-->
    <h3 class="ui dividing header ">变更项
    </h3>

    <div class="ui divided list">
        <#if different?has_content && (different?size > 0)>
            <#list different as dif >
                 <div class="item">
                     <div class="content">
                         <#-- 使用 ?string 确保 enum 与字符串比较稳健 -->
                         <#if (dif.model?string) == 'add'>
                             <i class=" small add icon "></i>
                         <#elseif (dif.model?string) == 'update'>
                             <i class=" small edit icon "></i>
                         <#elseif (dif.model?string) == 'delete'>
                             <i class=" small red remove icon "></i>
                         </#if>
                         ${dif.className}
                     </div>
                     <div class="ui horizontal link list">
                         <#-- 增加对 methods 的空值保护 -->
                         <#if dif.methods?has_content>
                             <#list dif.methods as method>
                                  <div class="item">
                                      <div class="content">
                                          <#-- 同样使用 ?string 比较 method.model，并对 method.name 提供占位 -->
                                          <#if (method.model?string) == 'add'>
                                              <span class="ui mini green label"><i class="add icon"></i>${method.name!"(方法名不可用)"}</span>
                                          <#elseif (method.model?string) == 'update'>
                                              <span class="ui mini orange label"><i class="edit icon"></i>${method.name!"(方法名不可用)"}</span>
                                          <#elseif (method.model?string) == 'delete'>
                                              <span class="ui mini red label"><i class="remove icon"></i>${method.name!"(方法名不可用)"}</span>
                                          </#if>
                                         <#if method.desc?has_content && (method.model?string) != 'add' && (method.model?string) != 'update' && (method.model?string) != 'delete'>
                                             ${method.name!"(方法名不可用)"}
                                         </#if>
                                         <#if method.desc?has_content>
                                             <span style="color:#888; font-size:0.9em;"> (行: ${method.desc})</span>
                                         </#if>
                                      </div>
                                  </div>
                             </#list>
                         <#else>
                             <div class="item"><div class="content" style="color:#888;">（无方法详情）</div></div>
                         </#if>
                     </div>
                 </div>
            </#list>
        <#else>
            <div class="ui message">未发现变更项。</div>
        </#if>
     </div>

     <h3 class="ui dividing header ">
         影响用例
     </h3>
     <div class="ui list">
    <#-- 基于目录对用例进行分组-->
      <#if usecaseGroups?has_content && (usecaseGroups?size > 0)>
          <#list usecaseGroups as dir >
               <div class="item">
                   <h5 class="header"><i class="ui icon folder open outline"></i>${dir.directoryName}</h5>
                   <div class="ui list">
                   <#-- 遍历分组中的用例-->
                     <#list dir.list as case>
                         <div class="item">
                         <#--跳转至用例详情-->
                             <a href="/p/${project.id}/${app.id}/snapshot/detail/${case.id}">
                                 ${case.name}

                             <#-- 遍历用例中的标签-->
                         <#list case.labels as label>
                             <div class="ui mini ${label.color} label">${label.name}</div>
                         </#list>
                             </a>
                             <div class="right floated content">
                                 <a class="ui"
                                    onclick="showDetail('differences_${case.id}')">影响点：${case.differences?size}</a>
                             </div>
                         <#--遍历用例中的影响点-->
                             <div id="differences_${case.id}" class="ui tiny link list"
                                  style="display:none;border: 1px">
                             <#list case.differences as dif>
                                 <div class="item">
                                     ${dif}
                                 </div>
                             </#list>
                             </div>
                         </div>
                     </#list>
                   </div>
               </div>
          </#list>
        <#else>
            <div class="ui message">未发现影响用例。</div>
        </#if>
     </div>

    <div class="ui button" onclick="$('#compareLogger').toggle();"> 比对日志</div>
    <div class="ui tiny message" id="compareLogger" style="display: none">
        <p>
        ${report.jobLog?replace("\n","</br>")}
        </p>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });

    function showDetail(id) {
        <!--显示节点详情-->
        "#" + id && $("#" + id).toggle();

    }
</script>
</body>
</html>

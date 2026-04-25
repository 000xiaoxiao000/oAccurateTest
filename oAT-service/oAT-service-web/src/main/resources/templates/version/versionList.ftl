<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-版本列表</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">

<!--面包屑导航-->
<div class="ui container">
    <div class="ui small breadcrumb version-breadcrumb">
        <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/${appId}/version/list">${appInfo.name}</a>
        <span class="divider">/</span>
        <div class="active section">版本列表</div>
    </div>
</div>

<!--内容主体-->
<div class="ui container version-center-page">
    <div class="version-page-layout">
        <!-- 左边导航菜单 -->
        <div class="version-page-side">
            <#assign appName=appInfo.name/>
            <#assign versionListActive="active"/>
            <#include "LeftNavigationMenu.ftl">
        </div>
        <!-- 中间内容 -->
        <div class="version-page-main">
            <div class="version-page-header">
                <div>
                    <div class="version-page-kicker">
                        <i class="list icon"></i>
                        版本中心
                    </div>
                    <h1 class="version-page-title">${appInfo.name}-版本列表</h1>
                    <p class="version-page-desc">查看应用版本、代码环境和覆盖率状态，快速设置当前版本或进入报告。</p>
                </div>
                <div class="version-page-actions">
                    <a class="ui primary button" href="/p/${project.id}/${appId}/version/new">
                        <i class="plus icon"></i>新增版本
                    </a>
                </div>
            </div>
            <div class="version-page-body">
                <div class="oat-list-toolbar js-list-control" data-table="#versionListTable" data-client-pagination="false" data-search-placeholder="搜索版本号、描述、分支或 Commit" data-empty-colspan="5"></div>
                <div class="version-table-wrap">
            <table id="versionListTable" class="ui celled table">
                <thead>
                <tr>
                    <th>版本号</th>
                    <th>描述说明</th>
                    <th>代码环境</th>
                    <th>创建时间</th>
                    <th>操作</th>
                </tr>
                </thead>
                <tbody>
                    <#list items as item>
                    <#assign isCurrent = (appInfo.currentVersion?? && item.versionNumber?? && appInfo.currentVersion == item.versionNumber) &&
                                       (appInfo.currentBranch?? && appInfo.currentBranch == (item.repoBranch!'')) &&
                                       (appInfo.currentCommitId?? && appInfo.currentCommitId == (item.repoCommitId!''))>
                    <tr class="<#if isCurrent>positive </#if><#if item.hasReport>has-coverage<#else>no-coverage</#if>">
                        <td>
                            <#if isCurrent>
                                <div class="ui tiny green label" style="margin-right: 8px;">
                                    <i class="check icon"></i>当前版本
                                </div>
                            </#if>
                            ${item.versionNumber!""}
                        </td>
                        <td>${item.describe!""}</td>

                        <td>
                            <#if item.repoBranch?? && item.repoBranch !="">
                                分支: ${item.repoBranch}
                                <#if item.repoCommitId?? && item.repoCommitId !="">
                                    <br>Commit: ${item.repoCommitId?substring(0, 7)}
                                </#if>
                                <br>
                            </#if>
                            <#if item.fileExist!false>
                                <a href="/r/${item.programFile!''}" title="${item.programName!''}">
                                    <i class="file alternate outline icon"></i>${item.programName!''}
                                </a>
                            <#else>
                                <span style="color: grey"><i class="file alternate outline icon"></i>已删除</span>
                            </#if>
                        </td>
                        <td>
                            <#if item.createTime??>${item.createTime?string("yyyy-MM-dd HH:mm:ss")}</#if>
                        </td>
                        <td class="center aligned">
                            <a href="/p/${(project.id)!''}/coverage/overview?appId=${item.appId!''}&versionNumber=${item.versionNumber!''}<#if item.repoCommitId??>&commitId=${item.repoCommitId}</#if>" title="查看覆盖率报告">
                                <i class="ui icon chart bar outline <#if item.hasReport!false>green<#else>grey</#if> link"></i>
                            </a>
                            <#if (loginNameRole!"visitor") != "visitor" >
                                <i class="ui icon check circle link <#if isCurrent>green<#else>blue</#if>" onclick="setCurrentVersion('${item.versionNumber!''}', '${item.repoBranch!}', '${item.repoCommitId!}')" title="设为当前版本"></i>
                            </#if>
                            <i class="ui icon remove red link" onclick="doDeleteVersion('${item.id!''}')">
                            </i>
                        </td>
                    </tr>
                    </#list>
                </tbody>
                <tfoot>
                <tr>
                    <th colspan="5">
                        <#if page.totalPages gt 0>
                        <div class="oat-list-pagination"><div class="oat-list-page-info">共 ${page.totalElements} 条，第 ${page.number + 1} / ${page.totalPages} 页</div><div class="ui pagination menu">
                            <#if page.hasPrevious()>
                                <a class="icon item" href="?page=${page.number - 1}&size=${page.size}">
                                    <i class="left chevron icon"></i>
                                </a>
                            </#if>
                            <#list 0..(page.totalPages - 1) as i>
                                <#if i == page.number>
                                    <a class="item active">${i + 1}</a>
                                <#else>
                                    <#if i gt page.number - 3 && i lt page.number + 3>
                                        <a class="item" href="?page=${i}&size=${page.size}">${i + 1}</a>
                                    </#if>
                                </#if>
                            </#list>
                            <#if page.hasNext()>
                                <a class="icon item" href="?page=${page.number + 1}&size=${page.size}">
                                    <i class="right chevron icon"></i>
                                </a>
                            </#if>
                        </div></div>
                        <#else>
                            <div class="oat-list-page-info">共 0 条</div>
                        </#if>
                    </th>
                </tr>
                </tfoot>
            </table>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- 删除版本弹出框-->
<div id="deleteVersionDialog" class="ui small modal">
    <div class="header">删除版本</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该版本吗？
        </div>
        <p> 删除之后将无法恢复</p>
    </div>

    <div class="actions">
        <div id="deleteVersionButton" class="ui negative button">删除</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.commit-id').popup();

    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }

    function doDeleteVersion(id) {
        $("#deleteVersionButton").click(function () {
            var resultInform = $.post({
                url: "/p/${project.id}/${appId}/version/delete?id=" + id,
                async: false
            }).responseJSON;
            if (resultInform.result) {
                // 刷新当前页 ，并传递删除成功的消息
                 // Use sessionStorage to pass message across reload
                 sessionStorage.setItem('toastMessage', '版本项目删除成功');
                 sessionStorage.setItem('toastMessageType', 'success');
                 window.location = window.location;
            } else {
                showToast(resultInform.errorMessage, 'error');
            }
        });
        // 显示删除对话框
        $("#deleteVersionDialog").modal('show');
    }

    function setCurrentVersion(versionNumber, branch, commitId) {
        var msg = "已成功设定当前版本为: " + versionNumber;
        if (branch) msg += " (分支: " + branch + ")";
        if (commitId) msg += " (Commit: " + commitId.substring(0, 7) + ")";

        $.post({
            url: "/p/${project.id}/${appId}/version/setCurrent",
            data: {
                versionNumber: versionNumber,
                branch: branch,
                commitId: commitId
            },
            success: function (result) {
                if (result.result) {
                    showToast(msg, 'success');
                    // 延迟刷新以让用户看到Toast
                    setTimeout(function() {
                        window.location.reload();
                    }, 1500);
                } else {
                    showToast(result.errorMessage, 'error');
                }
            },
            error: function() {
                showToast("设置失败，请稍后重试", "error");
            }
        });
    }


</script>
</body>
</html>

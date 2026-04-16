<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/html">
<head>
    <meta charset="UTF-8">
    <title>版本中心-比对控制台</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
    <span class="divider">/</span>
    <a class=" section" href="/p/${project.id}/${appInfo.id}/version/list">${appInfo.name}</a>
    <span class="divider">/</span>
    <div class="active section">版本比对</div>
</div>

<!--内容主体-->
<div class="ui grid attached container" style="margin-top: 14px">
    <div class="ui four wide column">
        <#assign appId=appInfo.id/>
        <#assign appName=appInfo.name/>
        <#assign versionCompareActive="active"/>
        <#include "LeftNavigationMenu.ftl">
    </div>

    <div class="ui twelve wide column">
        <h4 class="ui header top attached block">
        ${compareJob.name}
        </h4>
        <#if compareJob.gitBranch?? || compareJob.gitOldCommit?? || compareJob.gitNewCommit??>
            <div class="ui attached segment" style="padding-top: 10px; padding-bottom: 10px; color: rgba(0,0,0,.6);">
                <#if compareJob.gitBranch??>
                    分支: <code>${compareJob.gitBranch}</code>
                </#if>
                <#if compareJob.gitOldCommit??>
                    <#if compareJob.gitBranch??> ｜ </#if>旧: <code class="commit-id" data-content="${compareJob.gitOldCommit}" data-position="top center">${(compareJob.gitOldCommit?length > 8)?then(compareJob.gitOldCommit?substring(0,8), compareJob.gitOldCommit)}</code>
                </#if>
                <#if compareJob.gitNewCommit??>
                    <#if compareJob.gitBranch?? || compareJob.gitOldCommit??> ｜ </#if>新: <code class="commit-id" data-content="${compareJob.gitNewCommit}" data-position="top center">${(compareJob.gitNewCommit?length > 8)?then(compareJob.gitNewCommit?substring(0,8), compareJob.gitNewCommit)}</code>
                </#if>
            </div>
        </#if>
        <div class="ui attached segment">
            <div id="compareProgress" data-percent="${compareJob.progress}" class="ui active progress">
                <div class="bar">
                    <div class="progress"></div>
                </div>
                <div id="compareProgressName" class="label">${compareJob.progressName}</div>
            </div>
            <a id="openReport" class="ui primary  button" href="#" style="display: none">查看报告</a>
            <table class="ui celled table">
                <thead>
                <tr>
                    <th>变更项</th>
                    <th>新增</th>
                    <th>修改</th>
                    <th>删除</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td>类</td>
                    <td id="classAdd">0</td>
                    <td id="classUpdate">0</td>
                    <td id="classDelete">0</td>
                </tr>
                <tr>
                    <td>方法</td>
                    <td id="methodAdd">0</td>
                    <td id="methodUpdate">0</td>
                    <td id="methodDelete">0</td>
                </tr>
                </tbody>
            </table>
            <h4 class="header">日志输出</h4>
            <div id="compareLogger" class="ui compact message">
            ${compareJob.log?replace("\n","</br>")}
            </div>
        </div>
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
    $(function () {
        var future = setInterval(function () {
            var results = refreshJob();
            if (!results || !results.data) {
                window.location.href = "/p/${project.id}/version/report/${compareJob.id}";
                return;
            }
            if (results.data.finish) {
                clearInterval(future);
                $("#compareProgress").removeClass("active warning").addClass("success");
                $("#compareProgressName").html("正在生成报告，即将自动跳转...");
                var reportUrl = "/p/${project.id}/version/report/" + results.data.id;
                $("#openReport").attr("href", reportUrl).show();
                setTimeout(function () {
                    window.location.href = reportUrl;
                }, 400);
            }
        }, 500);
    });

    function refreshJob() {
        var results = $.ajax({
            url: "/p/${project.id}/version/compare/get?jobId=${compareJob.id}",
            async: false
        }).responseJSON;

        if (!results || !results.data) {
            return results;
        }

        var data = results.data;
        var $progress = $("#compareProgress");
        $progress.progress('set percent', data.progress || 0);
        $("#compareProgressName").html(data.progressName || "等待结果");

        if (data.finish) {
            $progress.removeClass("active warning").addClass("success");
        } else {
            $progress.addClass("active").removeClass("success warning");
        }

        $("#classAdd").html(data.addClassCount || 0);
        $("#classUpdate").html(data.updateClassCount || 0);
        $("#classDelete").html(data.deleteClassCount || 0);
        $("#methodAdd").html(data.addMethodCount || 0);
        $("#methodUpdate").html(data.updateMethodCount || 0);
        $("#methodDelete").html(data.deleteMethodCount || 0);
        $("#compareLogger").html((data.log || '').replace(/(\r\n)|(\n)/g,'<br>'));
        return results;
    }
</script>
</body>
</html>

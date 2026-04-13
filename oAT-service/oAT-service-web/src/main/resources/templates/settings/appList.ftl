<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-应用列表</title>
    <#include "../common.ftl">
    <#--属性编辑器-->
    <script src="/js/codemirror.min.js"></script>
    <script src="/js/properties.js"></script>
    <link href="/css/codemirror.min.css" rel="stylesheet">
    <style type="text/css">
        /*自动调整编辑器高度*/
        .CodeMirror {
            border: 1px solid #eee;
            height: auto;
        }

        .CodeMirror-scroll {
            overflow-y: hidden;
            overflow-x: auto;
        }

    </style>
</head>
<body>
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">设置</div>
</div>

<!--内容主体-->
<div id="center-content" class="ui grid attached container" style="margin-top: 5px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign settingsAppActive="active"/>
        <#assign appListItemActive="active"/>
        <#assign loginRole=loginNameRole />
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <h4 class="ui top attached block header">应用列表
            <#if loginNameRole != "visitor">
                <a class="ui button positive" style="float: right;margin-top: -8px" href="/p/${project.id}/app/create">新增</a>
            </#if>
        </h4>
        <div class="ui attached segment">
            <table class="ui celled table">
                <thead>
                <th>类型</th>
                <th>应用名称</th>
                <th>当前版本</th>
                <th>应用工程</th>
                <th class="center aligned">在线实例</th>
                <th class="center aligned">操作</th>
                </thead>
                <tbody>
                <#list apps as app >
                    <tr>
                        <td>
                            <#if app.createProjectId==project.id>
                                主导应用
                            <#else >
                                参与应用
                            </#if>
                        </td>
                        <td>
                            ${app.name}
                        </td>
                        <td>
                            <#if app.currentVersion??>
                                <span title="版本: ${app.currentVersion}&#10;分支: ${app.currentBranch!'未设置'}&#10;Commit: ${app.currentCommitId!'未设置'}">
                                    ${app.currentVersion}
                                    <#if app.currentBranch?? && app.currentBranch != "">
                                        <br><span style="color: grey; font-size: 0.9em;">分支: ${app.currentBranch}</span>
                                    </#if>
                                    <#if app.currentCommitId?? && app.currentCommitId != "">
                                        <br><span style="color: grey; font-size: 0.8em;">Commit: ${app.currentCommitId?substring(0, 7)}</span>
                                    </#if>
                                </span>
                            <#else>
                                <span style="color: grey">未设置</span>
                            </#if>
                        </td>
                        <td>
                            ${app.srcName!}
                        </td>
                        <td class="center aligned">
                            <a href="/p/${project.id}/app/online?appId=${app.id}">${app.onlineCount!}</a>
                        </td>

                        <td class="center aligned">
                            <#if loginNameRole != "visitor">
                                <#if app.createProjectId==project.id>
                                <#--<i class=" ui setting link icon " onclick="openEditDialog('${app.id}')"></i>-->

                                    <div class="ui dropdown quickMenu">
                                        <i class="ui setting link icon"></i>
                                        <div class="left menu">
                                            <div class="item" onclick="openEditDialog('${app.id}');">
                                                <i class="edit icon"></i>编辑
                                            </div>
                                            <a class="item" href="${app.id}/oAT.key" download=""><i class="download icon"></i>下载注册文件</a>
                                            <div class="divider"></div>
                                            <div class="item" onclick="openDelDialog('${app.id}');">
                                                <i class="remove icon red color"></i>
                                                <span class="text" style="color: red">删除</span>
                                            </div>
                                        </div>
                                    </div>
                                </#if>
                            </#if>
                        </td>
                    </tr>
                </#list>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div id="editDialog" class="ui modal standard ">

</div>

<!-- 删除用例弹出框-->
<div id="deleteDialog" class="ui small modal">
    <div class="header">删除应用</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该应用吗？
        </div>
        <p id="deleteUsecaseContent"></p>
    </div>
    <div class="actions">
        <a id="deleteAppButton" class="ui negative button">删除</a>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    $('#center-content .ui.dropdown').dropdown({
        on: 'hover'
    });

    function openEditDialog(appId) {
        $("#editDialog").load('/p/${project.id}/app/edit?appId=' + appId);
        $("#editDialog").modal('show');
    }

    function openDelDialog(appId) {
        // Set up the click handler for the confirmation button
        $("#deleteAppButton").off('click').on('click', function() {
            $.post('/p/${project.id}/app/doDelete', {appId: appId}, function(res) {
                if (res.success || res.result) {
                    showToast(res.message || '删除成功', 'success');
                    $("#deleteDialog").modal('hide');
                    setTimeout(function() {
                        location.reload();
                    }, 1000);
                } else {
                    showToast(res.message || '删除失败', 'error');
                }
            }).fail(function() {
                showToast('网络请求失败', 'error');
            });
        });

        $("#deleteDialog").modal('show');
    }
</script>
</body>
</html>

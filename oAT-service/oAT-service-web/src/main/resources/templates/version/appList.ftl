<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-应用列表</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">

<!--面包屑导航-->
<div class="ui container">
    <div class="ui small breadcrumb version-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <i class="right angle icon divider"></i>
        <div class="active section">版本中心</div>
    </div>
</div>

<!--内容主体-->
<div class="ui container version-apps-page">
    <div class="version-apps-card">
        <div class="version-apps-header">
            <div>
                <div class="version-apps-kicker">
                    <i class="tags icon"></i>
                    版本中心
                </div>
                <h1 class="version-apps-title">应用列表</h1>
                <p class="version-apps-desc">选择应用进入版本管理，可新增版本、生成报告或执行版本比对。</p>
            </div>
        </div>
        <div class="version-apps-body">
            <div class="ui info message version-apps-tip">
                <i class="close icon"></i>
                <div class="header">
                    操作提示
                </div>
                <p>请在列表中选择你要进行版本管理的应用</p>
            </div>
            <div class="version-table-wrap">
                <table class="ui selectable table">
                    <tbody>
                    <#list  apps as app>
                    <tr>
                        <td><a class="version-app-row-title" href="/p/${project.id}/${app.id}/version/list">${app.name}</a></td>
                        <td class="right aligned">
                            <a class="ui small button" href="/p/${project.id}/${app.id}/version/new">新增版本</a>
                            <a class="ui small button primary" href="/p/${project.id}/${app.id}/version/compare">比对版本</a>
                        </td>
                    </tr>
                    </#list>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script>
    // $('.ui.dropdown').dropdown({
    //     on: 'click'
    // });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.message .close')
            .on('click', function() {
                $(this).closest('.message').transition('fade');
            });
</script>
</body>
</html>
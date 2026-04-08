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
<div class="ui breadcrumb" style="margin: 20px 0px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <i class="right angle icon divider"></i>
    <div class="active section">版本中心</div>
</div>

<!--内容主体-->
<div class="ui text container  " style="margin-top: 50px">
    <!-- 中间内容 -->
    <div class="ui message">
        <i class="close icon"></i>
        <div class="header">
            操作提示
        </div>
        <p>请在列表中选择你要进行版本管理的应用</p>
    </div>
    <h3 class="ui top attached block header">应用列表</h3>
    <div class="ui attached segment" style="padding: 0px">
        <table class="ui selectable   table" style="margin: -1px -1px -1px -1px">
            <tbody>
            <#list  apps as app>
            <tr>
                <td class=""><a href="/p/${project.id}/${app.id}/version/list">${app.name}</td>
                <td class="right aligned">
                    <a class="ui small button " href="/p/${project.id}/${app.id}/version/new">新增版本</a>
                    <a class="ui small button primary" href="/p/${project.id}/${app.id}/version/compare">比对版本</a>
                </td>
            </tr>
            </#list>

            </tbody>
        </table>

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
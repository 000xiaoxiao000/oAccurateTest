<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>应用与代码管理</title>
    <#include "../common.ftl">
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
     <#assign settingsManageCodeActive="active"/>
     <#assign manageAppCodeActive="active"/>
     <#assign loginRole=loginNameRole />
     <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <h4 class="ui top attached block header">应用列表</h4>
        <div class="ui attached segment">
            <table class="ui celled table">
                <thead>
                <th>类型</th>
                <th>应用名称</th>
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
                                            <a class="item" href="/p/${project.id}/app/${app.id}/repository">
                                                <i class="edit icon"></i>仓库配置
                                            </a>
                                            <a class="item" href="/p/${project.id}/${app.id}/version/list">
                                                <i class="list icon"></i>版本列表
                                            </a>
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
<!--初始化UI-->
<script>
    $('#center-content .ui.dropdown').dropdown({
        on: 'hover'
    });


</script>
</body>

</html>

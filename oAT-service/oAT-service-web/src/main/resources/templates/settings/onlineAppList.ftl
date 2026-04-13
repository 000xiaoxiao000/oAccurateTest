<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-在线应用</title>
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
<div class="ui grid attached  container"  style="margin-top: 5px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
     <#assign settingsOnlineActive="active"/>
     <#assign onlineItemActive="active"/>
     <#assign loginRole=loginNameRole />
     <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <h4 class="ui top attached block header">在线应用</h4>
        <div class="ui attached segment">
            <table class="ui fixed table">
                <thead>
                <th class="three wide">系统IP</th>
                <th>应用名称</th>
                <th>项目工程</th>
                <th class="two wide">agent版本</th>
                <th class="two wide">在线时长</th>
                <th class="two wide right aligned">操作</th>
                </thead>
                <tbody>
                <#list sessions as session>
                    <tr>
                        <td class="three wide">${session.clientInfo.addressIp}</td>
                        <td>
                            ${(session.application.appName)!'未定义'}
                        </td>
                        <td>
                            ${(session.application.projectSrcName)!''}
                        </td>
                        <td class="two wide">${session.clientInfo.agentVersion}</td>
                        <td class="two wide">${session.onlineTime}</td>
                        <td class="two wide right aligned">
                            <a class="ui button" onclick="$('#app${session_index}').toggle();">详情</a>
                        </td>
                    </tr>
                    <!-- 列表详情 -->
                    <tr>
                        <td id="app${session_index}" colspan="6" style="display: none;padding: 0px">
                            <!--<div class="ui top block attached header ">详细信息</div>-->
                            <div class="ui red attached segment">
                                <div class="ui list">
                                    <div class="item">
                                        <div class="header"> 部署路径：</div>
                                        <div class="description " style="margin: 5px" >
                                            ${session.clientInfo.systemDir}
                                        </div>
                                    </div>
                                    <div class="item">
                                        <div class="header">进程ID：</div>
                                        <div class="description" style="margin: 5px">
                                            ${session.clientInfo.pid}
                                        </div>

                                    </div>
                                    <div class="item">
                                        <div class="header">JVM版本：</div>
                                        <div class="description" style="margin: 5px">
                                            ${session.clientInfo.jvmVersion}
                                        </div>
                                    </div>
                                    <div class="item">
                                        <div class="header">JVM启动参数：</div>
                                        <div  style="word-wrap: break-word" style="margin: 5px">
                                            ${(session.clientInfo.jvmOption)!''}
                                        </div>
                                    </div>
                                </div>
                            </div>
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
    /*$('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.poping.up').popup();*/

</script>

<script>
    <!--显示节点详情-->
    function openAppDetails(appId) {
        $("#" + appId).toggle();
        // var display= $("#"+appId).css('display');
        //  if (display == 'none') {
        //      $("#" + appId).css('display', 'table-cell');
        //  } else {
        //      $("#" + appId).css('display', 'none');
        //  }
    }
</script>
</body>

</html>

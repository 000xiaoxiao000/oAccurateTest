<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/html">
<head>
    <meta charset="UTF-8">
    <title>版本中心-比对控制台</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign appCenterActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui small breadcrumb">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <div class=" section"> ${appInfo.name} </div>
    <span class="divider">/</span>
    <div class="active section">版本比对</div>
</div>

<!--内容主体-->
<div class="ui grid attached container" style="margin-top: 14px">
    <div class="ui four wide column">
        <div class="ui vertical attached menu">
            <div class=" header item " style="background: #f3f4f5">
                <div class="ui inline click dropdown">
                    <span>${appInfo.name}</span>
                    <i class="icon click dropdown"></i>
                    <div class="menu">
                        <div class="ui search icon input">
                            <i class="search icon"></i>
                            <input type="text" name="search" placeholder="搜索...">
                        </div>
                        <div class="header">
                            选择应用
                        </div>
                        <div class="divider"></div>
                    <#list apps as a>
                        <a class="item" href="/p/${project.id}/${a.id}/version/compare">
                            ${a.name}
                        </a>
                    </#list>
                    </div>
                </div>
            </div>
            <a class="item" href="/p/${project.id}/${appInfo.id}/version/list">
                版本列表
            </a>
            <a class="item" href="/p/${project.id}/${appInfo.id}/version/report/list">
                报告列表
            </a>
            <a class="item active" href="/p/${project.id}/${appInfo.id}/version/compare">
                版本比对
            </a>
        </div>
    </div>

    <div class="ui twelve wide column">
        <h4 class="ui header top attached block">
        ${compareJob.name}
        </h4>
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
    $(function () {
        // 开启定时任务，时间间隔为3000 ms。
        clearInterval
        var future = setInterval(function () {
            var results = refreshJob();
            if (results.data.finish) {
                clearInterval(future);
                $("#openReport").attr("href", "/p/${project.id}/version/report/" + results.data.id);
                $("#openReport").show();
            }
        }, 500);
    });

    function refreshJob() {
        var results = $.ajax({
            url: "/p/${project.id}/version/compare/get?jobId=${compareJob.id}",
            async: false
        }).responseJSON;

        var $progress = $("#compareProgress");
        $progress.progress('set percent', results.data.progress);
        $("#compareProgressName").html(results.data.progressName);

        if (results.data.finish) {
            $progress.removeClass("active").addClass("success");
        } else {
            $progress.addClass("active").removeClass("success");
        }

        $("#classAdd").html(results.data.addClassCount);
        $("#classUpdate").html(results.data.updateClassCount);
        $("#classDelete").html(results.data.deleteClassCount);
        $("#methodAdd").html(results.data.addMethodCount);
        $("#methodUpdate").html(results.data.updateMethodCount);
        $("#methodDelete").html(results.data.deleteMethodCount);
        // 换行替换成<br>
        $("#compareLogger").html(results.data.log.replace(/(\r\n)|(\n)/g,'<br>'));
        return results;
    }
</script>
</body>
</html>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>${app.name}-系统快照</title>
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
    <div class=" section"> ${app.name} </div>
    <span class="divider">/</span>
    <div class="active section">系统快照</div>
</div>
<!--过滤条件-->
<div class="ui container">
    <div class="ui grid">
        <div class="four wide column"></div>
        <div class="ui twelve wide column" style="padding-bottom: 0px">
            <table class="ui basic compact table" style="border: none">
                <tbody>
                <tr>
                    <td>
                        <a href="list?directoryId=root&sort=${sort!'updateTime'}">/ROOT</a>
                        <#list dirTiers as tie>
                            /
                            <#if tie_index ==(dirTiers?size)-1>
                                ${tie.name}
                            <#else >
                                <a href="list?directoryId=${tie.id}&sort=${sort!'updateTime'}"> ${tie.name} </a>
                            </#if>
                        </#list>
                    </td>
                    <td>
                        <form id="filterForm" class="ui form" action="list">
                            <input type="hidden" name="directoryId" value="${currentDir}">
                            <div class="ui text menu" style="margin: auto;float: right">
                                <div class="dropdown item">
                                    <i class="icon refresh"> </i>
                                    <a href="javascript:location.reload()">刷新</a>
                                </div>
                                <div class="ui filter dropdown item" tabindex="2">
                                    <input id="filterSort" type="hidden" name="sort" value="${sort!'updateTime'}">
                                    <i class="ui sort numeric ascending link icon"> </i>
                                    <span class="text"><#if (sort=='name')??>快照名称<#else >更新时间</#if></span>
                                    <div class="left menu transition hidden" tabindex="-1">
                                        <div class="item active selected" data-value="updateTime">更新时间</div>
                                        <div class="item" data-value="name">快照名称</div>
                                    </div>
                                </div>
                                <#if loginNameRole != "visitor">
                                    <div class="dropdown item" onclick="openDirectoryDialog();">
                                        <i class="folder icon"></i>
                                        新建目录
                                    </div>
                                </#if>
                            </div>
                        </form>
                    </td>
                </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>
<!--内容主体-->
<div class="ui grid attached container">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical attached menu">
            <div class=" header item " style="background: #f3f4f5">
                <div class="ui inline click dropdown">
                    <span>${app.name}</span>
                    <i class="icon click dropdown"></i>
                    <div class="menu">
                        <div class="ui search  icon input">
                            <i class="search icon"></i>
                            <input type="text" name="search" placeholder="搜索...">
                        </div>
                        <div class="header">选择应用</div>
                        <div class="divider"></div>
                        <#list apps as a>
                            <a class="item" href="/p/${project.id}/${a.id}/snapshot/list">
                                ${a.name}
                            </a>
                        </#list>
                    </div>
                </div>
            </div>
            <a class=" active item  " href="/p/${project.id}/${app.id}/snapshot/list">
                系统快照
            </a>
            <a class="item" href="/p/${project.id}/${app.id}/version/compare">
                版本比对
            </a>
            <a class="ui item" href="/p/${project.id}/app/${app.id}/settings">
                设置
            </a>
        </div>
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <table class="ui selectable table">
            <thead>
            <tr>
                <th>快照名称</th>
                <th class="three wide">更新时间</th>
                <th class="one wide">操作</th>
            </tr>
            </thead>
            <tbody>
            <#--            路径-->
            <#list dirs as dir>
                <tr directoryId="${dir.id}">
                    <td>
                        <a href="list?directoryId=${dir.id}&sort=${sort!'updateTime'}"><i class="folder icon"></i> ${dir.name}
                        </a>
                    </td>
                    <td><#--${dir.updateTime?datetime}--></td>
                    <td>
                        <#if loginNameRole != "visitor">
                            <div class="ui dropdown quickMenu">
                                <i class="list link setting icon"></i>

                                <div class="left menu">
                                    <div class="item" onclick="openDirectoryDialog('${dir.id}','${dir.name}');">
                                        <i class="edit icon"></i>重命名
                                    </div>
                                    <div class="divider"></div>

                                    <div class="item" onclick="openDeleteDirectoryDialog('${dir.id}');">
                                        <i class="remove icon red color"></i>
                                        <span class="text" style="color: red">删除</span>
                                    </div>
                                </div>
                            </div>
                        </#if>
                    </td>
                </tr>
            </#list>
            <#--快照-->
            <#list snapshots as snapshot >
                <tr>
                    <td>
                        <a href="detail/${snapshot.id}">
                            <i class="file outline icon"></i>
                            ${snapshot.title}
                        </a>
                    </td>
                    <td>${(snapshot.versionLastUpdate?string('yyyy-MM-dd HH:mm:ss'))!'-'}</td>
                    <td>
                        <#if loginNameRole != "visitor">
                            <div class="ui dropdown quickMenu" tabindex="0">
                                <i class="list link setting icon"></i>
                                <div class="left menu" tabindex="-1">
                                    <div class="divider"></div>
                                    <div class="item" onclick="openDeleteSystemSnapshotDialog('${snapshot.id}');">
                                        <i class="remove icon red color"></i>
                                        <span class="text" style="color: red">删除</span>
                                    </div>
                                </div>
                            </div>
                        </#if>
                    </td>
                </tr>
            </#list>
            </tbody>
        </table>
    </div>
</div>

<!-- 修改目录弹出框-->
<div id="directoryDialog" class="ui small modal">
    <div class="header">编辑快照目录</div>
    <div class="content">
        <form id="directoryForm" class="ui form">
            <input type="hidden" name="id">
            <input type="hidden" value="${currentDir}" name="parentId">
            <label>
                <input type="text" name="name" placeholder="请输入目录名称">
            </label>
        </form>
    </div>
    <div class="actions">
        <div class="ui positive button" onclick="doSaveDirectory();">保存</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<!-- 删除快照路径弹出框-->
<div id="deleteDirectoryDialog" class="ui small modal">
    <div class="header">删除快照路径</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该快照路径吗？
        </div>
        <p id="deleteDirectoryContent"></p>
    </div>
    <div class="actions">
        <div id="deleteDirectoryButton" class="ui negative button">删除</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<!-- 删除快照弹出框-->
<div id="deleteSystemSnapshotDialog" class="ui small modal">
    <div class="header">删除用例</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该用例吗？
        </div>
    </div>
    <div class="actions">
        <div id="deleteSystemSnapshotButton" class="ui negative button">删除</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });

    $(function () {
        $('.ui.click.dropdown').dropdown({
            on: 'click'
        });
        $('.ui.filter.dropdown').dropdown({
            on: 'click'
        });
    });

    // 打开 目录编辑窗口
    function openDirectoryDialog(id, name) {
        $('#directoryForm')[0].reset();
        $("#directoryForm [name='id']").val(id);
        $("#directoryForm [name='name']").val(name);
        $("#directoryDialog").modal('show');
    }

    function doSaveDirectory() {
        var resultInform = $.ajax({
            url: "/p/${project.id}/${app.id}/snapshot/directory",
            type: 'POST',
            data: $("#directoryForm").serialize(),
            async: false
        }).responseJSON;
        if (resultInform.result) {
            // 刷新当前页
            window.location = window.location;
        } else {
            showToast(resultInform.message, 'error');
        }
    }

    //打开 目录删除窗口和删除
    function openDeleteDirectoryDialog(directoryId) {
        $("#deleteDirectoryButton").click(function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/${app.id}/snapshot/directory?directoryId=" + directoryId,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                // 删除目录
                $("tr[directoryId='" + directoryId + "']").remove();
            } else {
                showToast(resultInform.message, 'error');
            }
        });
        $('#deleteDirectoryDialog').modal('show');
    }

    /**
     *删除系统快照
     */
    function openDeleteSystemSnapshotDialog(id) {
        $("#deleteSystemSnapshotButton").click(function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/${app.id}/snapshot/doDelete?id=" + id,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                showToast(resultInform.message, 'success');
                // 刷新当前页 ，并传递删除成功的消息
                window.location = window.location;
            } else {
                showToast(resultInform.message, 'error');
            }
        });
        $("#deleteSystemSnapshotDialog").modal('show');
    }

</script>
<script>
    $(function () {
        $("#filterSort").change(function () {
            $("#filterForm").submit();
        });
    });
</script>
</body>
</html>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-用例列表</title>
    <#include "../common.ftl">
</head>
<body>
<#assign usecaseItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">用例列表</div>
</div>

<!--过滤条件-->
<div class="ui container">
    <div class="ui grid">
        <div class="four wide column"></div>
        <div class="ui twelve wide column" style="padding-bottom: 0px">


            <table class="ui basic compact table" style="border: none">
                <tr>
                    <td>
                        <a class="ui tiny teal basic button" href="/p/${project.id}/usecase/list">
                            <i class="home icon"></i>用例中心
                        </a>
                        &nbsp;&nbsp;
                        <a href="/p/${project.id}/usecase/list">/ROOT</a>
                        <#if dirTiers??>
                            <#list dirTiers as tie>
                                <#if tie_index ==(dirTiers?size)-1>
                                    ${tie.name}
                                <#else>
                                    <a href="/p/${project.id}/usecase/list?directory=${tie.id}"> ${tie.name} </a>
                                </#if>
                            </#list>
                        </#if>
                    </td>
                    <td>
                        <form id="filterForm" class="ui form" action="list">
                            <input type="hidden" name="directory" value="${directory!'root'}">
                            <div class="ui text menu" style="margin: auto;float: right">
                                <div class="ui filter dropdown item" tabindex="2">
                                    <input id="filterSort" type="hidden" name="sort" value="${sort!}">
                                    <i class="ui sort numeric ascending link icon"> </i>
                                    <span class="text">排序</span>
                                    <div class="left menu transition hidden" tabindex="-1">
                                        <div class="item" data-value="updateTime">更新时间</div>
                                        <div class="item" data-value="name">快照名称</div>
                                    </div>
                                </div>
                                <div id="newAction" class="ui pointing dropdown item" tabindex="-1">
                                    <#-- <i class="add icon"> </i>
                                     <span class="text "> 新建</span>-->
                                    <div class="ui primary button">&nbsp新建&nbsp</div>
                                    <div class="menu" tabindex="1">
                                        <div class="item" onclick="openAddFolderDialog()"><i class="folder icon"></i>新建目录
                                        </div>
                                        <a class="item" href="/p/${project.id}/usecase/new?directory=${currentDir}"><i
                                                    class="file icon"></i>新建用例</a>
                                    </div>
                                </div>
                            </div>
                        </form>
                    </td>
                </tr>
            </table>
        </div>
    </div>
</div>
<!--内容主体-->
<div class="ui grid attached  container">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical menu">
            <div class="header item">用例中心</div>
            <a class="active item" href="/p/${project.id}/usecase/list">
                用例中心
            </a>
            <#if app??>
                <a class="item" href="/p/${project.id}/${app.id}/snapshot/list">
                    系统快照
                </a>
                <a class="ui item" href="/p/${project.id}/app/${app.id}/settings">
                    设置
                </a>
            <#else>
                <a class="ui item" href="/p/${project.id}/edit">
                    设置
                </a>
            </#if>
        </div>
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <table class="ui selectable table">
            <thead>
            <tr>
                <th>文件名</th>
                <th class="three wide">更新时间</th>
                <th class="one wide">操作</th>
            </tr>
            </thead>
            <tbody>
            <#list dirs as dir>
                <tr>
                    <td>
                        <a href="/p/${project.id}/usecase/list?directory=${dir.id}"> <i class="folder icon"></i> ${dir.name}
                        </a>
                    </td>
                    <td>${dir.updateTime?datetime}</td>
                    <td>
                        <div class="ui dropdown quickMenu">
                            <i class="list link setting icon"></i>
                            <div class="left menu">
                                <div class="item" onclick="openEditFolderDialog('${dir.id}','${dir.name}')">
                                    <i class="edit icon"></i>重命名
                                </div>
                                <div class="divider"></div>
                                <div class="item">
                                    <i class="remove icon red color"></i>
                                    <span class="text" style="color: red" onclick="openDelFolderDialog('${dir.id}','${dir.name}')">删除</span>
                                </div>
                            </div>
                        </div>
                    </td>
                </tr>
            </#list>
            <#list cases as cas>
                <tr>
                    <td>
                        <a href="/p/${project.id}/usecase/detail?id=${cas.id}"> <i
                                    class="file outline icon"></i> ${cas.title}</a>
                    </td>
                    <td>${cas.updateTime?datetime}</td>
                    <td>
                        <div class="ui dropdown quickMenu">
                            <i class="list link setting icon"></i>
                            <div class="left menu">
                                <div class="item"><i class="share alternate icon"></i>共享 <span
                                            class="description">生成链接</span></div>
                                <div class="divider"></div>
                                <a class="item" href="/p/${project.id}/usecase/edit?id=${cas.id}"><i class="edit icon"></i>编辑</a>
                                <div class="divider"></div>
                                <div class="item" onclick="openDeleteCaseDialog('${cas.id}','${cas.title}')">
                                    <i class="remove icon red color"></i>
                                    <span class="text" style="color: red">删除</span>
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

<!-- 新建目录弹出框-->
<div id="addFolder" class="ui small modal">
    <div class="header">新建目录</div>
    <div class="content">
        <form id="addFolderForm" class="ui form">
            <input type="hidden" value="${currentDir}" name="parentId">
            <input type="text" name="name" placeholder="请输入目录名称">
        </form>
    </div>
    <div class="actions">
        <div class="ui positive button" onclick="doCreateFolder()">创建</div>
        <div class="ui cancel button">不</div>
    </div>
</div>
<!-- 修改目录弹出框-->
<div id="editFolder" class="ui small modal">
    <div class="header">修改目录</div>
    <div class="content">
        <form id="editFolderForm" class="ui form">
            <input id="editFolderId" type="hidden" name="id">
            <input type="hidden" value="${currentDir}" name="parentId">
            <input id="editFolderName" type="text" name="name" placeholder="请输入目录名称">
        </form>
    </div>
    <div class="actions">
        <div class="ui positive button" onclick="doSaveFolder()">更新</div>
        <div class="ui cancel button">不</div>
    </div>
</div>
<!-- 删除目录弹出框-->
<div id="delFolder" class="ui small modal">
    <div class="header">删除用例路径</div>
    <div class="ui negative message">
        <input type="hidden" value="${currentDir}" id="parentId">
        <div class="header">
            你确定删除该用例路径吗？
        </div>
        <p id="deFolderContent"></p>
    </div>
    <div class="actions">
        <div id="deFolderButton" class="ui negative button">删除</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<!-- 删除用例弹出框-->
<div id="deleteUsecaseDialog" class="ui small modal">
    <div class="header">删除用例</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该用例吗？
        </div>
        <p id="deleteUsecaseContent"></p>
    </div>
    <div class="actions">
        <div id="deleteUsecaseButton" class="ui negative button">删除</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    $('.ui.dropdown.quickMenu').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'hover'
    });
    $('#newAction').dropdown({
        on: 'click'
    });

    // 打开 新增目录窗口
    function openAddFolderDialog() {
        $('#addFolderForm')[0].reset();
        $("#addFolder").modal('show');
    }

    // 打开 编辑目录窗口
    function openEditFolderDialog(id, name) {
        $('#editFolderForm')[0].reset();
        //  初始化值
        $('#editFolderId').val(id);
        $('#editFolderName').val(name);
        $("#editFolder").modal('show');
    }

    // 打开 删除目录窗口并删除目录
    function openDelFolderDialog(directoryId, directoryName) {
        $("#deFolderContent").html(directoryName);
        var parentId = $("#parentId").val();
        $("#deFolderButton").click(function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/usecase/directory/del?id=" + directoryId + "&parentId=" + parentId + "&name=" + directoryName,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                // 刷新当前页 ，并传递删除成功的消息
                 window.location = window.location;
            } else {
                showToast(resultInform.message, 'error');
            }
        });
        $('#delFolder').modal('show');
    }

    /**
     *打开删除对话框并删除用例
     * @param id 用例id
     * @param name 用例名称
     */
    function openDeleteCaseDialog(id, name) {
        $("#deleteUsecaseContent").html(name)
        $("#deleteUsecaseButton").click(function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/usecase/doDelete?id=" + id,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                console.log(resultInform)
                // 刷新当前页 ，并传递删除成功的消息
                 window.location = window.location;
            } else {
                showToast(resultInform.errorMessage, 'error');
            }
        });
        $("#deleteUsecaseDialog").modal('show');
    }

    function doCreateFolder() {
        var resultInform = $.ajax({
            url: "/p/${project.id}/usecase/directory/new",
            data: $("#addFolderForm").serialize(),
            async: false
        }).responseJSON;
        if (resultInform.result) {
            // 刷新当前页
             window.location = window.location;
        } else {
            showToast(resultInform.errorMessage, 'error');
        }
    }

    function doSaveFolder() {
        var resultInform = $.ajax({
            url: "/p/${project.id}/usecase/directory/save",
            data: $("#editFolderForm").serialize(),
            async: false
        }).responseJSON;
        if (resultInform.result) {
            // 刷新当前页
             window.location = window.location;
        } else {
            showToast(resultInform.errorMessage, 'error');
        }
    }

    $(function () {
        $("#filterSort").change(function () {
            $("#filterForm").submit();
        });
    });
</script>

</body>
</html>

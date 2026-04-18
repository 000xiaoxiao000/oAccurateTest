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
<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
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
                        <a href="list?directoryId=root&sort=${sort!'updateTime'}<#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>">/ROOT</a>
                        <#list dirTiers as tie>
                            /
                            <#if tie_index ==(dirTiers?size)-1>
                                ${tie.name}
                            <#else >
                                <a href="list?directoryId=${tie.id}&sort=${sort!'updateTime'}<#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>"> ${tie.name} </a>
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
                                <div class="ui icon input" style="margin-right: 8px; display: inline-flex; align-items: center; gap: 6px;">
                                    <input type="text" name="keyword" value="${keyword!}" placeholder="搜索快照名称..." style="min-width: 180px;">
                                    <i class="search icon"></i>
                                    <#if (keyword!'')?has_content>
                                        <a class="ui basic mini button" href="list?directoryId=${currentDir}&sort=${sort!'updateTime'}">清空</a>
                                    </#if>
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
        <#assign snapshotGroupName=app.name/>
        <#assign snapshotListHref="/p/${project.id}/${app.id}/snapshot/list"/>
        <#assign settingsHref="/p/${project.id}/app/${app.id}/settings"/>
        <#assign systemSnapshotListActive="active"/>
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <style>
            #systemSnapshotListTable thead th,
            #systemSnapshotListTable tbody td {
                padding-top: 0.56em;
                padding-bottom: 0.56em;
            }

            #systemSnapshotListTable .snapshot-title,
            #systemSnapshotListTable .dir-title {
                display: inline-flex;
                align-items: center;
                gap: 0.35em;
                max-width: 100%;
            }

            #systemSnapshotListTable .snapshot-title span,
            #systemSnapshotListTable .dir-title span {
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }

            #systemSnapshotListTable .meta-text {
                font-size: 0.88em;
                color: #666;
                white-space: nowrap;
            }

            #systemSnapshotListTable .quickMenu > .icon {
                margin: 0;
            }

            #systemSnapshotListTable .empty-state-row td {
                color: #999;
                padding: 30px 0;
                text-align: center;
            }
        </style>
        <table id="systemSnapshotListTable" class="ui selectable compact very basic table" style="table-layout: fixed;">
            <thead>
            <tr>
                <th>快照名称</th>
                <th style="width: 110px;">更新时间</th>
                <th style="width: 56px;">操作</th>
            </tr>
            </thead>
            <tbody id="systemSnapshotTableBody">
            <#--            路径-->
            <#list dirs as dir>
                <tr data-directory-id="${dir.id}">
                    <td>
                        <a class="dir-title" href="list?directoryId=${dir.id}&sort=${sort!'updateTime'}<#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>"><i class="folder icon"></i><span>${dir.name}</span>
                        </a>
                    </td>
                    <td class="meta-text">-</td>
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
                <tr data-system-snapshot-id="${snapshot.id}">
                    <td>
                        <a class="snapshot-title" href="detail/${snapshot.id}">
                            <i class="file outline icon"></i>
                            <span>${snapshot.title}</span>
                        </a>
                    </td>
                    <td class="meta-text"><@relativeTime value=snapshot.versionLastUpdate showTooltip=true /></td>
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
            <#if dirs?size == 0 && snapshots?size == 0>
                <tr id="systemSnapshotEmptyRow" class="empty-state-row">
                    <td colspan="3">暂无数据</td>
                </tr>
            </#if>
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

    function getResultMessage(resultInform, fallbackMessage) {
        return resultInform?.errorMessage || resultInform?.message || fallbackMessage;
    }

    function ensureSystemSnapshotEmptyState() {
        var hasDataRow = $('#systemSnapshotTableBody tr[data-directory-id], #systemSnapshotTableBody tr[data-system-snapshot-id]').length > 0;
        if (!hasDataRow && $('#systemSnapshotEmptyRow').length === 0) {
            $('#systemSnapshotTableBody').append('<tr id="systemSnapshotEmptyRow" class="empty-state-row"><td colspan="3">暂无数据</td></tr>');
        }
        if (hasDataRow) {
            $('#systemSnapshotEmptyRow').remove();
        }
    }

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
            showToast(getResultMessage(resultInform, '目录保存成功'), 'success');
            window.location = window.location;
        } else {
            showToast(getResultMessage(resultInform, '目录保存失败'), 'error');
        }
    }

    //打开 目录删除窗口和删除
    function openDeleteDirectoryDialog(directoryId) {
        $("#deleteDirectoryButton").off('click').on('click', function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/${app.id}/snapshot/directory?directoryId=" + directoryId,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                showToast(getResultMessage(resultInform, '目录删除成功'), 'success');
                $("#deleteDirectoryDialog").modal('hide');
                $("tr[data-directory-id='" + directoryId + "']").remove();
                ensureSystemSnapshotEmptyState();
            } else {
                showToast(getResultMessage(resultInform, '目录删除失败'), 'error');
            }
        });
        $('#deleteDirectoryDialog').modal('show');
    }

    /**
     *删除系统快照
     */
    function openDeleteSystemSnapshotDialog(id) {
        $("#deleteSystemSnapshotButton").off('click').on('click', function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/${app.id}/snapshot/doDelete?id=" + id,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                showToast(getResultMessage(resultInform, '删除快照成功'), 'success');
                $("#deleteSystemSnapshotDialog").modal('hide');
                $("tr[data-system-snapshot-id='" + id + "']").remove();
                ensureSystemSnapshotEmptyState();
            } else {
                showToast(getResultMessage(resultInform, '删除快照失败'), 'error');
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

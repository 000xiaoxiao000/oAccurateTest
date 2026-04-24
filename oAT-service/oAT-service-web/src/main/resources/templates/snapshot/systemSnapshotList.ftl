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
<div class="ui container app-unified-page">
    <div class="ui small breadcrumb app-unified-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
        <span class="divider">/</span>
        <div class="active section">系统快照</div>
    </div>
    <#if (missingSnapshotId!'')?has_content>
        <div class="ui warning message">
            <div class="header">系统快照不存在或已被删除</div>
            <p>未找到系统快照 ID：${missingSnapshotId}</p>
        </div>
    </#if>

    <style>
        .system-snapshot-toolbar__selection {
            display: inline-flex;
            align-items: center;
            padding: 4px 10px;
            border-radius: 999px;
            background: #f8f9fb;
            border: 1px solid #dfe3ea;
            color: #4a5568;
            font-size: 12px;
            line-height: 1.4;
            white-space: nowrap;
        }

        .system-snapshot-toolbar__selection strong {
            color: #2185d0;
            margin: 0 4px;
            font-size: 13px;
        }

        .system-snapshot-batch-actions {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }
    </style>

    <div class="app-toolbar-card">
        <div class="app-toolbar-path">
            <a href="list?directoryId=root&sort=${sort!'updateTime'}<#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>">/ROOT</a>
            <#list dirTiers as tie>
                <span>/</span>
                <#if tie_index ==(dirTiers?size)-1>
                    <span>${tie.name}</span>
                <#else>
                    <a href="list?directoryId=${tie.id}&sort=${sort!'updateTime'}<#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>">${tie.name}</a>
                </#if>
            </#list>
        </div>
        <form id="filterForm" class="ui form app-toolbar-actions" action="list">
            <input type="hidden" name="directoryId" value="${currentDir}">
            <a class="ui basic mini button" href="javascript:location.reload()">
                <i class="refresh icon"></i>刷新
            </a>
            <div class="ui icon input">
                <input type="text" name="keyword" value="${keyword!}" placeholder="搜索快照名称...">
                <i class="search icon"></i>
            </div>
            <#if (keyword!'')?has_content>
                <a class="ui basic mini button" href="list?directoryId=${currentDir}&sort=${sort!'updateTime'}">清空</a>
            </#if>
            <div class="ui filter dropdown item" tabindex="2">
                <input id="filterSort" type="hidden" name="sort" value="${sort!'updateTime'}">
                <i class="ui sort numeric ascending link icon"></i>
                <span class="text"><#if (sort=='name')??>快照名称<#else>更新时间</#if></span>
                <div class="left menu transition hidden" tabindex="-1">
                    <div class="item active selected" data-value="updateTime">更新时间</div>
                    <div class="item" data-value="name">快照名称</div>
                </div>
            </div>
            <#if loginNameRole != "visitor">
                <button type="button" class="ui basic mini button" onclick="openDirectoryDialog();">
                    <i class="folder icon"></i>新建目录
                </button>
            </#if>
        </form>
        <#if loginNameRole != "visitor">
            <div class="system-snapshot-batch-actions">
                <button type="button" class="ui primary mini button" id="systemSnapshotBatchBindTrigger">
                    <i class="linkify icon"></i>
                    批量关联用例
                </button>
                <div class="ui mini basic buttons">
                    <button type="button" class="ui button" id="systemSnapshotSelectAllTrigger">全选</button>
                    <button type="button" class="ui button" id="systemSnapshotClearSelectionTrigger">清空选择</button>
                </div>
                <div class="system-snapshot-toolbar__selection">
                    已选 <strong id="systemSnapshotSelectedCount">0</strong> 项
                </div>
            </div>
        </#if>
    </div>

    <div class="app-unified-layout">
        <div class="app-unified-side">
            <#assign snapshotGroupName=app.name/>
            <#assign snapshotListHref="/p/${project.id}/${app.id}/snapshot/list"/>
            <#assign settingsHref="/p/${project.id}/app/${app.id}/settings"/>
            <#assign systemSnapshotListActive="active"/>
            <#include "LeftNavigationMenu.ftl">
        </div>
        <div class="app-unified-main">
            <div class="app-unified-header">
                <div>
                    <div class="app-unified-kicker">
                        <i class="camera retro icon"></i>
                        系统快照
                    </div>
                    <h1 class="app-unified-title">${app.name} 快照列表</h1>
                    <p class="app-unified-desc">集中管理应用系统快照、目录和接口覆盖情况。</p>
                </div>
            </div>
            <div class="app-unified-content">
        <style>
            #systemSnapshotListTable thead th,
            #systemSnapshotListTable tbody td {
                padding-top: 0.56em;
                padding-bottom: 0.56em;
                vertical-align: middle;
            }

            #systemSnapshotListTable tbody td {
                overflow: visible;
            }

            #systemSnapshotListTable .quickMenu {
                display: flex;
                justify-content: center;
                align-items: center;
                min-width: 24px;
            }

            #systemSnapshotListTable .snapshot-title,
            #systemSnapshotListTable .dir-title {
                display: flex;
                align-items: center;
                gap: 0.35em;
                width: 100%;
                min-width: 0;
            }

            #systemSnapshotListTable .snapshot-title .icon,
            #systemSnapshotListTable .dir-title .icon {
                flex: 0 0 auto;
                margin-right: 0;
            }

            #systemSnapshotListTable .snapshot-title span,
            #systemSnapshotListTable .dir-title span {
                display: block;
                min-width: 0;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }

            #systemSnapshotListTable .meta-text {
                font-size: 0.88em;
                color: #666;
                text-align: right;
                white-space: nowrap;
            }

            #systemSnapshotListTable .api-coverage-cell {
                color: #4a5568;
                font-size: 0.88em;
                text-align: center;
                white-space: nowrap;
            }

            #systemSnapshotListTable .quickMenu > .icon {
                margin: 0;
            }

            #systemSnapshotListTable .snapshot-select-cell {
                width: 30px;
                text-align: left;
                padding-left: 0.28em;
                padding-right: 0.28em;
            }

            #systemSnapshotListTable .snapshot-select-cell .ui.checkbox {
                margin: 0;
                min-height: 17px;
            }

            #systemSnapshotListTable .snapshot-select-cell .ui.checkbox label {
                padding-left: 17px;
            }

            #systemSnapshotListTable .empty-state-row td {
                color: #999;
                padding: 30px 0;
                text-align: center;
            }
        </style>
        <div class="app-unified-table-wrap">
            <table id="systemSnapshotListTable" class="ui selectable compact very basic table unified-list-table" style="table-layout: fixed;">
            <thead>
            <tr>
                <th style="width: 30px;"></th>
                <th>快照名称</th>
                <th style="width: 86px; text-align: center;">接口覆盖</th>
                <th style="width: 82px; text-align: right;">更新时间</th>
                <th style="width: 44px; text-align: center;">操作</th>
            </tr>
            </thead>
            <tbody id="systemSnapshotTableBody">
            <#--            路径-->
            <#list dirs as dir>
                <tr data-directory-id="${dir.id}">
                    <td class="snapshot-select-cell"></td>
                    <td style="min-width: 0;">
                        <a class="dir-title" href="list?directoryId=${dir.id}&sort=${sort!'updateTime'}<#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>"><i class="folder icon"></i><span>${dir.name}</span>
                        </a>
                    </td>
                    <td class="api-coverage-cell">-</td>
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
                    <td class="snapshot-select-cell">
                        <div class="ui checkbox system-snapshot-select-checkbox">
                            <input type="checkbox" class="system-snapshot-batch-select" value="${snapshot.id}">
                            <label></label>
                        </div>
                    </td>
                    <td style="min-width: 0;">
                        <a class="snapshot-title" href="detail/${snapshot.id}">
                            <i class="file outline icon"></i>
                            <span>${snapshot.title}</span>
                        </a>
                    </td>
                    <td class="api-coverage-cell" title="接口覆盖（已覆盖数 / 总数）">${(snapshotApiCoverageTextMap[snapshot.id])!'0 / 0'}</td>
                    <td class="meta-text"><span title="${(snapshotTimeTextMap[snapshot.id])!'-'}">${(snapshotRelativeTimeTextMap[snapshot.id])!'-'}</span></td>
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
                    <td colspan="5">暂无数据</td>
                </tr>
            </#if>
            </tbody>
            </table>
        </div>
            </div>
        </div>
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
<div id="systemSnapshotBatchBindDialog" class="ui small modal">
    <div class="header">批量关联测试用例</div>
    <div class="content">
        <div class="ui form">
            <div class="field">
                <label>已选系统快照</label>
                <div id="systemSnapshotBatchBindSelectedText" style="color: #666; line-height: 1.8;">未选择系统快照</div>
            </div>
            <div class="field">
                <label>关联到测试用例</label>
                <div class="ui multiple search selection dropdown fluid" id="systemSnapshotBatchUsecaseDropdown">
                    <input type="hidden" id="systemSnapshotBatchUsecaseIds">
                    <i class="dropdown icon"></i>
                    <div class="default text">选择要关联的测试用例</div>
                    <div class="menu">
                        <#if allUsecases??>
                            <#list allUsecases as item>
                                <div class="item" data-value="${item.id}">${item.title}</div>
                            </#list>
                        </#if>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div class="actions">
        <div class="ui cancel button">取消</div>
        <div class="ui primary button" id="systemSnapshotBatchBindConfirm">确认关联</div>
    </div>
</div>

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
            $('#systemSnapshotTableBody').append('<tr id="systemSnapshotEmptyRow" class="empty-state-row"><td colspan="4">暂无数据</td></tr>');
        }
        if (hasDataRow) {
            $('#systemSnapshotEmptyRow').remove();
        }
    }

    function getSelectedSystemSnapshotIds() {
        return $('.system-snapshot-batch-select:checked').map(function () {
            return $(this).val();
        }).get();
    }

    function updateSystemSnapshotSelectionState() {
        $('#systemSnapshotSelectedCount').text(getSelectedSystemSnapshotIds().length);
    }

    function selectAllSystemSnapshots() {
        $('.system-snapshot-batch-select').prop('checked', true);
        $('.system-snapshot-select-checkbox').checkbox('set checked');
        updateSystemSnapshotSelectionState();
    }

    function clearSystemSnapshotSelection() {
        $('.system-snapshot-batch-select').prop('checked', false);
        $('.system-snapshot-select-checkbox').checkbox('set unchecked');
        updateSystemSnapshotSelectionState();
    }

    function openSystemSnapshotBatchBindDialog() {
        var selectedIds = getSelectedSystemSnapshotIds();
        if (!selectedIds.length) {
            showToast('请先勾选要关联的系统快照', 'warning');
            return;
        }
        var selectedNames = [];
        selectedIds.forEach(function (id) {
            var row = $("#systemSnapshotTableBody tr[data-system-snapshot-id='" + id + "']");
            var name = $.trim(row.find('.snapshot-title span').text());
            if (name) {
                selectedNames.push(name);
            }
        });
        $('#systemSnapshotBatchUsecaseDropdown').dropdown('clear');
        $('#systemSnapshotBatchBindSelectedText').text(selectedNames.join('、'));
        $('#systemSnapshotBatchBindDialog').modal('show');
    }

    function bindSelectedSystemSnapshotsToUsecases() {
        var snapshotIds = getSelectedSystemSnapshotIds();
        if (!snapshotIds.length) {
            showToast('请先勾选要关联的系统快照', 'warning');
            return;
        }
        var usecaseValues = $('#systemSnapshotBatchUsecaseDropdown').dropdown('get value');
        var usecaseIds = usecaseValues ? usecaseValues.split(',').filter(Boolean) : [];
        if (!usecaseIds.length) {
            showToast('请选择要关联的测试用例', 'warning');
            return;
        }
        $.ajax({
            url: '/p/${project.id}/${app.id}/snapshot/usecase/batchBind',
            data: {snapshotIds: snapshotIds, usecaseIds: usecaseIds},
            traditional: true,
            success: function (result) {
                if (result && (result.result || result.success)) {
                    showToast(result.message || '批量关联成功', 'success');
                    $('#systemSnapshotBatchBindDialog').modal('hide');
                } else {
                    showToast((result && (result.errorMessage || result.message)) || '批量关联失败', 'error');
                }
            },
            error: function () {
                showToast('批量关联失败', 'error');
            }
        });
    }

    $(function () {
        $('.ui.click.dropdown').dropdown({
            on: 'click'
        });
        $('.ui.filter.dropdown').dropdown({
            on: 'click'
        });
        $('.system-snapshot-select-checkbox').checkbox({
            onChecked: updateSystemSnapshotSelectionState,
            onUnchecked: updateSystemSnapshotSelectionState
        });
        $('#systemSnapshotBatchUsecaseDropdown').dropdown({
            on: 'click'
        });
        $('#systemSnapshotBatchBindTrigger').on('click', function () {
            openSystemSnapshotBatchBindDialog();
        });
        $('#systemSnapshotSelectAllTrigger').on('click', function () {
            selectAllSystemSnapshots();
        });
        $('#systemSnapshotClearSelectionTrigger').on('click', function () {
            clearSystemSnapshotSelection();
        });
        $('#systemSnapshotBatchBindConfirm').on('click', function () {
            bindSelectedSystemSnapshotsToUsecases();
        });
        $(document).on('change', '.system-snapshot-batch-select', function () {
            updateSystemSnapshotSelectionState();
        });
        $(document).on('click', '.snapshot-select-cell input, .snapshot-select-cell label', function (e) {
            e.stopPropagation();
        });
        updateSystemSnapshotSelectionState();
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

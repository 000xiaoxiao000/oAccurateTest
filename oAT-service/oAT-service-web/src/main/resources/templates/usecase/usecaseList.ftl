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
<div class="ui container app-unified-page">
    <div class="ui small breadcrumb app-unified-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
        <span class="divider">/</span>
        <div class="active section">用例列表</div>
    </div>

    <#if missingUsecaseMessage?? && missingUsecaseMessage?has_content>
        <div class="ui warning message">
            <div class="header">用例不可访问</div>
            <p>${missingUsecaseMessage}</p>
        </div>
    </#if>

    <div class="app-toolbar-card">
        <div class="app-toolbar-path">
            <a class="ui tiny teal basic button" href="/p/${project.id}/usecase/list">
                <i class="home icon"></i>用例中心
            </a>
            <a href="/p/${project.id}/usecase/list">/ROOT</a>
            <#if dirTiers??>
                <#list dirTiers as tie>
                    <span>/</span>
                    <#if tie_index ==(dirTiers?size)-1>
                        <span>${tie.name}</span>
                    <#else>
                        <a href="/p/${project.id}/usecase/list?directory=${tie.id}">${tie.name}</a>
                    </#if>
                </#list>
            </#if>
        </div>
        <form id="filterForm" class="ui form app-toolbar-actions" action="list">
            <input type="hidden" name="directory" value="${directory!'root'}">
            <div class="ui icon input">
                <input type="text" name="keyword" value="${keyword!}" placeholder="搜索名称...">
                <i class="search icon"></i>
            </div>
            <#if (keyword!'')?has_content>
                <a class="ui basic mini button" href="/p/${project.id}/usecase/list?directory=${directory!}<#if sort?? && sort?has_content>&sort=${sort}</#if>">清空</a>
            </#if>
            <div class="ui filter dropdown item" tabindex="2">
                <input id="filterSort" type="hidden" name="sort" value="${sort!}">
                <i class="ui sort numeric ascending link icon"></i>
                <span class="text">排序</span>
                <div class="left menu transition hidden" tabindex="-1">
                    <div class="item" data-value="updateTime">更新时间</div>
                    <div class="item" data-value="name">名称</div>
                </div>
            </div>
            <a class="ui basic mini button" href="/p/${project.id}/usecase/template/download">
                <i class="download icon"></i>下载模板
            </a>
            <button type="button" class="ui basic mini button" onclick="selectUsecaseImportFile()">
                <i class="upload icon"></i>上传用例
            </button>
            <a class="ui basic mini button" href="/p/${project.id}/usecase/export?directory=${currentDir}<#if sort?? && sort?has_content>&sort=${sort}</#if><#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>">
                <i class="file excel outline icon"></i>导出
            </a>
            <div id="newAction" class="ui pointing dropdown item" tabindex="-1">
                <div class="ui primary button">新建</div>
                <div class="menu" tabindex="1">
                    <div class="item" onclick="openAddFolderDialog()"><i class="folder icon"></i>新建目录</div>
                    <a class="item" href="/p/${project.id}/usecase/new?directory=${currentDir}"><i class="file icon"></i>新建用例</a>
                </div>
            </div>
        </form>
    </div>

    <div class="app-unified-layout">
        <div class="app-unified-side">
            <div class="ui vertical menu settings-nav">
                <div class="section-title item">用例中心</div>
                <a class="top-level item active" href="/p/${project.id}/usecase/list">
                    用例中心
                </a>
                <#if app??>
                    <a class="top-level item" href="/p/${project.id}/${app.id}/snapshot/list">
                        系统快照
                    </a>
                    <a class="top-level item" href="/p/${project.id}/app/${app.id}/settings">
                        设置
                    </a>
                <#else>
                    <a class="top-level item" href="/p/${project.id}/edit">
                        设置
                    </a>
                </#if>
            </div>
        </div>
        <div class="app-unified-main">
            <div class="app-unified-header">
                <div>
                    <div class="app-unified-kicker">
                        <i class="tasks icon"></i>
                        用例中心
                    </div>
                    <h1 class="app-unified-title">用例列表</h1>
                    <p class="app-unified-desc">按目录管理项目用例，快速查看关联快照、缺陷和需求覆盖情况。</p>
                </div>
            </div>

            <div class="app-unified-content">
                <style>
            #usecaseListTable thead th,
            #usecaseListTable tbody td {
                padding-top: 0.56em;
                padding-bottom: 0.56em;
            }

            #usecaseListTable thead th {
                font-size: 0.92em;
            }

            #usecaseListTable .usecase-title,
            #usecaseListTable .dir-title {
                display: inline-flex;
                align-items: center;
                gap: 0.35em;
                line-height: 1.25;
                max-width: 100%;
            }

            #usecaseListTable .usecase-title span,
            #usecaseListTable .dir-title span {
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }

            #usecaseListTable .relation-overview {
                display: -webkit-box;
                -webkit-box-orient: vertical;
                -webkit-line-clamp: 2;
                overflow: hidden;
                max-height: 3.2em;
            }

            #usecaseListTable .meta-text {
                font-size: 0.88em;
                color: #666;
                white-space: nowrap;
            }

            #usecaseListTable .quickMenu {
                display: flex;
                justify-content: center;
                align-items: center;
                min-width: 24px;
            }

            #usecaseListTable .quickMenu > .icon {
                margin: 0;
            }

            #usecaseListTable .empty-state-row td {
                color: #999;
                padding: 30px 0;
                text-align: center;
            }
        </style>

        <div class="app-unified-table-wrap">
            <table id="usecaseListTable" class="ui selectable compact very basic table unified-list-table" style="table-layout: fixed;">
            <thead>
            <tr>
                <th>文件名</th>
                <th class="three wide">关联概览</th>
                <th style="width: 96px;">维护者</th>
                <th style="width: 170px;">更新时间</th>
                <th style="width: 72px; text-align: center;">操作</th>
            </tr>
            </thead>
            <tbody id="usecaseTableBody">
            <#list dirs as dir>
                <tr data-directory-id="${dir.id}">
                    <td>
                        <a class="dir-title" href="/p/${project.id}/usecase/list?directory=${dir.id}<#if sort?? && sort?has_content>&sort=${sort}</#if><#if keyword?? && keyword?has_content>&keyword=${keyword?url}</#if>"><i class="folder icon"></i><span>${dir.name}</span>
                        </a>
                    </td>
                    <td>-</td>
                    <td class="meta-text">-</td>
                    <td class="meta-text"><span title="${dir.updateTimeText!'-'}">${dir.updateTimeRelativeText!'-'}</span></td>
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
                <tr data-usecase-id="${cas.id}">
                    <td>
                        <a class="usecase-title" href="/p/${project.id}/usecase/detail?id=${cas.id}"><i
                                    class="file outline icon"></i><span>${cas.title}</span></a>
                    </td>
                    <td>
                        <div class="relation-overview">
                            <div class="ui mini labels" style="margin: 0; line-height: 1.15;">
                                <span class="ui basic label" style="margin: 0 3px 3px 0; padding: 0.2em 0.46em;">快照 ${cas.snapshotCount!0}</span>
                                <span class="ui basic label" style="margin: 0 3px 3px 0; padding: 0.2em 0.46em;">系统快照 ${cas.systemSnapshotCount!0}</span>
                                <span class="ui basic label" style="margin: 0 3px 3px 0; padding: 0.2em 0.46em;">缺陷 ${(cas.defects?size)!0}</span>
                                <span class="ui basic label" style="margin: 0 3px 3px 0; padding: 0.2em 0.46em;">PRD ${(cas.prdRequirements?size)!0}</span>
                            </div>
                        </div>
                    </td>
                    <td class="meta-text">${maintainerNameMap[cas.lastUpdateAuthor]!maintainerNameMap[(cas.authors[0])!'']!'未设置'}</td>
                    <td class="meta-text"><span title="${cas.updateTimeText!'-'}">${cas.updateTimeText!'-'}</span></td>
                    <td>
                        <div class="ui dropdown quickMenu">
                            <i class="list link setting icon"></i>
                            <div class="left menu">
                                <div class="ui dropdown item">
                                    <i class="share alternate icon"></i>
                                    <i class="dropdown icon"></i>
                                    共享设置
                                    <div class="menu">
                                        <div class="header">
                                            <div class="ui toggle checkbox usecase-share-switch" data-id="${cas.id}">
                                                <input type="checkbox" <#if cas.share??&&cas.share==true>checked="checked"</#if>>
                                            </div>
                                        </div>
                                        <a class="item share-usecase-url <#if cas.share??&&cas.share==true><#else>disabled</#if>" data-id="${cas.id}" href="/share/usecase/${cas.id}" target="_blank">
                                            <i class="ui linkify icon"></i>
                                            访问共享页
                                        </a>
                                    </div>
                                </div>
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
            <#if dirs?size == 0 && cases?size == 0>
                <tr id="usecaseEmptyRow" class="empty-state-row">
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
        <div id="createFolderButton" class="ui positive button" onclick="doCreateFolder()">创建</div>
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
        <div id="saveFolderButton" class="ui positive button" onclick="doSaveFolder()">更新</div>
        <div class="ui cancel button">不</div>
    </div>
</div>
<!-- 删除目录弹出框-->
<div id="delFolder" class="ui small modal">
    <div class="header">删除用例路径</div>
    <div class="content">
        <input type="hidden" value="${currentDir}" id="parentId">
        <div class="ui negative message" style="margin-bottom: 1em;">
            <div class="header">该操作不可恢复</div>
            <p id="deFolderContent"></p>
        </div>
        <div id="deFolderImpact" class="ui list" style="display: none; margin-top: 0;">
            <div id="deFolderImpactDirectories" class="item"></div>
            <div id="deFolderImpactUsecases" class="item"></div>
        </div>
    </div>
    <div class="actions">
        <div id="deFolderButton" class="ui negative button">删除</div>
        <div class="ui cancel button">取消</div>
    </div>
</div>

<!-- 删除用例弹出框-->
<input id="usecaseImportFile" type="file" accept=".xlsx,.xls" style="display: none;">
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

    function getResultMessage(resultInform, fallbackMessage) {
        return resultInform?.errorMessage || resultInform?.message || fallbackMessage;
    }

    $('.usecase-share-switch').checkbox({
        onChecked: function () {
            var id = $(this).closest('.usecase-share-switch').data('id');
            $.getJSON('/p/${project.id}/usecase/openShare/' + encodeURIComponent(id), function (resultInform) {
                if (resultInform && resultInform.result) {
                    $('.share-usecase-url[data-id="' + id + '"]').removeClass('disabled');
                    return;
                }
                showToast(getResultMessage(resultInform, '用例共享开启失败'), 'error');
            });
        },
        onUnchecked: function () {
            var id = $(this).closest('.usecase-share-switch').data('id');
            $.getJSON('/p/${project.id}/usecase/closeShare/' + encodeURIComponent(id), function (resultInform) {
                if (resultInform && resultInform.result) {
                    $('.share-usecase-url[data-id="' + id + '"]').addClass('disabled');
                    return;
                }
                showToast(getResultMessage(resultInform, '用例共享关闭失败'), 'error');
            });
        }
    });

    function ensureUsecaseEmptyState() {
        var hasDataRow = $('#usecaseTableBody tr[data-directory-id], #usecaseTableBody tr[data-usecase-id]').length > 0;
        if (!hasDataRow && $('#usecaseEmptyRow').length === 0) {
            $('#usecaseTableBody').append('<tr id="usecaseEmptyRow" class="empty-state-row"><td colspan="5">暂无数据</td></tr>');
        }
        if (hasDataRow) {
            $('#usecaseEmptyRow').remove();
        }
    }

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

    function reloadUsecaseList(targetDirectory) {
        var currentDirectory = '${currentDir}';
        var nextDirectory = targetDirectory || currentDirectory || 'root';
        window.location = '/p/${project.id}/usecase/list?directory=' + encodeURIComponent(nextDirectory) + '&sort=' + encodeURIComponent($('#filterSort').val() || '${sort!"updateTime"}');
    }

    function renderFolderDeleteDialog(directoryName, impact) {
        if (impact.hasImpact) {
            $('#deFolderContent').html('确定删除目录“' + directoryName + '”以及其关联内容吗？');
            $('#deFolderImpact').show();
            if (impact.directoryCount > 0) {
                $('#deFolderImpactDirectories').html('<i class="folder icon"></i>将一并删除 <strong>' + impact.directoryCount + '</strong> 个子目录');
                $('#deFolderImpactDirectories').show();
            } else {
                $('#deFolderImpactDirectories').hide();
            }
            if (impact.usecaseCount > 0) {
                $('#deFolderImpactUsecases').html('<i class="file outline icon"></i>将一并删除 <strong>' + impact.usecaseCount + '</strong> 个用例');
                $('#deFolderImpactUsecases').show();
            } else {
                $('#deFolderImpactUsecases').hide();
            }
            return;
        }
        $('#deFolderContent').html('确定删除目录“' + directoryName + '”吗？');
        $('#deFolderImpact').hide();
        $('#deFolderImpactDirectories').hide();
        $('#deFolderImpactUsecases').hide();
    }

    function buildFolderDeleteImpact(resultInform) {
        var data = resultInform && resultInform.data ? resultInform.data : {};
        return {
            hasImpact: !!data.requiresCascade,
            directoryCount: data.directoryCount || 0,
            usecaseCount: data.usecaseCount || 0
        };
    }

    function openDelFolderDialog(directoryId, directoryName) {
        var parentId = $("#parentId").val();
        var previewUrl = "/p/${project.id}/usecase/directory/deletePreview?id=" + encodeURIComponent(directoryId)
            + "&parentId=" + encodeURIComponent(parentId)
            + "&name=" + encodeURIComponent(directoryName);
        var baseUrl = "/p/${project.id}/usecase/directory/del?id=" + encodeURIComponent(directoryId)
            + "&parentId=" + encodeURIComponent(parentId)
            + "&name=" + encodeURIComponent(directoryName);

        var previewResult = $.ajax({
            url: previewUrl,
            type: 'GET',
            async: false
        }).responseJSON;

        if (!previewResult) {
            showToast('目录删除失败', 'error');
            return;
        }

        var message = getResultMessage(previewResult, '目录删除失败');
        var impact = buildFolderDeleteImpact(previewResult);
        renderFolderDeleteDialog(directoryName, impact);

        $('#deFolderButton').off('click').on('click', function () {
            var $scope = $('#delFolder');
            if (oatIsFormSubmitting($scope)) {
                return;
            }
            var submittingOptions = {
                submitButton: '#deFolderButton',
                extraControls: '#delFolder .actions .ui.button',
                message: '正在删除目录...'
            };
            var requestUrl = baseUrl + (impact.hasImpact ? '&deleteUsecases=true' : '');
            oatSetFormSubmitting($scope, true, submittingOptions);
            $.ajax({
                url: requestUrl,
                type: 'DELETE',
                success: function(deleteResult) {
                    if (deleteResult && deleteResult.result) {
                        showToast(getResultMessage(deleteResult, '目录删除成功'), 'success');
                        $('#delFolder').modal('hide');
                        var currentDirectory = '${currentDir}';
                        if (currentDirectory === directoryId) {
                            reloadUsecaseList('${directory!"root"}' === 'root' ? 'root' : $('#parentId').val());
                            return;
                        }
                        reloadUsecaseList();
                    } else {
                        oatSetFormSubmitting($scope, false, submittingOptions);
                        showToast(getResultMessage(deleteResult, '目录删除失败'), 'error');
                    }
                },
                error: function() {
                    oatSetFormSubmitting($scope, false, submittingOptions);
                    showToast('目录删除失败', 'error');
                }
            });
        });

        $('#delFolder').modal('show');
    }

    function openDeleteCaseDialog(id, name) {
        $("#deleteUsecaseContent").html(name);
        $("#deleteUsecaseButton").off('click').on('click', function () {
            var $scope = $('#deleteUsecaseDialog');
            if (oatIsFormSubmitting($scope)) {
                return;
            }
            var submittingOptions = {
                submitButton: '#deleteUsecaseButton',
                extraControls: '#deleteUsecaseDialog .actions .ui.button',
                message: '正在删除用例...'
            };
            oatSetFormSubmitting($scope, true, submittingOptions);
            $.ajax({
                url: "/p/${project.id}/usecase/doDelete?id=" + id,
                type: 'DELETE',
                success: function(resultInform) {
                    if (resultInform.result) {
                        showToast(getResultMessage(resultInform, '用例删除成功'), 'success');
                        $("#deleteUsecaseDialog").modal('hide');
                        reloadUsecaseList();
                    } else {
                        oatSetFormSubmitting($scope, false, submittingOptions);
                        showToast(getResultMessage(resultInform, '用例删除失败'), 'error');
                    }
                },
                error: function() {
                    oatSetFormSubmitting($scope, false, submittingOptions);
                    showToast('用例删除失败', 'error');
                }
            });
        });
        $("#deleteUsecaseDialog").modal('show');
    }

    function selectUsecaseImportFile() {
        $('#usecaseImportFile').val('');
        $('#usecaseImportFile').trigger('click');
    }

    $('#usecaseImportFile').on('change', function () {
        var file = this.files && this.files.length > 0 ? this.files[0] : null;
        if (!file) {
            return;
        }
        var formData = new FormData();
        formData.append('file', file);
        formData.append('directory', '${currentDir}');
        $.ajax({
            url: '/p/${project.id}/usecase/upload',
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function (resultInform) {
                if (resultInform && resultInform.result) {
                    showToast(getResultMessage(resultInform, '用例上传成功'), 'success');
                    reloadUsecaseList();
                    return;
                }
                showToast(getResultMessage(resultInform, '用例上传失败'), 'error');
            },
            error: function () {
                showToast('用例上传失败', 'error');
            }
        });
    });

    function doCreateFolder() {
        var $form = $("#addFolderForm");
        if (oatIsFormSubmitting($form)) {
            return;
        }
        var data = $form.serialize();
        var submittingOptions = {
            submitButton: '#createFolderButton',
            extraControls: '#addFolder .actions .ui.button',
            message: '正在创建目录...'
        };
        oatSetFormSubmitting($form, true, submittingOptions);
        $.ajax({
            url: "/p/${project.id}/usecase/directory/new",
            data: data,
            success: function(resultInform) {
                if (resultInform.result) {
                    showToast(getResultMessage(resultInform, '目录创建成功'), 'success');
                    window.location = window.location;
                } else {
                    oatSetFormSubmitting($form, false, submittingOptions);
                    showToast(getResultMessage(resultInform, '目录创建失败'), 'error');
                }
            },
            error: function() {
                oatSetFormSubmitting($form, false, submittingOptions);
                showToast('目录创建失败', 'error');
            }
        });
    }

    function doSaveFolder() {
        var $form = $("#editFolderForm");
        if (oatIsFormSubmitting($form)) {
            return;
        }
        var data = $form.serialize();
        var submittingOptions = {
            submitButton: '#saveFolderButton',
            extraControls: '#editFolder .actions .ui.button',
            message: '正在保存目录...'
        };
        oatSetFormSubmitting($form, true, submittingOptions);
        $.ajax({
            url: "/p/${project.id}/usecase/directory/save",
            data: data,
            success: function(resultInform) {
                if (resultInform.result) {
                    showToast(getResultMessage(resultInform, '目录保存成功'), 'success');
                    window.location = window.location;
                } else {
                    oatSetFormSubmitting($form, false, submittingOptions);
                    showToast(getResultMessage(resultInform, '目录保存失败'), 'error');
                }
            },
            error: function() {
                oatSetFormSubmitting($form, false, submittingOptions);
                showToast('目录保存失败', 'error');
            }
        });
    }

    $(function () {
        $("#filterSort").change(function () {
            $("#filterForm").submit();
        });
    });
</script>

</body>
</html>

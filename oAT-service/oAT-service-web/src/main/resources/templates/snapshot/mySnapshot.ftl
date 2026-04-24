<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>我的快照</title>
    <#include "../common.ftl">
    <script src="/js/tipsy.js?v=${.now}"></script>
    <link href="/css/tipsy.css" rel="stylesheet">
    <script src="/js/systemSnapshotFlow.js?v=${.now}"></script>
    <script src="/js/treeTable.js?v=${.now}"></script>
    <link href="/css/treeTable.css" rel="stylesheet">
    <script src="/js/d3.min.js"></script>
    <script src="/js/dagre-d3.min.js"></script>
    <!--语法高亮-->
    <link href="/css/github.min.css" rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script src="/js/spark-md5.min.js"></script>
    <script src="/js/upload.js"></script>
    <style>
        .snapshot-page {
            padding: 10px 10px 0;
        }

        .snapshot-toolbar {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
        }

        .snapshot-toolbar .snapshot-bulk-action {
            margin-left: auto;
        }

        .snapshot-bulk-group {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 4px 8px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.06);
            border: 1px solid rgba(33, 133, 208, 0.14);
        }

        .snapshot-selected-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            border-radius: 999px;
            background: #f3f4f6;
            color: #6b7280;
            border: 1px solid rgba(34, 36, 38, .08);
            font-weight: 600;
            line-height: 1;
            transition: all .2s ease;
        }

        .snapshot-selected-badge.active {
            background: rgba(33, 133, 208, 0.12);
            color: #1f2937;
            border-color: rgba(33, 133, 208, 0.24);
            box-shadow: 0 0 0 1px rgba(33, 133, 208, 0.05) inset;
        }

        .snapshot-selected-badge .count {
            color: inherit;
            font-size: 1.05em;
        }

        .snapshot-selected-badge.active .count {
            color: var(--page-accent);
        }

        .snapshot-report-entry {
            margin-left: 2px;
        }

        .snapshot-select-cell {
            width: 30px;
            text-align: left;
            padding-left: 0.28em !important;
            padding-right: 0.28em !important;
        }

        .snapshot-select-cell .ui.checkbox {
            margin: 0;
            min-height: 17px;
        }

        .snapshot-select-cell .ui.checkbox label {
            padding-left: 17px;
        }

        .snapshot-detail-body .ui.secondary.compact.menu {
            margin: 0;
            border-bottom: 1px solid rgba(34, 36, 38, .08);
            box-shadow: none;
            background: #fbfcfd;
        }

        .snapshot-detail-body .ui.secondary.compact.menu .item.active {
            color: var(--page-accent);
            font-weight: 600;
            border-color: var(--page-accent);
        }

        #mySnapshotListTable {
            table-layout: fixed;
            width: 100%;
            border: 1px solid rgba(34, 36, 38, .08);
        }

        #mySnapshotListTable tbody td {
            overflow: hidden;
            vertical-align: middle;
        }

        #mySnapshotListTable tbody tr.focus .snapshot-title,
        #mySnapshotListTable tbody tr.selected .snapshot-title {
            color: var(--page-accent);
        }

        .snapshot-filter-input {
            margin-right: 8px;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }

        .snapshot-filter-input input {
            min-width: 180px;
        }

        .snapshot-toolbar-text {
            margin: auto;
        }

        .snapshot-list-scroll {
            padding: 0;
            height: calc(100vh - 212px);
            overflow: auto;
        }

        #mySnapshotListTable .snapshot-title {
            display: flex;
            align-items: center;
            gap: 0.35em;
            width: 100%;
            min-width: 0;
            overflow: hidden;
        }

        #mySnapshotListTable .snapshot-title .icon {
            flex: 0 0 auto;
            margin-right: 0;
        }

        #mySnapshotListTable .snapshot-title span {
            display: block;
            flex: 1 1 auto;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        #mySnapshotListTable .snapshot-name-cell {
            min-width: 0;
            max-width: 0;
        }

        #mySnapshotListTable.show-full-name .snapshot-title span {
            overflow: visible;
            text-overflow: unset;
        }

        #mySnapshotListTable .meta-text {
            width: 68px;
            font-size: 0.88em;
            color: #666;
            text-align: right;
            white-space: nowrap;
        }

        #mySnapshotListTable .api-coverage-cell {
            width: 58px;
            text-align: center;
            color: #4a5568;
            white-space: nowrap;
            font-size: 0.88em;
        }

        #mySnapshotListTable .hover.dropdown > .icon {
            margin: 0;
        }

        #mySnapshotListTable .empty-state-row td {
            color: #999;
            padding: 30px 0;
            text-align: center;
        }

        @media (max-width: 960px) {
            .snapshot-layout {
                flex-direction: column;
            }

            .snapshot-list-panel,
            .snapshot-detail-panel {
                flex: 1 1 auto;
                max-width: none;
                min-width: 0;
            }

            .snapshot-resizer {
                display: none;
            }

            .snapshot-list-scroll {
                height: auto;
                max-height: 45vh;
            }
        }

        .node rect {
            stroke: #999;
            fill: #fff;
            stroke-width: 1.5px;
            cursor: pointer;
        }

        .node.error rect {
            stroke: red;
        }

        .node rect:hover {
            /*fill: azure;*/
            stroke: dodgerblue;
            stroke-width: 1.5px;
        }

        .node .label {
            pointer-events: none;
        }

        .node text {
            font-weight: 300;
            font-family: "Helvetica Neue", Helvetica, Arial, sans-serf;
            font-size: 14px;
            pointer-events: none;
        }

        .edgePath path {
            stroke: #333;
            stroke-width: 1.5px;
        }

        #stackNodeDetail.max {
            left: 0px;
            right: 0px;
            padding: 20px;
            width: 100vw;
        }

        tr.selected td {
            background-color: #ffe48d;
        }

        body.pushable > .pusher {
            background: #f7f7f7;
        }

        /*鼠标悬浮*/
        .ellipsis-tooltip {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: none; /* 控制元素最大宽度 */
            cursor: pointer;
            box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
            background-color: #f0f0f0;
            border: 1px solid #ccc;
        }

        .tooltip-content {
            display: none;
            position: absolute;
            border: 1px solid #ccc;
            padding: 5px;
            background-color: #fff;
            z-index: 9999;

        }
    </style>
</head>
<body class="page-theme">
<#include "../commonFunction.ftl">

<div id="stackNodeDetail" class="ui right vertical wide sidebar raised segment"
     style="background-color: white;overflow: hidden">
    <div class="ui top attached label" style="border: none;top: -0.5px">
        节点详情
        <i class="close link icon snapshot-detail-close" style="float: right;font-size: 1.1em;"></i>

        <script>
            function maxDetailWindow() {
                $('#stackNodeDetail').toggleClass('max');
                $('#stackNodeDetail .window.icon').toggleClass('maximize');
                $('#stackNodeDetail .window.icon').toggleClass('restore');
            }
        </script>
        <i class="window maximize outline link icon snapshot-detail-toggle" style="float: right;font-size: 1.1em;"></i>
    </div>
    <div class="ui content container" style="padding: 5px;position:absolute;top: 5px;bottom:5px;overflow-y: auto;word-break: break-all">
    </div>
</div>

<div class="pusher">
    <!--头部菜单 引入-->
    <#assign monitorItemActive="active">
    <#include "../projectHeader.ftl">

    <div class="snapshot-page">
        <form id="filterForm" class="ui form" action="/p/${project.id}/snapshot/my">
        <div class="ui text small menu snapshot-toolbar page-toolbar-card">
            <div class="ui multiple click dropdown item">
                <input id="filterLabels" type="hidden" name="labels" value="${filterLabels!}">
                <i class="tag link icon"></i>
                <span class="text snapshot-toolbar-text">标签过滤</span>
                <div class="menu">
                    <div class="ui icon search input">
                        <i class="search icon"></i>
                        <input type="text" placeholder="搜索标签...">
                    </div>
                    <div class="divider"></div>
                    <div class="header">
                        <i class="tags icon"></i>
                        标签
                    </div>
                    <div class="scrolling menu">
                        <#list snapshotLabels as lab>
                            <div class="item" data-value="${lab.name}">
                                <div class="ui ${lab.color} empty circular label"></div>
                                ${lab.name}
                            </div>
                        </#list>
                    </div>
                </div>
            </div>
            <div class="ui icon input snapshot-filter-input">
                <input type="text" name="keyword" value="${keyword!}" placeholder="搜索快照名称...">
                <i class="search icon"></i>
                <#if (keyword!'')?has_content>
                    <a class="ui basic mini button" href="/p/${project.id}/snapshot/my">清空</a>
                </#if>
            </div>
            <div class="ui filter click dropdown item" tabindex="2">
                <input id="filterSort" type="hidden" name="sort" value="${sort!}">
                <i class="ui sort numeric ascending link icon"> </i>
                <span class="text">排序</span>
                <div class="left menu transition hidden" tabindex="-1">
                    <div class="item" data-value="updateTime">更新时间</div>
                    <div class="item" data-value="name">快照名称</div>
                </div>
            </div>
            <div class="snapshot-bulk-group">
                <button type="button" class="ui primary mini button snapshot-bulk-action" id="snapshotBatchBindTrigger">
                    <i class="linkify icon"></i>
                    批量关联用例
                </button>
                <div class="ui mini basic buttons">
                    <button type="button" class="ui button" id="snapshotSelectAllTrigger">全选</button>
                    <button type="button" class="ui button" id="snapshotClearSelectionTrigger">清空选择</button>
                </div>
                <div class="snapshot-selected-badge" title="当前已选中的快照数量">
                    已选 <span class="count" id="snapshotSelectedCount">0</span> 项
                </div>
            </div>
            <div class="ui multiple click dropdown item snapshot-report-entry" tabindex="3">
                <i class="file icon"> </i>
                <span class="text snapshot-open-report snapshot-toolbar-text" title="我的快照中所有接口">
                    查看报告
                </span>
            </div>
        </div>
    </form>
    <!-- 主体内容 -->
    <div class="snapshot-layout page-split-layout">
        <div id="snapshotListPanel" class="snapshot-list-panel page-split-list-panel">
            <div class="ui segment snapshot-panel-segment page-panel-shell">
                <div class="ui block header top attached segment snapshot-header-main page-section-header">
                    <div class="ui compact tiny menu snapshot-nav-menu page-nav-menu">
                        <a class="item snapshot-nav-link page-nav-link" href="/p/${project.id}/monitor">
                            <i class="line graph icon"></i>
                            实时监控
                        </a>
                        <a class="item snapshot-nav-link page-nav-link active" href="/p/${project.id}/snapshot/my">
                            <i class="copy outline icon"></i>
                            我的快照
                        </a>
                    </div>
                    <div class="snapshot-header-actions page-section-actions">
                        <div class="ui mini basic icon button snapshot-refresh-list" title="刷新列表">
                            <i class="refresh icon"></i>
                        </div>
                    </div>
                </div>
                <div class="ui attached segment snapshot-list-scroll">
                    <table id="mySnapshotListTable" class="ui selectable compact very basic single line table">
                        <colgroup>
                            <col style="width: 30px;">
                            <col>
                            <col style="width: 58px;">
                            <col style="width: 68px;">
                            <col style="width: 34px;">
                        </colgroup>
                        <tbody id="mySnapshotTableBody">
                        <#list snapshots as snap >
                            <tr data-snapshot-id="${snap.id}" data-trace-id="${snap.traceId}" class="snapshot-row">
                                <td class="snapshot-select-cell">
                                    <div class="ui checkbox snapshot-select-checkbox">
                                        <input type="checkbox" class="snapshot-batch-select" value="${snap.id}">
                                        <label></label>
                                    </div>
                                </td>
                                <td class="snapshot-name-cell" title="${snap.name}">
                                    <a class="snapshot-title" href="javascript:void(0);"><i class="file outline icon"></i><span>${snap.name}</span></a>
                                </td>
                                <td class="api-coverage-cell" title="接口覆盖（已覆盖数 / 总数）">
                                    ${snap.apiCoverageText!'0 / 0'}
                                </td>
                                <td class="right aligned meta-text">
                                    <span title="${snap.updateTimeText!'-'}">${snap.updateTimeRelativeText!'-'}</span>
                                </td>
                                <td style="width: 34px; text-align: center; padding-left: 0.28em; padding-right: 0.28em;">
                                    <div class="ui hover dropdown">
                                        <i class="setting link icon"></i>
                                        <div class="ui left menu">
                                            <div class="ui dropdown item">
                                                <i class="share alternate icon"></i>
                                                <i class="dropdown icon"></i>
                                                共享设置
                                                <div class="menu">
                                                    <div class="header">
                                                        <div class="ui toggle checkbox snapshot-share-toggle ${snap.id}"
                                                             data-snapshot-id="${snap.id}">
                                                            <input type="checkbox" <#if snap.share??&&snap.share==true>
                                                                checked="checked"</#if> >
                                                        </div>
                                                    </div>
                                                    <a class="item shareUrl ${snap.id} <#if snap.share??&&snap.share==true>  <#else>disabled </#if> "
                                                       href="/share/snapshot/${snap.id}" target="_blank">
                                                        <i class="ui linkify icon "></i>
                                                        访问共享页
                                                    </a>
                                                </div>
                                            </div>
                                            <a class="item snapshot-edit-trigger" data-snapshot-id="${snap.id}"><i class="edit icon"></i>
                                                编辑
                                            </a>
                                            <div class="divider"></div>
                                            <a class="item snapshot-delete-trigger" href="javascript:void(0);" data-snapshot-id="${snap.id}">
                                                <i class="remove icon red color"></i>
                                                <span class="text" style="color: red">删除</span>
                                            </a>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </#list>
                        <#if snapshots?size == 0>
                            <tr id="mySnapshotEmptyRow" class="empty-state-row">
                                <td colspan="5">暂无数据</td>
                            </tr>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div id="snapshotResizer" class="snapshot-resizer split-resizer" aria-hidden="true">
            <span class="snapshot-resizer-hint split-resizer-hint">拖拽调整宽度</span>
        </div>
        <div class="snapshot-detail-panel page-split-detail-panel" style="padding-right: 0;">
            <!--未选择请求时提示-->
            <div id="emptyTip" class="ui grid middle aligned center aligned segment page-empty-state snapshot-empty-state"
                 style="background: #f7f7f7">
                <div class="column">
                    <h2 class="ui header ">
                        监控详情视图
                        <div class="ui sub header">
                            从左边监控列表选择您要查控的请求
                        </div>
                    </h2>
                </div>
            </div>

            <!--监控详情-->
            <div id="monitorDetail" style="display: none" traceId="">
                <div class="snapshot-detail-shell page-detail-shell">
                    <div class="snapshot-detail-header page-detail-header-card">
                        <div class="snapshot-detail-title-row page-detail-title-row">
                            <div>
                                <div class="snapshot-detail-kicker page-detail-kicker">快照详情</div>
                                <div id="monitorDetailTitle" class="snapshot-detail-title page-detail-title"></div>
                            </div>
                            <div class="snapshot-header-actions page-action-group page-section-actions">
                                <div class="ui secondary button snapshot-save-system" data-tooltip="保存至系统快照" data-position="left center"
                                     style="font-size: 0.9em;">
                                    保存
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="snapshot-detail-body page-detail-card">
                        <!--表头-->
                        <div id="tabSwitch" class="ui top attached secondary compact menu">
                            <a class="item active" data-tab="flow">流程图</a>
                            <a class="item" data-tab="stack">堆栈列表</a>
                        </div>
                        <!-- 内容 -->
                        <div class="ui attached segment page-plain-segment page-fill-height">
                            <div class="ui tab active" data-tab="flow" style="padding: 2px">
                                <svg id="svg-canvas" class="page-fill-height" style="padding: 0px;" width="100%"></svg>
                            </div>
                            <div class="ui tab" data-tab="stack" style="padding: 2px">
                            </div>
                            <!--节点详情-->
                        </div>
                    </div>
                </div>
            </div>

            <!--代码报告-->
            <div id="codeReport" style="display: none">
                <div class="ui tab active" data-tab="report" style="padding: 2px; max-height: calc(100vh - 110px);overflow:auto">
                </div>
            </div>
        </div>
    </div>

    <#--保存系统快照 窗口-->
    <div id="systemSnapshotDialog" class="ui modal standard save snapshot">
    </div>

    <div id="snapshotEditDialog" class="ui dynamic modal standard">
    </div>

    <div id="snapshotBatchBindDialog" class="ui small modal">
        <div class="header">批量关联测试用例</div>
        <div class="content">
            <div class="ui form">
                <div class="field">
                    <label>已选快照</label>
                    <div id="snapshotBatchBindSelectedText" style="color: #666; line-height: 1.8;">未选择快照</div>
                </div>
                <div class="field">
                    <label>关联到测试用例</label>
                    <div class="ui multiple search selection dropdown fluid" id="snapshotBatchUsecaseDropdown">
                        <input type="hidden" id="snapshotBatchUsecaseIds">
                        <i class="dropdown icon"></i>
                        <div class="default text">选择要追加关联的测试用例</div>
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
            <div class="ui primary button" id="snapshotBatchBindConfirm">确认关联</div>
        </div>
    </div>

    <#-- 删除确认 Modal -->
    <div id="deleteSnapshotConfirmDialog" class="ui small modal">
            <div class="header">删除快照</div>
            <div class="content">
                <p>确认要删除该快照吗？此操作不可恢复。</p>
            </div>
            <div class="actions">
                <div class="ui negative button">取消</div>
                <div class="ui positive right labeled icon button confirm">
                    确定
                    <i class="checkmark icon"></i>
                </div>
            </div>
        </div>

        <script>
            function ensureMySnapshotEmptyState() {
                var hasDataRow = $('#mySnapshotTableBody tr[data-snapshot-id]').length > 0;
                if (!hasDataRow && $('#mySnapshotEmptyRow').length === 0) {
                    $('#mySnapshotTableBody').append('<tr id="mySnapshotEmptyRow" class="empty-state-row"><td colspan="5">暂无数据</td></tr>');
                }
                if (hasDataRow) {
                    $('#mySnapshotEmptyRow').remove();
                }
            }

            function getSelectedSnapshotIds() {
                return $('.snapshot-batch-select:checked').map(function () {
                    return $(this).val();
                }).get();
            }

            function updateSnapshotSelectionState() {
                var selectedCount = getSelectedSnapshotIds().length;
                $('#snapshotSelectedCount').text(selectedCount);
                $('#snapshotBatchBindTrigger').toggleClass('disabled', selectedCount === 0);
                $('#snapshotClearSelectionTrigger').toggleClass('disabled', selectedCount === 0);
                $('.snapshot-selected-badge').toggleClass('active', selectedCount > 0);
            }

            function selectAllSnapshots() {
                $('.snapshot-batch-select').prop('checked', true);
                $('.snapshot-select-checkbox').checkbox('set checked');
                updateSnapshotSelectionState();
            }

            function clearSnapshotSelection() {
                $('.snapshot-batch-select').prop('checked', false);
                $('.snapshot-select-checkbox').checkbox('set unchecked');
                updateSnapshotSelectionState();
            }

            function openSnapshotBatchBindDialog() {
                var selectedIds = getSelectedSnapshotIds();
                if (!selectedIds.length) {
                    showToast('请先勾选要关联的快照', 'warning');
                    return;
                }
                var selectedNames = [];
                selectedIds.forEach(function (id) {
                    var row = $("#mySnapshotTableBody tr[data-snapshot-id='" + id + "']");
                    var name = $.trim(row.find('.snapshot-title span').text());
                    if (name) {
                        selectedNames.push(name);
                    }
                });
                $('#snapshotBatchUsecaseDropdown').dropdown('clear');
                $('#snapshotBatchBindSelectedText').text(selectedNames.join('、'));
                $('#snapshotBatchBindDialog').modal('show');
            }

            function bindSelectedSnapshotsToUsecases() {
                var snapshotIds = getSelectedSnapshotIds();
                if (!snapshotIds.length) {
                    showToast('请先勾选要关联的快照', 'warning');
                    return;
                }
                var usecaseValues = $('#snapshotBatchUsecaseDropdown').dropdown('get value');
                var usecaseIds = usecaseValues ? usecaseValues.split(',').filter(Boolean) : [];
                if (!usecaseIds.length) {
                    showToast('请选择要关联的测试用例', 'warning');
                    return;
                }
                $.ajax({
                    url: '/p/${project.id}/snapshot/usecase/batchBind',
                    data: {snapshotIds: snapshotIds, usecaseIds: usecaseIds},
                    traditional: true,
                    success: function (result) {
                        if (result && (result.result || result.success)) {
                            showToast(result.message || '批量关联成功', 'success');
                            $('#snapshotBatchBindDialog').modal('hide');
                        } else {
                            showToast((result && (result.errorMessage || result.message)) || '批量关联失败', 'error');
                        }
                    },
                    error: function () {
                        showToast('批量关联失败', 'error');
                    }
                });
            }

            // 删除快照逻辑
            function deleteSnapshot(id) {
                $('#deleteSnapshotConfirmDialog').modal({
                    onApprove: function() {
                        $.post('/p/${project.id}/snapshot/doDelete', {id: id}, function(res) {
                            if (res.success || res.result) {
                                showToast(res.message || '删除成功', 'success');
                                $("tr[data-snapshot-id='" + id + "']").remove();
                                ensureMySnapshotEmptyState();
                            } else {
                                showToast((res.errorMessage || res.message || '删除失败'), 'error');
                            }
                        }).fail(function() {
                            showToast('请求失败', 'error');
                        });
                    }
                }).modal('show');
            }

            // 打开创建系统快照事件窗口
            function openCreateSystemSnapshot() {
                var selectTraceId = $('#monitorDetail').attr('traceId');
                $("#systemSnapshotDialog").load('/p/${project.id}/monitor/openSystemSnapshot?traceId=' + selectTraceId);
                $("#systemSnapshotDialog").modal('show');
            }

            $('.snapshot-detail-close').on('click', function () {
                $('#stackNodeDetail').sidebar('hide');
            });
            $('.snapshot-detail-toggle').on('click', function () {
                maxDetailWindow();
            });
            $('.snapshot-open-report').on('click', function () {
                openCodeReport();
            });
            $('.snapshot-refresh-list').on('click', function () {
                location.reload();
            });
            $(document).on('click', '.snapshot-row', function () {
                var traceId = $(this).data('trace-id');
                if (traceId) {
                    openMonitorDetail(traceId);
                }
            });
            $(document).on('click', '.snapshot-select-cell input, .snapshot-select-cell label', function (e) {
                e.stopPropagation();
            });
            $('#snapshotBatchBindTrigger').on('click', function () {
                if ($(this).hasClass('disabled')) {
                    return;
                }
                openSnapshotBatchBindDialog();
            });
            $('#snapshotSelectAllTrigger').on('click', function () {
                selectAllSnapshots();
            });
            $('#snapshotClearSelectionTrigger').on('click', function () {
                if ($(this).hasClass('disabled')) {
                    return;
                }
                clearSnapshotSelection();
            });
            $('#snapshotBatchBindConfirm').on('click', function () {
                bindSelectedSnapshotsToUsecases();
            });
            $(document).on('change', '.snapshot-batch-select', function () {
                updateSnapshotSelectionState();
            });
            $(document).on('click', '.snapshot-edit-trigger', function (e) {
                e.preventDefault();
                e.stopPropagation();
                openSnapshotEdit($(this).data('snapshot-id'));
            });
            $(document).on('click', '.snapshot-delete-trigger', function (e) {
                e.preventDefault();
                e.stopPropagation();
                deleteSnapshot($(this).data('snapshot-id'));
            });
            $('.snapshot-save-system').on('click', function () {
                openCreateSystemSnapshot();
            });
            $('.snapshot-share-toggle').each(function () {
                var checkbox = $(this);
                var snapshotId = checkbox.data('snapshot-id');
                checkbox.checkbox({
                    onChecked: function () {
                        $.getJSON('/p/${project.id}/snapshot/openShare/' + snapshotId, function () {
                            $('.shareUrl.' + snapshotId).removeClass('disabled');
                        });
                    },
                    onUnchecked: function () {
                        $.getJSON('/p/${project.id}/snapshot/closeShare/' + snapshotId, function () {
                            $('.shareUrl.' + snapshotId).addClass('disabled');
                        });
                    }
                });
            });
            $('.ui.hover.dropdown').dropdown({
                on: 'hover'
            });
            $('.ui.click.dropdown').dropdown({
                on: 'click'
            });
            $('.snapshot-select-checkbox').checkbox({
                onChecked: updateSnapshotSelectionState,
                onUnchecked: updateSnapshotSelectionState
            });
            updateSnapshotSelectionState();
            $('#snapshotBatchUsecaseDropdown').dropdown({
                on: 'click'
            });
            $(".menu .item[data-tab]").tab();
            $("#stackNodeDetail").sidebar({
                "transition": 'overlay',
                "dimPage": false
            });
            $("table.selectable tr").click(function (e) {
                $(this).parent().children("tr").removeClass("selected");
                $(this).addClass("selected");
            })

            function openMonitorDetail(traceId) {
                // 初始化界面
                $("#emptyTip").hide();
                $("#codeReport").hide();
                $("#monitorDetail").show();
                $('#monitorDetail').attr('traceId', traceId);
                $.tab('change tab', 'flow');
                $("#tabSwitch .item.active").removeClass("active");
                $("#tabSwitch .item[data-tab='flow']").addClass("active");
                // 装载监控数据
                $.ajax({
                    url: "detail/graph/" + traceId,
                    dataType: "json",
                    success: function (monitorData) {
                        $("#monitorDetailTitle").text(monitorData.title);
                        $("#svg-canvas").children().remove();
                        buildTopo("svg-canvas", monitorData);
                    }
                });
                // 构建堆栈列表
                buildStackTable(traceId);
            }

            function openSnapshotById(snapshotId) {
                if (!snapshotId) {
                    return false;
                }
                var targetRow = $("#mySnapshotTableBody tr[data-snapshot-id='" + snapshotId + "']").first();
                if (targetRow.length === 0) {
                    return false;
                }
                targetRow.trigger('click');
                return true;
            }

            function buildStackTable(traceId) {
                let stackTab = $("#monitorDetail .ui.tab[data-tab='stack']");
                // 加载内容
                $.ajax({
                    "url": "detail/stack/" + traceId,
                    "dataType": "html",
                    "success": function (responseHtml) {
                        //  清空选项卡
                        // stackTab.empty();
                        stackTab.html(responseHtml);
                        // 构建树表格
                        treeTable(stackTab.find('.tree.table'));

                        $(".tree.table tbody tr").click(function (e) {
                            e.stopPropagation();//阻止事件冒泡
                            $(this).parent().children("tr").removeClass("selected");
                            $(this).addClass("selected");
                            $('#stackNodeDetail').sidebar('show');
                            $('#stackNodeDetail .ui.content')
                                .first()
                                .load("node?traceId=" + traceId + "&nodeId=" + $(this).attr('nodeId'))
                        });
                    }
                });
            }

            $("#filterLabels").change(function () {
                $("#filterForm").submit();
            });
            $("#filterSort").change(function () {
                $("#filterForm").submit();
            });

            // 代码关系图
            function openCodeMap(traceId) {
                let url = "/p/${project.id}/map/code?traceId=" + traceId;
                window.open(url, 'codeMapDialog', 'toolbar=no,location=no,resizable=no, height=500, width=680,,scrollbars=yes ,left=380,top=100');
            }

            // 代码报告
            function openCodeReport() {
                $("#emptyTip").hide();
                $("#monitorDetail").hide();
                $("#codeReport").show();
                let reportTab = $("#codeReport .ui.tab[data-tab='report']");
                let url = "/p/${project.id}/snapshot/mySnapshotsCodeReport";
                // window.open(url, 'codeMapDialog', 'toolbar=no,location=no,resizable=no, height=500, width=680,,scrollbars=yes ,left=380,top=100');

                // 加载内容
                $.ajax({
                    "url": url,
                    "dataType": "html",
                    "success": function (responseHtml) {
                        reportTab.html(responseHtml);
                    }
                });

            }

            function openSnapshotEdit(id) {
                $('#snapshotEditDialog').load('/p/${project.id}/snapshot/edit?id=' + id);
                $('#snapshotEditDialog').modal('show');
            }

            var snapshotLayoutWidthController = {
                storageKey: 'mySnapshotListWidth',
                defaultWidth: 360,
                minWidth: 280,
                maxReservedWidth: 360,
                page: null,
                listPanel: null,
                resizer: null,
                applyWidth: function (width) {
                    if (!this.page || !this.listPanel) {
                        return width;
                    }
                    var maxWidth = Math.max(this.minWidth, this.page.width() - this.maxReservedWidth);
                    var nextWidth = Math.min(Math.max(width, this.minWidth), maxWidth);
                    this.listPanel.css('flex-basis', nextWidth + 'px');
                    return nextWidth;
                }
            };

            function resetSnapshotLayoutWidth() {
                if (window.innerWidth <= 960) {
                    return;
                }
                localStorage.removeItem(snapshotLayoutWidthController.storageKey);
                snapshotLayoutWidthController.applyWidth(snapshotLayoutWidthController.defaultWidth);
                syncSnapshotTitleDisplay(snapshotLayoutWidthController.defaultWidth);
            }

            function syncSnapshotTitleDisplay(width) {
                var table = $('#mySnapshotListTable');
                if (!table.length) {
                    return;
                }
                if (width >= 460) {
                    table.addClass('show-full-name');
                } else {
                    table.removeClass('show-full-name');
                }
            }

            function initSnapshotLayoutResizer() {
                var controller = snapshotLayoutWidthController;
                controller.page = $('.snapshot-page');
                controller.listPanel = $('#snapshotListPanel');
                controller.resizer = $('#snapshotResizer');
                if (!controller.page.length || !controller.listPanel.length || !controller.resizer.length || window.innerWidth <= 960) {
                    return;
                }

                var storedWidth = parseInt(localStorage.getItem(controller.storageKey), 10);
                if (!isNaN(storedWidth)) {
                    controller.applyWidth(storedWidth);
                    syncSnapshotTitleDisplay(storedWidth);
                } else {
                    controller.applyWidth(controller.defaultWidth);
                    syncSnapshotTitleDisplay(controller.defaultWidth);
                }

                var dragging = false;

                controller.resizer.on('dblclick', function () {
                    resetSnapshotLayoutWidth();
                });

                controller.resizer.on('mousedown', function (event) {
                    if (window.innerWidth <= 960) {
                        return;
                    }
                    dragging = true;
                    controller.resizer.addClass('dragging');
                    $('body').css('cursor', 'col-resize');
                    event.preventDefault();
                });

                $(document).on('mousemove.snapshotResizer', function (event) {
                    if (!dragging) {
                        return;
                    }
                    var pageOffset = controller.page.offset();
                    if (!pageOffset) {
                        return;
                    }
                    var width = event.pageX - pageOffset.left;
                    var appliedWidth = controller.applyWidth(width);
                    syncSnapshotTitleDisplay(appliedWidth);
                });

                $(document).on('mouseup.snapshotResizer', function () {
                    if (!dragging) {
                        return;
                    }
                    dragging = false;
                    controller.resizer.removeClass('dragging');
                    $('body').css('cursor', '');
                    var currentWidth = parseInt(controller.listPanel.css('flex-basis'), 10);
                    if (!isNaN(currentWidth)) {
                        localStorage.setItem(controller.storageKey, currentWidth);
                        syncSnapshotTitleDisplay(currentWidth);
                    }
                });

                $(window).on('resize.snapshotResizer', function () {
                    if (window.innerWidth <= 960) {
                        controller.listPanel.css('flex-basis', 'auto');
                        $('#mySnapshotListTable').addClass('show-full-name');
                        return;
                    }
                    var currentWidth = parseInt(localStorage.getItem(controller.storageKey), 10);
                    if (!isNaN(currentWidth)) {
                        controller.applyWidth(currentWidth);
                        syncSnapshotTitleDisplay(currentWidth);
                    } else {
                        controller.applyWidth(controller.defaultWidth);
                        syncSnapshotTitleDisplay(controller.defaultWidth);
                    }
                });
            }

            $(function () {
                initSnapshotLayoutResizer();
                updateSnapshotSelectionState();
                var requestedSnapshotId = '${snapshotId!}';
                if (requestedSnapshotId) {
                    openSnapshotById(requestedSnapshotId);
                }
            });
        </script>
    </div>
</div>
</body>
</html>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>监控台</title>
    <link href="/css/font-awesome.min.css" rel="stylesheet">
    <#include "../common.ftl">
    <script src="/js/d3.min.js" charset="utf-8"></script>

    <script src="/js/dagre-d3.min.js"></script>

    <link href="/css/monokai_sublime.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>
    <#--流程图 图标-->
    <link rel="stylesheet" href="/css/all.css"
          integrity="sha384-UHRtZLI+pbxtHCWp1t77Bi1L4ZtiqrqD80Kn4Z8NTSRyMA2Fd33n5dQ8lWUE00s/"
          crossorigin="anonymous">

    <script src="/js/common.js"></script>
    <script src="/js/monitorPlatform.js?v=${.now}"></script>
    <link href="/css/common.css" rel="stylesheet">
    <script src="/js/upload.js?v=${.now}"></script>
    <script src="/js/spark-md5.min.js"></script>
    <style type="text/css">
        .ui.right.aligned {
            float: right
        }

        #monitorListBody tr.focus .monitor-primary-text {
            color: var(--page-accent);
        }

        #monitorListBody tr {
            cursor: pointer;
        }

        .monitor-page {
            padding: 10px 10px 0;
        }

        .monitor-toolbar-left {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
            min-width: 0;
        }

        .monitor-toolbar-right {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            flex-wrap: wrap;
            gap: 12px;
            margin-top: 10px;
        }

        .monitor-autosave-inline {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 0;
            margin: 0;
            border: none;
            background: transparent;
            box-shadow: none;
        }

        .monitor-detail-content {
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .monitor-detail-title.page-detail-title {
            line-height: 1.4;
            word-break: break-word;
        }

        .monitor-graph-card {
            min-height: 220px;
        }

        .monitor-node-card {
            min-height: calc(100vh - 520px);
        }

        .monitor-panel-segment {
            height: 100%;
            display: flex;
            flex-direction: column;
        }

        .monitor-list-scroll {
            padding: 0;
            height: calc(100vh - 212px);
            overflow: auto;
        }

        #monitorListTable .monitor-name-cell {
            min-width: 0;
        }

        #monitorListTable .monitor-primary {
            display: flex;
            align-items: center;
            gap: 0.35em;
            width: 100%;
            min-width: 0;
        }

        #monitorListTable .monitor-primary-text {
            flex: 1 1 auto;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        #monitorListTable.show-full-name .monitor-primary-text {
            overflow: visible;
            text-overflow: unset;
        }

        @media (max-width: 960px) {
            .monitor-layout {
                flex-direction: column;
            }

            .monitor-list-panel,
            .monitor-detail-panel {
                flex: 1 1 auto;
                max-width: none;
                min-width: 0;
            }

            .monitor-resizer {
                display: none;
            }

            .monitor-list-scroll {
                height: auto;
                max-height: 45vh;
            }
        }
    </style>

<body class="page-theme">
<!--头部菜单 引入-->
<#assign  monitorItemActive="active">
<#include "../projectHeader.ftl">

<div class="monitor-page">
<!--中间过滤条件-->
<div id="middleFilter" class="ui sticky top segment monitor-toolbar" style="margin-bottom: 14px;">
    <div class="monitor-toolbar-left">
        <form id="itemFilter" class="ui form" action="/p/${projectId}/monitor/getNodeByTime" style="margin: 0;">
            <div class="inline fields" style="margin: 0; gap: 8px; align-items: center; flex-wrap: wrap;">
                <span style="display: inline-block; margin: 0; white-space: nowrap;">
                    <i class="filter icon"></i>条件过滤：
                </span>
                <input type="hidden" name="maxSize" value="100">
                <div class="ui dropdown" style="padding: 7px; min-width: 100px">
                    <#--默认值3分钟-->
                    <input type="hidden" name="upToTime" value="180">
                    <span class="text">三分钟内</span>
                    <div class="ui divider" style="margin:5px 0px 0px 0px"></div>
                    <div class="menu">
                        <div class="item" data-value="60">
                            一分钟内
                        </div>
                        <div class="item active" data-value="180">
                            三分钟内
                        </div>
                        <div class="item" data-value="300">
                            五分钟内
                        </div>
                        <div class="item" data-value="1800">
                            三十分钟内
                        </div>
                    </div>
                </div>

                <div class="ui multiple search compact selection dropdown"
                     style="border: none; margin-right: 0px; padding-right: 7px; min-width: 120px">
                    <input type="hidden" name="appIds" value="${appId!}">
                    <input class="search" autocomplete="off" tabindex="0">
                    <div class="text">应用过滤</div>
                    <div class="ui divider" style="margin: 0px"></div>
                    <div class="menu" tabindex="-1">
                        <#list apps as app >
                            <div class="item" data-value="${app.id}">${app.name}</div>
                        </#list>
                    </div>
                </div>
                <div class="ui multiple compact search selection dropdown ipFilter"
                     style="border: none; min-width: 110px; padding-right: 7px">
                    <input type="hidden" name="clientIps">
                    <input class="search" autocomplete="off" tabindex="0">
                    <div class=" default text">ip 过滤</div>
                    <div class="ui divider" style="margin: 0px"></div>
                    <div class="menu" tabindex="-1"></div>
                </div>
            </div>
        </form>
    </div>

    <div class="monitor-toolbar-right">
        <div class="ui pointing dropdown small disabled" id="saveSnapshotDropdown" tabindex="-1">
            <button class="ui primary button disabled" id="saveSnapshotDropdownButton" style="margin: 0;">
                保存快照
            </button>
            <div class="menu" tabindex="1">
                <div class="item" onclick="openCreateSnapshot();">我的快照</div>
                <div class="item" onclick="openCreateSystemSnapshot();">系统快照</div>
            </div>
        </div>
        <div class="ui form monitor-autosave-inline">
            <div class="field" style="margin: 0;">
                <div class="ui toggle checkbox" id="autoSaveMySnapshotWrapper" data-tooltip="监控新数据进入列表后自动保存；同一条链路不会重复自动保存" data-position="top center">
                    <input type="checkbox" id="autoSaveMySnapshotToggle">
                    <label for="autoSaveMySnapshotToggle">自动保存我的快照</label>
                </div>
            </div>
            <div class="field" style="margin: 0;">
                <div class="ui toggle checkbox" id="autoSaveSystemSnapshotWrapper" data-tooltip="监控新数据进入列表后自动保存；同一条链路不会重复自动保存" data-position="top center">
                    <input type="checkbox" id="autoSaveSystemSnapshotToggle">
                    <label for="autoSaveSystemSnapshotToggle">自动保存系统快照</label>
                </div>
            </div>
            <div id="monitorActionHint" class="ui mini grey text">自动保存会在新监控数据进入列表时触发；手动保存请先选择一条记录</div>
        </div>
    </div>
        <!--保存快照弹出框-->
        <div id="snapshotDialog" class="ui modal standard  save snapshot">
            <div class="ui block header attached ">
                保存当前快照
            </div>
            <i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>

            <div class="ui content">
                <form id="newSnapshotForm" class="ui form" action="/p/${projectId}/snapshot/save">
                    <div class="required field">
                        <label>快照名称</label>
                        <input type="text" name="name" placeholder="输入快照名称">
                    </div>
                    <div class="field">
                        <label>添加标签</label>
                        <select multiple="3" class="ui search dropdown" name="labels">
                            <#list labels as lab>
                                <option value="${lab.name}">${lab.name}</option>
                            </#list>
                        </select>
                    </div>
                    <div class="field">
                        <label> 快照描述</label>
                        <div class="content">
                            <textarea rows="3" name="describe"></textarea>
                        </div>
                    </div>
                    <div class="ui error message">

                    </div>
                </form>
            </div>
            <div class="actions">
                <div class="ui black deny button">
                    取消
                </div>
                <div id="saveSnapshotButton" class="ui positive right labeled icon button save">
                    保存
                    <i class="checkmark icon"></i>
                </div>
            </div>
        </div>
        <#--保存系统快照 窗口-->
        <div id="systemSnapshotDialog" class="ui modal standard save snapshot ">


        </div>
    </div>
</div>
<div class="monitor-layout page-split-layout">
    <div id="monitorListPanel" class="monitor-list-panel page-split-list-panel">
        <div class="ui segment monitor-panel-segment page-panel-shell">
            <div class="ui block header top attached segment page-section-header">
                <div class="ui compact tiny menu page-nav-menu">
                    <a class="item page-nav-link active" href="/p/${projectId}/monitor">
                        <i class="line graph icon"></i>
                        实时监控
                    </a>
                    <a class="item page-nav-link" href="/p/${projectId}/snapshot/my">
                        <i class="copy outline icon"></i>
                        我的快照
                    </a>
                </div>
                <div class="page-section-actions">
                    <div class="ui mini basic icon button poping up" onclick="pullNewItem('${projectId}');" data-content="获取最新数据"
                         data-variation="tiny inverted" title="刷新列表">
                        <i class="refresh icon"></i>
                    </div>
                </div>
            </div>
            <div class="ui attached segment monitor-list-scroll">
                <table id="monitorListTable" class="ui selectable single line compact fixed table" style="border: 1px solid rgba(34,36,38,.08);">
                    <tbody id="monitorListBody">
                    <!-- 监控列表 -->
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <div id="monitorResizer" class="monitor-resizer split-resizer" aria-hidden="true">
        <span class="monitor-resizer-hint split-resizer-hint">拖拽调整宽度</span>
    </div>
    <div class="monitor-detail-panel page-split-detail-panel" style="padding-right: 0;">
        <!--欢迎提示面版-->
        <div id="emptyTip" class="ui grid middle aligned center aligned segment monitor-empty-state page-empty-state">
            <div class="column">
                <h2 class="ui header">
                    监控详情视图
                    <div class="ui sub header">
                        从左边监控列表选择您要查看的请求
                    </div>
                </h2>
            </div>
        </div>

        <!--监控详情-->
        <div id="monitorDetail" style="display: none">
            <div class="monitor-detail-shell page-detail-shell">
                <div class="monitor-detail-header page-detail-header-card">
                    <div class="monitor-detail-header-main page-detail-title-row">
                        <div>
                            <div class="monitor-detail-kicker page-detail-kicker">实时监控详情</div>
                            <div id="monitorDetailTitle" class="monitor-detail-title page-detail-title"></div>
                        </div>
                    </div>
                </div>
                <div class="monitor-detail-content">
                    <div class="monitor-graph-card page-detail-card">
                        <div class="monitor-card-title page-detail-card-title">调用链路</div>
                        <svg id="svg-canvas" height="200" width="900"></svg>
                    </div>
                    <div class="monitor-node-card page-detail-card">
                        <div class="monitor-card-title page-detail-card-title">节点详情</div>
                        <div id="nodeDetail" class="ui segment basic" style="min-height: calc(100vh - 560px); padding: 0;"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>


</div>
<#--界面初始化-->
<script language="JavaScript">
    var monitorLayoutWidthController = {
        storageKey: 'monitorListWidth',
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

    function syncMonitorTitleDisplay(width) {
        var table = $('#monitorListTable');
        if (!table.length) {
            return;
        }
        if (width >= 460 || window.innerWidth <= 960) {
            table.addClass('show-full-name');
        } else {
            table.removeClass('show-full-name');
        }
    }

    function resetMonitorLayoutWidth() {
        if (window.innerWidth <= 960) {
            return;
        }
        localStorage.removeItem(monitorLayoutWidthController.storageKey);
        monitorLayoutWidthController.applyWidth(monitorLayoutWidthController.defaultWidth);
        syncMonitorTitleDisplay(monitorLayoutWidthController.defaultWidth);
    }

    function initMonitorLayoutResizer() {
        var controller = monitorLayoutWidthController;
        controller.page = $('.monitor-page');
        controller.listPanel = $('#monitorListPanel');
        controller.resizer = $('#monitorResizer');
        if (!controller.page.length || !controller.listPanel.length || !controller.resizer.length) {
            return;
        }

        if (window.innerWidth <= 960) {
            controller.listPanel.css('flex-basis', 'auto');
            syncMonitorTitleDisplay(controller.defaultWidth);
            return;
        }

        var storedWidth = parseInt(localStorage.getItem(controller.storageKey), 10);
        if (!isNaN(storedWidth)) {
            controller.applyWidth(storedWidth);
            syncMonitorTitleDisplay(storedWidth);
        } else {
            controller.applyWidth(controller.defaultWidth);
            syncMonitorTitleDisplay(controller.defaultWidth);
        }

        var dragging = false;

        controller.resizer.on('dblclick', function () {
            resetMonitorLayoutWidth();
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

        $(document).on('mousemove.monitorResizer', function (event) {
            if (!dragging) {
                return;
            }
            var pageOffset = controller.page.offset();
            if (!pageOffset) {
                return;
            }
            var width = event.pageX - pageOffset.left;
            var appliedWidth = controller.applyWidth(width);
            syncMonitorTitleDisplay(appliedWidth);
        });

        $(document).on('mouseup.monitorResizer', function () {
            if (!dragging) {
                return;
            }
            dragging = false;
            controller.resizer.removeClass('dragging');
            $('body').css('cursor', '');
            var currentWidth = parseInt(controller.listPanel.css('flex-basis'), 10);
            if (!isNaN(currentWidth)) {
                localStorage.setItem(controller.storageKey, currentWidth);
                syncMonitorTitleDisplay(currentWidth);
            }
        });

        $(window).on('resize.monitorResizer', function () {
            if (window.innerWidth <= 960) {
                controller.listPanel.css('flex-basis', 'auto');
                $('#monitorListTable').addClass('show-full-name');
                return;
            }
            var currentWidth = parseInt(localStorage.getItem(controller.storageKey), 10);
            if (!isNaN(currentWidth)) {
                controller.applyWidth(currentWidth);
                syncMonitorTitleDisplay(currentWidth);
            } else {
                controller.applyWidth(controller.defaultWidth);
                syncMonitorTitleDisplay(controller.defaultWidth);
            }
        });
    }

    $('.ui.sticky').sticky();
    $('#middleFilter .ui.dropdown').dropdown({
        onChange: function (value, text, selectedItem) {
            refreshMonitorList('${projectId}');
        }
    });
    $('.ipFilter').dropdown({
        allowAdditions: true,
        onChange: function (value, text, selectedItem) {
            refreshMonitorList('${projectId}');
        }
    });

    $('#middleFilter .search.dropdown.add.type')
        .dropdown({
            allowAdditions: true
        });
    $('#newAction').dropdown({
        on: 'click'
    });

    $('.ui.click.dropdown').dropdown({
        on: 'click'
    });

    $('.poping.up').popup();

    $(function () {
        initMonitorLayoutResizer();
        syncMonitorAutoSaveSwitches('${projectId}');
        $('.ui.toggle.checkbox').checkbox();
        $('#autoSaveMySnapshotToggle').on('change', function () {
            setMonitorAutoSaveEnabled('${projectId}', 'my', $(this).is(':checked'));
        });
        $('#autoSaveSystemSnapshotToggle').on('change', function () {
            setMonitorAutoSaveEnabled('${projectId}', 'system', $(this).is(':checked'));
        });
        $('#saveSnapshotDropdown .item').on('click', function (event) {
            if (!selectTraceId) {
                event.preventDefault();
                showToast('请先从左侧监控列表选择一条记录', 'warning');
            }
        });
        updateMonitorActionAvailability();
        // 初始化表单验证规则
        $('#newSnapshotForm').form({
                inline: false,
                onFailure: function (formErrors, fields) {
                    if (formErrors && formErrors.length > 0) {
                        showToast(formErrors[0], 'error');
                    }
                    return false;
                },
                fields: {
                    name: {
                        identifier: 'name',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入快照名称'
                            },
                            {
                                type: 'minLength[4]',
                                prompt: '快照名称至少包含4个字符'
                            },
                            {
                                type: 'maxLength[50]',
                                prompt: '快照名称不能超过50个字符'
                            }
                        ]
                    },
                    describe: {
                        identifier: 'describe',
                        rules: [
                            {
                                type: 'maxLength[512]',
                                prompt: '快照描述不能超过512个字符'
                            }
                        ]
                    }
                }
            });

        $('#saveSnapshotButton').on('click', function() {
            if ($('#newSnapshotForm').form('is valid')) {
                doSaveSnapshot('${projectId}', function () {
                    $("#newSnapshotForm").parents(".modal").modal('hide');
                });
            }
        });
    });
</script>

<script>
    $(function () {
        // 刷新监控列表
        refreshMonitorList('${projectId}');
    });
</script>

</body>
</html>

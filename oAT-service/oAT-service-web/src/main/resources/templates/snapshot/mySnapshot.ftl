<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>系统快照-我的快照</title>
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
</head>

<style id="css">
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

<body>
<#include "../commonFunction.ftl">

<div id="stackNodeDetail" class="ui right vertical wide sidebar raised segment"
     style="background-color: white;overflow: hidden">
    <div class="ui top attached label" style="border: none;top: -0.5px">
        节点详情
        <i class="close link icon" style="float: right;font-size: 1.1em;"
           onclick="$('#stackNodeDetail').sidebar('hide');"></i>

        <script>
            function maxDetailWindow() {
                $('#stackNodeDetail').toggleClass('max');
                $('#stackNodeDetail .window.icon').toggleClass('maximize');
                $('#stackNodeDetail .window.icon').toggleClass('restore');
            }
        </script>
        <i class="window maximize outline link icon" onclick="maxDetailWindow();" style="float: right;font-size: 1.1em;"></i>
    </div>
    <div class="ui content container" style="padding: 5px;position:absolute;top: 5px;bottom:5px;overflow-y: auto;word-break: break-all">
    </div>
</div>

<div class="pusher">
    <!--头部菜单 引入-->
    <#assign monitorItemActive="active">
    <#include "../projectHeader.ftl">

    <!--中间过滤条件-->
    <form id="filterForm" class="ui form" action="/p/${project.id}/snapshot/my">
        <#if (missingSnapshotId!'')?has_content>
            <div class="ui warning message" style="margin: 0 10px 16px 10px;">
                <div class="header">我的快照不存在或已被删除</div>
                <p>未找到快照 ID：${missingSnapshotId}</p>
            </div>
        </#if>
        <div class="ui text small menu" style="margin: 10px 10px 20px 10px">
            <div class="ui multiple click dropdown item">
                <input id="filterLabels" type="hidden" name="labels" value="${filterLabels!}">
                <i class="tag link icon"></i>
                <span class="text" style="margin: auto">标签过滤</span>
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
            <div class="ui icon input" style="margin-right: 8px; display: inline-flex; align-items: center; gap: 6px;">
                <input type="text" name="keyword" value="${keyword!}" placeholder="搜索快照名称..." style="min-width: 180px;">
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
            <div class="ui multiple click dropdown item" tabindex="3">
                <i class="file icon"> </i>
                <a class="text" style="margin: auto" title="我的快照中所有接口" href="/p/${project.id}/snapshot/mySnapshotsCodeReport">
                    查看报告
                </a>
            </div>
        </div>
    </form>
    <!-- 主体内容 -->
    <div class="ui grid attached">
        <div class="ui four wide column" style="padding: 0px 0px 0px 10px;">
            <style>
                #mySnapshotListTable tbody td {
                    padding-top: 0.56em;
                    padding-bottom: 0.56em;
                }

                #mySnapshotListTable .snapshot-title {
                    display: inline-flex;
                    align-items: center;
                    gap: 0.35em;
                    max-width: 100%;
                }

                #mySnapshotListTable .snapshot-title span {
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }

                #mySnapshotListTable .meta-text {
                    font-size: 0.88em;
                    color: #666;
                    white-space: nowrap;
                }

                #mySnapshotListTable .hover.dropdown > .icon {
                    margin: 0;
                }

                #mySnapshotListTable .empty-state-row td {
                    color: #999;
                    padding: 30px 0;
                    text-align: center;
                }
            </style>
            <div class="segment">
                <div class="ui block header top attached segment">
                    <div class="dropdown item" style="margin: auto;float: right">
                        <i class="icon refresh"> </i>
                        <a href="javascript:location.reload()">刷新</a>
                    </div>
                    <div class="ui inline click dropdown">
                        <div class="text">
                            我的快照
                        </div>
                        <i class="dropdown icon"></i>
                        <div class="menu">
                            <a class="item" href="/p/${project.id}/monitor">
                                实时监控
                            </a>
                            <a class="item active" href="/p/${project.id}/snapshot/my">
                                我的快照
                            </a>
                        </div>
                    </div>
                </div>
                <div class="ui attached segment" style="padding: 0; height: calc(100vh - 150px);overflow:auto">
                    <table id="mySnapshotListTable" class="ui selectable compact very basic single line table" style="table-layout: fixed; border: 1px solid rgba(34,36,38,.08);">
                        <tbody id="mySnapshotTableBody">
                        <#list snapshots as snap >
                            <tr data-snapshot-id="${snap.id}" onclick="openMonitorDetail('${snap.traceId}');">
                                <td title="${snap.name}">
                                    <a class="snapshot-title" href="javascript:void(0);"><i class="file outline icon"></i><span>${snap.name}</span></a>
                                </td>
                                <td class="right aligned meta-text">
                                    <span title="${snap.updateTimeText!'-'}">${snap.updateTimeRelativeText!'-'}</span>
                                </td>
                                <td style="width: 44px;">
                                    <div class="ui hover dropdown">
                                        <i class="setting link icon"></i>
                                        <div class="ui left menu">
                                            <div class="ui dropdown item">
                                                <i class="share alternate icon"></i>
                                                <i class="dropdown icon"></i>
                                                共享设置
                                                <div class="menu">
                                                    <div class="header">
                                                        <div class="ui toggle checkbox ${snap.id}">
                                                            <input type="checkbox" <#if snap.share??&&snap.share==true>
                                                                checked="checked"</#if> >
                                                        </div>
                                                        <script>
                                                            $(function () {
                                                                $('.ui.checkbox.${snap.id}').checkbox({
                                                                    onChecked: function () {
                                                                        $.getJSON("/p/${project.id}/snapshot/openShare/${snap.id}", function (results) {
                                                                            $(".shareUrl.${snap.id}").removeClass("disabled");
                                                                        })
                                                                    },
                                                                    onUnchecked: function () {
                                                                        $.getJSON("/p/${project.id}/snapshot/closeShare/${snap.id}", function (results) {
                                                                            $(".shareUrl.${snap.id}").addClass("disabled");
                                                                        })
                                                                    }
                                                                });
                                                            })
                                                        </script>
                                                    </div>
                                                    <a class="item shareUrl ${snap.id} <#if snap.share??&&snap.share==true>  <#else>disabled </#if> "
                                                       href="/share/snapshot/${snap.id}" target="_blank">
                                                        <i class="ui linkify icon "></i>
                                                        访问共享页
                                                    </a>
                                                </div>
                                            </div>
                                            <a class="item" onclick="openSnapshotEdit('${snap.id}');"><i class="edit icon"></i>
                                                编辑
                                                <div id="snapshotEditDialog" class="ui dynamic modal standard">
                                                </div>
                                            </a>
                                            <div class="divider"></div>
                                            <a class="item" href="javascript:void(0);" onclick="deleteSnapshot('${snap.id}');">
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
                                <td colspan="3">暂无数据</td>
                            </tr>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="ui twelve wide column" style="padding: 0px 14px 0px 14px;">
            <!--未选择请求时提示-->
            <div id="emptyTip" class="ui grid middle aligned center aligned segment"
                 style="height: 100%;margin-top: 0px; background: #f7f7f7">
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
                <!--表头-->
                <div id="tabSwitch" class="ui top attached secondary compact menu">
                    <a class="item active" data-tab="flow">流程图</a>
                    <a class="item" data-tab="stack">堆栈列表</a>
                    <span id="monitorDetailTitle" class="ui tiny header" style="color: gray;padding: 0;margin-top: 12px;margin-bottom: 10px;"></span>
                    <div class="right menu">
                        <div class="item">
                            <div class="ui secondary button" data-tooltip="保存至系统快照" data-position="left center"
                                 onclick="openCreateSystemSnapshot();" style="font-size: 0.9em;">
                                保存
                            </div>
                        </div>
                    </div>
                </div>
                <!-- 内容 -->
                <div class="ui segment attached" style="min-height: calc(100vh - 150px);padding: 0px;">
                    <div class="ui tab active" data-tab="flow" style="padding: 2px">
                        <svg id="svg-canvas" style="min-height: calc(100vh - 150px);padding: 0px;" width="100%"></svg>
                    </div>
                    <div class="ui tab" data-tab="stack" style="padding: 2px">
                    </div>
                    <!--节点详情-->
                </div>
            </div>

            <!--代码报告-->
            <div id="codeReport" style="display: none">
                <div class="ui tab active" data-tab="report" style="padding: 2px; max-height: calc(100vh - 110px);overflow:auto">
                </div>
            </div>
        </div>


        <#--保存系统快照 窗口-->
        <div id="systemSnapshotDialog" class="ui modal standard save snapshot">
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
                    $('#mySnapshotTableBody').append('<tr id="mySnapshotEmptyRow" class="empty-state-row"><td colspan="3">暂无数据</td></tr>');
                }
                if (hasDataRow) {
                    $('#mySnapshotEmptyRow').remove();
                }
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

            $('.ui.hover.dropdown').dropdown({
                on: 'hover'
            });
            $('.ui.click.dropdown').dropdown({
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
        </script>
    </div>
</div>
</body>
</html>

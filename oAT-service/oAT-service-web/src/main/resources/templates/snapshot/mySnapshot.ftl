<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>系统快照-我的快照</title>
    <#include "../common.ftl">
    <script src="/js/d3.min.js" charset="utf-8"></script>
    <script src="/js/dagre-d3.min.js"></script>
    <link href="/css/monokai_sublime.min.css" rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script src="/js/clipboard.min.js"></script>
    <script src="/js/common.js?v=2"></script>
    <link href="/css/common.css" rel="stylesheet">
</head>

<style id="css">
    tr.selected td {
        background-color: #ffe48d;
    }

    body.pushable > .pusher {
        background: #f7f7f7;
    }

    #snapshotDetailPane {
        background: #fff;
        border: 1px solid rgba(34,36,38,.15);
        border-radius: .28571429rem;
        min-height: calc(100vh - 140px);
        overflow: hidden;
    }

    #snapshotDetailToolbar {
        margin-bottom: 10px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
    }

    #snapshotDetailCurrentName {
        font-size: 16px;
        font-weight: 600;
        color: rgba(0,0,0,.85);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    #snapshotDetailContainer {
        min-height: calc(100vh - 190px);
        overflow: auto;
        background: #fff;
    }

    #snapshotDetailContainer > .ui.grid.attached.container {
        width: 100% !important;
        margin-top: 0 !important;
        margin-left: 0 !important;
        margin-right: 0 !important;
    }

    #snapshotDetailContainer > .ui.grid.attached.container > .four.wide.column {
        display: none !important;
    }

    #snapshotDetailContainer > .ui.grid.attached.container > .twelve.wide.column {
        width: 100% !important;
    }

    #snapshotDetailContainer .ui.breadcrumb,
    #snapshotDetailContainer .ui.vertical.basic.large.icon.buttons {
        display: none !important;
    }
</style>

<body>
<#include "../commonFunction.ftl">

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
                    color: rgba(0,0,0,.87);
                }

                #mySnapshotListTable tr.snapshot-row {
                    cursor: pointer;
                }

                #mySnapshotListTable tr.snapshot-row td {
                    transition: background-color .15s ease, color .15s ease;
                }

                #mySnapshotListTable tr.snapshot-row:hover td {
                    background: rgba(0,0,0,.03);
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
                            <tr class="snapshot-row" data-snapshot-id="${snap.id}">
                                <td title="${snap.name}">
                                    <a class="snapshot-title" href="${mySnapshotDetailHref(project.id, snap.id)}"><i class="file outline icon"></i><span>${snap.name}</span></a>
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
            <div id="snapshotDetailToolbar" class="text">
                <div id="snapshotDetailCurrentName">快照详情</div>
                <a id="snapshotDetailLink" href="javascript:void(0)"><i class="icon arrow right"></i>打开独立详情页</a>
            </div>
            <div id="snapshotDetailPane">
                <div id="snapshotDetailContainer"></div>
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
                    $('#mySnapshotTableBody').append('<tr id="mySnapshotEmptyRow" class="empty-state-row"><td colspan="3">暂无数据</td></tr>');
                }
                if (hasDataRow) {
                    $('#mySnapshotEmptyRow').remove();
                }
            }

            function runInlineScripts(container) {
                container.find('script').each(function () {
                    var oldScript = this;
                    var newScript = document.createElement('script');

                    $.each(oldScript.attributes, function () {
                        newScript.setAttribute(this.name, this.value);
                    });

                    if (oldScript.text) {
                        newScript.text = oldScript.text;
                    }

                    document.body.appendChild(newScript);
                    document.body.removeChild(newScript);
                });
            }

            function openSnapshotDetail(snapshotId, detailUrl) {
                let detailContainer = $('#snapshotDetailContainer');
                var selectedName = $.trim($("tr[data-snapshot-id='" + snapshotId + "'] .snapshot-title span").text()) || '快照详情';
                detailContainer.html(
                    '<div class="ui active inverted dimmer" style="min-height: 240px; border-radius: 12px;">'
                    + '<div class="ui text loader">正在加载快照详情...</div>'
                    + '</div>'
                );
                $('#snapshotDetailCurrentName').text(selectedName);
                $('#snapshotDetailLink').attr('href', detailUrl || 'javascript:void(0)');

                $.ajax({
                    url: '/p/${project.id}/snapshot/detail?id=' + encodeURIComponent(snapshotId),
                    dataType: 'html',
                    success: function (responseHtml) {
                        detailContainer.html(responseHtml);
                        runInlineScripts(detailContainer);
                        if (window.hljs && typeof window.hljs.initHighlighting === 'function') {
                            try {
                                window.hljs.initHighlighting.called = false;
                                window.hljs.initHighlighting();
                            } catch (e) {
                            }
                        }
                        detailContainer.scrollTop(0);
                    },
                    error: function () {
                        $('#snapshotDetailCurrentName').text('快照详情');
                        $('#snapshotDetailLink').attr('href', 'javascript:void(0)');
                        detailContainer.html('<div class="ui negative message" style="margin: 12px;">详情加载失败，请稍后重试。</div>');
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

            $('.ui.hover.dropdown').dropdown({
                on: 'hover'
            });
            $('.ui.click.dropdown').dropdown({
                on: 'click'
            });
            $("table.selectable tr.snapshot-row").click(function (e) {
                $(this).parent().children("tr.snapshot-row").removeClass("selected");
                $(this).addClass("selected");
            });

            $("table.selectable tr.snapshot-row").on('click', function (e) {
                if ($(e.target).closest('.ui.hover.dropdown, .ui.hover.dropdown *').length > 0) {
                    return;
                }

                if ($(e.target).closest('.snapshot-title, .snapshot-title *').length > 0) {
                    return;
                }

                var $row = $(this);
                var snapshotId = $row.data('snapshot-id');
                var detailUrl = $row.find('.snapshot-title').attr('href');
                openSnapshotDetail(snapshotId, detailUrl);
            });

            $("#filterLabels").change(function () {
                $("#filterForm").submit();
            });
            $("#filterSort").change(function () {
                $("#filterForm").submit();
            });

            function openSnapshotEdit(id) {
                $('#snapshotEditDialog').load('/p/${project.id}/snapshot/edit?id=' + id);
                $('#snapshotEditDialog').modal('show');
            }

            var firstSnapshotRow = $("table.selectable tr.snapshot-row").first();
            if (firstSnapshotRow.length > 0) {
                firstSnapshotRow.addClass('selected');
                openSnapshotDetail(firstSnapshotRow.data('snapshot-id'), firstSnapshotRow.find('.snapshot-title').attr('href'));
            } else {
                $('#snapshotDetailCurrentName').text('快照详情');
                $('#snapshotDetailContainer').html('<div class="ui placeholder segment" style="margin:12px;"><div class="ui icon header"><i class="file outline icon"></i>暂无快照可展示</div></div>');
            }
        </script>
    </div>
</div>
</body>
</html>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>系统快照-我的快照</title>
    <#include "../common.ftl">
</head>

<style id="css">
    tr.selected td {
        background-color: #ffe48d;
    }

    body.pushable > .pusher {
        background: #f7f7f7;
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
                            <tr class="snapshot-row" data-snapshot-id="${snap.id}" onclick="window.location.href='${mySnapshotDetailHref(project.id, snap.id)}';">
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
            <div class="analysis-panel-shell">
                <div class="analysis-hero">
                    <div class="analysis-hero-title">快照分析工作台</div>
                    <div class="analysis-hero-subtitle">选中左侧快照后，可在这里查看覆盖报告、影响分析日志和汇总信息。</div>
                    <div class="analysis-hero-tags">
                        <span class="analysis-hero-tag">覆盖分析</span>
                        <span class="analysis-hero-tag">影响追踪</span>
                        <span class="analysis-hero-tag">用例命中</span>
                    </div>
                </div>
                <div class="analysis-body">
                    <div class="analysis-summary-grid">
                        <div class="analysis-stat-card primary">
                            <div class="label">当前选中</div>
                            <div id="analysisSelectedCount" class="value">0</div>
                            <div class="hint">已选快照数量</div>
                        </div>
                        <div class="analysis-stat-card positive">
                            <div class="label">我的快照</div>
                            <div id="analysisSnapshotCount" class="value">${snapshots?size}</div>
                            <div class="hint">当前筛选结果</div>
                        </div>
                        <div class="analysis-stat-card warning">
                            <div class="label">标签筛选</div>
                            <div id="analysisLabelCount" class="value">${(filterLabels!'')?has_content?then(filterLabels?split(',')?size, 0)}</div>
                            <div class="hint">已启用标签数</div>
                        </div>
                        <div class="analysis-stat-card danger">
                            <div class="label">关键词搜索</div>
                            <div id="analysisKeywordState" class="value">${(keyword!'')?has_content?then('ON', 'OFF')}</div>
                            <div class="hint">当前是否按名称筛选</div>
                        </div>
                    </div>

                    <div id="analysisEmptyState" class="analysis-empty">
                        <i class="chart bar outline icon"></i>
                        <div class="analysis-empty-title">分析工作台即将开放</div>
                        <div class="analysis-empty-desc">
                            当前页面主要用于管理“我的快照”。如需继续操作，请从左侧进入快照详情，或直接打开覆盖报告。这里后续会接入真实分析数据，不再展示示意日志。
                        </div>
                    </div>

                    <div id="analysisWorkspace" style="display: none;">
                        <div class="analysis-section-title">
                            <h4 class="ui header">工作台规划</h4>
                            <span class="ui mini grey basic label">占位说明</span>
                        </div>
                        <div class="ui placeholder segment" style="border-radius: 12px; min-height: 220px;">
                            <div class="paragraph">
                                <div class="line"></div>
                                <div class="line"></div>
                                <div class="line"></div>
                                <div class="line"></div>
                            </div>
                            <div class="ui small message" style="margin-top: 16px; text-align: left;">
                                <div class="header">后续计划展示内容</div>
                                <ul class="list" style="margin-top: 8px;">
                                    <li>快照变更摘要</li>
                                    <li>真实影响分析日志</li>
                                    <li>覆盖率与命中用例联动信息</li>
                                </ul>
                            </div>
                            <div style="margin-top: 14px;">
                                <a class="ui mini primary basic button" href="/p/${project.id}/snapshot/mySnapshotsCodeReport">查看覆盖报告</a>
                            </div>
                        </div>
                    </div>

                    <!--代码报告-->
                    <div id="codeReport" style="display: none; margin-top: 18px;">
                        <div class="analysis-section-title">
                            <h4 class="ui header">覆盖率报告</h4>
                        </div>
                        <div class="ui tab active" data-tab="report" style="padding: 2px; max-height: calc(100vh - 110px);overflow:auto">
                        </div>
                    </div>
                </div>
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
            function escapeAnalysisHtml(text) {
                return $('<div>').text(text || '').html();
            }

            function detectAnalysisLogType(line) {
                if (line.indexOf('新增') >= 0) {
                    return 'add';
                }
                if (line.indexOf('修改') >= 0) {
                    return 'update';
                }
                if (line.indexOf('删除') >= 0) {
                    return 'delete';
                }
                if (line.indexOf('比对完成') >= 0 || line.indexOf('分析完成') >= 0 || line.indexOf('报告') >= 0) {
                    return 'done';
                }
                if (line.indexOf('查找') >= 0 || line.indexOf('检索') >= 0 || line.indexOf('命中') >= 0 || line.indexOf('影响') >= 0) {
                    return 'search';
                }
                return 'default';
            }

            function detectAnalysisLogGroup(line) {
                if (line.indexOf('发现 [新增]') >= 0 || line.indexOf('发现 [修改]') >= 0 || line.indexOf('发现 [删除]') >= 0) {
                    return '变更发现';
                }
                if (line.indexOf('比对完成') >= 0 || line.indexOf('变更统计') >= 0 || line.indexOf('当前应用快照数') >= 0) {
                    return '比对汇总';
                }
                if (line.indexOf('开始分析用例影响') >= 0 || line.indexOf('查找快照影响') >= 0 || line.indexOf('查找影响用例') >= 0 || line.indexOf('命中用例') >= 0) {
                    return '影响分析';
                }
                return '运行日志';
            }

            function highlightAnalysisLogText(text) {
                var html = escapeAnalysisHtml(text);
                html = html.replace(/(类名|类级候选|方法候选|检索候选|方法源码键|影响数|命中用例ID|命中用例|当前应用快照数|变更统计)(：|:)/g, '<span class="highlight-key">$1$2</span>');
                html = html.replace(/(发现 \[新增\]|发现 \[修改\]|发现 \[删除\]|比对完成|开始分析用例影响)/g, '<span class="highlight-key">$1</span>');
                html = html.replace(/(影响数\s*[：:]?\s*0|命中快照\s*[：:]?\s*-|命中用例ID\s*[：:]?\s*-|命中用例\s*[：:]?\s*-|未找到)/g, '<span class="highlight-danger">$1</span>');
                html = html.replace(/(影响数\s*[：:]?\s*[1-9]\d*|命中用例ID\s*[：:]?\s*[^,，\s]+|命中用例\s*[：:]?\s*\[[^\]]+\])/g, '<span class="highlight-success">$1</span>');
                html = html.replace(/(web3Server\.[A-Za-z0-9_$.]+|[A-Za-z0-9_/$.-]+\.[A-Za-z0-9_$.]+\([^)]*\)|\/[A-Za-z0-9_\-\/{}]+|[A-Za-z0-9_.$-]+\*[A-Za-z0-9_.$*\-]*)/g, '<span class="highlight-value">$1</span>');
                html = html.replace(/(候选|检索|回退到类级别检索|源码键)/g, '<span class="highlight-warning">$1</span>');
                return html;
            }

            function buildAnalysisLogTag(type) {
                var labels = {
                    add: '新增',
                    update: '修改',
                    delete: '删除',
                    done: '完成',
                    search: '分析',
                    default: '日志'
                };
                return '<span class="analysis-log-tag ' + type + '">' + labels[type] + '</span>';
            }

            function renderAnalysisLogs(logText) {
                if (!logText) {
                    return '<div class="analysis-log-placeholder">暂无分析日志，当前展示的是预留面板样式。</div>';
                }

                var groups = [];
                var groupMap = {};
                logText.split(/\r?\n/)
                    .filter(function (line) { return $.trim(line).length > 0; })
                    .forEach(function (line) {
                        var groupName = detectAnalysisLogGroup(line);
                        if (!groupMap[groupName]) {
                            groupMap[groupName] = { name: groupName, lines: [] };
                            groups.push(groupMap[groupName]);
                        }
                        groupMap[groupName].lines.push(line);
                    });

                return groups.map(function (group) {
                    var body = group.lines.map(function (line) {
                        var match = line.match(/^(\d{2}:\d{2}:\d{2})\s*(.*)$/);
                        var time = match ? match[1] : '日志';
                        var content = match ? match[2] : line;
                        var type = detectAnalysisLogType(content);
                        return '<div class="analysis-log-line">'
                            + '<span class="analysis-log-marker ' + type + '"></span>'
                            + '<div class="analysis-log-main">'
                            + '<span class="analysis-log-time">' + escapeAnalysisHtml(time) + '</span>'
                            + '<div class="analysis-log-content">'
                            + '<span class="analysis-log-tags">' + buildAnalysisLogTag(type) + '</span>'
                            + '<span class="analysis-log-text">' + highlightAnalysisLogText(content) + '</span>'
                            + '</div>'
                            + '</div>'
                            + '</div>';
                    }).join('');

                    return '<div class="analysis-log-group">'
                        + '<div class="analysis-log-group-header">'
                        + '<div class="analysis-log-group-title"><i class="tasks icon"></i><span>' + escapeAnalysisHtml(group.name) + '</span></div>'
                        + '<div class="analysis-log-group-count">' + group.lines.length + ' 条</div>'
                        + '</div>'
                        + '<div class="analysis-log-group-body">' + body + '</div>'
                        + '</div>';
                }).join('');
            }

            function buildDemoAnalysisLog(snapshotName) {
                return [
                    '22:35:56 发现 [新增] 类: web3Server.config.MultipartConfig',
                    '22:35:56 发现 [新增] 类: web3Server.controller.DetailController',
                    '22:35:56 发现 [修改] 类: web3Server.controller.FileUploadController',
                    '22:35:56 比对完成: 共分析 5 个类(新增:2, 修改:3, 删除:0)',
                    '22:35:56 变更统计: 新增:6, 修改:1, 删除:0',
                    '22:35:56 当前应用快照数: 1',
                    '22:35:56 开始分析用例影响',
                    '22:35:56 查找快照影响类名: web3Server.controller.FileUploadController 方法: uploadFileWithCommons 影响数: 1, 命中用例ID: Xt_QoJ0BpIVhk_7mKoVb, 命中用例: [' + snapshotName + ' /web3/upload/commons]',
                    '22:35:57 查找影响用例类名: web3Server.controller.Web3Controller 方法: web3 未找到, 回退到类级别检索, 影响数: 0, 命中用例ID: -, 命中用例: -'
                ].join('\n');
            }

            function showAnalysisForSnapshot(snapshotId, snapshotName, snapshotTime, detailUrl) {
                $('#analysisSelectedCount').text(snapshotId ? 1 : 0);
                $('#analysisSnapshotName').text(snapshotName || '-');
                $('#analysisSnapshotTime').text(snapshotTime || '-');
                $('#analysisDetailLink').attr('href', detailUrl || 'javascript:void(0);');
                $('#analysisEmptyState').hide();
                $('#analysisWorkspace').show();
                $('#analysisLogBoard').html(renderAnalysisLogs(buildDemoAnalysisLog(snapshotName || '当前快照')));
                openCodeReport(snapshotId, snapshotName);
            }

            function clearAnalysisWorkspace() {
                $('#analysisSelectedCount').text(0);
                $('#analysisEmptyState').show();
                $('#analysisWorkspace').hide();
                $('#analysisSnapshotName').text('-');
                $('#analysisSnapshotTime').text('-');
                $('#analysisDetailLink').attr('href', 'javascript:void(0);');
                $('#analysisLogBoard').html(renderAnalysisLogs(''));
            }

            function ensureMySnapshotEmptyState() {
                var hasDataRow = $('#mySnapshotTableBody tr[data-snapshot-id]').length > 0;
                if (!hasDataRow && $('#mySnapshotEmptyRow').length === 0) {
                    $('#mySnapshotTableBody').append('<tr id="mySnapshotEmptyRow" class="empty-state-row"><td colspan="3">暂无数据</td></tr>');
                }
                if (hasDataRow) {
                    $('#mySnapshotEmptyRow').remove();
                }
            }

            function openCodeReport(snapshotId, snapshotName) {
                $('#codeReport').show();
                let reportTab = $('#codeReport .ui.tab[data-tab="report"]');
                let url = snapshotId
                    ? '/p/${project.id}/snapshot/mySnapshotCodeReport?snapshotId=' + encodeURIComponent(snapshotId)
                    : '/p/${project.id}/snapshot/mySnapshotsCodeReport';

                reportTab.html(
                    '<div class="ui active inverted dimmer" style="min-height: 240px; border-radius: 12px;">'
                    + '<div class="ui text loader">正在加载' + escapeAnalysisHtml(snapshotName || '覆盖率报告') + '...</div>'
                    + '</div>'
                );

                $.ajax({
                    url: url,
                    dataType: 'html',
                    success: function (responseHtml) {
                        reportTab.html(responseHtml);
                        if (snapshotName) {
                            reportTab.prepend(
                                '<div class="ui small positive message" style="margin-bottom:12px;">'
                                + '当前已选快照：<strong>' + escapeAnalysisHtml(snapshotName) + '</strong>，已自动加载该快照的专属覆盖率报告。'
                                + '</div>'
                            );
                        }
                    },
                    error: function () {
                        reportTab.html('<div class="ui negative message">报告加载失败，请稍后重试。</div>');
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

            $('#analysisLogBoard').html(renderAnalysisLogs(''));

            $("table.selectable tr.snapshot-row").on('click', function (e) {
                var $row = $(this);
                var snapshotId = $row.data('snapshot-id');
                var snapshotName = $.trim($row.find('.snapshot-title span').text());
                var snapshotTime = $.trim($row.find('.meta-text span').attr('title') || $row.find('.meta-text span').text());
                var detailUrl = $row.attr('onclick').match(/window.location.href='([^']+)'/);
                showAnalysisForSnapshot(snapshotId, snapshotName, snapshotTime, detailUrl ? detailUrl[1] : 'javascript:void(0);');
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
        </script>
    </div>
</div>
</body>
</html>

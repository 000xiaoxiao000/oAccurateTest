<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/html">
<head>
    <meta charset="UTF-8">
    <title>版本中心-比对控制台</title>
    <#include "../common.ftl">
    <style>
        body {
            background: #f6f8fb;
        }

        .compare-shell {
            background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
            border: 1px solid rgba(34, 36, 38, 0.08);
            border-radius: 14px;
            box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
            overflow: hidden;
        }

        .compare-hero {
            padding: 18px 20px 14px;
            background: linear-gradient(135deg, #1f6feb 0%, #5b8def 100%);
            color: #fff;
        }

        .compare-hero .title-row {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
        }

        .compare-hero .main-title {
            font-size: 1.2rem;
            font-weight: 700;
            margin-bottom: 6px;
        }

        .compare-hero .sub-title {
            color: rgba(255,255,255,.88);
            font-size: 0.95rem;
        }

        .compare-badges {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-top: 12px;
        }

        .compare-badge {
            background: rgba(255,255,255,.16);
            border: 1px solid rgba(255,255,255,.22);
            color: #fff;
            padding: 6px 10px;
            border-radius: 999px;
            font-size: 12px;
            line-height: 1;
            backdrop-filter: blur(4px);
        }

        .compare-content {
            padding: 18px;
        }

        .summary-grid {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 14px;
            margin: 16px 0 18px;
        }

        .summary-card {
            background: #fff;
            border: 1px solid rgba(34, 36, 38, 0.08);
            border-radius: 12px;
            padding: 14px 16px;
            box-shadow: 0 3px 10px rgba(15, 23, 42, 0.04);
        }

        .summary-card .label {
            color: #6b7280;
            font-size: 12px;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: .04em;
        }

        .summary-card .value {
            font-size: 28px;
            font-weight: 700;
            color: #111827;
            line-height: 1.1;
        }

        .summary-card .hint {
            margin-top: 8px;
            color: #6b7280;
            font-size: 12px;
        }

        .summary-card.positive .value {
            color: #16a34a;
        }

        .summary-card.warning .value {
            color: #d97706;
        }

        .summary-card.negative .value {
            color: #dc2626;
        }

        .section-title {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin: 18px 0 10px;
        }

        .section-title h4 {
            margin: 0;
        }

        .compare-stats-table th {
            background: #f9fafb;
        }

        .compare-stats-table td,
        .compare-stats-table th {
            text-align: center;
            vertical-align: middle;
        }

        .compare-stats-table td:first-child,
        .compare-stats-table th:first-child {
            text-align: left;
            font-weight: 600;
        }

        .metric-pill {
            display: inline-flex;
            min-width: 56px;
            justify-content: center;
            padding: 4px 10px;
            border-radius: 999px;
            font-weight: 700;
        }

        .metric-pill.add {
            background: #ecfdf3;
            color: #16a34a;
        }

        .metric-pill.update {
            background: #fff7ed;
            color: #ea580c;
        }

        .metric-pill.delete {
            background: #fef2f2;
            color: #dc2626;
        }

        #compareLogger {
            background: linear-gradient(180deg, #0f172a 0%, #111827 100%);
            color: #dbeafe;
            border-radius: 12px;
            border: 1px solid rgba(15, 23, 42, 0.35);
            padding: 14px 16px;
            max-height: 520px;
            overflow: auto;
            font-family: Menlo, Monaco, Consolas, monospace;
            font-size: 13px;
            line-height: 1.75;
            white-space: normal;
            box-shadow: inset 0 1px 0 rgba(255,255,255,.03);
        }

        #compareLogger .log-group {
            margin-bottom: 16px;
            border: 1px solid rgba(148, 163, 184, 0.12);
            border-radius: 10px;
            overflow: hidden;
            background: rgba(255,255,255,0.02);
        }

        #compareLogger .log-group:last-child {
            margin-bottom: 0;
        }

        #compareLogger .log-group-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            padding: 10px 12px;
            background: rgba(30, 41, 59, 0.9);
            border-bottom: 1px solid rgba(148, 163, 184, 0.12);
        }

        #compareLogger .log-group-title {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            color: #f8fafc;
            font-weight: 700;
        }

        #compareLogger .log-group-count {
            color: #93c5fd;
            font-size: 12px;
        }

        #compareLogger .log-group-body {
            padding: 6px 12px 8px;
        }

        #compareLogger .log-line {
            display: flex;
            align-items: flex-start;
            gap: 12px;
            padding: 8px 0;
            border-bottom: 1px dashed rgba(148, 163, 184, 0.12);
            position: relative;
        }

        #compareLogger .log-line:last-child {
            border-bottom: none;
        }

        #compareLogger .log-marker {
            width: 10px;
            height: 10px;
            margin-top: 8px;
            border-radius: 50%;
            flex-shrink: 0;
            box-shadow: 0 0 0 4px rgba(255,255,255,0.04);
        }

        #compareLogger .log-marker.add {
            background: #22c55e;
        }

        #compareLogger .log-marker.update {
            background: #f59e0b;
        }

        #compareLogger .log-marker.delete {
            background: #ef4444;
        }

        #compareLogger .log-marker.done {
            background: #38bdf8;
        }

        #compareLogger .log-marker.search {
            background: #a78bfa;
        }

        #compareLogger .log-marker.default {
            background: #64748b;
        }

        #compareLogger .log-main {
            display: flex;
            gap: 10px;
            flex: 1;
            min-width: 0;
        }

        #compareLogger .log-time {
            color: #93c5fd;
            min-width: 64px;
            flex-shrink: 0;
        }

        #compareLogger .log-content {
            flex: 1;
            min-width: 0;
        }

        #compareLogger .log-tags {
            display: inline-flex;
            gap: 6px;
            flex-wrap: wrap;
            margin-right: 6px;
        }

        #compareLogger .log-tag {
            display: inline-flex;
            align-items: center;
            padding: 1px 8px;
            border-radius: 999px;
            font-size: 11px;
            font-weight: 700;
            letter-spacing: .02em;
        }

        #compareLogger .log-tag.add {
            background: rgba(34, 197, 94, 0.15);
            color: #86efac;
        }

        #compareLogger .log-tag.update {
            background: rgba(245, 158, 11, 0.15);
            color: #fcd34d;
        }

        #compareLogger .log-tag.delete {
            background: rgba(239, 68, 68, 0.15);
            color: #fca5a5;
        }

        #compareLogger .log-tag.done {
            background: rgba(56, 189, 248, 0.16);
            color: #7dd3fc;
        }

        #compareLogger .log-tag.search {
            background: rgba(167, 139, 250, 0.16);
            color: #c4b5fd;
        }

        #compareLogger .log-text {
            color: #e5eefc;
            word-break: break-word;
        }

        #compareLogger .log-text .muted {
            color: #94a3b8;
        }

        #compareLogger .log-text .highlight-key {
            color: #f8fafc;
            font-weight: 700;
        }

        #compareLogger .log-text .highlight-value {
            color: #7dd3fc;
        }

        #compareLogger .log-text .highlight-success {
            color: #86efac;
            font-weight: 700;
        }

        #compareLogger .log-text .highlight-warning {
            color: #fcd34d;
            font-weight: 700;
        }

        #compareLogger .log-text .highlight-danger {
            color: #fca5a5;
            font-weight: 700;
        }

        .progress-panel {
            background: #fff;
            border: 1px solid rgba(34, 36, 38, 0.08);
            border-radius: 12px;
            padding: 16px;
        }

        .progress-panel .top-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
            margin-bottom: 10px;
        }

        .progress-panel .status-text {
            color: #4b5563;
            font-size: 13px;
        }
    </style>
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui container">
    <div class="ui small breadcrumb version-breadcrumb">
        <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/${appInfo.id}/version/list">${appInfo.name}</a>
        <span class="divider">/</span>
        <div class="active section">比对控制台</div>
    </div>
</div>

<!--内容主体-->
<div class="ui container version-center-page">
    <div class="version-page-layout">
        <div class="version-page-side">
            <#assign appId=appInfo.id/>
            <#assign appName=appInfo.name/>
            <#assign versionCompareActive="active"/>
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="version-page-main">
            <div class="version-page-header">
                <div>
                    <div class="version-page-kicker">
                        <i class="terminal icon"></i>
                        版本中心
                    </div>
                    <h1 class="version-page-title">比对控制台</h1>
                    <p class="version-page-desc">实时跟踪版本差异分析进度、统计结果和运行日志。</p>
                </div>
                <div class="version-page-actions">
                    <a class="ui button" href="/p/${project.id}/${appInfo.id}/version/compare">
                        <i class="left arrow icon"></i>返回版本比对
                    </a>
                    <a id="openReport" class="ui primary button" href="#" style="display: none">查看报告</a>
                </div>
            </div>
            <div class="version-page-body">
                <div class="compare-shell">
            <div class="compare-hero">
                <div class="title-row">
                    <div>
                        <div class="main-title">${compareJob.name}</div>
                        <div class="sub-title">版本差异分析正在执行，下面会实时展示进度、统计与日志。</div>
                    </div>
                </div>
                <div class="compare-badges">
                    <#if compareJob.gitBranch??>
                        <span class="compare-badge">分支：${compareJob.gitBranch}</span>
                    </#if>
                    <#if compareJob.gitOldCommit??>
                        <span class="compare-badge">旧提交：<span class="commit-id" data-content="${compareJob.gitOldCommit}" data-position="top center">${(compareJob.gitOldCommit?length > 8)?then(compareJob.gitOldCommit?substring(0,8), compareJob.gitOldCommit)}</span></span>
                    </#if>
                    <#if compareJob.gitNewCommit??>
                        <span class="compare-badge">新提交：<span class="commit-id" data-content="${compareJob.gitNewCommit}" data-position="top center">${(compareJob.gitNewCommit?length > 8)?then(compareJob.gitNewCommit?substring(0,8), compareJob.gitNewCommit)}</span></span>
                    </#if>
                </div>
            </div>

            <div class="compare-content">
                <div class="progress-panel">
                    <div class="top-row">
                        <div>
                            <div class="ui tiny header" style="margin-bottom: 4px;">当前进度</div>
                            <div id="compareProgressName" class="status-text">${compareJob.progressName}</div>
                        </div>
                    </div>
                    <div id="compareProgress" data-percent="${compareJob.progress}" class="ui active blue indicating progress" style="margin-bottom: 0;">
                        <div class="bar">
                            <div class="progress"></div>
                        </div>
                    </div>
                </div>

                <div class="summary-grid">
                    <div class="summary-card positive">
                        <div class="label">新增变更</div>
                        <div id="totalAdd" class="value">0</div>
                        <div class="hint">类 + 方法新增总数</div>
                    </div>
                    <div class="summary-card warning">
                        <div class="label">修改变更</div>
                        <div id="totalUpdate" class="value">0</div>
                        <div class="hint">类 + 方法修改总数</div>
                    </div>
                    <div class="summary-card negative">
                        <div class="label">删除变更</div>
                        <div id="totalDelete" class="value">0</div>
                        <div class="hint">类 + 方法删除总数</div>
                    </div>
                </div>

                <div class="section-title">
                    <h4 class="ui header">变更统计</h4>
                </div>
                <table class="ui celled table compare-stats-table">
                    <thead>
                    <tr>
                        <th>变更项</th>
                        <th>新增</th>
                        <th>修改</th>
                        <th>删除</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                        <td><i class="cube icon"></i> 类</td>
                        <td><span id="classAdd" class="metric-pill add">0</span></td>
                        <td><span id="classUpdate" class="metric-pill update">0</span></td>
                        <td><span id="classDelete" class="metric-pill delete">0</span></td>
                    </tr>
                    <tr>
                        <td><i class="code icon"></i> 方法</td>
                        <td><span id="methodAdd" class="metric-pill add">0</span></td>
                        <td><span id="methodUpdate" class="metric-pill update">0</span></td>
                        <td><span id="methodDelete" class="metric-pill delete">0</span></td>
                    </tr>
                    </tbody>
                </table>

                <div class="section-title">
                    <h4 class="ui header">实时日志</h4>
                </div>
                <div id="compareLogger"></div>
            </div>
        </div>
            </div>
        </div>
    </div>
</div>

<script>
    function escapeHtml(text) {
        return $('<div>').text(text || '').html();
    }

    function detectLogType(line) {
        if (line.indexOf('新增') >= 0) {
            return 'add';
        }
        if (line.indexOf('修改') >= 0) {
            return 'update';
        }
        if (line.indexOf('删除') >= 0) {
            return 'delete';
        }
        if (line.indexOf('比对完成') >= 0 || line.indexOf('分析完成') >= 0 || line.indexOf('报告已生成') >= 0) {
            return 'done';
        }
        if (line.indexOf('查找') >= 0 || line.indexOf('检索') >= 0 || line.indexOf('命中') >= 0 || line.indexOf('影响') >= 0) {
            return 'search';
        }
        return 'default';
    }

    function detectLogGroup(line) {
        if (line.indexOf('发现 [新增]') >= 0 || line.indexOf('发现 [修改]') >= 0 || line.indexOf('发现 [删除]') >= 0 || line.indexOf('新增方法') >= 0) {
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

    function highlightLogText(text) {
        var html = escapeHtml(text);

        html = html.replace(/(类名|类级候选|方法候选|检索候选|方法源码键|影响数|命中用例ID|命中用例|当前应用快照数|变更统计|分支|旧|新)(：|:)/g, '<span class="highlight-key">$1$2</span>');
        html = html.replace(/(发现 \[新增\]|发现 \[修改\]|发现 \[删除\]|比对完成|开始分析用例影响|报告已生成)/g, '<span class="highlight-key">$1</span>');
        html = html.replace(/(影响数\s*[：:]?\s*0|命中快照\s*[：:]?\s*-|命中用例ID\s*[：:]?\s*-|命中用例\s*[：:]?\s*-|未找到)/g, '<span class="highlight-danger">$1</span>');
        html = html.replace(/(影响数\s*[：:]?\s*[1-9]\d*|命中用例ID\s*[：:]?\s*[^,，\s]+|命中用例\s*[：:]?\s*\[[^\]]+\])/g, '<span class="highlight-success">$1</span>');
        html = html.replace(/(web3Server\.[A-Za-z0-9_$.]+|[A-Za-z0-9_/$.-]+\.[A-Za-z0-9_$.]+\([^)]*\)|\/[A-Za-z0-9_\-\/{}]+|[A-Za-z0-9_.$-]+\*[A-Za-z0-9_.$*\-]*)/g, '<span class="highlight-value">$1</span>');
        html = html.replace(/(候选|检索|回退到类级别检索|源码键)/g, '<span class="highlight-warning">$1</span>');

        return html;
    }

    function buildLogTag(type) {
        var labelMap = {
            add: '新增',
            update: '修改',
            delete: '删除',
            done: '完成',
            search: '分析',
            default: '日志'
        };
        return '<span class="log-tag ' + type + '">' + labelMap[type] + '</span>';
    }

    function renderCompareLogs(logText) {
        if (!logText) {
            return '<div class="log-group"><div class="log-group-body"><div class="log-line"><span class="log-marker default"></span><div class="log-main"><span class="log-time">--:--:--</span><div class="log-content"><span class="log-tags">' + buildLogTag('default') + '</span><span class="log-text">等待日志输出...</span></div></div></div></div></div>';
        }

        var groups = [];
        var groupMap = {};

        logText.split(/\r?\n/)
            .filter(function (line) { return $.trim(line).length > 0; })
            .forEach(function (line) {
                var groupName = detectLogGroup(line);
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
                var type = detectLogType(content);

                return '<div class="log-line">'
                    + '<span class="log-marker ' + type + '"></span>'
                    + '<div class="log-main">'
                    + '<span class="log-time">' + escapeHtml(time) + '</span>'
                    + '<div class="log-content">'
                    + '<span class="log-tags">' + buildLogTag(type) + '</span>'
                    + '<span class="log-text">' + highlightLogText(content) + '</span>'
                    + '</div>'
                    + '</div>'
                    + '</div>';
            }).join('');

            return '<div class="log-group">'
                + '<div class="log-group-header">'
                + '<div class="log-group-title"><i class="tasks icon"></i><span>' + escapeHtml(group.name) + '</span></div>'
                + '<div class="log-group-count">' + group.lines.length + ' 条</div>'
                + '</div>'
                + '<div class="log-group-body">' + body + '</div>'
                + '</div>';
        }).join('');
    }

    function updateSummaryCards(data) {
        var classAdd = data.addClassCount || 0;
        var classUpdate = data.updateClassCount || 0;
        var classDelete = data.deleteClassCount || 0;
        var methodAdd = data.addMethodCount || 0;
        var methodUpdate = data.updateMethodCount || 0;
        var methodDelete = data.deleteMethodCount || 0;

        $('#totalAdd').text(classAdd + methodAdd);
        $('#totalUpdate').text(classUpdate + methodUpdate);
        $('#totalDelete').text(classDelete + methodDelete);
    }

    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.commit-id').popup();

    $('#compareLogger').html(renderCompareLogs(${compareJob.log?json_string}));
    updateSummaryCards({
        addClassCount: ${compareJob.addClassCount!0},
        updateClassCount: ${compareJob.updateClassCount!0},
        deleteClassCount: ${compareJob.deleteClassCount!0},
        addMethodCount: ${compareJob.addMethodCount!0},
        updateMethodCount: ${compareJob.updateMethodCount!0},
        deleteMethodCount: ${compareJob.deleteMethodCount!0}
    });

    function buildReportUrl(reportId) {
        return "/p/${project.id}/version/report/detail/" + reportId;
    }

    function waitForReportReady(reportId, maxAttempts, delayMs) {
        var reportUrl = buildReportUrl(reportId);
        var deferred = $.Deferred();
        var attempts = 0;

        function probe() {
            attempts++;
            $.ajax({
                url: reportUrl,
                method: 'GET',
                cache: false
            }).done(function (html) {
                if (typeof html === 'string' && html.indexOf('比对报告生成中') >= 0) {
                    if (attempts >= maxAttempts) {
                        deferred.resolve(reportUrl);
                    } else {
                        setTimeout(probe, delayMs);
                    }
                    return;
                }
                deferred.resolve(reportUrl);
            }).fail(function () {
                if (attempts >= maxAttempts) {
                    deferred.resolve(reportUrl);
                } else {
                    setTimeout(probe, delayMs);
                }
            });
        }

        probe();
        return deferred.promise();
    }

    $(function () {
        var future = setInterval(function () {
            var results = refreshJob();
            if (!results || !results.data) {
                return;
            }
            if (results.data.finish) {
                clearInterval(future);
                $("#compareProgress").removeClass("active warning").addClass("success");
                $("#compareProgressName").html("比对已完成，正在等待报告可打开...");
                var reportUrl = buildReportUrl(results.data.id);
                $("#openReport").attr("href", reportUrl).show();
                waitForReportReady(results.data.id, 8, 700).then(function (readyUrl) {
                    $("#compareProgressName").html("报告已生成，正在跳转...");
                    window.location.href = readyUrl;
                });
            }
        }, 500);
    });

    function refreshJob() {
        var results = $.ajax({
            url: "/p/${project.id}/version/compare/get?jobId=${compareJob.id}",
            async: false
        }).responseJSON;

        if (!results || !results.data) {
            $("#compareProgressName").html("正在生成报告，请稍候...");
            return results;
        }

        var data = results.data;
        var $progress = $("#compareProgress");
        $progress.progress('set percent', data.progress || 0);
        $("#compareProgressName").html(data.progressName || "等待结果");

        if (data.finish) {
            $progress.removeClass("active warning").addClass("success");
        } else {
            $progress.addClass("active").removeClass("success warning");
        }

        $("#classAdd").text(data.addClassCount || 0);
        $("#classUpdate").text(data.updateClassCount || 0);
        $("#classDelete").text(data.deleteClassCount || 0);
        $("#methodAdd").text(data.addMethodCount || 0);
        $("#methodUpdate").text(data.updateMethodCount || 0);
        $("#methodDelete").text(data.deleteMethodCount || 0);
        updateSummaryCards(data);
        $("#compareLogger").html(renderCompareLogs(data.log || ''));
        return results;
    }
</script>
</body>
</html>

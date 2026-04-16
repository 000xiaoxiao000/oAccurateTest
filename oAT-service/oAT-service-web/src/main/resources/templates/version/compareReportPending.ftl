<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>比对报告生成中</title>
    <#include "../common.ftl">
    <style>
        .compare-report-pending-wrap {
            min-height: 58vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 40px 16px;
        }
        .compare-report-pending-card {
            width: min(560px, 100%);
            background: linear-gradient(180deg, #ffffff 0%, #f6f9fc 100%);
            border: 1px solid #dce5ef;
            border-radius: 18px;
            box-shadow: 0 18px 48px rgba(35, 57, 80, 0.12);
            padding: 28px 30px;
            text-align: center;
        }
        .compare-report-pending-dot {
            width: 14px;
            height: 14px;
            margin: 0 auto 16px;
            border-radius: 50%;
            background: #4d89ff;
            box-shadow: 0 0 0 rgba(77, 137, 255, 0.45);
            animation: comparePendingPulse 1.4s infinite;
        }
        .compare-report-pending-title {
            font-size: 22px;
            font-weight: 700;
            color: #213547;
            margin-bottom: 10px;
        }
        .compare-report-pending-text {
            color: #5c7086;
            line-height: 1.7;
            margin-bottom: 12px;
        }
        .compare-report-pending-meta {
            color: #8493a3;
            font-size: 12px;
            word-break: break-all;
            margin-bottom: 14px;
        }
        .compare-report-pending-counter {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 8px 12px;
            border-radius: 999px;
            background: #eef4fb;
            border: 1px solid #d8e2ef;
            color: #3b546e;
            font-size: 12px;
        }
        .compare-report-pending-actions {
            display: none;
            margin-top: 18px;
            gap: 12px;
            justify-content: center;
            flex-wrap: wrap;
        }
        .compare-report-pending-actions .ui.button {
            min-width: 148px;
        }
        .compare-report-pending-actions.is-visible {
            display: flex;
        }
        @keyframes comparePendingPulse {
            0% { box-shadow: 0 0 0 0 rgba(77, 137, 255, 0.45); }
            70% { box-shadow: 0 0 0 14px rgba(77, 137, 255, 0); }
            100% { box-shadow: 0 0 0 0 rgba(77, 137, 255, 0); }
        }
    </style>
</head>
<body>
<#assign versionItemActive="active">
<#assign appId = appId!"">
<#include "../projectHeader.ftl">
<div class="compare-report-pending-wrap">
    <div class="compare-report-pending-card">
        <div class="compare-report-pending-dot"></div>
        <div class="compare-report-pending-title">比对报告生成中</div>
        <div id="comparePendingText" class="compare-report-pending-text">${retryMessage!"比对报告正在生成或索引刷新中，页面会自动重试..."}</div>
        <div class="compare-report-pending-meta">reportId: ${reportId!"-"}</div>
        <div id="comparePendingCounter" class="compare-report-pending-counter">正在第 <span id="comparePendingAttempt">1</span> / <span id="comparePendingMax">5</span> 次重试</div>
        <div id="comparePendingActions" class="compare-report-pending-actions">
            <a class="ui primary button" href="/p/${project.id}/${appId}/version/report/list?tab=compare&highlightReportId=${reportId}">返回报告列表</a>
            <a class="ui button" href="/p/${project.id}/version/report/${reportId}">手动重试打开</a>
        </div>
    </div>
</div>
<script>
    (function () {
        var reportId = '${reportId}';
        var maxRetries = 5;
        var storageKey = 'compareReportPendingRetry:' + reportId;
        var currentRetry = parseInt(sessionStorage.getItem(storageKey) || '0', 10) + 1;
        sessionStorage.setItem(storageKey, String(currentRetry));
        $('#comparePendingAttempt').text(currentRetry);
        $('#comparePendingMax').text(maxRetries);

        if (currentRetry >= maxRetries) {
            $('#comparePendingText').text('比对报告暂时仍不可用，请返回报告列表查看是否已生成，或稍后手动重试。');
            $('#comparePendingCounter').hide();
            $('#comparePendingActions').addClass('is-visible');
            sessionStorage.removeItem(storageKey);
            return;
        }

        setTimeout(function () {
            window.location.href = '/p/${project.id}/version/report/' + reportId;
        }, 1200);
    })();
</script>
</body>
</html>

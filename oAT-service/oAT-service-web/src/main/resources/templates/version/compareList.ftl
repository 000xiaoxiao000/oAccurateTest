<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-报告列表</title>
    <#include "../common.ftl">
    <style>
        .report-breadcrumb {
            margin: 5px;
        }

        .report-content-grid {
            margin-top: 14px;
        }

        .compare-page-grid {
            margin-top: 12px;
        }

        .compare-page-column {
            padding-top: 0 !important;
            padding-bottom: 0 !important;
        }

        .compare-inline-fields {
            margin: 0 !important;
        }

        .compare-page-label,
        .compare-page-text {
            color: #666;
        }

        .compare-page-total {
            margin-left: 12px;
        }

        .compare-page-summary {
            padding: 8px 0 0 !important;
            border: none !important;
            box-shadow: none !important;
        }

        .coverage-report-meta {
            margin-top: 6px;
            font-size: 0.9em;
            color: #666;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            flex-wrap: wrap;
        }

        .coverage-report-meta > span {
            display: inline-flex;
            align-items: center;
        }

        .coverage-report-meta-label {
            color: #999;
        }

        .coverage-report-meta .meta-chip {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 2px 8px;
            border-radius: 999px;
            border: 1px solid #dde3ea;
            background: #f7f8fa;
            color: #4b5563;
            line-height: 1.4;
        }

        .coverage-type-chip {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-width: 52px;
            padding: 2px 10px;
            border-radius: 999px;
            font-size: 12px;
            font-weight: 700;
            line-height: 1.4;
        }

        .coverage-type-chip-full {
            background: #e8f8ef;
            border: 1px solid #b7e2c8;
            color: #1e7d46;
        }

        .coverage-type-chip-increment {
            background: #fff4e8;
            border: 1px solid #f0d2b4;
            color: #b36214;
        }

        .coverage-action-group {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 4px;
            flex-wrap: wrap;
        }

        .coverage-action-group .ui.button {
            margin: 0 !important;
            padding-left: 10px;
            padding-right: 10px;
        }

        .coverage-regenerate-tip {
            margin-top: 6px;
        }

        .coverage-action-button {
            margin-right: 5px;
        }

        .snapshot-report-trigger {
            float: right;
            margin-top: -5px;
        }

        .snapshots-report-container {
            display: none;
            margin-top: 20px;
        }

        .snapshots-report-segment {
            min-height: 200px;
        }

        .snapshot-owner-meta {
            font-size: 0.9em;
            color: #666;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            flex-wrap: wrap;
        }

        .snapshot-owner-meta .user.icon {
            margin-right: 0;
            color: #7a8694;
        }

        .snapshot-comment-group {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            flex-wrap: wrap;
        }

        .snapshot-comment-chip {
            display: inline-flex;
            align-items: center;
            padding: 2px 8px;
            border-radius: 999px;
            border: 1px solid #dde3ea;
            background: #f7f8fa;
            color: #4b5563;
            font-size: 12px;
            line-height: 1.4;
        }

        .snapshot-action-link {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 30px;
            height: 30px;
            border-radius: 999px;
            border: 1px solid #d7e3f0;
            background: #f4f9ff;
            color: #2185d0;
            transition: background .15s ease, border-color .15s ease, color .15s ease;
        }

        .snapshot-action-link:hover {
            background: #eaf4ff;
            border-color: #b8d5f0;
            color: #1663a8;
        }

        .snapshot-action-link .icon {
            margin: 0;
        }

        .compare-record-stats {
            margin-top: 6px;
            font-size: 0.85em;
            color: #666;
        }

        .compare-record-meta {
            font-size: 0.9em;
            color: #666;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            flex-wrap: wrap;
            white-space: normal;
            max-width: 100%;
            line-height: 1.6;
            vertical-align: middle;
        }

        .compare-record-meta > span {
            display: inline-flex;
            align-items: center;
        }

        .compare-record-meta-label {
            color: #999;
        }

        .compare-record-meta-label-old {
            color: #c97a1f;
        }

        .compare-record-meta-label-new {
            color: #2185d0;
        }

        .compare-record-meta .meta-chip {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 2px 8px;
            border-radius: 999px;
            border: 1px solid #dde3ea;
            background: #f7f8fa;
            color: #4b5563;
            line-height: 1.4;
        }

        .compare-record-meta .commit-id {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 999px;
            border: 1px solid #dde3ea;
            color: #34495e;
            font-size: 12px;
            line-height: 1.4;
        }

        .compare-record-meta .commit-id-old {
            background: #fff4e8;
            border-color: #f0d2b4;
            color: #9a5b16;
        }

        .compare-record-meta .commit-id-new {
            background: #edf6ff;
            border-color: #c8ddf4;
            color: #1f5f96;
        }

        .compare-record-action-group {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 4px;
        }

        .compare-record-action-group .ui.button {
            margin: 0 !important;
            padding-left: 10px;
            padding-right: 10px;
        }

        .compare-report-row-highlight {
            background: linear-gradient(90deg, rgba(255, 248, 214, 0.95) 0%, rgba(255, 252, 238, 0.95) 100%) !important;
            box-shadow: inset 4px 0 0 #f2c94c;
        }

        .compare-report-row-highlight td {
            background: transparent !important;
        }

        .compare-report-new-badge {
            display: inline-flex;
            align-items: center;
            margin-left: 8px;
            padding: 2px 8px;
            border-radius: 999px;
            background: #fff3c4;
            border: 1px solid #f0d36a;
            color: #8a6413;
            font-size: 11px;
            font-weight: 700;
            vertical-align: middle;
        }
    </style>
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui breadcrumb report-breadcrumb">
    <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
    <span class="divider">/</span>
    <a class=" section" href="/p/${project.id}/${appId}/version/list">${appInfo.name}</a>
    <span class="divider">/</span>
    <div class="active section">报告列表</div>
</div>

<!--内容主体-->
<div class="ui grid attached container report-content-grid">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign appName=appInfo.name/>
        <#assign reportListActive="active"/>
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <div class="ui top attached tabular menu">
            <a class="item <#if tab == 'coverage'>active</#if>"
               href="/p/${project.id}/${appId}/version/report/list?tab=coverage">覆盖率</a>
            <a class="item <#if tab == 'compare'>active</#if>"
               href="/p/${project.id}/${appId}/version/report/list?tab=compare">比对记录</a>
        </div>
        <div class="ui bottom attached segment">
            <#if tab == 'coverage'>
                <div id="coverageReportListArea">
                    <h4 class="ui dividing header">全量 / 增量覆盖率报告 (按版本生成)</h4>
                    <#if generatedReports?? && (generatedReports?size > 0)>
                        <table class="ui fixed selectable table celled">
                            <thead>
                            <tr>
                                <th class="five wide">版本号 / 描述</th>
                                <th class="two wide center aligned">报告类型</th>
                                <th class="three wide center aligned">代码 Commit</th>
                                <th class="two wide center aligned">生成时间</th>
                                <th class="two wide center aligned">操作</th>
                            </tr>
                            </thead>
                            <tbody>
                            <#list generatedReports as gReport>
                                <tr>
                                    <td>
                                        <b>${gReport.versionNumber}</b>
                                        <div class="coverage-report-meta">
                                            <#if gReport.reportType == 0>
                                                <#if gReport.repoBranch?has_content>
                                                    <span class="meta-chip"><span class="coverage-report-meta-label">分支:</span> <b>${gReport.repoBranch}</b></span>
                                                </#if>
                                            <#else>
                                                <#if gReport.baseVersionNumber?has_content>
                                                    <span class="meta-chip"><span class="coverage-report-meta-label">基于:</span> <b>${gReport.baseVersionNumber}</b></span>
                                                </#if>
                                            </#if>
                                        </div>
                                        <#if reportNeedRegenerateMap?? && reportNeedRegenerateMap[gReport.id]?? && reportNeedRegenerateMap[gReport.id]>
                                            <div class="coverage-regenerate-tip"><span class="ui mini red label">需重生成</span></div>
                                        </#if>
                                    </td>
                                    <td class="center aligned">
                                        <#if gReport.reportType == 0>
                                            <span class="coverage-type-chip coverage-type-chip-full">全量</span>
                                        <#else>
                                            <span class="coverage-type-chip coverage-type-chip-increment">增量</span>
                                        </#if>
                                    </td>
                                    <td class="center aligned"><i
                                                class="code icon"></i>
                                        <#if gReport.repoCommitId?has_content>
                                            <code class="commit-id commit-id-new" data-content="${gReport.repoCommitId}" data-position="top center">${(gReport.repoCommitId?length > 8)?then(gReport.repoCommitId?substring(0,8), gReport.repoCommitId)}</code>
                                        <#else>
                                            -
                                        </#if>
                                    </td>
                                    <td class="center aligned">${(gReport.createTime?string("yyyy-MM-dd HH:mm"))!'-'}</td>
                                    <td class="center aligned">
                                        <div class="coverage-action-group">
                                            <a href="/p/${project.id}/coverage/overview?appId=${appInfo.id}&versionNumber=${gReport.versionNumber}&reportId=${gReport.id}"
                                               class="ui mini basic blue button coverage-action-button">查看</a>
                                            <#if reportNeedRegenerateMap?? && reportNeedRegenerateMap[gReport.id]?? && reportNeedRegenerateMap[gReport.id]>
                                                <a href="/p/${project.id}/coverage/overview?appId=${appInfo.id}&versionNumber=${gReport.versionNumber}&reportId=${gReport.id}"
                                                   class="ui mini orange button coverage-action-button">去重生成</a>
                                            </#if>
                                            <button class="ui mini basic red button"
                                                    onclick="confirmDeleteGeneratedReport('${gReport.id}', this)">删除
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            </#list>
                            </tbody>
                        </table>
                    <#else>
                        <div class="ui placeholder segment">
                            <div class="ui icon header">
                                <i class="chart area icon"></i>
                                暂无生成的版本报告
                            </div>
                            <a href="/p/${project.id}/${appId}/version/list"
                               class="ui primary button">前往版本管理生成报告</a>
                        </div>
                    </#if>
                </div>

                <div class="ui hidden divider"></div>
                <h4 class="ui dividing header">
                    即时链路覆盖率 (基于系统快照)
                    <span class="snapshot-report-trigger">
                        <button class="ui mini primary basic button" onclick="loadSnapshotsReport('interfaceDetail')">
                            <i class="file alternate outline icon"></i> 查看实时报告
                        </button>
                    </span>
                </h4>

                <#if snapshots?? && (snapshots?size > 0)>
                    <table class="ui celled selectable fixed table">
                        <thead>
                        <tr>
                            <th class="five wide">快照名称</th>
                            <th class="three wide">创建人</th>
                            <th class="two wide">评论</th>
                            <th class="two wide">创建时间</th>
                            <th class="two wide center aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list snapshots as snap>
                            <tr>
                                <td class="ellipsis-tooltip" title="${snap.title!}"><b>${snap.title!}</b></td>
                                <td>
                                    <div class="snapshot-owner-meta">
                                        <i class="user icon"></i>
                                        <#if snap.principals?? && snap.principals?size gt 0>
                                            <#assign userId = snap.principals[0]>
                                            <#if userMap?? && userMap[userId]??>
                                                <span>${userMap[userId].name}(${userId})</span>
                                            <#else>
                                                <span>${userId}</span>
                                            </#if>
                                        <#else>
                                            <span>-</span>
                                        </#if>
                                    </div>
                                </td>
                                <td>
                                    <#if snap.labels??>
                                        <div class="snapshot-comment-group">
                                            <#list snap.labels as label>
                                                <span class="snapshot-comment-chip">${label}</span>
                                            </#list>
                                        </div>
                                    </#if>
                                </td>
                                <td>${(snap.createTime?string("yyyy-MM-dd HH:mm"))!'-'}</td>
                                <td class="center aligned">
                                    <a href="/p/${project.id}/${appId}/snapshot/detail/${snap.id}" target="_blank" class="snapshot-action-link" title="跳转到快照">
                                        <i class="external alternate blue link icon"></i>
                                    </a>
                                </td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                <#else>
                    <div class="ui info message">
                        <i class="info circle icon"></i>
                        提示：此处显示的是基于当前应用下所有“我的快照”的实时覆盖率数据。目前暂无可统计的快照。
                    </div>
                </#if>

                <div id="snapshotsReportContainer" class="snapshots-report-container">
                    <div class="ui segment snapshots-report-segment">
                        <div class="ui active inverted dimmer">
                            <div class="ui text loader">报告加载中...</div>
                        </div>
                        <div id="reportContent"></div>
                    </div>
                </div>
            <#elseif tab == 'compare'>
                <h4 class="ui dividing header compare-record-heading">代码比对报告记录</h4>
                <#if reports?? && (reports?size > 0)>
                    <table class="ui very basic celled table compare-record-table">
                        <thead>
                        <tr>
                            <th class="five wide">报告名称</th>
                            <th class="three wide center aligned">生成时间</th>
                            <th class="four wide center aligned">比对版本</th>
                            <th class="two wide center aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list reports as report>
                            <tr class="<#if highlightReportId?? && highlightReportId == report.id>compare-report-row-highlight</#if>">
                                <td class="ellipsis-tooltip" title="${report.name!}"><b>${report.name!}</b><#if highlightReportId?? && highlightReportId == report.id><span class="compare-report-new-badge">刚生成</span></#if>
                                    <#-- 显示统计子信息（如果 report 对象包含统计字段） -->
                                    <#if report.addClassCount?? || report.updateClassCount?? || report.deleteClassCount?? || report.addMethodCount?? || report.updateMethodCount?? || report.deleteMethodCount?? || report.impactCaseCount??>
                                        <div class="compare-record-stats">
                                            <#if report.addClassCount??>
                                                <span class="ui mini green label">新增类 ${report.addClassCount}</span>
                                            </#if>
                                            <#if report.updateClassCount??>
                                                <span class="ui mini orange label">更新类 ${report.updateClassCount}</span>
                                            </#if>
                                            <#if report.deleteClassCount??>
                                                <span class="ui mini red label">删除类 ${report.deleteClassCount}</span>
                                            </#if>
                                            <#if report.addMethodCount??>
                                                <span class="ui mini green">新增方法 ${report.addMethodCount}</span>
                                            </#if>
                                            <#if report.updateMethodCount??>
                                                <span class="ui mini">更新方法 ${report.updateMethodCount}</span>
                                            </#if>
                                            <#if report.deleteMethodCount??>
                                                <span class="ui mini red">删除方法 ${report.deleteMethodCount}</span>
                                            </#if>
                                            <#if report.impactCaseCount??>
                                                <span class="ui mini label">影响用例 ${report.impactCaseCount}</span>
                                            </#if>
                                        </div>
                                    </#if>
                                </td>
                                <td class="center aligned">${(report.createTime?string("yyyy-MM-dd HH:mm"))!'-'}</td>
                                <td class="center aligned">
                                    <div class="compare-record-meta">
                                        <#if report.gitBranch?has_content>
                                            <span class="meta-chip"><span class="compare-record-meta-label">分支:</span> <b>${report.gitBranch}</b></span>
                                        </#if>
                                        <#if report.gitOldCommit?has_content>
                                            <span><span class="compare-record-meta-label-old">旧:</span> <code class="commit-id commit-id-old" data-content="${report.gitOldCommit}" data-position="top center">${(report.gitOldCommit?length > 8)?then(report.gitOldCommit?substring(0,8), report.gitOldCommit)}</code></span>
                                        </#if>
                                        <#if report.gitNewCommit?has_content>
                                            <span><span class="compare-record-meta-label-new">新:</span> <code class="commit-id commit-id-new" data-content="${report.gitNewCommit}" data-position="top center">${(report.gitNewCommit?length > 8)?then(report.gitNewCommit?substring(0,8), report.gitNewCommit)}</code></span>
                                        </#if>
                                    </div>
                                 </td>
                                <td class="center aligned">
                                    <div class="compare-record-action-group">
                                        <a href="/p/${project.id}/version/report/detail/${report.id}"
                                           class="ui mini basic blue button">查看</a>
                                        <button class="ui mini basic red button" onclick="doDeleteReport('${report.id}', this)">
                                            删除
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                    <#if page?? && page.totalPages gt 1>
                        <#assign currentPage = page.number + 1>
                        <#assign totalPages = page.totalPages>
                        <#assign windowStart = currentPage - 2>
                        <#assign windowEnd = currentPage + 2>
                        <#if windowStart lt 2>
                            <#assign windowEnd = windowEnd + (2 - windowStart)>
                            <#assign windowStart = 2>
                        </#if>
                        <#if windowEnd gt totalPages - 1>
                            <#assign windowStart = windowStart - (windowEnd - (totalPages - 1))>
                            <#assign windowEnd = totalPages - 1>
                        </#if>
                        <#if windowStart lt 2>
                            <#assign windowStart = 2>
                        </#if>
                        <div class="ui stackable grid" style="margin-top: 12px;">
                            <div class="eight wide column compare-page-column">
                                <div class="ui mini form">
                                    <div class="inline fields compare-inline-fields">
                                        <label class="compare-page-label">每页</label>
                                        <select class="ui compact dropdown" onchange="window.location.href=this.value">
                                            <option value="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=10" <#if page.size == 10>selected</#if>>10 条</option>
                                            <option value="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=20" <#if page.size == 20>selected</#if>>20 条</option>
                                            <option value="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=50" <#if page.size == 50>selected</#if>>50 条</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                            <div class="eight wide right aligned column compare-page-column">
                                <div class="ui pagination menu">
                                    <#assign prevPage = page.number - 1>
                                    <#assign nextPage = page.number + 1>
                                    <a class="icon item <#if !page.hasPrevious()>disabled</#if>"
                                       href="/p/${project.id}/${appId}/version/report/list?tab=compare&page=${prevPage}&size=${page.size}">
                                        <i class="left chevron icon"></i>
                                    </a>
                                    <a class="item <#if currentPage == 1>active</#if>"
                                       href="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=${page.size}">1</a>
                                    <#if windowStart gt 2>
                                        <div class="disabled item">...</div>
                                    </#if>
                                    <#if windowEnd gte windowStart>
                                        <#list windowStart..windowEnd as pageIndex>
                                            <a class="item <#if pageIndex == currentPage>active</#if>"
                                               href="/p/${project.id}/${appId}/version/report/list?tab=compare&page=${pageIndex - 1}&size=${page.size}">${pageIndex}</a>
                                        </#list>
                                    </#if>
                                    <#if windowEnd lt totalPages - 1>
                                        <div class="disabled item">...</div>
                                    </#if>
                                    <#if totalPages gt 1>
                                        <a class="item <#if currentPage == totalPages>active</#if>"
                                           href="/p/${project.id}/${appId}/version/report/list?tab=compare&page=${totalPages - 1}&size=${page.size}">${totalPages}</a>
                                    </#if>
                                    <a class="icon item <#if !page.hasNext()>disabled</#if>"
                                       href="/p/${project.id}/${appId}/version/report/list?tab=compare&page=${nextPage}&size=${page.size}">
                                        <i class="right chevron icon"></i>
                                    </a>
                                </div>
                            </div>
                        </div>
                        <div class="ui clearing basic segment compare-page-summary">
                            <span class="compare-page-text">共 <span class="compare-total-count">${page.totalElements}</span> 条，第 <span class="compare-current-page">${page.number + 1}</span> / <span class="compare-total-pages">${page.totalPages}</span> 页</span>
                        </div>
                    <#elseif page??>
                        <div class="ui stackable grid" style="margin-top: 12px;">
                            <div class="sixteen wide column compare-page-column">
                                <div class="ui mini form">
                                    <div class="inline fields compare-inline-fields">
                                        <label class="compare-page-label">每页</label>
                                        <select class="ui compact dropdown" onchange="window.location.href=this.value">
                                            <option value="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=10" <#if page.size == 10>selected</#if>>10 条</option>
                                            <option value="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=20" <#if page.size == 20>selected</#if>>20 条</option>
                                            <option value="/p/${project.id}/${appId}/version/report/list?tab=compare&page=0&size=50" <#if page.size == 50>selected</#if>>50 条</option>
                                        </select>
                                        <span class="compare-page-text compare-page-total">共 <span class="compare-total-count">${page.totalElements}</span> 条</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </#if>
                <#else>
                    <div class="ui placeholder segment compare-empty-state">
                        <div class="ui icon header">
                            <i class="law icon"></i>
                            暂无代码比对记录
                        </div>
                    </div>
                </#if>
            </#if>
        </div>
    </div>
</div>

<div id="deleteVersionDialog" class="ui small modal">
    <div class="header">删除报告</div>
    <div class="ui negative message">
        <div class="header">
            确定删除该报告吗？
        </div>
        <p> 删除之后将无法恢复</p>
    </div>
    <div class="actions">
        <a id="deleteReportButton" href="#" class="ui negative button">删除</a>
        <div class="ui cancel button">不</div>
    </div>
</div>

<div id="deleteGeneratedReportDialog" class="ui small modal">
    <div class="header">删除覆盖率报告</div>
    <div class="ui negative message">
        <div class="header">
            确定要删除这份生成的覆盖率报告吗？
        </div>
        <p>此操作不可逆，删除之后将无法恢复。</p>
    </div>
    <div class="actions">
        <button id="confirmDeleteGenReportBtn" class="ui negative button">删除</button>
        <div class="ui cancel button">取消</div>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.commit-id').popup();

    function showDetail(id) {
        <!--显示节点详情-->
        "#" + id && $("#" + id).toggle();

    }

    function confirmDeleteGeneratedReport(reportId, elem) {
        // 记录当前点击的 ID 和行元素
        var row = $(elem).closest('tr');
        $("#confirmDeleteGenReportBtn").data('reportId', reportId).data('reportRow', row);
        // 显示语义化的 Modal 确认框
        $("#deleteGeneratedReportDialog").modal('show');
    }

    $(function() {
        // 绑定生成的覆盖率报告删除确认按钮
        $("#confirmDeleteGenReportBtn").on('click', function() {
            var reportId = $(this).data('reportId');
            if(!reportId) return;

            var deleteUrl = '/p/${project.id}/${appId}/version/coverageReport/delete';
            $.post(deleteUrl, {reportId: reportId}, function (res) {
                var ok = res && (res.success === true || res.result === true);
                if (ok) {
                    showToast('删除成功', 'success');
                    $("#deleteGeneratedReportDialog").modal('hide');
                    var row = $("#confirmDeleteGenReportBtn").data('reportRow');
                    if(row && row.length) row.remove();

                    var listTable = $("#coverageReportListArea table.ui.fixed.selectable.table.celled tbody tr");
                    if(listTable.length === 0) {
                        var placeholderHtml = '<h4 class="ui dividing header">全量 / 增量覆盖率报告 (按版本生成)</h4>'
                            + '<div class="ui placeholder segment">'
                            + '<div class="ui icon header">'
                            + '<i class="chart area icon"></i>'
                            + '暂无生成的版本报告</div>'
                            + '<a href="/p/${project.id}/${appId}/version/list" class="ui primary button">前往版本管理生成报告</a>'
                            + '</div>';
                        $("#coverageReportListArea").html(placeholderHtml);
                    }
                } else {
                    var errMsg = res ? (res.message || res.errorMessage || res.data || '未知错误') : '未知错误';
                    showToast('删除失败: ' + errMsg, 'error');
                }
            }).fail(function() {
                showToast('删除请求发送失败', 'error');
            });
        });
    });


    window.doDeleteReport = function(id, elem) {
        // 存储待删除 id 到按钮 data 属性并记录对应行，用于局部刷新
        var row = $(elem).closest('tr');
        $("#deleteReportButton").data('reportId', id).data('reportRow', row);
        // 显示删除对话框
        $("#deleteVersionDialog").modal('show');
    }

    // 在 modal 上绑定点击事件以使用 AJAX POST 删除
    $(function() {
        var comparePageNumber = <#if page??>${page.number}<#else>0</#if>;
        var comparePageSize = <#if page??>${page.size}<#else>10</#if>;
        var compareTotalElements = <#if page??>${page.totalElements?c}<#else>0</#if>;

        function buildCompareListUrl(pageNumber, pageSize) {
            return '/p/${project.id}/${appId}/version/report/list?tab=compare&page=' + pageNumber + '&size=' + pageSize;
        }

        $("#deleteReportButton").on('click', function() {
            var id = $(this).data('reportId');
            if(!id) return;
            // 发起 POST 请求到新的 JSON接口
            $.post('/p/${project.id}/${appId}/version/report/delete', {reportId: id}, function(res) {
                // support both `success` and legacy `result` fields
                var ok = res && (res.success === true || res.result === true);
                if(ok) {
                    // 显示成功 toast 并短暂延迟后局部更新列表
                    showToast(res.message || '删除成功', 'success');
                    // 移除对应的行
                    var row = $("#deleteReportButton").data('reportRow');
                    if(row && row.length) row.remove();
                    // 隐藏 modal
                    $("#deleteVersionDialog").modal('hide');

                    var remainingRows = $("table.ui.fixed.selectable.table.celled tbody tr").length;
                    var remainingTotal = Math.max(compareTotalElements - 1, 0);
                    var targetTotalPages = Math.max(Math.ceil(remainingTotal / comparePageSize), 1);
                    var targetPageNumber = Math.min(comparePageNumber, targetTotalPages - 1);
                    $(".compare-total-count").text(remainingTotal);
                    if ($(".compare-page-summary").length > 0) {
                        $(".compare-current-page").text(targetPageNumber + 1);
                        $(".compare-total-pages").text(targetTotalPages);
                    }
                    if (remainingRows === 0) {
                        if (remainingTotal === 0) {
                            var placeholder = '<div class="ui placeholder segment compare-empty-state">'
                                + '<div class="ui icon header">'
                                + '<i class="law icon"></i>'
                                + '暂无代码比对记录</div>'
                                + '</div>';
                            $("table.ui.fixed.selectable.table.celled").parent().html(placeholder);
                        } else {
                            window.location.href = buildCompareListUrl(targetPageNumber, comparePageSize);
                        }
                    }
                } else {
                    showToast('删除失败: ' + (res ? (res.message || res.errorMessage || '未知错误') : '未知错误'), 'error');
                }
            }).fail(function() {
                showToast('删除请求发送失败', 'error');
            });
        });
    });

    function loadSnapshotsReport(defaultTab) {
        $("#snapshotsReportContainer").show();
        $("#snapshotsReportContainer .dimmer").addClass("active");
        $("#reportContent").empty();

        $.ajax({
            url: "/p/${project.id}/${appId}/version/report/snapshots",
            type: "GET",
            success: function (html) {
                $("#snapshotsReportContainer .dimmer").removeClass("active");
                $("#reportContent").html(html);
                if (defaultTab) {
                    $.tab('change tab', defaultTab);
                }
                // 平滑滚动到报告区域
                $('html, body').animate({
                    scrollTop: $("#snapshotsReportContainer").offset().top - 100
                }, 500);
            },
            error: function () {
                $("#snapshotsReportContainer .dimmer").removeClass("active");
                $("#reportContent").html('<div class="ui negative message"><i class="warning icon"></i> 报告加载失败，请重试。</div>');
            }
        });
    }
</script>
</body>
</html>

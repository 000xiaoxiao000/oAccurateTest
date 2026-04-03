<!DOCTYPE html>
<html lang="en">
<head>
    <title>代码覆盖率概览</title>
    <#include "../common.ftl">
    <style>
        .stat-value {
            font-size: 24px;
            font-weight: bold;
            color: #21ba45;
        }

        .comp-card {
            cursor: pointer;
            transition: transform 0.2s;
        }

        .comp-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        }

        .comp-added {
            color: #21ba45 !important;
        }

        .comp-stable {
            color: #2185d0 !important;
        }

        .comp-decreased {
            color: #db2828 !important;
        }
    </style>
    <script src="/js/chart.js"></script>
</head>
<body>
<#assign monitorItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container" style="margin-top: 20px">
    <!-- 面包屑导航 -->
    <div class="ui breadcrumb" style="margin-bottom: 20px">
        <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${project.id}/${appId}/version/list">${appName!appId}</a>
        <i class="right angle icon divider"></i>
        <div class="active section">代码覆盖率概览</div>
    </div>

    <div class="ui segment" style="background-color: #f9f9f9;">
        <div class="ui grid">
            <div class="twelve wide column">
                <h2 class="ui header" style="margin-top: 5px;">
                    <i class="chart bar icon"></i>
                    <div class="content">
                        代码覆盖率概览 - ${appName!appId}
                        <div class="sub header">版本: ${versionNumber}</div>
                    </div>
                </h2>
            </div>
            <div class="four wide column right aligned">
                <#if version??>
                    <div class="ui horizontal list">
                        <div class="item">
                            <div class="content">
                                <div class="ui label"><i class="fork icon"></i> 分支: ${version.repoBranch!"-"}</div>
                            </div>
                        </div>
                        <div class="item">
                            <div class="content">
                                <#if version.repoCommitId?? && version.repoCommitId?length gt 7>
                                    <div class="ui label"><i class="code icon"></i> Commit: ${version.repoCommitId?substring(0, 7)}</div>
                                <#else>
                                    <div class="ui label"><i class="code icon"></i> Commit: ${version.repoCommitId!"-"}</div>
                                </#if>
                            </div>
                        </div>
                    </div>
                </#if>
            </div>
        </div>
    </div>

    <#if report?? || incReport??>
        <#if report?? && hasNewerData>
            <div class="ui warning message">
                <i class="close icon"></i>
                <div class="header">检测到系统快照发生变化</div>
                <p>自上次报告生成以来（${(report.createTime?string("yyyy-MM-dd HH:mm:ss"))!""}），系统快照有新增、删除或更新。
                    建议重新生成覆盖率报告；新报告会按快照时间线累积覆盖率数据。</p>
                <button id="btnRegenerate" class="ui mini orange button">重新生成报告</button>
            </div>
        </#if>

        <#if report??>
            <h3 class="ui dividing header">全量覆盖率指标</h3>
            <div class="ui four cards">
                <!-- Class Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">类覆盖率</div>
                        <div class="description center aligned">
                            <div class="stat-value">${report.coveredClasses} / ${report.totalClasses}</div>
                            <div class="ui green progress"
                                 data-percent="<#if (report.totalClasses > 0)>${(report.coveredClasses / report.totalClasses * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if (report.totalClasses > 0)>${(report.coveredClasses / report.totalClasses * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if (report.totalClasses > 0)>${(report.coveredClasses / report.totalClasses * 100)?string("0.00")}%<#else>0.00%</#if></div>
                            </div>
                            <div>未覆盖数: ${report.totalClasses - report.coveredClasses}</div>
                        </div>
                    </div>
                </div>

                <!-- Method Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">方法覆盖率</div>
                        <div class="description center aligned">
                            <div class="stat-value">${report.coveredMethods} / ${report.totalMethods}</div>
                            <div class="ui blue progress"
                                 data-percent="<#if (report.totalMethods > 0)>${(report.coveredMethods / report.totalMethods * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if (report.totalMethods > 0)>${(report.coveredMethods / report.totalMethods * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if (report.totalMethods > 0)>${(report.coveredMethods / report.totalMethods * 100)?string("0.00")}%<#else>0.00%</#if></div>
                            </div>
                            <div>未覆盖数: ${report.totalMethods - report.coveredMethods}</div>
                        </div>
                    </div>
                </div>

                <!-- Branch Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">分支覆盖率</div>
                        <div class="description center aligned">
                            <div class="stat-value">${report.coveredBranches} / ${report.totalBranches}</div>
                            <div class="ui orange progress"
                                 data-percent="<#if ((report.totalBranchConditions!0) > 0)>${((report.coveredBranchConditions!0) / (report.totalBranchConditions!0) * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if ((report.totalBranchConditions!0) > 0)>${((report.coveredBranchConditions!0) / (report.totalBranchConditions!0) * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if ((report.totalBranchConditions!0) > 0)>${((report.coveredBranchConditions!0) / (report.totalBranchConditions!0) * 100)?string("0.00")}%<#else>N/A</#if></div>
                            </div>
                            <div>未覆盖数: ${report.totalBranches - report.coveredBranches}</div>
                        </div>
                    </div>
                </div>

                <!-- Line Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">代码行覆盖率</div>
                        <div class="description center aligned">
                            <div class="stat-value">${report.coveredLines} / ${report.totalLines}</div>
                            <div class="ui teal progress"
                                 data-percent="<#if (report.totalLines > 0)>${(report.coveredLines / report.totalLines * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if (report.totalLines > 0)>${(report.coveredLines / report.totalLines * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if (report.totalLines > 0)>${(report.coveredLines / report.totalLines * 100)?string("0.00")}%<#else>0.00%</#if></div>
                            </div>
                            <div>未覆盖数: ${report.totalLines - report.coveredLines}</div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="ui segment center aligned" style="margin-top: 20px">
                <h4>全量总圈复杂度: ${report.totalComplexity}</h4>
                <a href="/p/${projectId}/coverage/details?reportId=${report.id}" class="ui primary button">查看详细数据</a>
                <a href="/p/${projectId}/coverage/export?reportId=${report.id}" class="ui success green button">导出类级
                    Excel</a>
                <a href="/p/${projectId}/coverage/export-methods?reportId=${report.id}" class="ui success green button">导出方法级
                    Excel</a>
                <button id="btnShowIncModal" class="ui orange button">生成增量报告</button>
                <button class="ui basic red button btn-delete-report" data-report-id="${report.id}" data-type="全量">删除报告</button>
            </div>
        <#else>
            <div class="ui info message">
                <div class="header">尚未生成全量覆盖率报告</div>
                <p>当前版本尚未生成全量覆盖率数据。您可以点击下方按钮生成。</p>
                <button id="btnGenerate" class="ui mini primary button">立即生成全量报告</button>
                <button id="btnShowIncModal" class="ui orange button">生成增量报告</button>
            </div>
        </#if>

        <#if incReport??>
            <h3 class="ui dividing header" style="margin-top: 30px; color: #f2711c !important;">
                增量覆盖率详情
                <span style="font-size: 14px; color: #666; font-weight: normal; margin-left: 10px;">
                    (基准: ${incReport.baseVersionNumber!"-"}
                    @ <#if incReport.baseRepoCommitId?? && incReport.baseRepoCommitId?length gt 7>${incReport.baseRepoCommitId?substring(0, 7)}<#else>${incReport.baseRepoCommitId!"-"}</#if>)
                </span>
            </h3>
            <div class="ui four cards">
                <!-- Incremental Class Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">增量类覆盖</div>
                        <div class="description center aligned">
                            <div class="stat-value" style="color: #f2711c">${incReport.incCoveredClasses}
                                / ${incReport.incTotalClasses}</div>
                            <div class="ui orange progress"
                                 data-percent="<#if (incReport.incTotalClasses > 0)>${(incReport.incCoveredClasses / incReport.incTotalClasses * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if (incReport.incTotalClasses > 0)>${(incReport.incCoveredClasses / incReport.incTotalClasses * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if (incReport.incTotalClasses > 0)>${(incReport.incCoveredClasses / incReport.incTotalClasses * 100)?string("0.00")}%<#else>0.00%</#if></div>
                            </div>
                            <div>未覆盖数: ${incReport.incTotalClasses - incReport.incCoveredClasses}</div>
                        </div>
                    </div>
                </div>

                <!-- Incremental Method Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">增量方法覆盖</div>
                        <div class="description center aligned">
                            <div class="stat-value" style="color: #f2711c">${incReport.incCoveredMethods}
                                / ${incReport.incTotalMethods}</div>
                            <div class="ui orange progress"
                                 data-percent="<#if (incReport.incTotalMethods > 0)>${(incReport.incCoveredMethods / incReport.incTotalMethods * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if (incReport.incTotalMethods > 0)>${(incReport.incCoveredMethods / incReport.incTotalMethods * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if (incReport.incTotalMethods > 0)>${(incReport.incCoveredMethods / incReport.incTotalMethods * 100)?string("0.00")}%<#else>0.00%</#if></div>
                            </div>
                            <div>未覆盖数: ${incReport.incTotalMethods - incReport.incCoveredMethods}</div>
                        </div>
                    </div>
                </div>

                <!-- Incremental Branch Coverage -->
                <div class="card">
                    <div class="content">
                        <div class="header center aligned">增量分支覆盖</div>
                        <div class="description center aligned">
                            <div class="stat-value" style="color: #f2711c">${incReport.incCoveredBranches}
                                / ${incReport.incTotalBranches}</div>
                            <div class="ui orange progress"
                                 data-percent="<#if ((incReport.incTotalBranchConditions!0) > 0)>${((incReport.incCoveredBranchConditions!0) / (incReport.incTotalBranchConditions!0) * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if ((incReport.incTotalBranchConditions!0) > 0)>${((incReport.incCoveredBranchConditions!0) / (incReport.incTotalBranchConditions!0) * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if ((incReport.incTotalBranchConditions!0) > 0)>${((incReport.incCoveredBranchConditions!0) / (incReport.incTotalBranchConditions!0) * 100)?string("0.00")}%<#else>N/A</#if></div>
                            </div>
                            <div>未覆盖数: ${incReport.incTotalBranches - incReport.incCoveredBranches}</div>
                        </div>
                    </div>
                </div>

                <div class="card">
                    <div class="content">
                        <div class="header center aligned">增量代码行覆盖</div>
                        <div class="description center aligned">
                            <div class="stat-value" style="color: #f2711c">${incReport.incCoveredLines}
                                / ${incReport.incTotalLines}</div>
                            <div class="ui orange progress"
                                 data-percent="<#if (incReport.incTotalLines > 0)>${(incReport.incCoveredLines / incReport.incTotalLines * 100)?string("0.00")}<#else>0</#if>">
                                <div class="bar"
                                     style="width: <#if (incReport.incTotalLines > 0)>${(incReport.incCoveredLines / incReport.incTotalLines * 100)?string("0.00")}<#else>0</#if>%"></div>
                                <div class="label">
                                    覆盖率: <#if (incReport.incTotalLines > 0)>${(incReport.incCoveredLines / incReport.incTotalLines * 100)?string("0.00")}%<#else>0.00%</#if></div>
                            </div>
                            <div>未覆盖行数: ${incReport.incTotalLines - incReport.incCoveredLines}</div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="ui segment center aligned" style="margin-top: 20px">
                <h4 class="ui grey header">增量总圈复杂度: ${incReport.incTotalComplexity}</h4>
                <a href="/p/${projectId}/coverage/details?reportId=${incReport.id}" class="ui orange button">查看增量详细数据</a>
                <a href="/p/${projectId}/coverage/export?reportId=${incReport.id}" class="ui orange basic button">导出增量类级
                    Excel</a>
                <a href="/p/${projectId}/coverage/export-methods?reportId=${incReport.id}"
                   class="ui orange basic button">导出增量方法级 Excel</a>
                <button class="ui basic red button btn-delete-report" data-report-id="${incReport.id}" data-type="增量">删除报告</button>
            </div>
        </#if>

        <#if comparison?? && report??>
            <h3 class="ui dividing header" style="margin-top: 30px">
                覆盖率变动对比 (较上一份报告)
                <div class="sub header">点击卡片查看具体方法列表</div>
            </h3>
            <div class="ui three cards">
                <div class="card comp-card" onclick="showCompModal('added')">
                    <div class="content">
                        <div class="ui textAlign center header comp-added">新增覆盖方法</div>
                        <div class="description center aligned">
                            <div class="stat-value comp-added">${comparison.addedCount}</div>
                        </div>
                    </div>
                </div>
                <div class="card comp-card" onclick="showCompModal('stable')">
                    <div class="content">
                        <div class="ui textAlign center header comp-stable">持平方法</div>
                        <div class="description center aligned">
                            <div class="stat-value comp-stable">${comparison.stableCount}</div>
                        </div>
                    </div>
                </div>
                <div class="card comp-card" onclick="showCompModal('decreased')">
                    <div class="content">
                        <div class="ui textAlign center header comp-decreased">覆盖下降方法</div>
                        <div class="description center aligned">
                            <div class="stat-value comp-decreased">${comparison.decreasedCount}</div>
                        </div>
                    </div>
                </div>
            </div>
        </#if>

        <h3 class="ui dividing header" style="margin-top: 30px">覆盖率趋势</h3>
        <div class="ui segment">
            <canvas id="trendChart" style="width: 100%; height: 300px;"></canvas>
        </div>



        <#if report?? && (report.incTotalLines > 0)>
            <div class="ui message info">
                <div class="header">全量报告包含的增量统计 (对比基准 Commit: ${(report.baseRepoCommitId?substring(0, 7))!"-"})</div>
                <ul class="list">
                    <li>涉及变更代码行: ${report.incCoveredLines} / ${report.incTotalLines}
                        (覆盖率: <b><#if (report.incTotalLines > 0)>${(report.incCoveredLines / report.incTotalLines * 100)?string("0.00")}<#else>0.00</#if>%</b>)
                    </li>
                    <li>变更涉及的分支: ${report.incCoveredBranches} / ${report.incTotalBranches}</li>
                </ul>
            </div>
        </#if>

    <#else>
        <div class="ui warning message" style="margin-top: 30px">
            <div class="header">尚未生成覆盖率报告</div>
            <p>点击下方按钮立即生成最新报告。</p>
            <button id="btnGenerate" class="ui warning button">立即生成</button>
        </div>
    </#if>

    <!-- Comparison Modals -->
    <div class="ui modal" id="compModal">
        <div class="header" id="compModalTitle">方法列表</div>
        <div class="content" style="max-height: 400px; overflow-y: auto;">
            <table class="ui celled table small">
                <thead>
                <tr>
                    <th>类名</th>
                    <th>方法名</th>
                    <th>描述</th>
                </tr>
                </thead>
                <tbody id="compModalBody">
                </tbody>
            </table>
        </div>
        <div class="actions">
            <div class="ui approve button">关闭</div>
        </div>
    </div>

    <!-- Progress Modal -->
    <div class="ui modal" id="jobModal">
        <div class="header">报告生成中</div>
        <div class="content">
            <div id="jobProgress" class="ui active progress" data-percent="0">
                <div class="bar">
                    <div class="progress"></div>
                </div>
                <div id="jobStatus" class="label">准备开始...</div>
            </div>
            <div id="jobLog"
                 style="max-height: 200px; overflow-y: auto; font-family: monospace; background: #f0f0f0; padding: 10px; font-size: 12px;">
            </div>
        </div>
        <div class="actions">
            <div id="btnCloseModal" class="ui disabled button">关闭</div>
        </div>
    </div>

    <!-- Incremental Config Modal -->
    <div class="ui tiny modal" id="incConfigModal">
        <div class="header">生成增量覆盖率报告</div>
        <div class="content">
            <form class="ui form" id="incForm">
                <div class="field">
                    <label>对比基准版本 (Base Version)</label>
                    <input type="text" name="baseVersionNumber" placeholder="例如: 1.0.0">
                </div>
                <div class="field">
                    <label>对比基准 Commit ID (Base Commit)</label>
                    <input type="text" name="baseCommitId" placeholder="例如: a1b2c3d">
                    <div class="ui pointing label small">填入旧版本的 Commit ID 以计算代码变更。</div>
                </div>
                <div class="ui info message mini">
                    <div class="header">说明</div>
                    <p>系统将计算当前版本与基准版本之间的 Diff，生成的报告仅统计变更行的覆盖情况。</p>
                </div>
            </form>
        </div>
        <div class="actions">
            <div class="ui cancel button">取消</div>
            <div class="ui orange approve button" id="btnStartInc">开始生成</div>
        </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div id="deleteConfirmModal" class="ui small modal">
        <div class="header">删除覆盖率报告</div>
        <div class="ui negative message">
            <div class="header">
                确定要删除这份生成的覆盖率报告吗？
            </div>
            <p>此操作不可逆，删除之后将无法恢复。</p>
        </div>
        <div class="actions">
            <div id="btnConfirmDelete" class="ui red approve button">删除</div>
            <div class="ui cancel button">取消</div>
        </div>
    </div>
</div>

<script>
    // Comparison Data
    var compData = {
        added: [
            <#if (comparison.addedMethods)??>
            <#list comparison.addedMethods as m>
            {
                className: '${m.className}',
                methodName: '${m.methodName}',
                methodDesc: '${m.methodDesc}'
            }<#if m_has_next>, </#if>
            </#list>
            </#if>
        ],
        stable: [
            <#if (comparison.stableMethods)??>
            <#list comparison.stableMethods as m>
            {
                className: '${m.className}',
                methodName: '${m.methodName}',
                methodDesc: '${m.methodDesc}'
            }<#if m_has_next>, </#if>
            </#list>
            </#if>
        ],
        decreased: [
            <#if (comparison.decreasedMethods)??>
            <#list comparison.decreasedMethods as m>
            {
                className: '${m.className}',
                methodName: '${m.methodName}',
                methodDesc: '${m.methodDesc}'
            }<#if m_has_next>, </#if>
            </#list>
            </#if>
        ]
    };

    function showCompModal(type) {
        var list = compData[type];
        var title = type === 'added' ? '新增覆盖方法' : (type === 'stable' ? '持平覆盖方法' : '覆盖下降方法');
        $('#compModalTitle').text(title + " (" + list.length + ")");
        var $body = $('#compModalBody').empty();

        if (list.length === 0) {
            $body.append('<tr><td colspan="3" class="center aligned">暂无数据</td></tr>');
        } else {
            // Limit display to 200 for performance
            var displayList = list.slice(0, 200);
            displayList.forEach(function (item) {
                $body.append('<tr><td>' + item.className + '</td><td>' + item.methodName + '</td><td>' + item.methodDesc + '</td></tr>');
            });
            if (list.length > 200) {
                $body.append('<tr><td colspan="3" class="center aligned grey italic">仅显示前 200 条...</td></tr>');
            }
        }
        $('#compModal').modal('show');
    }

    // Trend Chart
    $(function () {
        $.get('/p/${projectId}/coverage/trend-data', {
            appId: '${appId}',
            versionNumber: '${versionNumber}'
        }, function (data) {
            if (!data || data.length === 0) return;

            var ctx = document.getElementById('trendChart').getContext('2d');
            new Chart(ctx, {
                type: 'line',
                data: {
                    labels: data.map(function (d) {
                        return d.time;
                    }),
                    datasets: [
                        {
                            label: '行覆盖率 (%)',
                            data: data.map(function (d) {
                                return d.lineCoverage.toFixed(2);
                            }),
                            borderColor: '#00b5ad',
                            backgroundColor: 'rgba(0, 181, 173, 0.1)',
                            fill: true,
                            tension: 0.1
                        },
                        {
                            label: '方法覆盖率 (%)',
                            data: data.map(function (d) {
                                return d.methodCoverage.toFixed(2);
                            }),
                            borderColor: '#2185d0',
                            backgroundColor: 'rgba(33, 133, 208, 0.1)',
                            fill: true,
                            tension: 0.1
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            max: 100
                        }
                    }
                }
            });
        });
    });

    $('#btnGenerate, #btnRegenerate').click(function () {
        var $btn = $(this);
        $btn.addClass('loading disabled');

        var requestBranch = '<#if report?? && report.repoBranch??>${report.repoBranch}<#elseif version?? && version.repoBranch??>${version.repoBranch}<#else></#if>';
        var requestCommit = '<#if report?? && report.repoCommitId??>${report.repoCommitId}<#elseif version?? && version.repoCommitId??>${version.repoCommitId}<#else></#if>';

        $.post('/p/${projectId}/coverage/generate', {
            appId: '${appId}',
            versionNumber: '${versionNumber}',
            branch: requestBranch,
            commitId: requestCommit
        }, function (res) {
            if (res.result) {
                var jobId = res.data; // Using res.data instead of res.message
                $('#jobModal').modal({closable: false}).modal('show');
                pollJob(jobId);
            } else {
                $btn.removeClass('loading disabled');
                showToast('启动失败: ' + res.message, 'error');
            }
        });
    });

    function pollJob(jobId) {
        var interval = setInterval(function () {
            $.get('/p/${projectId}/coverage/job/' + jobId, function (job) {
                if (!job) {
                    clearInterval(interval);
                    $('#jobStatus').text('找不到任务信息');
                    $('#btnCloseModal').removeClass('disabled').text('关闭');
                    return;
                }

                var progress = job.progress;
                var percent = progress.percent;
                if (progress.total > 0) {
                    var current = (progress.loaded / progress.total * progress.proportion);
                    percent += current;
                }

                $('#jobProgress').progress({percent: percent});
                $('#jobStatus').text(progress.name || '运行中...');

                if (job.log) {
                    $('#jobLog').html(job.log.replace(/\n/g, '<br>'));
                    $('#jobLog').scrollTop($('#jobLog')[0].scrollHeight);
                }

                if (job.state === 'finish') {
                    clearInterval(interval);
                    $('#jobStatus').text('生成成功！页面即将刷新...');
                    $('#btnCloseModal').removeClass('disabled').text('完成');
                    setTimeout(function () {
                        location.reload();
                    }, 2000);
                } else if (job.state === 'error') {
                    clearInterval(interval);
                    $('#jobProgress').addClass('error');
                    // Display specific error if available in progress name
                    var errorMsg = (progress.name && progress.name.indexOf('失败') >= 0) ? progress.name : '生成失败';
                    $('#jobStatus').text(errorMsg);
                    $('#btnCloseModal').removeClass('disabled').text('关闭');
                    $('#btnCloseModal').click(function () {
                        $('#jobModal').modal('hide');
                        location.reload();
                    });
                }
            });
        }, 1000);
    }

    $('#btnShowIncModal').click(function () {
        $('#incConfigModal').modal('show');
    });

    $('#btnStartInc').click(function () {
        var params = {
            appId: '${appId}',
            versionNumber: '${versionNumber}',
            branch: '<#if incReport?? && incReport.repoBranch??>${incReport.repoBranch}<#elseif report?? && report.repoBranch??>${report.repoBranch}<#elseif version?? && version.repoBranch??>${version.repoBranch}<#else></#if>',
            commitId: '<#if incReport?? && incReport.repoCommitId??>${incReport.repoCommitId}<#elseif report?? && report.repoCommitId??>${report.repoCommitId}<#elseif version?? && version.repoCommitId??>${version.repoCommitId}<#else></#if>',
            baseVersionNumber: $('#incForm input[name=baseVersionNumber]').val(),
            baseCommitId: $('#incForm input[name=baseCommitId]').val()
        };

        if (!params.baseCommitId) {
            showToast('请填写基准 Commit ID', 'error');
            return false;
        }

        $.post('/p/${projectId}/coverage/generate-incremental', params, function (res) {
            if (res.result) {
                var jobId = res.data;
                $('#incConfigModal').modal('hide');
                $('#jobModal').modal({closable: false}).modal('show');
                pollJob(jobId);
            } else {
                showToast('启动失败: ' + res.message, 'error');
            }
        });
        return false;
    });

    // Delete report logic
    var reportToDelete = null;

    $('.btn-delete-report').click(function () {
        var reportId = $(this).data('report-id');
        var type = $(this).data('type');
        reportToDelete = { id: reportId, type: type, btn: $(this) };

        $('#deleteConfirmText').text('确定要删除这份' + type + '覆盖率报告吗？删除后不可恢复。');
        $('#deleteConfirmModal').modal({
            closable: false,
            onApprove: function() {
                executeDelete();
            }
        }).modal('show');
    });

    function executeDelete() {
        if (!reportToDelete) return;

        var $btn = reportToDelete.btn;
        $btn.addClass('loading disabled');

        $.post('/p/${projectId}/coverage/delete', { reportId: reportToDelete.id }, function (res) {
            var ok = res && (res.success === true || res.result === true);
            if (ok) {
                showToast(res.message || '删除成功', 'success');
                setTimeout(function() {
                    location.reload();
                }, 1000);
            } else {
                $btn.removeClass('loading disabled');
                showToast('删除失败: ' + (res ? (res.message || res.errorMessage || '未知错误') : '未知错误'), 'error');
            }
        });
    }
</script>
</body>
</html>




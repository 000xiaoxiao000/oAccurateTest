<!DOCTYPE html>
<html lang="en">
<head>
    <title>代码覆盖率详情</title>
    <#include "../common.ftl">
    <link href="/css/treeTable.css" rel="stylesheet">
    <script src="/js/treeTable.js"></script>
    <style>
        .tree.table { table-layout: fixed; width: 100%; }
        .tree.table th:first-child, .tree.table td:first-child { width: 40%; }
        .tree.table th:not(:first-child), .tree.table td:not(:first-child) { width: 8.5%; }
        .tree.table td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .tree.table tr.package-row { font-weight: bold; background-color: #f9f9f9 !important; }
        .tree.table i.icon { margin-right: 5px; }
        /* Reduce vertical spacing further */
        .tree.table td { padding-top: 4px !important; padding-bottom: 4px !important; font-size: 1.0em; }
        .tree.table tr.package-row td { background-color: #fcfcfc !important; }
    </style>
</head>
<body>
<#assign monitorItemActive="active">
<#include "../partials/projectHeaderNav.ftl">

<#include "../partials/aiFloatingWidgetFull.ftl">

<div class="ui container fluid" style="padding: 20px">
    <!-- 面包屑导航 -->
    <div class="ui breadcrumb" style="margin-bottom: 20px">
        <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${project.id}/${appId!}/version/list">${appName!appId!}</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${project.id}/coverage/overview?appId=${appId!}&versionNumber=${versionNumber!}">覆盖率概览</a>
    </div>

    <div class="ui segment" style="background-color: #f9f9f9;">
        <div class="ui grid">
            <div class="twelve wide column">
                <h2 class="ui header" style="margin-top: 5px;">
                    <i class="list alternate icon"></i>
                    <div class="content">
                        详细覆盖率数据 - ${appName!appId}
                        <div class="sub header">版本: ${versionNumber!}</div>
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

    <!-- Search Form -->
    <div class="ui segment">
        <form class="ui form" action="/p/${projectId}/coverage/details" method="get" id="searchForm">
            <input type="hidden" name="reportId" value="${reportId!}">
            <input type="hidden" name="appId" value="${appId!}">
            <input type="hidden" name="viewType" value="${viewType!'list'}">
            <div class="fields">
                <div class="four wide field">
                    <label>类名搜索</label>
                    <input type="text" name="className" placeholder="支持模糊匹配" value="${className!""}">
                </div>
                <div class="four wide field">
                    <label>方法名搜索</label>
                    <input type="text" name="methodName" placeholder="类中包含该方法" value="${methodName!""}">
                </div>
                <div class="two wide field">
                    <label>最小行覆盖率 (%)</label>
                    <input type="number" step="0.01" min="0" max="100" name="minRate" placeholder="0" value="${minRate!""}">
                </div>
                <div class="two wide field">
                    <label>最大行覆盖率 (%)</label>
                    <input type="number" step="0.01" min="0" max="100" name="maxRate" placeholder="100" value="${maxRate!""}">
                </div>
                <div class="two wide field">
                    <label>最小分支覆盖 (%)</label>
                    <input type="number" step="0.01" min="0" max="100" name="minBranchRate" placeholder="0" value="${minBranchRate!""}">
                </div>
                <div class="two wide field">
                    <label>最大分支覆盖 (%)</label>
                    <input type="number" step="0.01" min="0" max="100" name="maxBranchRate" placeholder="100" value="${maxBranchRate!""}">
                </div>
            </div>
            <div class="fields">
                <div class="two wide field">
                    <label>最小方法覆盖 (%)</label>
                    <input type="number" step="0.01" min="0" max="100" name="minMethodRate" placeholder="0" value="${minMethodRate!""}">
                </div>
                <div class="two wide field">
                    <label>最大方法覆盖 (%)</label>
                    <input type="number" step="0.01" min="0" max="100" name="maxMethodRate" placeholder="100" value="${maxMethodRate!""}">
                </div>
                <div class="two wide field">
                    <label>最小圈复杂度</label>
                    <input type="number" min="0" name="minComplexity" placeholder="0" value="${minComplexity!""}">
                </div>
                <div class="two wide field">
                    <label>最大圈复杂度</label>
                    <input type="number" min="0" name="maxComplexity" placeholder="9999" value="${maxComplexity!""}">
                </div>
                <div class="two wide field">
                    <label>&nbsp;</label>
                    <button class="ui primary button" type="submit">搜索</button>
                </div>
                <div class="two wide field">
                    <label>&nbsp;</label>
                    <#assign hasSearchFilter =
                        (className?? && className?has_content)
                        || (methodName?? && methodName?has_content)
                        || minRate??
                        || maxRate??
                        || minBranchRate??
                        || maxBranchRate??
                        || minMethodRate??
                        || maxMethodRate??
                        || minComplexity??
                        || maxComplexity??>
                    <#if hasSearchFilter>
                        <a href="/p/${projectId}/coverage/details?reportId=${reportId!}&appId=${appId!}&viewType=${viewType!'list'}" class="ui tiny grey basic button">清空筛选</a>
                    </#if>
                </div>
            </div>
        </form>
    </div>

    <!-- View Switcher -->
    <div class="ui secondary pointing menu" style="margin-bottom: 20px">
        <#assign commonParams = "reportId=${reportId!}&appId=${appId!}&className=${className!}&methodName=${methodName!}&minRate=${minRate!}&maxRate=${maxRate!}&minBranchRate=${minBranchRate!}&maxBranchRate=${maxBranchRate!}&minMethodRate=${minMethodRate!}&maxMethodRate=${maxMethodRate!}&minComplexity=${minComplexity!}&maxComplexity=${maxComplexity!}">
        <a class="item ${(viewType == 'list')?then('active', '')}" href="?${commonParams}&viewType=list">
            <i class="list icon"></i> 列表型
        </a>
        <a class="item ${(viewType == 'tree')?then('active', '')}" href="?${commonParams}&viewType=tree">
            <i class="sitemap icon"></i> 树型
        </a>
    </div>

    <#if viewType == "list">
        <table class="ui celled selectable table striped" style="table-layout: fixed; width: 100%;">
            <thead>
                <tr>
                    <th style="width: 25%">类名</th>
                    <th style="width: 12%">方法 (覆盖/总)</th>
                    <th style="width: 8%">方法覆盖率</th>
                    <th style="width: 12%">分支 (覆盖/总)</th>
                    <th style="width: 8%">分支覆盖率</th>
                    <th style="width: 12%">代码行 (覆盖/总)</th>
                    <th style="width: 10%">代码行覆盖率</th>
                    <th style="width: 7%">圈复杂度</th>
                    <th style="width: 7%">操作</th>
                </tr>
            </thead>
            <tbody>
                <#list classPage.content as item>
                <tr>
                    <td style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${item.className}">${item.className}</td>
                    <td class="center aligned">${item.coveredMethods} / ${item.totalMethods}</td>
                    <td class="center aligned <#if item.coveredMethods gt 0>positive<#else>negative</#if>"><#if (item.totalMethods > 0)>${(item.coveredMethods / item.totalMethods * 100)?string("0.00")}%<#else>0.00%</#if></td>
                    <td class="center aligned">${item.coveredBranchTargets} / ${item.totalBranchTargets}</td>
                    <td class="center aligned <#if (item.branchRate!0) gt 0>positive<#elseif item.totalBranchTargets gt 0>negative</#if>"><#if (item.totalBranchTargets > 0)>${(item.branchRate!0)?string("0.00")}%<#else>N/A</#if></td>
                    <td class="center aligned">${item.coveredLines} / ${item.totalLines}</td>
                    <td class="center aligned <#if item.coveredLines gt 0>positive<#else>negative</#if>"><#if (item.totalLines > 0)>${(item.coveredLines / item.totalLines * 100)?string("0.00")}%<#else>0.00%</#if></td>
                    <td class="center aligned">${item.totalComplexity}</td>
                    <td class="center aligned">
                        <a href="/p/${projectId}/coverage/code?reportId=${reportId!}&className=${item.className}&appId=${item.appId}" target="_blank" class="ui basic blue mini button">代码</a>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>

        <!-- Pagination -->
        <div class="ui right floated pagination menu">
            <#assign qryParams = commonParams + "&viewType=list">
            <#if classPage.hasPrevious()>
                <a class="icon item" href="?${qryParams}&page=${classPage.number - 1}">
                    <i class="left chevron icon"></i>
                </a>
            </#if>
            <span class="item disabled">${classPage.number + 1} / ${classPage.totalPages}</span>
            <#if classPage.hasNext()>
                <a class="icon item" href="?${qryParams}&page=${classPage.number + 1}">
                    <i class="right chevron icon"></i>
                </a>
            </#if>
        </div>
    <#else>
        <table class="ui celled selectable table striped tree table" id="treeTable">
            <thead>
            <tr>
                <th style="width: 25%">包/类</th>
                <th style="width: 12%">方法 (覆盖/总)</th>
                <th style="width: 8%">方法覆盖率</th>
                <th style="width: 12%">分支 (覆盖/总)</th>
                <th style="width: 8%">分支覆盖率</th>
                <th style="width: 12%">代码行 (覆盖/总)</th>
                <th style="width: 10%">代码行覆盖率</th>
                <th style="width: 9%">圈复杂度</th>
            </tr>
            </thead>
            <tbody>
                <#list rootNodes as item>
                    <@renderNode item "root" />
                </#list>
            </tbody>
        </table>
    </#if>

    <#if reportNeedRegenerate?? && reportNeedRegenerate>
        <div class="ui warning message">
            <div class="header">当前报告对应的系统快照已发生变化</div>
            <p>检测到快照有新增/删除/更新，建议重新生成覆盖率报告后再查看详情。</p>
            <button id="btnRegenerateHere" class="ui mini orange button">
                <#if report?? && report.reportType?? && report.reportType == 1>立即重生成(增量)<#else>立即重生成</#if>
            </button>
            <a class="ui mini basic button"
               href="/p/${projectId}/coverage/overview?appId=${appId}&versionNumber=${versionNumber}&reportId=${report.id}">前往概览</a>
        </div>
    </#if>
</div>

<#macro renderNode item parentId>
    <tr nodeId="${item.fullName}" parentId="${parentId}" class="${(item.type=='package')?then('package-row', 'class-row')}"
        data-type="${item.type}" data-loaded="${(item.hasChildren)?then('false', 'true')}">
        <td title="${item.fullName}" style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            <#if item.hasChildren>
                <i class='fold small plus square outline link icon '></i>
            <#else>
                <i class='mini grey minus icon'></i>
            </#if>
            <#if item.type == 'package'>
                <i class="folder outline icon"></i>
                ${item.name}
            <#else>
                <i class="file code outline icon"></i>
                ${item.name}
                <a href="/p/${projectId}/coverage/code?reportId=${reportId!}&className=${item.fullName}&appId=${appId}" target="_blank" style="margin-left: 10px"><i class="code icon"></i>代码</a>
            </#if>
        </td>
        <td class="center aligned">${item.coveredMethods} / ${item.totalMethods}</td>
        <td class="center aligned <#if item.coveredMethods gt 0>positive<#else>negative</#if>">${item.methodRate?string("0.00")}%</td>
        <td class="center aligned">${item.coveredBranchTargets} / ${item.totalBranchTargets}</td>
        <td class="center aligned <#if (item.branchRate!0) gt 0>positive<#elseif item.totalBranchTargets gt 0>negative</#if>"><#if (item.totalBranchTargets > 0)>${(item.branchRate!0)?string("0.00")}%<#else>N/A</#if></td>
        <td class="center aligned">${item.coveredLines} / ${item.totalLines}</td>
        <td class="center aligned <#if item.coveredLines gt 0>positive<#else>negative</#if>">${item.lineRate?string("0.00")}%</td>
        <td class="center aligned">${item.totalComplexity}</td>
    </tr>
</#macro>

<div class="ui modal" id="jobModal">
    <div class="header">报告生成中</div>
    <div class="content">
        <div id="jobProgress" class="ui active progress" data-percent="0">
            <div class="bar"><div class="progress"></div></div>
            <div id="jobStatus" class="label">准备开始...</div>
        </div>
        <div id="jobLog" style="max-height: 220px; overflow-y: auto; font-family: monospace; background: #f0f0f0; padding: 10px; font-size: 12px;"></div>
    </div>
    <div class="actions">
        <div id="btnCloseModal" class="ui disabled button">关闭</div>
    </div>
</div>

<div class="ui tiny modal" id="incRegenerateModal">
    <div class="header">增量报告重生成</div>
    <div class="content">
        <form class="ui form" id="incRegenerateForm">
            <div class="field">
                <label>基准版本</label>
                <input type="text" id="incBaseVersion" value="${(report.baseVersionNumber)!''}" placeholder="例如: 1.0.0">
            </div>
            <div class="field">
                <label>基准 Commit ID</label>
                <input type="text" id="incBaseCommit" value="${(report.baseRepoCommitId)!''}" placeholder="例如: a1b2c3d">
            </div>
        </form>
    </div>
    <div class="actions">
        <div class="ui cancel button">取消</div>
        <button id="btnStartIncRegenerate" class="ui orange button">开始重生成</button>
    </div>
</div>

<script>
    $(function() {
        if ($("#treeTable").length > 0) {
            treeTable($("#treeTable"));

            // Lazy load logic integration
            $("#treeTable").on('click', '.fold.icon.plus', function(e) {
                var $tr = $(this).closest('tr');
                if ($tr.attr('data-loaded') === 'false') {
                    loadChildren($tr);
                }
            });
        }
    });

    function loadChildren($tr) {
        var nodeId = $tr.attr('nodeId');
        var $icon = $tr.find('.fold.icon');
        $icon.removeClass('plus minus').addClass('spinner loading');

        var data = {
            reportId: "${reportId!}",
            parentPackage: nodeId,
            className: "${className!""}",
            methodName: "${methodName!""}",
            minRate: "${minRate!""}",
            maxRate: "${maxRate!""}",
            minBranchRate: "${minBranchRate!""}",
            maxBranchRate: "${maxBranchRate!""}",
            minMethodRate: "${minMethodRate!""}",
            maxMethodRate: "${maxMethodRate!""}",
            minComplexity: "${minComplexity!""}",
            maxComplexity: "${maxComplexity!""}"
        };

        $.ajax({
            url: "/p/${projectId}/coverage/treeNodes",
            data: data,
            success: function(nodes) {
                $tr.attr('data-loaded', 'true');
                $icon.removeClass('spinner loading').addClass('plus');

                if (!nodes || nodes.length === 0) {
                    $icon.removeClass('plus').addClass('minus grey');
                    $tr.find('.fold.icon').replaceWith("<i class='mini grey minus icon'></i>");
                    return;
                }

                var html = "";
                var level = $tr.find('td:first').css('padding-left');
                // Use indentation
                var nextLevel = (parseFloat(level || 0) + 12) + "px";

                nodes.forEach(function(item) {
                    var isPackage = item.type === 'package';
                    var isClass = item.type === 'class';

                    var icon = isPackage ? 'folder outline' : 'file code outline';
                    var rowClass = isPackage ? 'package-row' : 'class-row';
                    var hasChildren = !!item.hasChildren;
                    var rowNodeId = item.fullName;

                    html += '<tr nodeId="' + rowNodeId + '" parentId="' + nodeId + '" class="' + rowClass + ' hidden" data-type="' + item.type + '" data-loaded="' + (hasChildren ? 'false' : 'true') + '">';
                    html += '<td title="' + item.fullName + '" style="padding-left: ' + nextLevel + '; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">';
                    if (hasChildren) {
                        html += '<i class="fold small plus square outline link icon "></i>';
                    } else {
                        html += '<i class="mini grey minus icon"></i>';
                    }
                    html += '<i class="' + icon + ' icon"></i>' + item.name;
                    if (isClass) {
                        html += '<a href="/p/${projectId}/coverage/code?reportId=${reportId!}&className=' + item.fullName + '&appId=${appId}" target="_blank" style="margin-left: 10px"><i class="code icon"></i>代码</a>';
                    }
                    html += '</td>';
                    html += '<td class="center aligned">' + item.coveredMethods + ' / ' + item.totalMethods + '</td>';
                    html += '<td class="center aligned ' + (item.coveredMethods > 0 ? 'positive' : 'negative') + '">' + item.methodRate.toFixed(2) + '%</td>';
                    html += '<td class="center aligned">' + item.coveredBranchTargets + ' / ' + item.totalBranchTargets + '</td>';
                    html += '<td class="center aligned ' + ((item.branchRate || 0) > 0 ? 'positive' : (item.totalBranchTargets > 0 ? 'negative' : '')) + '">' + (item.totalBranchTargets > 0 ? (item.branchRate || 0).toFixed(2) + '%' : 'N/A') + '</td>';
                    html += '<td class="center aligned">' + item.coveredLines + ' / ' + item.totalLines + '</td>';
                    html += '<td class="center aligned ' + (item.coveredLines > 0 ? 'positive' : 'negative') + '">' + item.lineRate.toFixed(2) + '%</td>';
                    html += '<td class="center aligned">' + item.totalComplexity + '</td>';
                    html += '</tr>';
                });

                $tr.after(html);
                $tr.addClass('expand');
                $icon.removeClass('plus').addClass('minus');
                refresh_show($("#treeTable"));
            },
            error: function() {
                $icon.removeClass('spinner loading').addClass('plus');
                notifyToast('加载失败', 'error');
            }
        });
    }

    function pollJob(jobId) {
        var interval = setInterval(function () {
            $.get('/p/${projectId}/coverage/job/' + jobId, function (job) {
                if (!job) {
                    clearInterval(interval);
                    $('#jobStatus').text('找不到任务信息');
                    $('#jobProgress').addClass('error');
                    $('#btnCloseModal').off('click').on('click', function () {
                        $('#jobModal').modal('hide');
                        location.reload();
                    });
                    $('#btnCloseModal').removeClass('disabled').text('关闭');
                    return;
                }

                var progress = job.progress;
                var percent = progress.percent;
                if (progress.total > 0) {
                    percent += (progress.loaded / progress.total * progress.proportion);
                }

                $('#jobProgress').progress({percent: percent});
                $('#jobStatus').text(progress.name || '运行中...');

                if (job.log) {
                    $('#jobLog').html(job.log.replace(/\n/g, '<br>'));
                    $('#jobLog').scrollTop($('#jobLog')[0].scrollHeight);
                }

                if (job.state === 'finish') {
                    clearInterval(interval);
                    $('#jobStatus').text('生成成功，页面刷新中...');
                    $('#btnCloseModal').off('click').on('click', function () {
                        $('#jobModal').modal('hide');
                        location.reload();
                    });
                    $('#btnCloseModal').removeClass('disabled').text('完成');
                    setTimeout(function () { location.reload(); }, 1200);
                } else if (job.state === 'error') {
                    clearInterval(interval);
                    $('#jobProgress').addClass('error');
                    $('#jobStatus').text(progress.name || '生成失败');
                    $('#btnCloseModal').off('click').on('click', function () {
                        $('#jobModal').modal('hide');
                        location.reload();
                    });
                    $('#btnCloseModal').removeClass('disabled').text('关闭');
                }
            });
        }, 1000);
    }

    var regenerateReportType = <#if report?? && report.reportType??>${report.reportType}<#else>0</#if>;

    function startRegenerate(url, params, $btn) {
        $.post(url, params, function (res) {
            $btn.removeClass('loading disabled');
            if (res && res.result) {
                $('#jobModal').modal({closable: false}).modal('show');
                pollJob(res.data);
            } else {
                showToast('启动失败: ' + (res ? res.message : '未知错误'), 'error');
            }
        }).fail(function () {
            $btn.removeClass('loading disabled');
            showToast('启动失败: 网络异常', 'error');
        });
    }

    $('#btnRegenerateHere').click(function () {
        if (regenerateReportType === 1) {
            $('#incRegenerateModal').modal('show');
            return;
        }

        var $btn = $(this);
        $btn.addClass('loading disabled');
        startRegenerate('/p/${projectId}/coverage/generate', {
            appId: '${appId}',
            versionNumber: '${versionNumber}',
            branch: '<#if report?? && report.repoBranch??>${report.repoBranch}<#elseif version?? && version.repoBranch??>${version.repoBranch}<#else></#if>',
            commitId: '<#if report?? && report.repoCommitId??>${report.repoCommitId}<#elseif version?? && version.repoCommitId??>${version.repoCommitId}<#else></#if>'
        }, $btn);
    });

    $('#btnStartIncRegenerate').click(function () {
        var baseCommit = $('#incBaseCommit').val();
        if (!baseCommit) {
            showToast('请填写基准 Commit ID', 'error');
            return;
        }

        var $btn = $(this);
        $btn.addClass('loading disabled');
        $('#incRegenerateModal').modal('hide');
        startRegenerate('/p/${projectId}/coverage/generate-incremental', {
            appId: '${appId}',
            versionNumber: '${versionNumber}',
            branch: '<#if report?? && report.repoBranch??>${report.repoBranch}<#elseif version?? && version.repoBranch??>${version.repoBranch}<#else></#if>',
            commitId: '<#if report?? && report.repoCommitId??>${report.repoCommitId}<#elseif version?? && version.repoCommitId??>${version.repoCommitId}<#else></#if>',
            baseVersionNumber: $('#incBaseVersion').val(),
            baseCommitId: baseCommit
        }, $btn);
    });
</script>
</body>
</html>



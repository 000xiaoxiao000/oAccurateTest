<!DOCTYPE html>
<html lang="en">
<head>
    <title>代码覆盖率 - ${displayClassName!className}</title>
    <#include "../common.ftl">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; padding: 20px; line-height: 1.5; }
        .source-container { border: 1px solid #ddd; padding: 10px; border-radius: 5px; background: #fff; overflow-x: auto; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .method-list { margin-bottom: 25px; }
        /* 进度条样式优化 */
        .ui.progress { margin: 0; min-width: 80px; }
        .method-table td { vertical-align: middle !important; }
        .method-table .method-name { font-weight: 600; color: #1e70bf; }
        .method-table .stat-txt { font-size: 0.9em; white-space: nowrap; }
        /* 回到顶部按钮 */
        #backToTop {
            position: fixed;
            bottom: 40px;
            right: 40px;
            display: none;
            z-index: 999;
            padding: 10px 15px;
            background-color: #2185d0;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            box-shadow: 0 2px 5px rgba(0,0,0,0.2);
            font-size: 14px;
        }
        #backToTop:hover { background-color: #1678c2; }
        /* 调整源码显示样式 */
        .source-container pre { margin: 0; font-size: 13px; line-height: 18px; }
        /* 表格列宽 */
        .col-name { width: 30%; }
        .col-pct { width: 20%; }
        .col-jump { width: 15%; }
        .col-status { width: 15%; }
    </style>
</head>
<body>
<div class="ui container" style="width: 95%;">
    <div class="ui breadcrumb" style="margin-bottom: 20px;">
        <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${project.id}/${appId}/version/list">${appName!appId}</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${project.id}/coverage/overview?appId=${appId}&versionNumber=${versionNumber}">覆盖率概览</a>
        <i class="right angle icon divider"></i>
        <#if reportId??>
            <a class="section" href="/p/${project.id}/coverage/details?reportId=${reportId}">详细数据</a>
            <i class="right angle icon divider"></i>
        </#if>
        <div class="ui segment" style="background-color: #f9f9f9; margin-top: 10px; border-top: 2px solid #2185d0;">
        <div class="ui grid">
            <div class="twelve wide column">
                <h3 class="ui header" style="margin: 0;">
                    <i class="file code outline icon"></i>
                    <div class="content">
                        ${displayClassName!className}
                        <div class="sub header">应用: ${appName!appId} | 版本: ${versionNumber!}</div>
                    </div>
                </h3>
            </div>
            <div class="four wide column right aligned">
                <#if version??>
                    <div class="ui horizontal list">
                        <div class="item">
                            <div class="content">
                                <div class="ui mini label"><i class="fork icon"></i> ${version.repoBranch!"-"}</div>
                                <#if version.repoCommitId?? && version.repoCommitId?length gt 7>
                                    <div class="ui mini label"><i class="code icon"></i> ${version.repoCommitId?substring(0, 7)}</div>
                                <#else>
                                    <div class="ui mini label"><i class="code icon"></i> ${version.repoCommitId!"-"}</div>
                                </#if>
                            </div>
                        </div>
                    </div>
                </#if>
            </div>
        </div>
    </div>

    <#if reportNeedRegenerate?? && reportNeedRegenerate>
        <div class="ui warning message" style="margin-top: 10px;">
            <div class="header">当前覆盖率报告已过期</div>
            <p>系统快照有新增/删除/更新，建议重新生成报告后再查看源码覆盖情况。</p>
            <button id="btnRegenerateHere" class="ui mini orange button">
                <#if report?? && report.reportType?? && report.reportType == 1>立即重生成(增量)<#else>立即重生成</#if>
            </button>
            <a class="ui mini basic button"
               href="/p/${project.id}/coverage/overview?appId=${appId}&versionNumber=${versionNumber}&reportId=${report.id}">前往概览</a>
        </div>
    </#if>

    <div class="method-list">
        <h4 class="ui header">
            <i class="list icon"></i>
            <span class="content">
                方法覆盖列表 <#if classCov?? && classCov.methods??>(共 ${classCov.methods?size} 个)</#if>
            </span>
        </h4>

        <div class="ui form" style="margin-bottom: 10px;">
            <div class="fields">
                <div class="eight wide field">
                    <label for="methodSearchInput">方法搜索</label>
                    <div class="ui left icon input">
                        <i class="search icon"></i>
                        <input type="text" id="methodSearchInput" placeholder="按方法名筛选...">
                    </div>
                </div>
                <div class="eight wide field">
                    <label for="statusFilter">覆盖状态</label>
                    <select class="ui dropdown" id="statusFilter">
                        <option value="all">显示全部</option>
                        <option value="full">全部覆盖</option>
                        <option value="partial">部分覆盖</option>
                        <option value="uncovered">未覆盖</option>
                    </select>
                </div>
            </div>
        </div>

        <table class="ui compact basic celled table method-table" id="methodTable">
            <thead>
                <tr>
                    <th class="col-name">方法名称</th>
                    <th class="col-pct">代码行覆盖率</th>
                    <th class="col-pct">分支覆盖率</th>
                    <th class="col-jump">跳转</th>
                    <th class="col-status">覆盖状态</th>
                </tr>
            </thead>
            <tbody>
            <#if classCov?? && classCov.methods??>
            <#list classCov.methods as m>
                <#-- 计算百分比并保留小数以防整数截断 -->
                <#assign linePct = (m.totalLines > 0)?then(m.coveredLines * 1.0 / m.totalLines * 100, 0)>
                <#assign branchPct = (m.totalBranches > 0)?then(m.coveredBranches * 1.0 / m.totalBranches * 100, 0)>
                <tr>
                    <td class="method-name" title="${m.methodDesc!""}">${m.methodName}</td>
                    <td>
                        <span class="stat-txt">${m.coveredLines}/${m.totalLines} (${linePct?string("0.0")}%)</span>
                        <div class="ui tiny progress <#if linePct gt 0>success<#else>error</#if>" data-percent="${linePct}">
                            <div class="bar" style="width: ${linePct}%"></div>
                        </div>
                    </td>
                    <td>
                        <span class="stat-txt">${m.coveredBranches}/${m.totalBranches} (${branchPct?string("0.0")}%)</span>
                        <div class="ui tiny progress <#if branchPct gt 0>success<#elseif m.totalBranches gt 0>error</#if>" data-percent="${branchPct}">
                            <div class="bar" style="width: ${branchPct}%"></div>
                        </div>
                    </td>
                    <td>
                        <a href="#method_${m?index}" class="ui mini blue basic button">查看代码</a>
                    </td>
                    <td>
                        <#if m.coveredLines gt 0>
                            <span class="ui green label small">已覆盖</span>
                        <#else>
                            <span class="ui red label small">未覆盖</span>
                        </#if>
                    </td>
                </tr>
            </#list>
            <#else>
                <tr><td colspan="5" class="center aligned">暂无方法覆盖数据</td></tr>
            </#if>
            </tbody>
        </table>
    </div>

    <h4 class="ui dividing header">源代码</h4>
    <div class="source-container">
        ${coloredSource}
    </div>
</div>

<button id="backToTop" title="回到顶部"><i class="arrow up icon"></i> 回到顶部</button>

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
    $(document).ready(function() {
        var $searchInput = $('#methodSearchInput');
        var $statusFilter = $('#statusFilter');
        var $methodRows = $('#methodTable tbody tr');

        $('.ui.dropdown').dropdown();

        $(window).scroll(function() {
            if ($(this).scrollTop() > 300) {
                $('#backToTop').fadeIn();
            } else {
                $('#backToTop').fadeOut();
            }
        });

        $('#backToTop').click(function() {
            $('html, body').animate({scrollTop: 0}, 500);
            return false;
        });

        // Search and Filter Logic
        function filterMethods() {
            var searchTerm = $searchInput.val().toLowerCase();
            var statusTerm = $statusFilter.val();

            $methodRows.each(function() {
                var $row = $(this);
                var methodName = $row.find('.method-name').text().toLowerCase();
                var labelText = $row.find('.label').text();

                var isCovered = labelText.indexOf('已覆盖') !== -1;
                var isUncovered = labelText.indexOf('未覆盖') !== -1;

                var matchName = methodName.indexOf(searchTerm) !== -1;
                var matchStatus = false;

                if (statusTerm === 'all') {
                    matchStatus = true;
                } else if (statusTerm === 'full' || statusTerm === 'partial') {
                    matchStatus = isCovered;
                } else if (statusTerm === 'uncovered') {
                    matchStatus = isUncovered;
                }

                if (matchName && matchStatus) {
                    $row.show();
                } else {
                    $row.hide();
                }
            });
        }

        $searchInput.on('keyup', filterMethods);
        $statusFilter.on('change', filterMethods);

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
                        setTimeout(function () { location.reload(); }, 1200);
                    } else if (job.state === 'error') {
                        clearInterval(interval);
                        $('#jobProgress').addClass('error');
                        $('#jobStatus').text(progress.name || '生成失败');
                        $('#btnCloseModal').removeClass('disabled').text('关闭').off('click').on('click', function () {
                            $('#jobModal').modal('hide');
                        });
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
    });
</script>
</body>
</html>


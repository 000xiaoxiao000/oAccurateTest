<!DOCTYPE html>
<html lang="en">
<head>
    <title>代码覆盖率 - ${displayClassName!className}</title>
    <#include "../common.ftl">
    <style>
        html, body { max-width: 100%; overflow-x: hidden; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; padding: 20px; line-height: 1.5; box-sizing: border-box; }
        body .ui.container { width: calc(100vw - 40px) !important; max-width: calc(100vw - 40px) !important; margin: 0 auto !important; box-sizing: border-box; }
        .page-breadcrumb { margin-bottom: 20px; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; }
        .header-segment { background-color: #f9f9f9; margin-top: 10px; border-top: 2px solid #2185d0; overflow: hidden; }
        .header-title { margin: 0; min-width: 0; }
        .header-title .content { min-width: 0; overflow-wrap: anywhere; word-break: break-word; }
        .source-container { border: 1px solid #ddd; padding: 10px; border-radius: 5px; background: #fff; max-width: 100%; overflow-x: auto; overflow-y: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .method-list { margin-bottom: 25px; }
        /* 进度条样式优化 */
        .ui.progress { margin: 0; min-width: 80px; }
        .method-table { width: 100%; table-layout: fixed; }
        .method-table th, .method-table td { overflow-wrap: anywhere; word-break: break-word; }
        .method-table td { vertical-align: middle !important; }
        .method-table .method-name { font-weight: 600; color: #1e70bf; }
        .method-table .stat-txt { font-size: 0.9em; white-space: normal; }
        /* 回到顶部按钮 */
        #backToTop {
            position: fixed !important;
            bottom: 40px !important;
            right: 40px !important;
            display: none !important;
            z-index: 9999 !important;
            padding: 10px 16px !important;
            background-color: #2185d0 !important;
            color: #fff !important;
            border: none !important;
            border-radius: 4px !important;
            cursor: pointer !important;
            box-shadow: 0 2px 8px rgba(0,0,0,0.25) !important;
            font-size: 14px !important;
        }
        #backToTop.visible { display: block !important; }
        #backToTop:hover { background-color: #1678c2 !important; box-shadow: 0 4px 12px rgba(0,0,0,0.35) !important; }
        /* 调整源码显示样式 */
        .source-container pre { margin: 0; font-size: 13px; line-height: 18px; min-width: 100%; }
        .source-container pre > div { min-width: max-content; }
        /* 表格列宽 */
        .col-name { width: 24%; }
        .col-pct { width: 16%; }
        .col-jump { width: 8%; }
        .col-status { width: 8%; }
    </style>
</head>
<body>
<div class="ui container" style="width: 95%;">
    <div class="ui breadcrumb page-breadcrumb">
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
    </div>
    <div class="ui segment header-segment">
        <div class="ui grid">
            <div class="twelve wide column">
                <h3 class="ui header header-title">
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
                <#assign branchPct = m.branchRate!0>
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
                        <div class="ui tiny progress <#if branchPct == 100>success<#elseif branchPct gt 0>warning<#elseif m.totalBranches gt 0>error</#if>" data-percent="${branchPct}">
                            <div class="bar" style="width: ${branchPct}%"></div>
                        </div>
                    </td>
                    <td>
                        <a href="#method_${m?index}" class="ui mini blue basic button">查看代码</a>
                    </td>
                    <td>
                        <#if linePct == 100 && (m.totalBranches == 0 || branchPct == 100)>
                            <span class="ui green label small">全覆盖</span>
                        <#elseif linePct gt 0 || branchPct gt 0>
                            <span class="ui orange label small">部分覆盖</span>
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

        var $sourceContainer = $('.source-container');

        function isSourceContainerVisible() {
            if (!$sourceContainer.length) {
                return false;
            }

            var rect = $sourceContainer[0].getBoundingClientRect();
            var viewportHeight = window.innerHeight || document.documentElement.clientHeight;
            return rect.top < viewportHeight && rect.bottom > 0;
        }

        function checkBackToTopVisible() {
            if (isSourceContainerVisible()) {
                $('#backToTop').addClass('visible');
            } else {
                $('#backToTop').removeClass('visible');
            }
        }

        // 滚动或窗口变化时，源码区域进入视口就显示按钮
        $(window).on('scroll.backToTop resize.backToTop hashchange.backToTop', function() {
            checkBackToTopVisible();
        });

        // 点击"查看代码"时先立即显示，跳转完成后再按实际位置校准
        $('a[href^="#method_"]').on('click.backToTop', function() {
            $('#backToTop').addClass('visible');
            setTimeout(checkBackToTopVisible, 50);
        });

        // 鼠标滚轮进入源码区域时立即显示按钮
        $sourceContainer.on('wheel.backToTop mousewheel.backToTop DOMMouseScroll.backToTop', function() {
            $('#backToTop').addClass('visible');
            setTimeout(checkBackToTopVisible, 50);
        });

        // 初始化
        setTimeout(checkBackToTopVisible, 100);

        $('#backToTop').on('click', function(e) {
            e.preventDefault();
            $('html, body').stop().animate({scrollTop: 0}, 400, function() {
                $('#backToTop').removeClass('visible');
            });
            return false;
        });

        // Search and Filter Logic
        function filterMethods() {
            var searchTerm = $searchInput.val().toLowerCase();
            var statusTerm = $statusFilter.val();

            $methodRows.each(function() {
                var $row = $(this);
                var methodName = $row.find('.method-name').text().toLowerCase();
                var labelText = $.trim($row.find('.label').first().text());

                var isFull = labelText.indexOf('全覆盖') !== -1;
                var isPartial = labelText.indexOf('部分覆盖') !== -1;
                var isUncovered = labelText.indexOf('未覆盖') !== -1;

                var matchName = methodName.indexOf(searchTerm) !== -1;
                var matchStatus = false;

                if (statusTerm === 'all') {
                    matchStatus = true;
                } else if (statusTerm === 'full') {
                    matchStatus = isFull;
                } else if (statusTerm === 'partial') {
                    matchStatus = isPartial;
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
    });
</script>

<!-- AI 悬浮小人 -->
<div id="aiFloatingWidget"
     class="ai-floating-widget"
     data-project-id="${project.id}"
     data-project-name="${project.name}"
     data-ask-url="/p/${project.id}/AIInteractive/ask"
     data-ai-url="/p/${project.id}/AIInteractive"
     data-mascot-primary="${mascotPrimary!('#00b5ad')}">
    <button id="aiFloatingLauncher" class="ai-floating-launcher" type="button" title="打开 AI 助手">
        <canvas id="aiFloatingMascotCanvas" width="88" height="88"></canvas>
        <span class="ai-floating-launcher-text">AI</span>
    </button>

    <div id="aiFloatingPanel" class="ai-floating-panel">
        <div class="ai-floating-panel-header">
            <div>
                <div id="aiFloatingPanelEyebrow" class="ai-floating-panel-eyebrow">AI Interactive</div>
                <div id="aiFloatingPanelTitle" class="ai-floating-panel-title">项目悬浮助手</div>
            </div>
            <div class="ai-floating-panel-tools">
                <a class="ai-floating-link" href="/p/${project.id}/AIInteractive" target="_blank">工作台</a>
                <button id="aiFloatingHideMascot" class="ai-floating-icon-btn" type="button" title="隐藏小人">-</button>
                <button id="aiFloatingCollapse" class="ai-floating-icon-btn" type="button" title="收起助手">x</button>
            </div>
        </div>

        <div class="ai-floating-section" id="aiFloatingMessageSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">对话</span>
                <button class="ai-floating-section-toggle" type="button"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingMessageList" class="ai-floating-section-body ai-floating-message-list"></div>
        </div>

        <div id="aiFloatingContextStatus" class="ai-floating-context-status"></div>

        <div class="ai-floating-section" id="aiFloatingQuickLinkSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">快捷入口</span>
                <button class="ai-floating-section-toggle" type="button"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingQuickLinkList" class="ai-floating-section-body ai-floating-quick-links"></div>
        </div>

        <div class="ai-floating-section" id="aiFloatingStarterSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">快捷提问</span>
                <button class="ai-floating-section-toggle" type="button"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingStarterList" class="ai-floating-section-body ai-floating-starters"></div>
        </div>

        <div class="ai-floating-compose">
            <!-- file input 必须在 button 外部 -->
            <input type="file" id="aiFloatingImageInput" accept="image/*" style="display:none">
            <textarea id="aiFloatingQuestionInput" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看"></textarea>
            <div class="ai-floating-compose-actions">
                <span id="aiFloatingState" class="ai-floating-state">就绪</span>
                <div class="ai-floating-compose-toolbar">
                    <button type="button" id="aiFloatingImageUploadBtn" class="ai-floating-toolbar-btn" title="上传图片">
                        <i class="image icon"></i>
                        <img class="ai-floating-image-preview" alt="preview">
                        <span class="ai-image-remove-btn" title="移除图片"><i class="close icon"></i></span>
                    </button>
                    <button type="button" id="aiFloatingVoiceRecordBtn" class="ai-floating-toolbar-btn" title="语音输入">
                        <i class="microphone icon"></i>
                    </button>
                </div>
                <button id="aiFloatingSendButton" class="ui teal mini button" type="button">发送</button>
            </div>
        </div>
    </div>

    <button id="aiFloatingRestore" class="ai-floating-restore" type="button">显示小人</button>
</div>

</body>
</html>


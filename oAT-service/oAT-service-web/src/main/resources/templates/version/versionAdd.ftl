<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-新增版本</title>
    <#include "../common.ftl">
    <script src="/js/upload.js?version=1"></script>
    <script src="/js/spark-md5.min.js"></script>


<style>
    .repository-config-missing {
        opacity: 0.58;
        filter: grayscale(0.18);
    }

    .repository-config-missing .ui.selection.dropdown,
    .repository-config-missing input,
    .repository-config-missing button {
        pointer-events: none;
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
        <a class="section" href="/p/${project.id}/${appId}/version/list">${app.name}</a>
        <span class="divider">/</span>
        <div class="active section">新增版本</div>
    </div>
</div>

<!--内容主体-->
<div class="ui container version-center-page">
    <div class="version-page-layout">
        <!-- 左边导航菜单 -->
        <div class="version-page-side">
            <#assign appName=app.name/>
            <#assign versionListActive="active"/>
            <#include "LeftNavigationMenu.ftl">
        </div>
        <!-- 中间内容 -->
        <div class="version-page-main">
            <div class="version-page-header">
                <div>
                    <div class="version-page-kicker">
                        <i class="code branch icon"></i>
                        版本中心
                    </div>
                    <h1 class="version-page-title">新增版本</h1>
                    <p class="version-page-desc">从 Git 拉取代码或上传制品包，创建应用版本并用于后续覆盖率和比对分析。</p>
                </div>
                <div class="version-page-actions">
                    <#assign repositoryConfigured=(app.repoAddress?? && app.repoAddress?trim != '')>
                    <#assign repositoryConfigTip = '当前应用尚未配置代码仓库，点击可前往仓库配置完成地址与认证信息设置。'>
                    <#if repositoryConfigured>
                        <#assign repositoryConfigTip = '当前应用已配置代码仓库，点击可查看或调整仓库地址、分支与认证信息。'>
                    </#if>
                    <a class="ui <#if repositoryConfigured>teal basic<#else>orange basic</#if> button repository-config-button" id="repositoryConfigButton" data-content="${repositoryConfigTip?html}" data-position="bottom center" title="${repositoryConfigTip?html}" href="/p/${project.id}/app/${appId}/repository">
                        <i class="setting icon"></i>仓库配置
                    </a>
                    <a class="ui button guard-pulled-code" id="backVersionListButton" href="/p/${project.id}/${appId}/version/list">
                        <i class="left arrow icon"></i>返回版本列表
                    </a>
                </div>
            </div>
            <div class="version-page-body">
            <div class="ui pointing secondary menu">
                <a class="item active" data-tab="git">从Git拉取</a>
                <a class="item" data-tab="upload">上传文件</a>
            </div>

            <form class="ui form version-add-form" id="versionForm" action="/p/${project.id}/${appId}/version/doAdd" method="post">
                <input type="hidden" name="projectId" value="${project.id}">
                <input type="hidden" name="appId" value="${appId}">
                <input type="hidden" name="programFile" id="programFile">
                <input type="hidden" name="sourceType" id="sourceType" value="git">

                <div class="field required">
                    <label class="label">版本号:</label>
                    <input type="text" name="versionNumber" placeholder="输入版本号">
                </div>
                <div class="field required">
                    <label class="label">版本描述:</label>
                    <textarea rows="3" name="describe"></textarea>
                </div>

                <!-- Git Tab -->
                <div class="ui tab segment active <#if !repositoryConfigured>repository-config-missing</#if>" data-tab="git" style="border: none; box-shadow: none; padding: 0;">
                    <#if !repositoryConfigured>
                    <div class="ui tiny warning message" style="margin-bottom: 14px;">
                        当前应用尚未配置代码仓库，请先完成仓库配置后再使用 Git 拉取。
                    </div>
                    </#if>
                    <div class="field required">
                        <label>分支</label>
                        <div class="fields">
                            <div class="twelve wide field">
                                <div class="ui selection dropdown <#if !repositoryConfigured>disabled</#if>" id="branchDropdown" <#if !repositoryConfigured>aria-disabled="true"</#if>>
                                     <input type="hidden" name="repoBranch" id="repoBranch" <#if !repositoryConfigured>disabled</#if>>
                                     <i class="dropdown icon"></i>
                                     <div class="default text">选择分支</div>
                                     <div class="menu"></div>
                                </div>
                            </div>
                            <div class="four wide field">
                                <button class="ui button" type="button" id="refreshBranchesButton" onclick="fetchBranches()" <#if !repositoryConfigured>disabled</#if>>
                                    <i class="sync icon"></i> 刷新分支
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="field">
                        <label>Commit ID (可选)</label>
                        <input type="text" name="repoCommitId" id="repoCommitId" placeholder="输入 Commit ID" <#if !repositoryConfigured>disabled</#if>>
                    </div>
                    <div class="field">
                        <label>排除路径 (可选, 多个路径用逗号隔开，例如: src/test/,README.md)</label>
                        <input type="text" name="excludePaths" id="excludePaths" placeholder="输入排除路径" <#if !repositoryConfigured>disabled</#if>>
                    </div>
                    <div class="field">
                         <button class="ui button" type="button" id="checkGitConnectionButton" onclick="checkGitConnection(this)"
                                  style="margin-left: 10px;" <#if !repositoryConfigured>disabled</#if>>检测是否可拉取代码</button>
                        <button class="ui teal button" type="button" id="startPullBtn" onclick="startPull(this)" <#if !repositoryConfigured>disabled</#if>>远程拉取代码</button>
                        <button class="ui orange button" type="button" id="deleteCodeBtn" style="display: none;"
                                onclick="deletePulledCode(this)" <#if !repositoryConfigured>disabled</#if>>删除远程拉取代码</button>
                    </div>
                    <div class="field" id="gitProgressField" style="display: none;">
                        <label>拉取进度</label>
                        <div id="gitProgress" class="ui progress active">
                            <div class="bar">
                                <div class="progress"></div>
                            </div>
                            <div class="label">等待开始...</div>
                        </div>
                    </div>
                </div>

                <!-- Upload Tab -->
                <div class="ui tab segment" data-tab="upload" style="border: none; box-shadow: none; padding: 0;">
                    <div class="field required">
                        <label class="label">程序文件:</label>
                        <input type="file" id="programFileSelect" readonly="readonly">
                        <div id="programProgress" class="ui bottom attached progress">
                            <div class="bar">
                                <div class="progress"></div>
                            </div>
                            <div class="label">等待选择文件...</div>
                        </div>
                    </div>
                </div>

                <br>
                <div class="field">
                    <div class="ui checkbox">
                        <input type="checkbox" name="setAsCurrent" value="on" <#if !hasVersion!true>checked="checked"</#if>>
                        <label>设为当前版本</label>
                    </div>
                </div>
                <div class="version-form-actions">
                    <button class="ui button positive" type="submit" id="versionCreateButton">创建新的版本</button>
                    <button class="ui button" type="reset" id="versionResetButton">重置</button>
                </div>
                <div class="ui error message"></div>
            </form>

            </div>
        </div>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.checkbox').checkbox();
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.repository-config-button').popup({
        on: 'hover'
    });

    var repositoryConfigured = ${repositoryConfigured?c};

    function ensureRepositoryConfigured() {
        if (repositoryConfigured) {
            return true;
        }
        showToast('当前应用尚未配置代码仓库，请先完成仓库配置后再使用该功能。', 'warning', 8000);
        return false;
    }

    function applyRepositoryConfigurationState() {
        var disabled = !repositoryConfigured;
        $('.ui.tab.segment[data-tab="git"]').toggleClass('repository-config-missing', disabled);
        $('#branchDropdown').toggleClass('disabled', disabled).attr('aria-disabled', disabled ? 'true' : 'false');
        $('#repoBranch').prop('disabled', disabled);
        $('#repoCommitId, #excludePaths').prop('disabled', disabled);
        $('#refreshBranchesButton, #checkGitConnectionButton, #startPullBtn, #deleteCodeBtn').prop('disabled', disabled).toggleClass('disabled', disabled);
    }

    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }

    $('.menu .item').tab({
        onVisible: function(tabPath) {
            $('#sourceType').val(tabPath);
            resetPackageReadyState();
        }
    });

    function setCreateButtonEnabled(enabled) {
        $('#versionCreateButton').toggleClass('disabled', !enabled).prop('disabled', !enabled);
    }

    var versionPageBusy = false;

    function setVersionPageBusy(busy, message) {
        versionPageBusy = busy;
        var $form = $('#versionForm');
        $form.data('oatSubmitting', busy);
        $form.toggleClass('oat-form-submitting', busy)
                .attr('aria-busy', busy ? 'true' : 'false');
        if (message) {
            $form.attr('data-oat-submitting-message', message);
        } else {
            $form.removeAttr('data-oat-submitting-message');
        }
        $form.find('input, textarea, select, button').prop('disabled', busy);
        $form.find('.ui.button, .ui.checkbox, .ui.radio.checkbox, .ui.dropdown').toggleClass('disabled', busy);
        $('.version-page-actions .ui.button, .version-page-body .ui.menu .item, .version-page-side .item')
                .toggleClass('disabled', busy)
                .attr('aria-disabled', busy ? 'true' : 'false');
        if (!busy) {
            applyRepositoryConfigurationState();
        }
    }

    $(document).on('click', 'a, button, input, textarea, select, .ui.dropdown, .ui.checkbox, .menu .item', function(event) {
        if (!versionPageBusy || $(event.target).closest('.toast').length) {
            return;
        }
        event.preventDefault();
        event.stopImmediatePropagation();
        return false;
    });

    function hasPulledGitCode() {
        return $('#sourceType').val() === 'git' && !!$('#programFile').val() && $('#deleteCodeBtn').is(':visible');
    }

    function resetPackageReadyState() {
        $('#programFile').val('');
        setCreateButtonEnabled(false);
    }

    function formatCommitId(commitId) {
        if (!commitId) {
            return '-';
        }
        return commitId.length > 12 ? commitId.substring(0, 12) : commitId;
    }

    function buildPackageCommitVerifyMessage(verify) {
        if (!verify) {
            return 'CommitId 校验：未获取到运行时目标系统 CommitId，无法校验';
        }
        if (verify.probeOnline === false) {
            return 'CommitId 校验：探针不在线，无法获取运行时目标系统 CommitId，请先启动目标系统探针后重试';
        }
        if (verify.unavailableReason) {
            return 'CommitId 校验：' + verify.unavailableReason;
        }
        if (!verify.runtimeCommitId) {
            return 'CommitId 校验：未获取到运行时目标系统 CommitId，无法校验';
        }
        if (!verify.targetCommitId) {
            return 'CommitId 校验：未获取到代码或上传包 CommitId，无法校验';
        }
        if (verify.matched) {
            return 'CommitId 校验一致：运行时 ' + formatCommitId(verify.runtimeCommitId) + '，目标 ' + formatCommitId(verify.targetCommitId);
        }
        return 'CommitId 校验不一致：运行时 ' + formatCommitId(verify.runtimeCommitId) + '，目标 ' + formatCommitId(verify.targetCommitId);
    }

    function getPackageCommitVerifyToastType(verify, defaultType) {
        if (verify && verify.probeOnline === false) {
            return 'warning';
        }
        if (!verify || !verify.runtimeCommitId || !verify.targetCommitId || verify.matched === false) {
            return 'warning';
        }
        return defaultType || 'success';
    }

    function getPackageCommitVerifyToastDuration(verify) {
        if (verify && verify.probeOnline === false) {
            return 15000;
        }
        if (!verify || !verify.runtimeCommitId || !verify.targetCommitId || verify.matched === false) {
            return 12000;
        }
        return 6000;
    }

    setCreateButtonEnabled(false);

    $('#versionResetButton').on('click', function() {
        setTimeout(function() {
            resetPackageReadyState();
            $('#startPullBtn').removeClass('loading');
            $('#deleteCodeBtn').hide().removeClass('loading');
            $('#gitProgressField').hide();
            $('#programProgress').progress({ percent: 0 });
            $('#programProgress .label').text('等待选择文件...');
            applyRepositoryConfigurationState();
        }, 0);
    });

    $(document).on('click', 'a.guard-pulled-code, a[href*="/version/list"]', function(event) {
        if (hasPulledGitCode()) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showToast('已拉取远程代码，请先删除远程拉取的代码再返回版本列表', 'warning');
            return false;
        }
    });

    // Init branch dropdown with change listener
    $('#branchDropdown').dropdown({
        onChange: function(value, text, $selectedItem) {
            // $('#repoBranch').val(value); // Semantic UI updates hidden input automatically
            fetchLatestCommit(value);
        }
    });

    function fetchBranches() {
        if (!ensureRepositoryConfigured()) {
            return;
        }
        $.get("/p/${project.id}/app/${appId}/git/branches", function(data){
            if(data.success) {
                var menu = $('#branchDropdown .menu');
                menu.empty();
                $.each(data.branches, function(i, branch){
                    menu.append('<div class="item" data-value="'+branch+'">'+branch+'</div>');
                });

                // Refresh items and clear selection
                $('#branchDropdown').dropdown('refresh');
                $('#branchDropdown').dropdown('clear');
                showToast("分支刷新成功", 'success');
            } else {
                showToast("获取分支失败: " + data.message, 'error');
                console.error("获取分支失败: " + data.message);
            }
        });
    }

    function fetchLatestCommit(branch) {
        if (!ensureRepositoryConfigured()) {
            return;
        }
        if(!branch) return;
        // Optional: show loading on commit input?
        $('#repoCommitId').parent().addClass('loading');
        $.get("/p/${project.id}/${appId}/version/git/commit?branch="+branch, function(data){
            $('#repoCommitId').parent().removeClass('loading');
            if(data.result) {
                $('#repoCommitId').val(data.data);
                showToast('Commit ID获取成功', 'success');
            } else {
                showToast("获取 CommitID 失败: " + data.message, 'error');
                console.error("获取 CommitID : " + data.message);
                // Don't clear user input, they might want to type manually
            }
        });
    }

    function buildGitPullEstimateMessage(estimate) {
        var parts = ['检测通过'];
        if (estimate) {
            if (estimate.estimatedDurationMs || estimate.estimatedDurationMs === 0) {
                parts.push('预计耗时：' + formatGitPullDuration(estimate.estimatedDurationMs));
            }
            if (estimate.estimatedPackageSizeBytes || estimate.estimatedPackageSizeBytes === 0) {
                parts.push('预计大小：' + formatGitPullSize(estimate.estimatedPackageSizeBytes));
            }
        }
        return parts.join('，');
    }

    function buildGitPullEstimateMessage(estimate) {
        var parts = ['检测通过'];
        if (estimate) {
            if (estimate.estimatedDurationMs || estimate.estimatedDurationMs === 0) {
                parts.push('预计耗时：' + formatGitPullDuration(estimate.estimatedDurationMs));
            }
            if (estimate.estimatedPackageSizeBytes || estimate.estimatedPackageSizeBytes === 0) {
                parts.push('预计大小：' + formatGitPullSize(estimate.estimatedPackageSizeBytes));
            }
        }
        return parts.join('，');
    }

    function checkGitConnection(btn) {
        if (!ensureRepositoryConfigured()) {
            return;
        }
        var branch = $('#repoBranch').val();
        if(!branch) {
            showToast('请先选择分支', 'error');
            return;
        }
        var commitId = $('#repoCommitId').val();
        var versionNumber = $('input[name="versionNumber"]').val();
        var excludePaths = $('#excludePaths').val();

        $(btn).addClass('loading');
        $.get("/p/${project.id}/${appId}/version/checkGitPull?branch="+encodeURIComponent(branch)+"&commitId="+encodeURIComponent(commitId)+"&versionNumber="+encodeURIComponent(versionNumber)+"&excludePaths="+encodeURIComponent(excludePaths), function(data){
            $(btn).removeClass('loading');
            if(data.result) {
                var verify = data.data ? data.data.packageCommitVerify : null;
                showToast(buildGitPullEstimateMessage(data.data) + '；' + buildPackageCommitVerifyMessage(verify), getPackageCommitVerifyToastType(verify, 'success'), getPackageCommitVerifyToastDuration(verify));
            } else {
                showToast(data.message, 'error');
            }
        });
    }

    function formatGitPullDuration(durationMs) {
        if (!durationMs && durationMs !== 0) {
            return '-';
        }
        if (durationMs < 1000) {
            return durationMs + 'ms';
        }
        var seconds = durationMs / 1000;
        if (seconds < 60) {
            return seconds.toFixed(seconds >= 10 ? 1 : 2).replace(/\.0$/, '') + 's';
        }
        var minutes = Math.floor(seconds / 60);
        var remainSeconds = (seconds % 60).toFixed(1).replace(/\.0$/, '');
        return minutes + '分' + remainSeconds + '秒';
    }

    function formatGitPullSize(sizeBytes) {
        if (!sizeBytes && sizeBytes !== 0) {
            return '-';
        }
        var units = ['B', 'KB', 'MB', 'GB'];
        var size = sizeBytes;
        var unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size = size / 1024;
            unitIndex++;
        }
        var formatted = unitIndex === 0 ? size.toString() : size.toFixed(size >= 10 ? 1 : 2).replace(/\.0$/, '');
        return formatted + ' ' + units[unitIndex];
    }

    function buildGitPullSuccessMessage(job) {
        var parts = ['拉取成功'];
        if (job) {
            if (job.pullDurationMs || job.pullDurationMs === 0) {
                parts.push('耗时：' + formatGitPullDuration(job.pullDurationMs));
            }
            if (job.packageSizeBytes || job.packageSizeBytes === 0) {
                parts.push('大小：' + formatGitPullSize(job.packageSizeBytes));
            }
        }
        return parts.join('，');
    }

    function formatGitProgressName(progressName, percent) {
        if (!progressName) {
            return '正在处理远程代码...';
        }
        var normalized = progressName.replace(/^Git:\s*/i, '').trim();
        var lower = normalized.toLowerCase();
        var text = normalized;
        if (lower.indexOf('counting objects') >= 0) {
            text = '正在统计远程代码对象';
        } else if (lower.indexOf('compressing objects') >= 0) {
            text = '正在压缩传输数据';
        } else if (lower.indexOf('receiving objects') >= 0) {
            text = '正在接收远程代码';
        } else if (lower.indexOf('resolving deltas') >= 0) {
            text = '正在解析代码差异';
        } else if (lower.indexOf('checking out files') >= 0 || lower.indexOf('checkout') >= 0) {
            text = '正在检出代码文件';
        } else if (lower.indexOf('remote') >= 0) {
            text = '正在连接远程仓库';
        } else if (lower.indexOf('切换 commit') >= 0) {
            text = '正在切换到指定 Commit';
        } else if (lower.indexOf('排除指定路径') >= 0) {
            text = '正在处理排除路径';
        } else if (lower.indexOf('打包') >= 0 || lower.indexOf('zip') >= 0) {
            text = '正在打包代码文件';
        }
        if (percent || percent === 0) {
            text += '（' + percent + '%）';
        }
        return text;
    }

    function checkGitPullStatus(jobId, btn) {
        $.get("/p/${project.id}/${appId}/version/git/status?jobId=" + jobId, function(data){
            if(data.result) {
                var job = data.data;
                var percent = job.progress;
                $('#gitProgress').progress({
                    percent: percent
                });
                $('#gitProgress .label').text(formatGitProgressName(job.progressName, percent));

                if (job.finish) {
                    if (job.success) {
                        $('#gitProgress .bar').addClass('success');
                        $('#gitProgress .label').text("拉取完成");
                        $('#programFile').val(job.cachePath); // Set the hidden file path
                        $.get("/p/${project.id}/${appId}/version/package/verifyCommit?commitId="+encodeURIComponent(job.repoCommitId || $('#repoCommitId').val()), function(verifyData){
                            var verify = verifyData.result ? verifyData.data : null;
                            var verifyMessage = verifyData.result ? buildPackageCommitVerifyMessage(verify) : 'CommitId 校验失败：' + (verifyData.message || '请求失败');
                            showToast(buildGitPullSuccessMessage(job) + '；' + verifyMessage, getPackageCommitVerifyToastType(verify, verifyData.result ? 'success' : 'warning'), getPackageCommitVerifyToastDuration(verify));
                        }).fail(function() {
                            showToast(buildGitPullSuccessMessage(job) + '；CommitId 校验失败：网络请求失败', 'warning', 12000);
                        });
                        // 拉取成功后：置灰拉取按钮，显示删除按钮
                        setVersionPageBusy(false);
                        setCreateButtonEnabled(true);
                        $(btn).addClass('disabled').prop('disabled', true).removeClass('loading');
                        $('#deleteCodeBtn').show().removeClass('disabled').prop('disabled', false);
                    } else {
                        setVersionPageBusy(false);
                        setCreateButtonEnabled(false);
                        $(btn).removeClass('loading');
                        $('#gitProgress .bar').addClass('error');
                        $('#gitProgress .label').text("失败: " + job.message);
                        showToast("拉取失败: " + job.message, 'error');
                    }
                } else {
                    // Continue polling
                    setTimeout(function() { checkGitPullStatus(jobId, btn); }, 1000);
                }
            } else {
                setVersionPageBusy(false);
                setCreateButtonEnabled(false);
                $(btn).removeClass('loading');
                showToast("查询进度失败: " + data.message, 'error');
            }
        }).fail(function() {
            setVersionPageBusy(false);
            setCreateButtonEnabled(false);
            $(btn).removeClass('loading');
            showToast('查询进度失败: 网络请求失败', 'error');
        });
    }

    function startPull(btn) {
        if (!ensureRepositoryConfigured()) {
            return;
        }
        var branch = $('#repoBranch').val();
        if(!branch) {
            showToast('请先选择分支', 'error');
            return;
        }
        var commitId = $('#repoCommitId').val();
        var excludePaths = $('#excludePaths').val();
        var versionNumber = $('input[name="versionNumber"]').val();

        if(!versionNumber) {
            showToast('请先输入版本号', 'error');
            return;
        }

        setVersionPageBusy(true);
        $(btn).addClass('loading').removeClass('disabled').prop('disabled', false);
        $('#gitProgressField').show();
        $('#gitProgress').progress({ percent: 0 });
        $('#gitProgress .label').text("正在连接远程仓库...");
        $('#gitProgress .bar').removeClass('success error');

        $.get("/p/${project.id}/${appId}/version/git/pull?branch="+encodeURIComponent(branch)+"&commitId="+encodeURIComponent(commitId)+"&excludePaths="+encodeURIComponent(excludePaths)+"&versionNumber="+encodeURIComponent(versionNumber), function(data){
            if(data.result) {
                // Return jobId
                var jobId = data.data;
                checkGitPullStatus(jobId, btn);
            } else {
                setVersionPageBusy(false);
                setCreateButtonEnabled(false);
                $(btn).removeClass('loading');
                showToast(data.message, 'error');
            }
        }).fail(function() {
            setVersionPageBusy(false);
            setCreateButtonEnabled(false);
            $(btn).removeClass('loading');
            showToast('远程代码拉取失败: 网络请求失败', 'error');
        });
    }

    function deletePulledCode(btn) {
        if (!ensureRepositoryConfigured()) {
            return;
        }
        var cachePath = $('#programFile').val();
        if (!cachePath) {
            showToast('没有可删除的代码', 'warning');
            return;
        }

        $(btn).addClass('loading');
        $.get("/p/${project.id}/${appId}/version/git/deleteCode?cachePath=" + cachePath, function(data) {
            $(btn).removeClass('loading');
            if (data.result) {
                showToast('删除成功', 'success');
                $('#programFile').val('');
                setCreateButtonEnabled(false);
                // 删除成功后：按钮置灰不可点击
                $(btn).addClass('disabled').prop('disabled', true);
                // 远程拉取代码亮起可点击
                $('#startPullBtn').removeClass('disabled').prop('disabled', false);
            } else {
                showToast('删除失败: ' + data.message, 'error');
            }
        });
    }

    applyRepositoryConfigurationState();

    // fetchBranches(); // Manual trigger only
</script>

<script>
    document.getElementById("programFileSelect").addEventListener("change", function (ev1) {
        if (!this.files || this.files.length === 0) {
            return;
        }
        $("#programFile").val(null);
        setCreateButtonEnabled(false);
        setVersionPageBusy(true);
        $('#programFileSelect').prop('disabled', false);
        $('#programProgress').progress({ percent: 0 });
        $('#programProgress .label').text('正在准备上传...');
        uploadFile(this.files[0], "/resource/upload"
                , function (ev2) {
                    setVersionPageBusy(false);
                    var results = eval("(" + this.responseText + ")");
                    if (!(results.success || results.result)) {
                        showToast(results.message || '程序文件上传失败', 'error');
                        setCreateButtonEnabled(false);
                        return;
                    }
                    programFilePath = results.data;
                    console.log(programFilePath);
                    $("#programFile").val(programFilePath);

                    $('#programProgress').progress('complete');
                    $('#programProgress .label').text('上传完成');
                    setCreateButtonEnabled(true);
                    $.get("/p/${project.id}/${appId}/version/package/verifyCommit?programFile="+encodeURIComponent(programFilePath), function(data) {
                        var verify = data.result ? data.data : null;
                        var verifyMessage = data.result ? buildPackageCommitVerifyMessage(verify) : 'CommitId 校验失败：' + (data.message || '请求失败');
                        showToast('程序文件上传成功；' + verifyMessage, getPackageCommitVerifyToastType(verify, data.result ? 'success' : 'warning'), getPackageCommitVerifyToastDuration(verify));
                    }).fail(function() {
                        showToast('程序文件上传成功；CommitId 校验失败：网络请求失败', 'warning', 12000);
                    });
                }
                , function (evt) {
                    // 进度条
                    var percent = evt.total ? Math.round(evt.loaded * 100 / evt.total) : 0;
                    $('#programProgress').progress({ percent: percent });
                    $('#programProgress .label').text('正在上传程序文件（' + percent + '%）');
                }
                , function () {
                    setVersionPageBusy(false);
                    setCreateButtonEnabled(false);
                    $('#programProgress .label').text('上传失败');
                    showToast('程序文件上传失败: 网络请求失败', 'error');
                });
    });

    /*$(function () {
        $("#versionForm").submit(function (event) {
            if ($("#programFile").val() == null || $("#programFile").val() == "") {
                // 阻止提交
                showToast("请先选择程序文件上传", 'error');
                event.preventDefault();
            }
        });
    });*/

    // 屏蔽回车提交
    $('.ui.form :text').keydown(function (e) {
        if (e.keyCode == 13) {
            return false;
        }
    })
    $.fn.form.settings.rules.alreadyExist = function (value) {
       var results=$.ajax({
           url:"checkExist?name="+value,
           async:false
       }).responseJSON;
        return results.data;
    };

    $.fn.form.settings.rules.checkGit = function(value) {
        if($('#sourceType').val() === 'git') {
            return value != null && value != '';
        }
        return true;
    };

    $.fn.form.settings.rules.checkUpload = function(value) {
        if($('#sourceType').val() === 'upload') {
            return value != null && value != '';
        }
        return true;
    };

    $.fn.form.settings.rules.checkGitFile = function(value) {
        if($('#sourceType').val() === 'git') {
            return value != null && value != '';
        }
        return true;
    };

    $('.ui.form')
            .form({
                inline: false, // 行内显示验证异常
                onSuccess: function(event, fields) {
                    // Prevent default form submission
                    event.preventDefault();

                    if ($('#sourceType').val() === 'git' && !repositoryConfigured) {
                        showToast('当前应用尚未配置代码仓库，请先完成仓库配置后再使用 Git 拉取。', 'error');
                        return false;
                    }

                    if ($('#versionCreateButton').prop('disabled')) {
                        showToast('请先成功拉取代码或上传程序文件', 'error');
                        return false;
                    }

                    var successMessage = '版本创建成功';
                    if ($('input[name="setAsCurrent"]').is(':checked')) {
                        successMessage += '，并已设为当前版本';
                    }

                    var $form = $(this);
                    if (oatIsFormSubmitting($form)) {
                        return false;
                    }
                    var action = $form.attr('action');
                    var data = $form.serialize();

                    setVersionPageBusy(true, '正在创建版本...');
                    $('#versionCreateButton').addClass('loading').removeClass('disabled').prop('disabled', false);
                    $.ajax({
                        type: 'POST',
                        url: action,
                        data: data,
                        success: function(res) {
                            if (res.success || res.result) {
                                sessionStorage.setItem('toastMessage', successMessage);
                                sessionStorage.setItem('toastMessageType', 'success');
                                showToast(successMessage, 'success');
                                setTimeout(function() {
                                    window.location.href = "/p/${project.id}/${appId}/version/list";
                                }, 1000);
                            } else {
                                setVersionPageBusy(false);
                                setCreateButtonEnabled(true);
                                showToast(res.message || '版本创建失败', 'error');
                            }
                        },
                        error: function() {
                            setVersionPageBusy(false);
                            setCreateButtonEnabled(true);
                            showToast('网络请求失败', 'error');
                        }
                    });

                    return false;
                },
                onFailure: function (formErrors, fields) {
                    if (formErrors && formErrors.length > 0) {
                        showToast(formErrors[0], 'error');
                    }
                    return false;
                },
                keyboardShortcuts: false,// 屏蔽回车提交
                fields: {
                    versionNumber: {
                        identifier: 'versionNumber',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入版本号'
                            },
                            {
                                type: 'minLength[4]',
                                prompt: '版本号至少包含4个字符'
                            },
                            {
                                type: 'maxLength[30]',
                                prompt: '版本号不能超过30个字符'
                            }
                        ]
                    },
                    describe: {
                        identifier: 'describe',
                        rules: [
                            {
                                type: 'maxLength[512]',
                                prompt: '版本描述不能超过512个字符'
                            }
                        ]
                    },
                    programFileSelect: {
                        identifier: 'programFileSelect',
                        rules: [
                            {
                                type: 'checkUpload',
                                prompt: '请选择程序文件'
                            }
                        ]
                    },
                    programFile: {
                        identifier: 'programFile',
                        rules: [
                            {
                                type: 'checkUpload',
                                prompt: '请等待程序文件上传完成'
                            },
                            {
                                type: 'checkGitFile',
                                prompt: '请先拉取代码'
                            }
                        ]
                    },
                    repoBranch: {
                        identifier: 'repoBranch',
                        rules: [
                            {
                                type: 'checkGit',
                                prompt: '请选择Git分支'
                            }
                        ]
                    }
                }
            });

</script>
</body>
</html>

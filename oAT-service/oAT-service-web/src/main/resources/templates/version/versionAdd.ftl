<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-新增版本</title>
    <#include "../common.ftl">
    <script src="/js/upload.js?version=1"></script>
    <script src="/js/spark-md5.min.js"></script>

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
                    <a class="ui button" href="/p/${project.id}/app/${appId}/repository">
                        <i class="setting icon"></i>仓库配置
                    </a>
                    <a class="ui button" href="/p/${project.id}/${appId}/version/list">
                        <i class="left arrow icon"></i>返回版本列表
                    </a>
                </div>
            </div>
            <div class="version-page-body">
            <div class="ui pointing secondary menu">
                <a class="item active" data-tab="git">从Git拉取</a>
                <a class="item" data-tab="upload">上传文件</a>
            </div>

            <form class="ui form" id="versionForm" action="/p/${project.id}/${appId}/version/doAdd" method="post">
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
                <div class="ui tab segment active" data-tab="git" style="border: none; box-shadow: none; padding: 0;">
                    <div class="field required">
                        <label>分支</label>
                        <div class="fields">
                            <div class="twelve wide field">
                                <div class="ui selection dropdown" id="branchDropdown">
                                     <input type="hidden" name="repoBranch" id="repoBranch">
                                     <i class="dropdown icon"></i>
                                     <div class="default text">选择分支</div>
                                     <div class="menu"></div>
                                </div>
                            </div>
                            <div class="four wide field">
                                <button class="ui button" type="button" onclick="fetchBranches()">
                                    <i class="sync icon"></i> 刷新分支
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="field">
                        <label>Commit ID (可选)</label>
                        <input type="text" name="repoCommitId" id="repoCommitId" placeholder="输入 Commit ID">
                    </div>
                    <div class="field">
                        <label>排除路径 (可选, 多个路径用逗号隔开，例如: src/test/,README.md)</label>
                        <input type="text" name="excludePaths" id="excludePaths" placeholder="输入排除路径">
                    </div>
                    <div class="field">
                         <button class="ui button" type="button" onclick="checkGitConnection(this)"
                                  style="margin-left: 10px;">检测是否可拉取代码</button>
                        <button class="ui teal button" type="button" id="startPullBtn" onclick="startPull(this)">远程拉取代码</button>
                        <button class="ui orange button" type="button" id="deleteCodeBtn" style="display: none;"
                                onclick="deletePulledCode(this)">删除远程拉取代码</button>
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
                            <div class="bar"></div>
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
                    <button class="ui button" type="reset">重置</button>
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

    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }

    $('.menu .item').tab({
        onVisible: function(tabPath) {
            $('#sourceType').val(tabPath);
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
                showToast(buildGitPullEstimateMessage(data.data), 'success');
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

    function checkGitPullStatus(jobId, btn) {
        $.get("/p/${project.id}/${appId}/version/git/status?jobId=" + jobId, function(data){
            if(data.result) {
                var job = data.data;
                var percent = job.progress;
                $('#gitProgress').progress({
                    percent: percent
                });
                $('#gitProgress .label').text(job.progressName);

                if (job.finish) {
                    if (job.success) {
                        $('#gitProgress .bar').addClass('success');
                        $('#gitProgress .label').text("拉取完成");
                        $('#programFile').val(job.cachePath); // Set the hidden file path
                        showToast(buildGitPullSuccessMessage(job), 'success');

                        // 拉取成功后：置灰拉取按钮，显示删除按钮
                        $(btn).addClass('disabled').prop('disabled', true).removeClass('loading');
                        $('#deleteCodeBtn').show().removeClass('disabled').prop('disabled', false);
                    } else {
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
                $(btn).removeClass('loading');
                showToast("查询进度失败: " + data.message, 'error');
            }
        });
    }

    function startPull(btn) {
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

        $(btn).addClass('loading');
        $('#gitProgressField').show();
        $('#gitProgress').progress({ percent: 0 });
        $('#gitProgress .label').text("正在请求...");
        $('#gitProgress .bar').removeClass('success error');

        $.get("/p/${project.id}/${appId}/version/git/pull?branch="+encodeURIComponent(branch)+"&commitId="+encodeURIComponent(commitId)+"&excludePaths="+encodeURIComponent(excludePaths)+"&versionNumber="+encodeURIComponent(versionNumber), function(data){
            if(data.result) {
                // Return jobId
                var jobId = data.data;
                checkGitPullStatus(jobId, btn);
            } else {
                $(btn).removeClass('loading');
                showToast(data.message, 'error');
            }
        });
    }

    function deletePulledCode(btn) {
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
                // 删除成功后：按钮置灰不可点击
                $(btn).addClass('disabled').prop('disabled', true);
                // 远程拉取代码亮起可点击
                $('#startPullBtn').removeClass('disabled').prop('disabled', false);
            } else {
                showToast('删除失败: ' + data.message, 'error');
            }
        });
    }

    // fetchBranches(); // Manual trigger only
</script>

<script>
    document.getElementById("programFileSelect").addEventListener("change", function (ev1) {
        $("#programFile").val(null);
        uploadFile(this.files[0], "/resource/upload"
                , function (ev2) {
                    var results = eval("(" + this.responseText + ")");
                    programFilePath = results.data;
                    console.log(programFilePath);
                    $("#programFile").val(programFilePath);

                    $('#programProgress').progress('complete');
                }
                , function (evt) {
                    // 进度条
                    $('#programProgress').progress('set progress', evt.loaded * 75 / 100);
                    $('#programProgress').progress('set total', evt.total);
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
                    var submittingOptions = {
                        submitButton: '#versionCreateButton',
                        extraControls: '.version-page-actions .ui.button, .version-page-body .ui.menu .item'
                    };

                    oatSetFormSubmitting($form, true, submittingOptions);
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
                                oatSetFormSubmitting($form, false, submittingOptions);
                                showToast(res.message || '版本创建失败', 'error');
                            }
                        },
                        error: function() {
                            oatSetFormSubmitting($form, false, submittingOptions);
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

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-版本比对</title>
    <#include "../common.ftl">
    <script src="/js/upload.js?version=1"></script>
    <script src="/js/spark-md5.min.js"></script>
</head>
<body>
<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
    <span class="divider">/</span>
    <a class=" section" href="/p/${project.id}/${app.id}/version/list">${app.name}</a>
    <span class="divider">/</span>
    <div class="active section">版本比对</div>
</div>

<!--内容主体-->
<div class="ui grid attached container" style="margin-top: 14px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign appId=app.id/>
        <#assign appName=app.name/>
        <#assign versionCompareActive="active"/>
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <div class="ui attached positive  message">
            <div class="header">
                开始比对版本
            </div>
            <p>基于 Git 或 制品包（JAR 包或 WAR 包）比对其内部文件差异，并根据差异分析出对系统的功能影响范围</p>
        </div>

        <div class="ui attached segment" style="min-height: 200px">
            <div class="ui top attached tabular menu">
                <a class="item active" data-tab="git">基于 Git 差异</a>
                <a class="item" data-tab="package">制品包比对</a>
            </div>
            <div class="ui bottom attached segment">
                <div class="ui tab active" data-tab="git">
                    <form class="ui form" action="/p/${project.id}/${app.id}/version/git/compare" method="get">
                        <div class="three fields">
                            <div class="field">
                                <label>分支 (branch)：</label>
                                <input type="text" name="branch" placeholder="例如: master">
                            </div>
                            <div class="field">
                                <label>旧 Commit / ref：</label>
                                <input type="text" name="oldCommit" placeholder="旧版本 commit id / 分支">
                            </div>
                            <div class="field">
                                <label>新 Commit / ref：</label>
                                <input type="text" name="newCommit" placeholder="新版本 commit id / 分支">
                            </div>
                        </div>
                        <div class="field">
                            <label>比较范围（包名）：</label>
                            <input type="text" name="packageName" placeholder="默认是包的所有范围（例如 com.example）">
                        </div>
                        <button class="ui button primary" type="submit">开始 Git 比对</button>
                    </form>
                </div>

                <div class="ui tab" data-tab="package">
                    <form class="ui form" action="compare/start" method="post">
                        <div class="two fields">
                            <div class=" field upload file">
                                <label>源版本(新)：</label>
                                <div class="ui fluid search selection dropdown uploaded-files" id="sourceFileDropdown">
                                    <input type="hidden" name="sourceFile">
                                    <i class="dropdown icon"></i>
                                    <div class="default text">上传或选择已上传包</div>
                                    <div class="menu">
                                        <#list items as item>
                                            <div class="item" data-value="${item.programFile!}" data-name="${item.programName!}">
                                                <i class="file alternate outline icon"></i>
                                                <span class="text">${item.programName!}</span>
                                                <i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;" onclick="doDeleteFile('${item.programFile!}', event, this)"></i>
                                            </div>
                                        </#list>
                                    </div>
                                </div>
                                <div style="margin-top: 8px;">
                                    <input type="file" accept=".war,.jar" style="display: none;" id="sourceFileInput">
                                    <button class="ui mini basic button" type="button" onclick="$('#sourceFileInput').click()"><i class="upload icon"></i>上传新包</button>
                                </div>
                                <div class="ui bottom attached progress" style="margin-top: 5px; height: 4px;">
                                    <div class="bar"></div>
                                </div>
                                <div class="file-info" style="margin-top: 5px; display: none;">
                                    <span class="file-name" style="font-size: 0.9em; color: #666;"></span>
                                    <a href="javascript:void(0)" class="delete-file" style="margin-left: 10px; color: #db2828; font-size: 0.85em;"><i class="trash alternate icon"></i>删除物理文件</a>
                                </div>
                            </div>
                            <i class="ui icon large grey retweet" style="position: relative;top: 30px"></i>
                            <div class="field upload file">
                                <label>目标版本(旧)：</label>
                                <div class="ui fluid search selection dropdown uploaded-files" id="targetFileDropdown">
                                    <input type="hidden" name="targetFile">
                                    <i class="dropdown icon"></i>
                                    <div class="default text">上传或选择已上传包</div>
                                    <div class="menu">
                                        <#list items as item>
                                            <div class="item" data-value="${item.programFile!}" data-name="${item.programName!}">
                                                <i class="file alternate outline icon"></i>
                                                <span class="text">${item.programName!}</span>
                                                <i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;" onclick="doDeleteFile('${item.programFile!}', event, this)"></i>
                                            </div>
                                        </#list>
                                    </div>
                                </div>
                                <div style="margin-top: 8px;">
                                    <input type="file" accept=".war,.jar" style="display: none;" id="targetFileInput">
                                    <button class="ui mini basic button" type="button" onclick="$('#targetFileInput').click()"><i class="upload icon"></i>上传新包</button>
                                </div>
                                <div class="ui bottom attached progress" style="margin-top: 5px; height: 4px;">
                                    <div class="bar"></div>
                                </div>
                                <div class="file-info" style="margin-top: 5px; display: none;">
                                    <span class="file-name" style="font-size: 0.9em; color: #666;"></span>
                                    <a href="javascript:void(0)" class="delete-file" style="margin-left: 10px; color: #db2828; font-size: 0.85em;"><i class="trash alternate icon"></i>删除物理文件</a>
                                </div>
                            </div>
                        </div>
                        <div class="field">
                            <label>比较范围（应用包名）：</label>
                            <input type="text" name="packageName" placeholder="默认是包的所有范围">
                        </div>
                        <button class="ui button primary" type="submit">开始比对</button>
                    </form>
                </div>
            </div>
        </div>


        <h4 class="ui header top attached block">
            <i class="grey icon history"></i>
            比对记录
        </h4>
        <div class="ui attached segment">
            <table class="ui fixed selectable table celled">
                <thead>
                <tr>
                    <th class="five wide">报告名称</th>
                    <th class="four wide center aligned">创建时间</th>
                    <th class="four wide center aligned">比对版本 (新 / 旧)</th>
                    <th class="three wide center aligned">操作</th>
                </tr>
                </thead>
                <tbody>
                <#list reports as report >
                <tr>
                    <td class="ellipsis-tooltip" title="${report.name!}"><b>${report.name!}</b>
                        <#if report.addClassCount?? || report.updateClassCount?? || report.deleteClassCount?? || report.addMethodCount?? || report.updateMethodCount?? || report.deleteMethodCount?? || report.impactCaseCount??>
                            <div style="margin-top:6px;font-size:0.85em;color:#666;">
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
                                    <span class="ui mini">新增方法 ${report.addMethodCount}</span>
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
                    <td class="center aligned">
                        ${report.createTime?datetime}
                    </td>
                    <td class="center aligned">
                        <div class="ui mini label">${report.sourceVersion!'-'}</div>
                        <i class="right arrow icon"></i>
                        <div class="ui mini label">${report.targetVersion!'-'}</div>
                    </td>
                    <td class="center aligned">
                        <div style="display: flex; justify-content: center; gap: 5px;">
                            <a href="/p/${project.id}/version/report/${report.id}" class="ui mini basic blue button">查看</a>
                            <button class="ui mini basic red button" onclick="doDeleteReport('${report.id}', this)">删除</button>
                        </div>
                    </td>
                </tr>
                </#list>
                </tbody>
            </table>
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

<div id="deleteFileDialog" class="ui small modal">
    <div class="header">删除本地文件</div>
    <div class="ui negative message">
        <div class="header">
            确定要删除本地服务器上的这个文件吗？
        </div>
        <p>删除之后将无法恢复，且下拉列表中该选项也将消失。</p>
    </div>

    <div class="actions">
        <button id="confirmDeleteFileButton" class="ui negative button">确定删除</button>
        <div class="ui cancel button">取消</div>
    </div>
</div>

<script>
    // 初始化下拉菜单并设置持久化
    $(function() {
        var appId = "${app.id}";
        var storageKeySource = "oAT_compare_source_" + appId;
        var storageKeyTarget = "oAT_compare_target_" + appId;
        var storageKeyPkg = "oAT_compare_pkg_" + appId;
        var storageKeyAllFiles = "oAT_uploaded_files_" + appId;

        // 绑定删除事件的辅助函数
        function bindDeleteItemEvents($context) {
            $context.find('.delete-item').off('click').on('click', function(e) {
                var filePath = $(this).closest('.item').data('value');
                doDeleteFile(filePath, e, this);
            });
        }

        $('.uploaded-files').dropdown({
            onChange: function(value, text, $choice) {
                var id = $(this).attr('id');
                if (value) {
                    if (id === 'sourceFileDropdown') {
                        localStorage.setItem(storageKeySource, value);
                        localStorage.setItem(storageKeySource + "_text", $choice.data('name') || text);
                    } else if (id === 'targetFileDropdown') {
                        localStorage.setItem(storageKeyTarget, value);
                        localStorage.setItem(storageKeyTarget + "_text", $choice.data('name') || text);
                    }
                }
            }
        });

        // 恢复所有手动上传的文件到下拉列表
        var allUploadedFiles = JSON.parse(localStorage.getItem(storageKeyAllFiles) || "[]");
        if (allUploadedFiles.length > 0) {
            var extraItemsHtml = '';
            allUploadedFiles.forEach(function(f) {
                // 仅当菜单中不存在时添加（避免与后端返回的重复）
                if ($('.uploaded-files .menu .item[data-value="' + f.path + '"]').length === 0) {
                    extraItemsHtml += '<div class="item" data-value="' + f.path + '" data-name="' + f.name + '">' +
                        '<i class="file alternate outline icon"></i>' +
                        '<span class="text">' + f.name + '</span>' +
                        '<i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;"></i>' +
                        '</div>';
                }
            });
            if (extraItemsHtml) {
                $('.uploaded-files .menu').prepend(extraItemsHtml);
                $('.uploaded-files').dropdown('refresh');
                bindDeleteItemEvents($('.uploaded-files .menu'));
            }
        } else {
            // 对现有的项也要绑定一次删除事件
            bindDeleteItemEvents($('.uploaded-files .menu'));
        }

        // 恢复持久化数据
        var savedSource = localStorage.getItem(storageKeySource);
        var savedSourceText = localStorage.getItem(storageKeySource + "_text");
        if (savedSource) {
            $('#sourceFileDropdown').dropdown('set selected', savedSource);
            if (savedSourceText) $('#sourceFileDropdown').dropdown('set text', savedSourceText);
        }

        var savedTarget = localStorage.getItem(storageKeyTarget);
        var savedTargetText = localStorage.getItem(storageKeyTarget + "_text");
        if (savedTarget) {
            $('#targetFileDropdown').dropdown('set selected', savedTarget);
            if (savedTargetText) $('#targetFileDropdown').dropdown('set text', savedTargetText);
        }

        var savedPkg = localStorage.getItem(storageKeyPkg);
        if (savedPkg) {
            $('input[name="packageName"]').val(savedPkg);
        }

        $('input[name="packageName"]').on('input', function() {
            localStorage.setItem(storageKeyPkg, $(this).val());
        });
    });

    // 处理删除下拉项中的文件
    function doDeleteFile(filePath, event, elem) {
        if (event) event.stopPropagation();
        if (!filePath) return;

        // 存储待删除路径到 Modal 确认按钮
        $("#confirmDeleteFileButton").data('filePath', filePath);
        // 显示 Modal 代替原生 confirm
        $("#deleteFileDialog").modal('show');
    }

    $(function() {
        // 绑定删除文件确认按钮点击事件
        $("#confirmDeleteFileButton").on('click', function() {
            var filePath = $(this).data('filePath');
            if (!filePath) return;

            $.post('/p/${project.id}/${appId}/version/file/delete', {filePath: filePath}, function(res) {
                if (res.success) {
                    showToast('本地文件已删除', 'success');
                    // 隐藏 Modal
                    $("#deleteFileDialog").modal('hide');

                    // 从左右下拉菜单中移除
                    $('.uploaded-files .menu .item[data-value="' + filePath + '"]').remove();

                    // 同步清理 localStorage 中的上传记录
                    var appId = "${app.id}";
                    var storageKeyAllFiles = "oAT_uploaded_files_" + appId;
                    var allUploadedFiles = JSON.parse(localStorage.getItem(storageKeyAllFiles) || "[]");
                    allUploadedFiles = allUploadedFiles.filter(function(f) { return f.path !== filePath; });
                    localStorage.setItem(storageKeyAllFiles, JSON.stringify(allUploadedFiles));

                    // 如果当前选中了该文件，重置下拉框
                    $('.uploaded-files').each(function() {
                        if ($(this).dropdown('get value') === filePath) {
                            $(this).dropdown('clear');
                            var id = $(this).attr('id');
                            var appId = "${app.id}";
                            if (id === 'sourceFileDropdown') {
                                localStorage.removeItem("oAT_compare_source_" + appId);
                                localStorage.removeItem("oAT_compare_source_" + appId + "_text");
                            } else {
                                localStorage.removeItem("oAT_compare_target_" + appId);
                                localStorage.removeItem("oAT_compare_target_" + appId + "_text");
                            }
                        }
                    });
                } else {
                    showToast('删除失败: ' + res.message, 'error');
                }
            });
        });
    });

    $(".upload.file input:file").change(function () {
        var $field = $(this).closest('.field');
        var dropdown = $field.find(".uploaded-files");
        var valueItem = $field.find("input:hidden");
        var progressItem = $field.find("div.progress");
        var fileInfo = $field.find(".file-info");
        var fileNameSpan = fileInfo.find(".file-name");

        var file = this.files[0];
        if (!file) return;

        uploadFile(file, "/resource/upload"
                , function (ev2) {
                    var results = eval("(" + this.responseText + ")");
                    if (results.success) {
                        var filePath = results.data;
                        progressItem.progress('complete');

                        // 记录到 localStorage 以便刷新后恢复
                        var appId = "${app.id}";
                        var storageKeyAllFiles = "oAT_uploaded_files_" + appId;
                        var allUploadedFiles = JSON.parse(localStorage.getItem(storageKeyAllFiles) || "[]");
                        // 查重
                        if (!allUploadedFiles.some(function(f) { return f.path === filePath; })) {
                            allUploadedFiles.push({path: filePath, name: file.name});
                            localStorage.setItem(storageKeyAllFiles, JSON.stringify(allUploadedFiles));
                        }

                        // 动态添加到下拉菜单
                        var newItemHtml = '<div class="item" data-value="' + filePath + '" data-name="' + file.name + '">' +
                            '<i class="file alternate outline icon"></i>' +
                            '<span class="text">' + file.name + '</span>' +
                            '<i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;"></i>' +
                            '</div>';

                        // 如果已存在相同路径的项，先移除
                        $('.uploaded-files .menu .item[data-value="' + filePath + '"]').remove();

                        // 两个下拉框都添加，确保同步
                        $('.uploaded-files .menu').prepend(newItemHtml);

                        // 重新绑定删除事件（因为 prepend 的是字符串）
                        $('.uploaded-files .menu .item[data-value="' + filePath + '"] .delete-item').off('click').on('click', function(e) {
                            doDeleteFile(filePath, e, this);
                        });

                        // 选中新上传的文件
                        $('.uploaded-files').dropdown('refresh');
                        dropdown.dropdown('set selected', filePath);
                        dropdown.dropdown('set text', file.name);

                        showToast('文件上传成功', 'success');
                    } else {
                        showToast('上传失败: ' + results.message, 'error');
                        progressItem.progress('reset');
                    }
                }
                , function (evt) {
                    // 进度条
                    progressItem.progress('set progress', evt.loaded * 100 / evt.total);
                });

    });

    // 修改现有的删除逻辑，兼容下拉框
    $(".delete-file").off('click').click(function() {
        var $field = $(this).closest('.field');
        var dropdown = $field.find(".uploaded-files");
        var filePath = dropdown.dropdown('get value');
        doDeleteFile(filePath);
    });

    $('.ui.click.dropdown').dropdown({
        on: 'click'
    });
    // tab init
    $('.menu .item').tab();
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });

    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }

    function doDeleteReport(id, elem) {
        // 如果传入 elem，则记录该行用于局部刷新
        var row = elem ? $(elem).closest('tr') : null;
        // 存储待删除 id 与对应行到按钮 data 属性（避免直接使用 href 导致跳转）
        $("#deleteReportButton").data('reportId', id).data('reportRow', row).removeAttr('href');
        // 显示删除对话框
        $("#deleteVersionDialog").modal('show');
    }

    // 使用 AJAX POST 删除并优先进行局部刷新
    $(function() {
        $("#deleteReportButton").on('click', function(e) {
            // 阻止默认链接行为
            e.preventDefault();
            var id = $(this).data('reportId');
            if (!id) return;
            var row = $(this).data('reportRow');
            $.post('/p/${project.id}/${appId}/version/report/delete', {reportId: id}, function(res) {
                // Support both `success` (new) and legacy `result` (old)
                var ok = res && (res.success === true || res.result === true);
                if (ok) {
                    // 显示成功 toast
                    showToast(res.message || '删除成功', 'success');
                    // 优先进行局部移除行操作
                    if (row && row.length) {
                        row.remove();
                        // 隐藏 modal
                        $("#deleteVersionDialog").modal('hide');
                        // 如果表格已空，替换为占位提示
                        if ($("table.ui.fixed.selectable.table.celled tbody tr").length === 0) {
                            var placeholder = '<div class="ui placeholder segment">'
                                + '<div class="ui icon header">'
                                + '<i class="law icon"></i>'
                                + '暂无代码比对记录</div>'
                                + '<a href="/p/${project.id}/${appId}/version/compare" class="ui primary button">前往发起比对</a>'
                                + '</div>';
                            $("table.ui.fixed.selectable.table.celled").parent().html(placeholder);
                        }
                    } else {
                        // 如果无法定位到行（例如从其它页面触发），则跳转回列表页以刷新
                        setTimeout(function() {
                            window.location.href = '/p/${project.id}/${appId}/version/report/list?tab=compare';
                        }, 700);
                    }
                } else {
                    showToast('删除失败: ' + (res ? (res.message || res.errorMessage || '未知错误') : '未知错误'), 'error');
                }
            }).fail(function() {
                showToast('删除请求发送失败', 'error');
            });
        });
    });
</script>
</body>
</html>

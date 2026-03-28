<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-报告列表</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
    <span class="divider">/</span>
    <a class=" section" href="/p/${project.id}/${appId}/version/list">${appInfo.name}</a>
    <span class="divider">/</span>
    <div class="active section">报告列表</div>
</div>

<!--内容主体-->
<div class="ui grid attached container" style="margin-top: 14px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical menu">
            <div class="header item">版本中心</div>
            <a class="item" href="/p/${project.id}/${appId}/version/list">
                版本列表
            </a>
            <a class="item active" href="/p/${project.id}/${appId}/version/report/list">
                报告列表
            </a>
            <a class="teal teal item" href="/p/${project.id}/${appId}/version/compare">
                <i class="law icon"></i>版本比对
            </a>
        </div>
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
                                <th class="four wide">版本号 / 描述</th>
                                <th class="two wide center aligned">报告类型</th>
                                <th class="four wide center aligned">代码 Commit</th>
                                <th class="three wide center aligned">生成时间</th>
                                <th class="three wide center aligned">操作</th>
                            </tr>
                            </thead>
                            <tbody>
                            <#list generatedReports as gReport>
                                <tr>
                                    <td>
                                        <b>${gReport.versionNumber}</b>
                                        <#if gReport.reportType == 0>
                                            <#if gReport.repoBranch?has_content>(分支: ${gReport.repoBranch})</#if>
                                        <#else>
                                            <#if gReport.baseVersionNumber?has_content>(基于: ${gReport.baseVersionNumber})</#if>
                                        </#if>
                                        <#if reportNeedRegenerateMap?? && reportNeedRegenerateMap[gReport.id]?? && reportNeedRegenerateMap[gReport.id]>
                                            <div style="margin-top: 6px;"><span class="ui mini red label">需重生成</span></div>
                                        </#if>
                                    </td>
                                    <td class="center aligned">
                                        <#if gReport.reportType == 0>
                                            <div class="ui green horizontal label">全量</div>
                                        <#else>
                                            <div class="ui orange horizontal label">增量</div>
                                        </#if>
                                    </td>
                                    <td class="center aligned"><i
                                                class="code icon"></i> ${(gReport.repoCommitId?substring(0,7))!'-'}</td>
                                    <td class="center aligned">${(gReport.createTime?string("yyyy-MM-dd HH:mm"))!'-'}</td>
                                    <td class="center aligned">
                                        <a href="/p/${project.id}/coverage/overview?appId=${appInfo.id}&versionNumber=${gReport.versionNumber}&reportId=${gReport.id}"
                                           class="ui mini basic blue button" style="margin-right: 5px;">查看</a>
                                        <#if reportNeedRegenerateMap?? && reportNeedRegenerateMap[gReport.id]?? && reportNeedRegenerateMap[gReport.id]>
                                            <a href="/p/${project.id}/coverage/overview?appId=${appInfo.id}&versionNumber=${gReport.versionNumber}&reportId=${gReport.id}"
                                               class="ui mini orange button" style="margin-right: 5px;">去重生成</a>
                                        </#if>
                                        <button class="ui mini basic red button"
                                                onclick="confirmDeleteGeneratedReport('${gReport.id}', this)">删除
                                        </button>
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
                    <span style="float: right; margin-top: -5px;">
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
                            <th class="four wide">评论</th>
                            <th class="three wide">创建时间</th>
                            <th class="one wide center aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list snapshots as snap>
                            <tr>
                                <td class="ellipsis-tooltip" title="${snap.title!}"><b>${snap.title!}</b></td>
                                <td>
                                    <i class="user icon"></i>
                                    <#if snap.principals?? && snap.principals?size gt 0>
                                        <#assign userId = snap.principals[0]>
                                        <#if userMap?? && userMap[userId]??>
                                            ${userMap[userId].name}(${userId})
                                        <#else>
                                            ${userId}
                                        </#if>
                                    <#else>
                                        -
                                    </#if>
                                </td>
                                <td>
                                    <#if snap.labels??>
                                        <#list snap.labels as label>
                                            <div class="ui mini label">${label}</div>
                                        </#list>
                                    </#if>
                                </td>
                                <td>${snap.createTime!'-'}</td>
                                <td class="center aligned">
                                    <a href="/p/${project.id}/${appId}/snapshot/detail/${snap.id}" target="_blank">
                                        <i class="external alternate blue link icon" title="跳转到快照"></i>
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

                <div id="snapshotsReportContainer" style="display: none; margin-top: 20px;">
                    <div class="ui segment" style="min-height: 200px;">
                        <div class="ui active inverted dimmer">
                            <div class="ui text loader">报告加载中...</div>
                        </div>
                        <div id="reportContent"></div>
                    </div>
                </div>
            <#elseif tab == 'compare'>
                <h4 class="ui dividing header">代码比对报告记录</h4>
                <#if reports?? && (reports?size > 0)>
                    <table class="ui fixed selectable table celled">
                        <thead>
                        <tr>
                            <th class="five wide">报告名称</th>
                            <th class="four wide center aligned">生成时间</th>
                            <th class="four wide center aligned">比对版本 (新 / 旧)</th>
                            <th class="three wide center aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list reports as report>
                            <tr>
                                <td class="ellipsis-tooltip" title="${report.name!}"><b>${report.name!}</b>
                                    <#-- 显示统计子信息（如果 report 对象包含统计字段） -->
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
                                    <div class="ui mini label">${report.sourceVersion!'-'}</div>
                                    <i class="right arrow icon"></i>
                                    <div class="ui mini label">${report.targetVersion!'-'}</div>
                                    <#-- 显示 Git 元信息（分支、提交短码） -->
                                    <div style="margin-top:6px; font-size:0.9em; color:#666;">
                                        <#if report.gitBranch?has_content>
                                            分支: <b>${report.gitBranch}</b>
                                        </#if>
                                        <#if report.gitOldCommit?has_content>
                                            &nbsp; 旧: <code>${(report.gitOldCommit?substring(0,7))!report.gitOldCommit}</code>
                                        </#if>
                                        <#if report.gitNewCommit?has_content>
                                            &nbsp; 新: <code>${(report.gitNewCommit?substring(0,7))!report.gitNewCommit}</code>
                                        </#if>
                                    </div>
                                 </td>
                                <td class="center aligned">
                                    <div style="display: flex; justify-content: center; gap: 5px;">
                                        <a href="/p/${project.id}/version/report/${report.id}"
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
                <#else>
                    <div class="ui placeholder segment">
                        <div class="ui icon header">
                            <i class="law icon"></i>
                            暂无代码比对记录
                        </div>
                        <a href="/p/${project.id}/${appId}/version/compare" class="ui primary button">前往发起比对</a>
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
                    // 如果表格已空，替换为占位提示
                    if($("table.ui.fixed.selectable.table.celled tbody tr").length === 0) {
                        var placeholder = '<div class="ui placeholder segment">'
                            + '<div class="ui icon header">'
                            + '<i class="law icon"></i>'
                            + '暂无代码比对记录</div>'
                            + '<a href="/p/${project.id}/${appId}/version/compare" class="ui primary button">前往发起比对</a>'
                            + '</div>';
                        $("table.ui.fixed.selectable.table.celled").parent().html(placeholder);
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

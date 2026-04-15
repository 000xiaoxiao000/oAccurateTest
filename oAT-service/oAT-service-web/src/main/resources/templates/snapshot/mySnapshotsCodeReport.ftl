<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>覆盖率报告</title>
    <#include "../common.ftl">
    <link href="/css/font-awesome.min.css" rel="stylesheet">
    <style>
        body { padding: 20px; background-color: #f4f7f6; }
        .stat-value { font-size: 24px; font-weight: bold; color: #21ba45; }
        .card .content .header { margin-bottom: 10px; }
        .ui.progress { margin-top: 5px; margin-bottom: 5px; }
        .table-progress { margin: 0 !important; }
        .interface-name { font-family: monospace; color: #4183c4; font-weight: bold; word-break: break-all; }
    </style>
</head>
<body>

<div class="ui breadcrumb" style="margin-bottom: 20px;">
    <#if fromVersionCenter?? && fromVersionCenter>
        <a class="section" href="/p/${projectId}/version/apps">版本中心</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${projectId}/${appId}/version/report/list">报告列表</a>
    <#elseif fromSystemSnapshot?? && fromSystemSnapshot>
        <a class="section" href="/p/${projectId}/${appId}/snapshot/list">系统快照</a>
        <i class="right angle icon divider"></i>
        <a class="section" href="/p/${projectId}/${appId}/snapshot/detail/${snapshotId}">快照详情</a>
    <#else>
        <a class="section" href="/p/${projectId}/snapshot/my">我的快照</a>
    </#if>
    <i class="right angle icon divider"></i>
    <div class="active section">覆盖率报告</div>
</div>

<#if report??>
    <div class="ui segment" style="background: #f9f9f9; border-top: 3px solid #21ba45;">
        <h3 class="ui dividing header">快照概览</h3>
        <div class="ui four cards">
            <#-- Summary cards already exist in previous code, ensure they fit well -->
            <#-- Class Coverage -->
            <div class="card">
                <div class="content">
                    <div class="header center aligned">触达类覆盖</div>
                    <div class="description center aligned">
                        <div class="stat-value">${report.coveredClasses} / ${report.totalClasses}</div>
                        <div class="ui green progress" data-percent="100"><div class="bar" style="width: 100%"></div></div>
                        <div class="label" style="font-size: 0.9em; color: gray;">涉及类总数</div>
                    </div>
                </div>
            </div>
            <#-- Method Coverage -->
            <div class="card">
                <div class="content">
                    <div class="header center aligned">方法覆盖率</div>
                    <div class="description center aligned">
                        <#assign methodPct = (report.totalMethods > 0)?then(report.coveredMethods * 100.0 / report.totalMethods, 0)>
                        <div class="stat-value">${report.coveredMethods} / ${report.totalMethods}</div>
                        <div class="ui blue progress" data-percent="${methodPct?string("0")}">
                            <div class="bar" style="width: ${methodPct?string("0.00")}%"></div>
                        </div>
                        <div class="label" style="font-size: 0.9em; color: gray;">${methodPct?string("0.00")}%</div>
                    </div>
                </div>
            </div>
            <#-- Branch Coverage -->
            <div class="card">
                <div class="content">
                    <div class="header center aligned">分支覆盖率</div>
                    <div class="description center aligned">
                        <#assign branchPct = (report.totalBranchTargets > 0)?then(report.coveredBranchTargets * 100.0 / report.totalBranchTargets, 0)>
                        <div class="stat-value">${report.coveredBranchTargets} / ${report.totalBranchTargets}</div>
                        <div class="ui orange progress" data-percent="${branchPct?string("0")}">
                            <div class="bar" style="width: ${branchPct?string("0.00")}%"></div>
                        </div>
                        <div class="label" style="font-size: 0.9em; color: gray;">${branchPct?string("0.00")}%</div>
                    </div>
                </div>
            </div>
            <#-- Line Coverage -->
            <div class="card">
                <div class="content">
                    <div class="header center aligned">代码行覆盖率</div>
                    <div class="description center aligned">
                        <#assign linePct = (report.totalLines > 0)?then(report.coveredLines * 100.0 / report.totalLines, 0)>
                        <div class="stat-value">${report.coveredLines} / ${report.totalLines}</div>
                        <div class="ui teal progress" data-percent="${linePct?string("0")}">
                            <div class="bar" style="width: ${linePct?string("0.00")}%"></div>
                        </div>
                        <div class="label" style="font-size: 0.9em; color: gray;">${linePct?string("0.00")}%</div>
                    </div>
                </div>
            </div>
        </div>
        <div class="ui segment center aligned basic" style="margin-top: 10px; padding: 0;">
            <div class="ui label large green basic">总圈复杂度: ${report.totalComplexity}</div>
        </div>
    </div>
</#if>

<!-- View Switcher -->
<div class="ui secondary pointing menu" style="margin-top: 30px;">
    <a class="active item" data-tab="interfaceDetail">
        <i class="sitemap icon"></i> 接口/地址维度
    </a>
    <a class="item" data-tab="classDetail">
        <i class="list icon"></i> 类级维度 (全量样式)
    </a>
</div>

<div class="ui active tab" data-tab="interfaceDetail">
    <#if codeRelationships??>
        <table class="ui celled selectable table">
            <thead style="background: #fdfdfd;">
                <tr>
                    <th style="width:40%;">接口 / 请求地址</th>
                    <th class="center aligned">涉及方法</th>
                    <th class="center aligned">覆盖进度 (代码行)</th>
                    <th class="center aligned">覆盖率</th>
                    <th class="center aligned">操作</th>
                </tr>
            </thead>
            <tbody>
            <#list codeRelationships as key, value>
                <#assign sumMethods = 0>
                <#assign sumLines = 0>
                <#assign sumDoLines = 0>
                <#list codeRelationships[key]?values as lists>
                    <#list lists as list>
                        <#if !(list.doLines?seq_contains(-1)) && list.lineTotal?? && list.lineTotal?size gt 0>
                            <#assign sumMethods = sumMethods + 1>
                            <#assign sumLines = sumLines + list.lineTotal?size>
                            <#assign sumDoLines = sumDoLines + list.doLines?size>
                        </#if>
                    </#list>
                </#list>
                <#assign rowPct = (sumLines > 0)?then(sumDoLines * 100.0 / sumLines, 0)>
                <tr>
                    <td class="interface-name">${key!}</td>
                    <td class="center aligned">${sumMethods}</td>
                    <td>
                        <div class="ui progress table-progress tiny ${(rowPct == 100)?then('success', (rowPct > 0)?then('blue', ''))}" data-percent="${rowPct?string("0")}">
                            <div class="bar" style="width: ${rowPct?string("0.00")}%"></div>
                        </div>
                    </td>
                    <td class="center aligned <#if rowPct == 100>positive<#elseif rowPct gt 0>warning</#if>">
                        <b>${rowPct?string("0.00")}%</b>
                    </td>
                    <td class="center aligned">
                        <button class="ui mini compact basic button" onclick="toggleInterfaceDetail('intf_${key_index}')">明细</button>
                    </td>
                </tr>
                <tr id="intf_${key_index}" style="display: none; background: #fcfcfc;">
                    <td colspan="5">
                        <div style="padding: 10px 20px;">
                            <h5 class="ui header gray">该接口触发的方法列表</h5>
                            <table class="ui very compact small table">
                                <thead>
                                    <tr>
                                        <th>类名</th>
                                        <th>方法名</th>
                                        <th class="center aligned">代码行覆盖率</th>
                                        <th class="center aligned">分支覆盖率</th>
                                        <th class="center aligned">覆盖状态</th>
                                        <th class="center aligned">操作</th>
                                    </tr>
                                </thead>
                                <tbody>
                                <#list codeRelationships[key]?values as lists>
                                    <#list lists as list>
                                        <#if !(list.doLines?seq_contains(-1)) && list.lineTotal?? && list.lineTotal?size gt 0>
                                            <#assign mPct = (list.lineTotal?size > 0)?then(list.doLines?size * 100.0 / list.lineTotal?size, 0)>
                                            <#assign methodBranchTotal = list.branchTotalCount!0>
                                            <#assign methodBranchCovered = list.branchCoveredCount!0>
                                            <#assign methodBranchPct = (methodBranchTotal > 0)?then(methodBranchCovered * 100.0 / methodBranchTotal, 0)>
                                            <tr>
                                                <td title="${list.className!}">${list.className?keep_after_last(".")}</td>
                                                <td>${list.methodName!}</td>
                                                <td class="center aligned">${list.doLines?size} / ${list.lineTotal?size} (${mPct?string("0.00")}%)</td>
                                                <td class="center aligned">
                                                    <#if methodBranchTotal gt 0>${methodBranchCovered} / ${methodBranchTotal} (${methodBranchPct?string("0.00")}%)<#else>N/A</#if>
                                                </td>
                                                <td class="center aligned">
                                                    <#if mPct == 100 && (methodBranchTotal == 0 || methodBranchPct == 100)><div class="ui mini green empty circular label" title="全覆盖"></div>
                                                    <#elseif mPct gt 0 || methodBranchPct gt 0><div class="ui mini orange empty circular label" title="部分覆盖"></div>
                                                    <#else><div class="ui mini empty circular label" title="未覆盖"></div></#if>
                                                </td>
                                                <td class="center aligned">
                                                    <#if fromSystemSnapshot?? && fromSystemSnapshot>
                                                        <a href="/p/${projectId}/${appId}/snapshot/report/code?snapshotId=${snapshotId!}&className=${list.className!}" target="_blank" class="ui mini compact basic blue button">源码</a>
                                                    <#else>
                                                        <a href="/p/${projectId}/snapshot/my/code?appId=${appId!}&className=${list.className!}" target="_blank" class="ui mini compact basic blue button">源码</a>
                                                    </#if>
                                                </td>
                                            </tr>
                                        </#if>
                                    </#list>
                                </#list>
                                </tbody>
                            </table>
                        </div>
                    </td>
                </tr>
            </#list>
            </tbody>
        </table>
    </#if>
</div>

<div class="ui tab" data-tab="classDetail">
    <table class="ui celled selectable table striped">
        <thead>
            <tr>
                <th>类名</th>
                <th class="center aligned">方法 (覆盖/总)</th>
                <th class="center aligned">方法覆盖率</th>
                <th class="center aligned">分支 (覆盖/总)</th>
                <th class="center aligned">分支覆盖率</th>
                <th class="center aligned">代码行 (覆盖/总)</th>
                <th class="center aligned">代码行覆盖率</th>
                <th class="center aligned" style="width: 80px;">圈复杂度</th>
                <th class="center aligned">操作</th>
            </tr>
        </thead>
        <tbody>
            <#list classStats as item>
            <tr>
                <td title="${item.className}">${item.className}</td>
                <td class="center aligned">${item.coveredMethods} / ${item.totalMethods}</td>
                <#assign mPct = (item.totalMethods > 0)?then(item.coveredMethods * 100.0 / item.totalMethods, 0)>
                <td class="center aligned <#if mPct gt 0>positive<#else>negative</#if>">
                    <b>${mPct?string("0.00")}%</b>
                </td>
                <td class="center aligned">${item.coveredBranches!0} / ${item.totalBranches!0}</td>
                <#assign bPct = item.branchRate!0>
                <td class="center aligned <#if bPct gt 0>positive<#elseif (item.totalBranches!0) gt 0>negative</#if>">
                    <#if ((item.totalBranches!0) > 0)><b>${bPct?string("0.00")}%</b><#else>N/A</#if>
                </td>
                <td class="center aligned">${item.coveredLines} / ${item.totalLines}</td>
                <#assign clPct = (item.totalLines > 0)?then(item.coveredLines * 100.0 / item.totalLines, 0)>
                <td class="center aligned <#if clPct gt 0>positive<#else>negative</#if>">
                    <b>${clPct?string("0.00")}%</b>
                </td>
                <td class="center aligned">${item.totalComplexity}</td>
                <td class="center aligned">
                    <#if fromSystemSnapshot?? && fromSystemSnapshot>
                        <a href="/p/${projectId}/${appId}/snapshot/report/code?snapshotId=${snapshotId!}&className=${item.className!}" target="_blank" class="ui mini compact basic blue button">源码</a>
                    <#else>
                         <a href="/p/${projectId}/snapshot/my/code?appId=${appId!}&className=${item.className!}" target="_blank" class="ui mini compact basic blue button">源码</a>
                    </#if>
                </td>
            </tr>
            </#list>
        </tbody>
    </table>
</div>

<script>
    $('.menu .item').tab();

    function toggleInterfaceDetail(id) {
        $('#' + id).toggle();
    }

    function showTable(idKey) {
        document.getElementById(idKey).style.display = "";
    }

    function hiddenTable(idKey) {
        document.getElementById(idKey).style.display = "none";
    }


    $(function () {
        // 鼠标悬浮事件
        $('.ellipsis-tooltip').hover(function (e) {
            // 获取完整内容
            var fullText = $(this).attr('custom-title');
            // 创建 tooltip 元素
            var tooltip = $('<div class="tooltip-content">' + fullText + '</div>');
            // 将 tooltip 元素添加到 body 中
            $('body').append(tooltip);
            // 设置 tooltip 元素位置（相对于鼠标位置）
            tooltip.css({
                'top': e.pageY + 10,
                'left': e.pageX + 10
            });
            // 显示 tooltip 元素
            tooltip.show();
        }, function () {
            // 鼠标移出事件，隐藏 tooltip 元素
            $('.tooltip-content').remove();
        });

        // 点击事件
        $('.ellipsis-tooltip').click(function () {
            // 获取完整内容
            var fullText = $(this).attr('custom-title');
            // 创建 textarea 元素
            var textarea = $('<textarea class="temp-textarea">' + fullText + '</textarea>');
            // 将 textarea 元素添加到 body 中
            $('body').append(textarea);
            // 选中 textarea 中的内容
            textarea.select();
            // 执行复制命令
            document.execCommand('copy');
            // 移除 textarea 元素
            textarea.remove();
            // 弹出复制成功提示框
            // alert('内容已复制');

            // 绑定显示提示框的事件
            $('#modalAlert').modal('show');
        });
    });

</script>

<div id="modalAlert" class="ui success message" style="display: none;">
    <i class="close icon"></i>
    <p>内容已复制</p>
</div>

</body>
</html>

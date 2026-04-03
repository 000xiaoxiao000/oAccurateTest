<!DOCTYPE html>
<html lang="en">
<head>
    <title>我的历史快照 Source View - ${className}</title>
    <#include "../common.ftl">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; padding: 20px; line-height: 1.5; }
        .source-container { border: 1px solid #ddd; padding: 10px; border-radius: 5px; background: #fff; max-width: 100%; overflow-x: auto; overflow-y: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .method-list { margin-bottom: 25px; }
        .ui.progress { margin: 0; min-width: 80px; }
        .method-table td { vertical-align: middle !important; }
        .method-table .method-name { font-weight: 600; color: #1e70bf; }
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
        }
        #backToTop:hover { background-color: #1678c2; }
        .source-container pre { margin: 0; font-size: 13px; line-height: 18px; min-width: 100%; }
        .source-container pre > div { min-width: max-content; }
    </style>
</head>
<body>
<div class="ui container" style="width: 95%;">
    <div class="ui breadcrumb" style="margin-bottom: 20px;">
        <a class="section" href="/p/${project.id}/snapshot/my">我的快照</a>
        <i class="right angle icon divider"></i>
        <div class="active section">类源码: ${className}</div>
    </div>

    <div class="ui info message">
        <i class="info circle icon"></i>
        这是基于您当前“我的快照”列表中所有快照的覆盖情况。源码匹配至该应用的最新版本
        <#if lastVersion??>
            (<b>${lastVersion.versionNumber!}</b> <#if lastVersion.repoBranch??>- 分支: ${lastVersion.repoBranch}</#if> <#if lastVersion.repoCommitId??>- Commit: ${lastVersion.repoCommitId?substring(0,7)}</#if>)
        <#else>
            (未找到版本记录)
        </#if>。
    </div>

    <div class="method-list">
        <h4 class="ui header">
            <i class="list icon"></i>
            <span class="content">方法覆盖列表 <#if classCov?? && classCov.methods??>(共 ${classCov.methods?size} 个)</#if></span>
        </h4>

        <table class="ui compact basic celled table method-table" id="methodTable">
            <thead>
                <tr>
                    <th>方法名称</th>
                    <th class="center aligned">代码行覆盖率</th>
                    <th class="center aligned">跳转</th>
                    <th class="center aligned">覆盖状态</th>
                </tr>
            </thead>
            <tbody>
            <#if classCov?? && classCov.methods??>
            <#list classCov.methods as m>
                <#assign linePct = (m.totalLines > 0)?then(m.coveredLines * 1.0 / m.totalLines * 100, 0)>
                <tr>
                    <td class="method-name">${m.methodName}</td>
                    <td>
                        <div class="ui tiny progress ${(linePct == 100)?then('success', (linePct > 0)?then('warning', ''))}" data-percent="${linePct?string("0")}">
                            <div class="bar" style="width: ${linePct?string("0.00")}%"></div>
                            <div class="label" style="font-size: 0.85em;">${m.coveredLines} / ${m.totalLines} (${linePct?string("0.00")}%)</div>
                        </div>
                    </td>
                    <td class="center aligned">
                        <a href="#method_${m_index}" class="ui mini compact basic blue button">查看代码</a>
                    </td>
                    <td class="center aligned">
                        <#if linePct == 100>
                            <div class="ui tiny green label">全覆盖</div>
                        <#elseif linePct gt 0>
                            <div class="ui tiny orange label">部分覆盖</div>
                        <#else>
                            <div class="ui tiny horizontal label">未覆盖</div>
                        </#if>
                    </td>
                </tr>
            </#list>
            <#else>
                <tr><td colspan="4" class="center aligned">暂无方法覆盖数据</td></tr>
            </#if>
            </tbody>
        </table>
    </div>

    <h4 class="ui header"><i class="code icon"></i> 源码视图</h4>
    <div class="source-container">
        ${coloredSource!"源码不可用"}
    </div>
</div>

<button id="backToTop" title="回到顶部"><i class="arrow up icon"></i> 回到顶部</button>

<script>
    $(document).ready(function() {
        $(window).scroll(function() {
            if ($(this).scrollTop() > 200) {
                $('#backToTop').fadeIn();
            } else {
                $('#backToTop').fadeOut();
            }
        });
        $('#backToTop').click(function() {
            $('html, body').animate({scrollTop : 0}, 400);
            return false;
        });
    });
</script>
</body>
</html>


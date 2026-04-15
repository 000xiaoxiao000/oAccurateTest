<!DOCTYPE html>
<html lang="en">
<head>
    <title>我的快照代码视图 - ${className}</title>
    <#include "../common.ftl">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; padding: 20px; line-height: 1.5; }
        .source-container { border: 1px solid #ddd; padding: 10px; border-radius: 5px; background: #fff; max-width: 100%; overflow-x: auto; overflow-y: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .branch-line { position: relative; }
        .branch-line .branch-flag {
            position: absolute;
            left: calc(var(--line-number-width, 3em) + 6px);
            top: 50%;
            transform: translateY(-50%);
            width: 9px;
            height: 9px;
            border-radius: 50%;
            background: #1e88e5;
            box-shadow: 0 0 0 2px rgba(30,136,229,0.18);
            pointer-events: auto;
            z-index: 2;
            transition: transform .12s ease, box-shadow .12s ease, opacity .12s ease;
        }
        .branch-line .branch-flag:hover {
            transform: translateY(-50%) scale(1.2);
            box-shadow: 0 0 0 3px rgba(30,136,229,0.26), 0 0 10px rgba(30,136,229,0.28);
        }
        .branch-line.branch-green .branch-flag {
            background: #2e7d32;
            box-shadow: 0 0 0 2px rgba(46,125,50,0.18);
        }
        .branch-line.branch-green .branch-flag:hover {
            box-shadow: 0 0 0 3px rgba(46,125,50,0.26), 0 0 10px rgba(46,125,50,0.26);
        }
        .branch-line.branch-orange .branch-flag {
            background: #ef6c00;
            box-shadow: 0 0 0 2px rgba(239,108,0,0.18);
        }
        .branch-line.branch-orange .branch-flag:hover {
            box-shadow: 0 0 0 3px rgba(239,108,0,0.26), 0 0 10px rgba(239,108,0,0.26);
        }
        .branch-line.branch-red .branch-flag {
            background: #c62828;
            box-shadow: 0 0 0 2px rgba(198,40,40,0.18);
        }
        .branch-line.branch-red .branch-flag:hover {
            box-shadow: 0 0 0 3px rgba(198,40,40,0.26), 0 0 10px rgba(198,40,40,0.26);
        }
        .branch-line::after {
            content: '';
            display: none;
        }
        .branch-line .branch-flag:hover + .branch-tooltip,
        .branch-line .branch-tooltip:hover {
            opacity: 1;
        }
        .branch-tooltip {
            position: absolute;
            left: calc(var(--line-number-width, 3em) + 24px);
            top: 50%;
            transform: translateY(-50%);
            background: rgba(20, 24, 33, 0.96);
            color: #f7f7f2;
            border: 1px solid rgba(255,255,255,0.12);
            border-radius: 6px;
            padding: 8px 10px;
            font-size: 12px;
            line-height: 1.45;
            white-space: pre-wrap;
            min-width: 220px;
            max-width: 420px;
            box-shadow: 0 10px 24px rgba(0,0,0,0.18);
            opacity: 0;
            pointer-events: none;
            z-index: 20;
            transition: opacity .12s ease;
        }
        .method-list { margin-bottom: 25px; }
        .ui.progress { margin: 0; min-width: 80px; }
        .method-table td { vertical-align: middle !important; }
        .method-table .method-name { font-weight: 600; color: #1e70bf; }
        .stat-txt { font-size: 0.9em; white-space: normal; }
        .col-name { width: 30%; }
        .col-pct { width: 20%; }
        .col-jump { width: 10%; }
        .col-status { width: 10%; }
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
                    <th class="col-name">方法名称</th>
                    <th class="center aligned col-pct">代码行覆盖率</th>
                    <th class="center aligned col-pct">分支覆盖率</th>
                    <th class="center aligned col-jump">跳转</th>
                    <th class="center aligned col-status">覆盖状态</th>
                </tr>
            </thead>
            <tbody>
            <#if classCov?? && classCov.methods??>
            <#list classCov.methods as m>
                <#assign linePct = (m.totalLines > 0)?then(m.coveredLines * 1.0 / m.totalLines * 100, 0)>
                <#assign branchTargetTotal = m.totalBranchTargets!0>
                <#assign branchTargetCovered = m.coveredBranchTargets!0>
                <#assign branchPct = m.branchRate!0>
                <tr>
                    <td class="method-name">${m.methodName}</td>
                    <td>
                        <span class="stat-txt">${m.coveredLines}/${m.totalLines} (${linePct?string("0.0")}%)</span>
                        <div class="ui tiny progress <#if linePct == 100>success<#elseif linePct gt 0>warning<#elseif m.totalLines gt 0>error</#if>" data-percent="${linePct}">
                            <div class="bar" style="width: ${linePct}%"></div>
                        </div>
                    </td>
                    <td>
                        <span class="stat-txt">${branchTargetCovered}/${branchTargetTotal} (${branchPct?string("0.0")}%)</span>
                        <div class="ui tiny progress <#if branchPct == 100>success<#elseif branchPct gt 0>warning<#elseif branchTargetTotal gt 0>error</#if>" data-percent="${branchPct}">
                            <div class="bar" style="width: ${branchPct}%"></div>
                        </div>
                    </td>
                    <td class="center aligned">
                        <a href="#method_${m_index}" class="ui mini compact basic blue button">查看代码</a>
                    </td>
                    <td class="center aligned">
                        <#if linePct == 100 && (branchTargetTotal == 0 || branchPct == 100)>
                            <div class="ui tiny green label">全覆盖</div>
                        <#elseif linePct gt 0 || branchPct gt 0>
                            <div class="ui tiny orange label">部分覆盖</div>
                        <#else>
                            <div class="ui tiny horizontal label">未覆盖</div>
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

    <h4 class="ui header"><i class="code icon"></i> 源码视图</h4>
    <div class="source-container">
        ${coloredSource!"源码不可用"}
    </div>
</div>

<button id="backToTop" title="回到顶部"><i class="arrow up icon"></i> 回到顶部</button>

<script>
    $(document).ready(function() {
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
    });
</script>
</body>
</html>


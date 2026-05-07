<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-图谱搜索</title>
     <#include "../common.ftl">
    <script src="/js/cytoscape.min.js"></script>
    <script src="/js/map.js?v=${.now}"></script>
</head>
<body>

<#assign searchItemActive="active"/>
<#include "../projectHeader.ftl">
<style>
    em {
        color: #b10000;
    }

    #map_body {
        position: fixed;
        top: 70px;
        left: 0px;
        right: 0px;
        bottom: 0px;
        z-index: 1;
    }

    #bottom_nodeInfo {
        position: absolute;
        left: 0px;
        bottom: 0px;
        right: 0px;
        z-index: 2;
        margin: 0px;
        padding: 7px 15px;
    }

    #find_element {
        position: absolute;
        top: 55px;
        right: 5px;
        z-index: 3;
        margin: 5px;
        padding: 10px 15px;
        display: none;
    }

    #content_menu {
        position: fixed;
        z-index: 3;
    }

    #content_tips {
        position: absolute;
        width: 500px; /*用于打开提示窗口的宽度*/
        z-index: 2;
    }

    #bottom_nodeInfo .list .item .header {
        display: inline-block;
    }

    #bottom_nodeInfo .sql-detail-list pre {
        margin: 4px 0 0 0;
        padding: 6px 8px;
        max-width: 100%;
        white-space: pre-wrap;
        word-break: break-word;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        color: #334155;
    }

    #right_toolbar {
        position: absolute;
        top: 55px;
        right: 5px;
        z-index: 2;
        margin: 5px;
        padding: 0px 5px;
    }

    #right_toolbar .item {
        padding: 5px 5px;
    }
</style>
<div class="ui container">
    <br>
    <div class="ui large category search focus">
        <div class="ui icon input" style="z-index: 2;width: 300px;">
            <input class="prompt" type="text" placeholder="搜索">
            <i class="search icon"></i>
        </div>
        <div class="results"></div>
    </div>
</div>
<div id="find_element" class="ui raised segment">
    <div class="ui icon input">
        <input type="text" placeholder="输入关键字后回车">
        <i class=" circular find link icon popup"
           data-title="匹配规则"
           data-content="前缀匹配 * 为通配符"
           data-variation="inverted"
           data-position="top center"></i>
    </div>
    <script>
        $('#find_element .popup').popup();
    </script>
    <label style="font-size: 0.9em;color: #9aa1ac;margin-left: 5px">0</label>
    <i class="link close icon" style="padding-left: 10px"></i>
</div>

<div id="right_toolbar" class="ui label">
    <#if visualAngle=="app">
          <#include "appToolbar.ftl">
    </#if>
</div>
<#--右键菜单-->
<div id="content_menu">
    <div class="ui dropdown">
         <#if visualAngle=="home">
             <#assign loginRole=loginNameRole />
            <#include "homeContextMenu.ftl">
         </#if>
    </div>
</div>
<script>
    $('#content_menu .ui.dropdown')
            .dropdown({
                duration: 0//  移除动画
            });
</script>

<div id="content_tips">
    <span class="tips" style="display: none">tip</span>
    <div class="ui wide basic popup" <#--style="min-width: 350px;"-->>
        <div class="header">Custom Header</div>
        <p class="content">The default theme's basic popup removes the pointing arrow.</p>
        <img class="ui bordered image" src="" />
    </div>
</div>

<script>
    $('#content_tips.tips').popup({
        on: 'click'
    });
</script>
<#--表结构关系图画布-->
<div id="map_body">

</div>
<div id="bottom_nodeInfo" class="ui mini segment">
    <div class="ui horizontal list">
    </div>
</div>
<script>
    function fitHomeMapView(cy) {
        if ('${visualAngle}' != 'home') {
            return;
        }
        var applyHomeFit = function () {
            cy.fit(cy.elements(), 180);
            if (cy.zoom() > 0.9) {
                cy.zoom(0.9);
                cy.center(cy.elements());
            }
        };
        cy.one('layoutstop', applyHomeFit);
        setTimeout(applyHomeFit, 0);
    }

    function fitAppMapView(cy) {
        if ('${visualAngle}' != 'app') {
            return;
        }
        var applyAppFit = function () {
            if (typeof cy.applyComfortableFit === 'function') {
                cy.applyComfortableFit(cy.elements());
            } else {
                cy.fit(cy.elements(), 150);
            }
        };
        cy.one('layoutstop', applyAppFit);
        setTimeout(applyAppFit, 0);
    }

    // 初始化画布
    fetch("${dataUrl}").then(function (res) {
        //隐藏节点信息框
        document.getElementById('bottom_nodeInfo').style.display = 'none';
        if (!res.ok) {
            return res.text().then(function (text) {
                throw new Error(text || ('图谱数据加载失败：' + res.status));
            });
        }
        return res.json();
    }).then(buildMap).then(function (cy) {
        fitHomeMapView(cy);
        fitAppMapView(cy);
        // 点击节点显示详情
        cy.on('select', 'node,edge', showDetail);
        // 设置
        if (typeof isShowUnion != 'undefined') {
            cy.settings.subSelectUnionNode = isShowUnion;
            refreshSelectedSnapshotReferenceHighlight(false);
        }
        // cy.on('mousemove', 'node', showDetail);
        // 显示右键菜单
        cy.on('cxttap', showContextMenu);
        // 设置提示框位置
        cy.on('tap cxttap', 'node', setTipPosition);
    }).catch(function (error) {
        console.error('图谱初始化失败：', error);
        if (typeof showToast === 'function') {
            showToast(error.message || '图谱初始化失败', 'error');
        }
    });

    // 显示概要信息
    function showDetail(event) {
        var _styleWidth = document.body.clientWidth / 2 + 'px';
        document.getElementById('bottom_nodeInfo').style.display = 'inline-block';
        document.getElementById('bottom_nodeInfo').style.position = 'fixed';
        document.getElementById('bottom_nodeInfo').style.width = _styleWidth;
        document.getElementById('bottom_nodeInfo').style.overflow = 'auto';

        $('#bottom_nodeInfo .list').empty();
        var ele = event.target;
        var files = new Array();
        if (ele === cy && cy.$(":selected").length == 0) {
            files[0] = {file: ele.nodes().length, value: "个节点"};
            files[1] = {file: ele.edges().length, value: "条关系"};
        } else {
            if (ele === cy) {
                ele = cy.$(":selected")[0];
            }
            var showCodeMetrics = ele.isNode && ele.isNode() && ele.hasClass('code_class') && !ele.hasClass('table');
            files[0] = {file: "id：", value: ele.id()};
            if (ele.data.hasOwnProperty('name') && ele.data('name') != null) {
                files[1] = {file: "名称：", value: ele.data('name')};
            }
            if (ele.data.hasOwnProperty('describe') && ele.data('describe') != null) {
                files[2] = {file: "描述：", value: ele.data('describe')};
            }
            if (showCodeMetrics && ele.data('doLines') != null) {
                files[3] = {file: "覆盖代码行数：", value: formatMetricValue(ele.data('doLines'))};
            }
            if (showCodeMetrics && ele.data('lineTotal') != null) {
                files[4] = {file: "总代码行数：", value: formatMetricValue(ele.data('lineTotal'))};
            }
            if (showCodeMetrics && ele.data('coverageRate') != null) {
                files[5] = {file: "代码覆盖率：", value: formatRateValue(ele.data('coverageRate')) + "%"};
            }
            if (showCodeMetrics && ele.data('cyclo') != null) {
                files[6] = {file: "圈复杂度V(G)：", value: ele.data('cyclo')};
            }
            if (showCodeMetrics && ele.data('executeMethodTotal') != null) {
                files[7] = {file: "覆盖方法数：", value: formatMetricValue(ele.data('executeMethodTotal'))};
            }
            if (showCodeMetrics && ele.data('methodTotal') != null) {
                files[8] = {file: "方法总数：", value: formatMetricValue(ele.data('methodTotal'))};
            }
            if (ele.data('sqlContents') != null) {
                files[9] = {file: ele.hasClass && ele.hasClass('snapshot') ? "执行 SQL：" : "关联 SQL：", value: formatSqlContents(ele.data('sqlContents'))};
            }
        }
        files.forEach(function (a) {
            $('#bottom_nodeInfo .list').append(
                    '<div class="item">' +
                    '            <div class="header">' + escapeHtml(a.file) +
                    '            </div>' + a.value + '</div>');
        });
    }

    function formatMetricValue(value) {
        if ($.isArray(value)) {
            return value.length === 1 ? value[0] : value.join(', ');
        }
        return value;
    }

    function formatRateValue(value) {
        var numberValue = Number(value);
        if (isNaN(numberValue)) {
            return value;
        }
        return numberValue.toFixed(2).replace(/\.00$/, '');
    }

    function formatSqlContents(value) {
        var sqls = $.isArray(value) ? value : [value];
        return '<div class="sql-detail-list">' + sqls.map(function (sql, index) {
            return '<pre>' + escapeHtml((index + 1) + '. ' + sql) + '</pre>';
        }).join('') + '</div>';
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
    }

    // 显示右键菜单
    function showContextMenu(e) {
        $("#content_menu .dropdown").dropdown('hide');
        $("#content_menu .dropdown").dropdown('clear');
        $("#content_menu").css('top', e.originalEvent.pageY - 10);
        $("#content_menu").css('left', e.originalEvent.pageX + 5);
        // 绑定选中节点 数据
        $("#content_menu .menu [targetElement]").hide();
        if (e.target === cy) {
            cy.elements(":selected").unselect();
            $("#content_menu .menu [targetElement='.context']").show();
        } else {
            //如果当前节点未选中 切换选中状态
            /*if (!e.target.selected()) {
            }*/
            cy.elements(":selected").unselect();
            e.target.select();
            $("#content_menu")[0].data = e.target.data();
            $("#content_menu .menu [targetElement]").filter(function (i, ele) {
                const select = $(ele).attr('targetElement');
                return e.target.is(select);
            }).show();
        }

        $("#content_menu .dropdown").dropdown('show');
        $("#content_menu .dropdown .menu").focus();
    }

    function setTipPosition(e) {
        // 绑定当前弹窗节点
        $("#content_tips")[0].target = e.target;
        //  移动tips 窗口位置 到当前节点
        $("#content_tips").css('top', e.originalEvent.pageY - 10);
        $("#content_tips").css('left', e.originalEvent.pageX + 5);
        $("#content_tips .popup .header").html('');
        $("#content_tips .popup .content").html('');
        $("#content_tips .popup .image").attr('src', '');
    }

    // 添加快捷键
    $(document).keydown(function (e) {
        if (e.ctrlKey && e.key.toLowerCase() == 'f') {
            e.stopPropagation();
            e.preventDefault();
            showDoFind();
        } else if (e.key.toLowerCase() == 'delete') {
            cy.nodes(":selected").remove();
        } else if (e.key.toLowerCase() == 'f2') {
            showNodeTips();
        } else if (e.ctrlKey && e.key.toLowerCase() == 'a') {
            if (e.target == $("body")[0]) {
                e.stopPropagation();
                e.preventDefault();
                cy.nodes().select();
            }
        }

    });

    function showNodeTips() {
        var node = $("#content_tips")[0].target;
        if (node != null && cy.nodes(":selected").has(node)) {
            if (node.is('.snapshot')) {
                $("#content_tips .popup .header").html(node.data('name'));
                $("#content_tips .popup .content").html(node.data('describe'));
                if(node.data('image')!=''){
                    $("#content_tips .popup .image").attr('src', "/r/" + node.data('image'));
                }else {
                    $("#content_tips .popup .image").attr('src', "/images/image.png");
                }
                $("#content_tips.tips").popup('show');
            }
        }
    }

    function showDoFind() {
        $("#find_element").show();
        $("#find_element :text").focus();
    }

    function doHiddenFind() {
        $("#find_element").toggle();
        $("#find_element :text").val("");
        $("#find_element label").html("0");
        doFind("");
    }

    // 查找定位
    $("#find_element :text").change(function (e) {
        var v = e.target.value.trim();
        doFind(v);
    });

    $("#find_element .close.icon").click(function (e) {
        doHiddenFind();
    });

    $("#find_element :text").keydown(function (e) {
        if (e.key.toLowerCase() == 'escape') {
            doHiddenFind();
        }
    });

</script>
<#--初始化 搜索框-->
<script>
    // 设置搜索中消息模板
    function noResultMessage(message, type) {
        if (type == 'empty') {
            return '<div class="message empty"><div class="header">无结果</div><div class="description">你的搜索没有返回任何结果</div></div>';
        } else {
            return '<div class="message ' + type + '"> ' + message + '</div>'
        }
    }
    $.fn.search.settings.templates.message = noResultMessage;

    $('.ui.search').search({
        minCharacters: 1,
        searchDelay: 200,
        showNoResults: true,
        apiSettings: {
            action: 'search',
            url: '/p/${project.id}/map/search?q={query}',
            onResponse: function (r) {
                r.results = r.results || [];
                r.results.forEach(function (results) { // 删除为空的字段
                    for (var key in results) {
                        if (results[key] === '' || results[key] == null) {
                            delete results[key]
                        }
                    }
                });
                return r;
            }
        },
        onSelect: function (results, response) {
            var currentNode = cy.$id(results.id);
            if (currentNode.nonempty()) {
                cy.nodes(':selected').deselect();
                currentNode.select();
                cy.center(currentNode);
                $(this).search('hide results');
                return false;
            }
            cy.loadElement("/p/${project.id}/map/layer/snapshot?id=" + results.id, true,true,results.title);
            $(this).search('hide results')
            return false;
        }
    });
</script>
</body>
</html>

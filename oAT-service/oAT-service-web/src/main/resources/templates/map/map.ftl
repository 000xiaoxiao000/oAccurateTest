<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>代码图层</title>
    <#include "../common.ftl">
    <#--    <script src="https://cdn.bootcss.com/cytoscape/3.5.2/cytoscape.min.js"></script>-->
    <script src="/js/cytoscape.min.js"></script>
    <script src="/js/map.js?v=${.now}"></script>

    <link rel="stylesheet" href="/css/codemirror.min.css"/>
    <script src="/js/codemirror.min.js"></script>
    <script src="/js/xml.min.js"></script>
</head>
<body>

<#assign searchItemActive="active"/>
<#--<#include "../projectHeader.ftl">-->
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

    #codemirror-container {
        position: absolute;
        left: auto;
        bottom: 0px;
        top: 50px;
        right: 0px;
        z-index: 2;
        margin: 5px;
        padding: 0px 5px;
    }

    .CodeMirror {
        height: 100%; /* 确保CodeMirror的高度充满其父容器 */
        width: 100%; /* 确保宽度充满 */
        /*overflow: auto; !* 如果内容过长，显示滚动条 *!*/
    }

    .highlighted-line {
        background-color: #01fa16 !important;
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
        <span>节点参考</span>
        <span style="background-color: #5e5757">灰色节点</span><span>是：VO,DTO,Enum,Utils的方法等；</span><span style="background-color:
        #9233ac">暗紫色节点</span>是：(调用)接口实现的方法；<span
                style="background-color: #b87a44">黄褐色节点</span><span>是：control层的方法</span>
    </div>
</div>
<div id="find_element" class="ui raised segment">
    <div class="ui icon input">
        <input type="text" placeholder="输入关键字后回车">
        <i class="circular find link icon popup"
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
    <#if visualAngle=="code">
    </#if>
</div>
<#--右键菜单-->
<div id="content_menu">
    <div class="ui dropdown">
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
        <img class="ui bordered image" src=""/>
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

<div id="codemirror-container" class="ui label">
</div>

<script>
    // 初始化画布
    fetch("${dataUrl}").then(function (res) {
        //隐藏节点信息框
        document.getElementById('bottom_nodeInfo').style.display = 'none';
        //隐藏代码详情框
        document.getElementById('codemirror-container').style.display = 'none';
        return res.json();
    }).then(buildMap).then(function (cy) {
        // 点击节点显示详情
        cy.on('select', 'node,edge', showDetail, showCodeTips);
        // 设置
        if (typeof isShowUnion != 'undefined') {
            cy.settings.subSelectUnionNode = isShowUnion;
        }
        // cy.on('mousemove', 'node', showDetail);
        // 显示右键菜单
        cy.on('cxttap', showContextMenu);
        // 设置提示框位置
        cy.on('tap cxttap', 'node', setTipPosition);
    }).catch(function (error) {
        // 如果有错误发生，这里可以捕获并处理
        console.error('Error fetching data:', error);
    }).then(function () {
        doRefresh();
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
            files[0] = {file: "id：", value: ele.id()};
            if (ele.data.hasOwnProperty('name') && ele.data('name') != null) {
                files[1] = {file: "名称：", value: ele.data('name')};
            }
            if (ele.data.hasOwnProperty('describe') && ele.data('describe') != null) {
                files[2] = {file: "描述：", value: ele.data('describe')};
            }
            if (ele.data('doLines') != null) {
                files[3] = {file: "执行到的代码行数：", value: ele.data('doLines')};
            }
            if (ele.data('lines') != null) {
                files[4] = {file: "代码行数：", value: ele.data('lines')};
            }
            if (ele.data('coverageRate') != null) {
                files[5] = {file: "代码覆盖率：", value: ele.data('coverageRate') + "%"};
            }
            if (ele.data('cyclo') != null) {
                files[6] = {file: "圈复杂度V(G)：", value: ele.data('cyclo')};
            }
        }
        files.forEach(function (a) {
            $('#bottom_nodeInfo .list').append(
                '<div class="item">' +
                '<div class="header">' + a.file +
                '</div>' + a.value + '</div>');
        });

        showCodeTips(ele.id(), ele.data('doLines'));
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
    }

    // 添加快捷键
    $(document).keydown(function (e) {
        if (e.ctrlKey && e.key.toLowerCase() == 'f') {
            e.stopPropagation();
            e.preventDefault();
            showDoFind();
        } else if (e.key.toLowerCase() === 'f2') {
            showNodeTips();
        } else if (e.ctrlKey && e.key.toLowerCase() === 'a') {
            if (e.target == $("body")[0]) {
                e.stopPropagation();
                e.preventDefault();
                cy.nodes().select();
            }
        } else if (e.ctrlKey && e.key.toLowerCase() === 'c') {
            showCodeTips();
        }
    });

    // 显示代码详情
    function showCodeTips(id, doLines) {
        if (id == null) {
            return;
        }
        if (!Array.isArray(doLines) || doLines.length === 0) {
            console.error('doLines is not a valid array or is empty');
            return;
        }
        var _id = id
        // 找到第一个空格的位置
        const spaceIndex = _id.indexOf(' ');
        // 分割字符串
        const siClassName = _id.substring(0, spaceIndex);
        const siMethodSignature = _id.substring(spaceIndex + 1);
        var _className = siClassName;
        var _methodName = siMethodSignature;
        if (_className == null || _methodName == null) {
            return;
        }
        var resultCode = $.ajax({
            url: "/p/${project.id}/map/codeSearch",
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({className: _className, methodName: _methodName}),
            async: false
        }).responseText;
        // console.log(resultCode);

        var _styleWidth = document.body.clientWidth / 2 + 'px';
        document.getElementById('codemirror-container').style.display = 'inline-block';
        document.getElementById('codemirror-container').style.position = 'fixed';
        document.getElementById('codemirror-container').style.width = _styleWidth;

        $('#codemirror-container').empty();
        var textArea = document.getElementById('codemirror-container');
        var editor = CodeMirror(textArea, {
            value: resultCode,
            mode: "x-java",
            theme: 'default',
            lineNumbers: false,
            readOnly: true,  // 不编辑代码，可以设置为 true
            height: 'auto',
            matchBrackets: true,    //括号匹配
            lineWrapping: true, //代码折叠
        });

        // 高亮行的范围
        var highlightLines = Array.from(doLines);

        // 为每一行添加高亮
        highlightLines.forEach(function (lineNumber) {
            // 获取特定行的行号
            var line = editor.getLineHandle(lineNumber);
            // console.log(line, lineNumber);

            // 确保 CodeMirror 实例在调用 addLineClass 之前已经初始化
            // if (editor) {
            //     // 添加高亮
            //     editor.addLineClass(line, 'background', 'highlighted-line');
            // } else {
            //     console.error('editor is undefined');
            // }
        });
    }

    function showNodeTips() {
        var node = $("#content_tips")[0].target;
        if (node != null && cy.nodes(":selected").has(node)) {
            if (node.is('.snapshot')) {
                $("#content_tips .popup .header").html(node.data('name'));
                $("#content_tips .popup .content").html(node.data('describe'));
                $("#content_tips .popup .image").attr('src', "/r/" + node.data('image'));
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
            return '<div class="message ' + type + '> ' + message + '</div>'
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
            cy.loadElement("/p/${project.id}/map/layer/snapshot?id=" + results.id, true, false);
            $(this).search('hide results')
            return false;
        }
    });
</script>
</body>
</html>

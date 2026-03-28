<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-快照详情</title>
    <link href="/css/font-awesome.min.css" rel="stylesheet">
    <#include "../common.ftl">
    <script src="/js/d3.min.js" charset="utf-8"></script>
    <script src="/js/dagre-d3.min.js"></script>

    <link href="/css/monokai_sublime.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>
    <script src="/js/clipboard.min.js"></script>
    <#--流程图 图标-->
    <link rel="stylesheet" href="/css/all.css"
          integrity="sha384-UHRtZLI+pbxtHCWp1t77Bi1L4ZtiqrqD80Kn4Z8NTSRyMA2Fd33n5dQ8lWUE00s/"
          crossorigin="anonymous">

    <script src="/js/common.js?v=2"></script>
    <link href="/css/common.css" rel="stylesheet">

    <style type="text/css">
        .ui.right {
            float: right
        }
    </style>
</head>
<body>
<!--头部菜单 引入-->
<#assign usecaseItemActive="active">
<#include "../projectHeader.ftl">

<#--面包屑导航-->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/snapshot/list">快照列表</a>
    <span class="divider">/</span>
    <div class="active section">详情</div>
</div>


<div class="ui container">
    <div class="text">
        <a href="javascript:window.history.go(-1)"><i class="icon arrow left"></i>返回</a>
    </div>
    <#--快照标题-->
    <h2 class="ui center aligned dividing header">
        ${snapshot.name}
        <#--<span class="ui dropdown  poping up" data-content="修改信息" data-variation="tiny inverted">
            <i class="icon small grey  setting"></i>
        </span>-->
        <#if labels??>
            <#list labels as lab>
                <div class="ui tiny ${lab.color} label">${lab.name}</div>
            </#list>
        </#if>

        <div class="sub header" style="margin: 5px 0px 5px 0px">
            ${snapshot.describe}
        </div>
        <div class="ui right sub header" style="margin-top: -15px;font-size: 13px">
            <span>${createUser.name}</span>
            <span>更新于</span>
            <span> ${snapshot.updateTime?datetime} </span>
        </div>
    </h2>
    <div id="monitorDetail" class="ui">
        <!--表头-->
        <div class="ui block header top attached  segment">
            <span class="ui">
                详情：
                <span id="monitorDetailTitle" style="color: #888888">
            </span>
            </span>
            <#--<div class="ui right">
                <a class=" poping up" href="#" data-content="图表视图"
                   data-variation="tiny inverted" data-position="left center">
                    <i class="ui area chart  icon"></i>
                </a>
                <a class="poping up" href="#" data-content="列表视图"
                   data-variation="tiny inverted" data-position="left center">
                    <i class="ui list  icon"></i>
                </a>
                <a class="poping up" href="#" data-content="性能视图"
                   data-variation="tiny inverted" data-position="left center">
                    <i class="ui rocket  icon"></i>
                </a>
            </div>-->
        </div>
        <!-- 内容 -->
        <div class="ui segment  attached " style="min-height: 200px;padding: 0px;">
            <svg id="svg-canvas" height="200" width="900"></svg>
            <!--节点详情-->
            <div id="nodeDetail" class="ui segment " style=" min-height: calc(100vh - 465px);padding: 0px;">

            </div>
        </div>
    </div>
</div>

<!--悬浮按钮 分享链接\增加评论-->
<div class="ui vertical basic large icon buttons" style="position: fixed;bottom: 50px;right: 20px">
    <button class="ui medium button poping up share "
            data-position="left center">
        <i class="ui share alternate icon"></i>
    </button>

    <#--共享对话框-->
    <div id="shareDialog" class="ui flowing popup top left transition hidden" style="min-width: 250px;">
        <div class="ui toggle  checkbox">
            <input type="checkbox" <#if snapshot.share??&&snapshot.share ==true> checked="checked"</#if> >
            <label id="shareLable">已开启共享</label>
        </div>
        <div class="ui divider"></div>
        <div id="shareUrlText" class="ui fluid small icon input" <#if snapshot.share??&&snapshot.share ==true>
        <#else>
            style="display: none"
        </#if> >
            <input id="urlInput" type="text" onfocus="this.select();" value="/share/snapshot/${snapshot.id}">
            <i class=" copy link icon" data-clipboard-target="#urlInput"></i>
        </div>
        <div id="copyTip" class="ui mini inverted popup" style="min-width: 55px">点击复制</div>
    </div>
    <script>
        $(function () {
            $('.copy.link.icon').popup({
                popup: $('#copyTip'),
                position: 'bottom left',
                target: '.copy.link.icon',
                on: 'click',
                onHidden: function () {
                    $('#copyTip').html("点击复制");
                }
            });
            // 初始共享内容
            $("#urlInput").val(window.location.origin + "/share/snapshot/${snapshot.id}");
            var clipboard = new ClipboardJS('.copy.link.icon');
            clipboard.on('success', function (e) {
                $('#copyTip').html("已复制");
            });
            clipboard.on('error', function (e) {
                $('#copyTip').html("复制失败");
            });
        })
        $('.share.button')
            .popup({
                popup: $('#shareDialog'),
                on: 'click'
            });
        $('.ui.checkbox').checkbox(
            {
                onChecked: function () {
                    $.getJSON("/p/${project.id}/snapshot/openShare/${snapshot.id}", function (results) {
                        $("#shareLable").html("已开启共享");
                        $("#shareUrlText").show();
                    })
                },
                onUnchecked: function () {
                    $.getJSON("/p/${project.id}/snapshot/closeShare/${snapshot.id}", function (results) {
                        $("#shareLable").html("已关闭共享");
                        $("#shareUrlText").hide();
                    })
                }
            }
        );

    </script>


    <button class="ui disabled medium button poping up" data-content="增加评论" data-variation="tiny inverted"
            data-position="left center">
        <i class="ui edit icon"></i>
    </button>
</div>


<script>
    $('.ui.accordion').accordion({
        exclusive: false
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
</script>

<script>

    $(function () {
        // 构建监控详情
        openMonitorDetail('${project.id}', '${snapshot.traceId}');
    });

    /*
     * 打开监控详情
     *
     * */
    function openMonitorDetail(projectid, traceId) {
        // 初始化界面
        $("#svg-canvas").children().remove();
        $("#svg-canvas").attr('width', $("#monitorDetail").width());
        // 清除节点详情信息
        $("#nodeDetail").children("div.entry").remove();
        // 装载监控数据
        var monitorData = $.ajax({
            url: "/p/" + projectid + "/monitor/getTraceGraph?traceId=" + traceId,
            async: false
        }).responseJSON;

        // 设置标题
        $("#monitorDetailTitle").text(monitorData.title);

        // 构建流程图
        var g = buildTopo("svg-canvas", monitorData, {
            nodeClick: function (id, index, array) {
                var node = g.node(id);
                openNodeDetails(projectid, traceId, id);
            }
        });
        // 打开默认节点详情
        openNodeDetails(projectid, traceId, monitorData.showDefaultNode.id);
    }

    /**
     * 打开监控详情中的某个节点
     * @param nodeId
     * @param nodeType
     */
    function openNodeDetails(projectid, traceId, nodeId) {
        $("#nodeDetail").children("div.entry").remove();
        var nodeDiv = $('<div></div>');
        nodeDiv.attr("nodeId", nodeId);
        nodeDiv.attr("class", "entry");
        var htmlobj = $.ajax({url: "/p/" + projectid + "/monitor/" + traceId + "/" + nodeId + ".html", async: false});
        nodeDiv.html(htmlobj.responseText);
        $("#nodeDetail").append(nodeDiv);

    }
</script>

</body>
</html>
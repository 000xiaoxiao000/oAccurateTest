<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>共享快照</title>
    <link href="/css/font-awesome.min.css" rel="stylesheet">
    <#include "../common.ftl">
    <script src="/js/d3.min.js" charset="utf-8"></script>
    <script src="/js/dagre-d3.min.js"></script>

    <link href="/css/monokai_sublime.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>
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
<div class="top ui segment" style="margin: 0px -2px 10px -2px;padding: 0px 5px">
    <div class="ui text  menu" style="margin: 0px">
        <#--<a class="item" href="#" style="padding: 0px">
            <img class="ui mini image" src="/images/logo.png">
        </a>-->
            <div class="item">
                <img class="ui image" src="/images/logo.png" style="width: 100px;height:auto;">
            </div>
            <div class="item">
               <span>系统快照共享</span>
            </div>
        <div class="right menu">
            <a class="ui dropdown item" href="/login">
                <i class="user icon"> </i>
                <span class="text">登陆/注册</span>
            </a>
        </div>
    </div>
</div>


<div class="ui container">
<#--快照标题-->
    <h2 class="ui center aligned  dividing  header">
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
    <div id="monitorDetail">
        <!--表头-->
        <div class="ui block header top attached  segment">
            <span class="ui">
                详情：
                <span id="monitorDetailTitle" style="color: #888888">
            </span>
            </span>
        </div>
        <!-- 内容 -->
        <div class="ui segment  attached " style="min-height: 200px;padding: 0px;">
            <svg id="svg-canvas" height="200" width="900"></svg>
            <!--节点详情-->
            <div id="nodeDetail" class="ui segment " style=" min-height: calc(100vh - 465px);padding: 0px ;">

            </div>
        </div>
    </div>


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
        var rUrl = encodeURIComponent("/p/" + projectid + "/monitor/getTraceGraph?traceId=" + traceId);
        var monitorData = $.ajax({
            // share 控制器跳转获取资源
            url: "/share/get?url=" + rUrl,
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
        // share 控制器跳转获取资源
        var rUrl = encodeURIComponent("/p/" + projectid + "/monitor/" + traceId + "/" + nodeId + ".html");
        var htmlobj = $.ajax({
            url: "/share/get?url=" + rUrl,
            async: false
        });
        nodeDiv.html(htmlobj.responseText);
        $("#nodeDetail").append(nodeDiv);

    }
</script>

</body>
</html>
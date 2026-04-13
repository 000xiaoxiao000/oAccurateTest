<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>系统快照-快照详情</title>
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

<#assign snapshotListHref=(snapshot.appId??)?then('/p/' + project.id + '/' + snapshot.appId + '/snapshot/list', '/p/' + project.id + '/snapshot/list')/>
<#assign settingsHref=(snapshot.appId??)?then('/p/' + project.id + '/app/' + snapshot.appId + '/settings', '/p/' + project.id + '/edit')/>
<#assign snapshotDetailHref=(snapshot.appId??)?then('/p/' + project.id + '/' + snapshot.appId + '/snapshot/detail/' + snapshot.id, '/p/' + project.id + '/snapshot/detail/' + snapshot.id)/>

<#--面包屑导航-->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <a class="section" href="${snapshotListHref}">系统快照</a>
    <span class="divider">/</span>
    <div class="active section">详情</div>
</div>

<div class="ui grid attached container" style="margin-top: 14px">
    <div class="ui four wide column">
        <#assign snapshotGroupName=(snapshot.title!snapshot.name)!"系统快照"/>
        <#assign snapshotDetailActive="active"/>
        <#include "LeftNavigationMenu.ftl">
    </div>
    <div class="ui twelve wide column">
        <div class="ui container">
            <div class="text">
                <a href="javascript:window.history.go(-1)"><i class="icon arrow left"></i>返回</a>
            </div>
            <#--快照标题-->
            <h2 class="ui center aligned dividing header">
                ${snapshot.name}
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
                <div class="ui block header top attached  segment">
                    <span class="ui">
                        详情：
                        <span id="monitorDetailTitle" style="color: #888888"></span>
                    </span>
                </div>
                <div class="ui segment  attached " style="min-height: 200px;padding: 0px;">
                    <svg id="svg-canvas" height="200" width="900"></svg>
                    <div id="nodeDetail" class="ui segment " style=" min-height: calc(100vh - 465px);padding: 0px;"></div>
                </div>
            </div>
        </div>
    </div>
</div>

<div class="ui vertical basic large icon buttons" style="position: fixed;bottom: 50px;right: 20px">
    <button class="ui medium button poping up share "
            data-position="left center">
        <i class="ui share alternate icon"></i>
    </button>

    <div id="shareDialog" class="ui flowing popup top left transition hidden" style="min-width: 250px;">
        <div class="ui toggle  checkbox">
            <input type="checkbox" <#if snapshot.share??&&snapshot.share ==true> checked="checked"</#if> >
            <label id="shareLable">已开启共享</label>
        </div>
        <div class="ui divider"></div>
        <div id="shareUrlText" class="ui fluid small icon input" <#if snapshot.share??&&snapshot.share ==true><#else>style="display: none"</#if>>
            <input id="urlInput" type="text" onfocus="this.select();" value="/share/snapshot/${snapshot.id}">
            <i class=" copy link icon" data-clipboard-target="#urlInput"></i>
        </div>
        <div id="copyTip" class="ui mini inverted popup" style="min-width: 55px">点击复制</div>
    </div>

    <button class="ui disabled medium button poping up" data-content="增加评论" data-variation="tiny inverted"
            data-position="left center">
        <i class="ui edit icon"></i>
    </button>
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
        $("#urlInput").val(window.location.origin + "/share/snapshot/${snapshot.id}");
        var clipboard = new ClipboardJS('.copy.link.icon');
        clipboard.on('success', function (e) {
            $('#copyTip').html("已复制");
        });
        clipboard.on('error', function (e) {
            $('#copyTip').html("复制失败");
        });

        $('.share.button').popup({
            popup: $('#shareDialog'),
            on: 'click'
        });

        $('.ui.checkbox').checkbox({
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
        });

        openMonitorDetail('${project.id}', '${snapshot.traceId}');
    });

    $('.ui.accordion').accordion({
        exclusive: false
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });

    function openMonitorDetail(projectid, traceId) {
        $("#svg-canvas").children().remove();
        $("#svg-canvas").attr('width', $("#monitorDetail").width());
        $("#nodeDetail").children("div.entry").remove();
        var monitorData = $.ajax({
            url: "/p/" + projectid + "/monitor/getTraceGraph?traceId=" + traceId,
            async: false
        }).responseJSON;

        $("#monitorDetailTitle").text(monitorData.title);

        var g = buildTopo("svg-canvas", monitorData, {
            nodeClick: function (id, index, array) {
                g.node(id);
                openNodeDetails(projectid, traceId, id);
            }
        });
        openNodeDetails(projectid, traceId, monitorData.showDefaultNode.id);
    }

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

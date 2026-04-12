<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-快照详情</title>
    <#include "../common.ftl">
    <link href="/css/treeTable.css" rel="stylesheet">
    <link href="/css/tipsy.css" rel="stylesheet">
    <link href="/css/font-awesome.min.css" rel="stylesheet">
    <script src="/js/d3.min.js"></script>
    <script src="/js/dagre-d3.min.js"></script>
    <script src="/js/tipsy.js?v=${.now}"></script>
    <script src="/js/systemSnapshotFlow.js?v=${.now}"></script>
    <script src="/js/treeTable.js"></script>
    <!--语法高亮-->
    <link href="/css/github.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>
    <script src="/js/spark-md5.min.js"></script>
    <script src="/js/upload.js?v=${.now}"></script>
</head>
<body>
<style>
    .ui.list > .item .description {
        color: rgba(0, 0, 0, .5);
        margin: 2px 0px;
    }

    .segment.hidden {
        display: none;
    }

    /* 内容自动换行 */
    pre {
        white-space: pre-wrap;
        word-wrap: break-word;
    }

    #stackNodeDetail {
        position: fixed;
        right: 0px;
        top: 0px;
        bottom: 5px;
        margin: 0px;
        z-index: 2;
        max-width: 25em;
        min-width: 20em;
        overflow-y: auto;
    }

    #stackNodeDetail.max {
        left: 0px;
        right: 0px;
        max-width: 100vw;
    }
</style>

<style id="css">
    .node rect {
        stroke: #999;
        fill: #fff;
        stroke-width: 1.5px;
        cursor: pointer;
    }

    .node.error rect {
        stroke: red;
    }

    .node rect:hover {
        /*fill: azure;*/
        stroke: dodgerblue;
        stroke-width: 1.5px;
    }

    .node .label {
        pointer-events: none;
    }

    .labelContent {
        margin-top: 5px;
        margin-left: 5px;
    }

    .subTitle {
        color: #9aa1ac;
    }

    .node text {
        font-weight: 300;
        font-family: "Helvetica Neue", Helvetica, Arial, sans-serf;
        font-size: 14px;
        pointer-events: none;
    }

    .edgePath path {
        stroke: #333;
        stroke-width: 1.5px;
    }
</style>

<style>
    .ui.input.edit input, .field.edit textarea {
        padding: 7px 7px 7px 0px;
    }

    .ui.form .field {
        margin-bottom: 0.2em;
    }

    form.ui .field.edit input {
        margin-top: -4px;
        /*padding: 7px;*/
        padding-left: 0px;
    }

    form.ui .field.edit .selection {
        margin-top: -4px;
        margin-left: -5px;
    }

    .ui.input.edit input:not(:hover):not(:focus),
    .ui.form .field.edit input:not(:hover):not(:focus),
    .field.edit textarea:not(:hover):not(:focus) {
        border-color: #fff;
    }

    .ui.text.container {
        background: #ffffff;
        min-width: 800px;
        padding: 20px;
    }

    /* 让容器内的 Semantic UI 菜单在宽度不足时自动换行，防止超出白色背景 */
    .ui.text.container .ui.menu {
        display: flex; /* menu 在 Semantic UI 中通常是 flex 布局，这里确保可换行 */
        flex-wrap: wrap;
        box-sizing: border-box;
    }
</style>

<!--头部菜单 引入-->
<#assign appCenterActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui small breadcrumb">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/${app.id}/snapshot/list"> ${app.name} </a>
    <span class="divider">/</span>
    <div class="active section">快照详情</div>
</div>
<!--过滤条件-->
<div class="ui text container">
    <div class="text">
        <a href="javascript:window.history.go(-1)"><i class="icon arrow left"></i>返回</a>
    </div>
    <div class="ui secondary pointing four item stackable menu big">
        <a class="item active " data-tab="definition">基本信息</a>
        <a class="item" data-tab="flow">流程图</a>
        <a class="item" data-tab="usage">堆栈列表</a>
        <a class="item" data-tab="coverage">覆盖率报告</a>
    </div>
</div>
<div class="ui tab active text container segment" data-tab="definition">
    <form class="ui auto form" action="/p/${project.id}/${app.id}/snapshot/update">
        <input type="hidden" name="id" value="${snapshot.id}">
        <div class="field edit">
            <h3 class="ui header">
                <input type="text" name="title" value="${snapshot.title}">
                <div class="sub header">  ${snapshot.subTitle!}</div>
            </h3>
        </div>
        <div class="field edit">
            <textarea rows="2" name="describe" value="${snapshot.describe!}">${snapshot.describe!}</textarea>
        </div>
    </form>
    <!--标题图片-->
    <#if (snapshot.topicImage)?? &&(snapshot.topicImage?length>1)>
        <img id="topicImage" class="ui centered bordered large image" src="/r/${snapshot.topicImage!}"
             style="margin-top: 5px">
    <#else>
        <img id="topicImage" class="ui centered bordered large image" src="/images/image.png" alt="上传图片"
             style="margin-top: 5px">
    </#if>
    <div id="topicImageEdit" class="ui popup tiny basic flowing transition hidden"
         style="margin: 0px 0px 3px 0px;padding: 0px;border: none">
        <button class="ui mini grey compact icon button" style="margin: 0px;border: none">
            <i class="edit icon"></i>
        </button>
        <input type="file" style="display: none" accept=".jpg,.png">
    </div>

    <h4 class="ui dividing header">
        动态
    </h4>
    <div class="ui feed">
        <#list dynamics as dynamic>
            <div class="event">
                <div class="label">
                    <#if dynamic.type=="comment">
                        <i class="ui comment icon"></i>
                    <#else >
                        <i class="ui edit icon"></i>
                    </#if>
                </div>
                <div class="content">
                    <div class="summary">
                        ${dynamic.title}
                        <div class="date">
                            ${dynamic.time}前
                        </div>
                    </div>
                    <#if dynamic.describe??>
                        <div class="extra text">
                            ${dynamic.describe!}
                        </div>
                    </#if>
                    <#if dynamic.type=="comment">
                        <div class="meta" onclick="doDelDescribe('${snapshot.id}','${dynamic.describe}','${dynamic.date?datetime}')">
                            <a class="like">
                                <i class="close icon"></i>删除评论
                            </a>
                        </div>
                    </#if>
                </div>
            </div>
        </#list>
    </div>
    <form id="describeForm" class="ui reply form">
        <div class="field">
            <textarea id="describeId"></textarea>
        </div>
        <div class="ui labeled submit icon button" onclick="doAddDescribe('${snapshot.id}')">
            <i class="icon edit"></i> 添加评论
        </div>
    </form>

    <!--右测悬浮 属性面版-->
    <div class="ui raised segment"
         style="position: absolute;top: 0px;right: -13em;min-width:10.5em;max-width: 12em;margin-top: 0px">
        <form class="ui auto form" action="/p/${project.id}/${app.id}/snapshot/update">
            <input type="hidden" name="id" value="${snapshot.id}">
            <div class="field">
                <label>所属应用</label>
                <div class="description">${app.name}</div>
            </div>
            <div class="field edit">
                <label>版本号</label>
                <input type="text" name="version" value="${snapshot.version}">
            </div>
            <div class="field edit">
                <label>版本周期(天)</label>
                <input type="text" name="versionCycle" value="${snapshot.versionCycle!}">
            </div>
            <div class="field edit">
                <label>标签</label>
                <div class="ui fluid   selection  multiple  click dropdown" style="border: none">
                    <input type="hidden" name="labels" value="${selectLabel}">
                    <i class="dropdown icon"></i>
                    <span class="default text">添加标签</span>
                    <div class="menu">
                        <#list labels as label>
                            <div class="item" data-value="${label.name}">
                                <div class="ui tiny mini label ${label.color}">
                                    ${label.name}
                                </div>

                            </div>
                        </#list>
                    </div>
                </div>
            </div>
            <div class="field edit">
                <label>负责人</label>
                <div class="ui fluid selection multiple  click dropdown" style="border: none">
                    <input type="hidden" name="principals" value="${principals}">
                    <i class="dropdown icon"></i>
                    <div class="default text">选择朋友</div>
                    <div class="menu">
                        <#list members as member>
                            <div class="item" data-value="${member.memberId}">${member.memberName}</div>
                        </#list>
                    </div>
                </div>
            </div>
        </form>
    </div>
</div>
<div class="ui tab " data-tab="flow"
     style="width: 100%;height: calc(100vh - 150px);">
    <svg id="svg-canvas" style="width: 100%;height: 100%">
    </svg>
</div>

<!--堆栈列表-->
<div class="ui tab text container segment" data-tab="usage">
    <table class="ui compact single line selectable tree table">
        <thead>
        <tr style=" font-size: 0.8em; color: rgba(0,0,0,0.4);">
            <th>应用名</th>
            <th>类型</th>
            <th>服务/方法</th>
            <th>用时</th>
        </tr>
        </thead>
        <tbody>
        <#list stackItems as item >
            <tr nodeId="${item.nodeId}" parentId="${item.parentId!'root'}" traceId="${item.traceId}"
                onclick="showTraceNodeDetail('${item.nodeId}');">
                <td>
                    ${item.appName}
                </td>
                <td>${item.type}</td>
                <td>${item.serverName}
                    <#if item.nodeId=="0">
                        <i class="ui icon code" onclick="event.stopPropagation(); openCodeMap('${item.traceId}'); return false;" title="代码关系图层"></i>
                    </#if></td>
                <#if item.appName=='redis'>
                    <td>0 ms</td>
                <#else >
                    <td>${item.useTime} ms</td>
                </#if>
            </tr>
        </#list>
        </tbody>
    </table>
    <div id="stackNodeDetail" class="ui raised segment hidden"
         style="">
        <div class="ui top attached grey  label" style="border: none;top: -0.5px">
            节点详情
            <i class="close link icon" style="float: right;font-size: 1.1em;"
               onclick="$('#stackNodeDetail').toggleClass('hidden');"></i>

            <script>
                function maxDetailWindow() {
                    $('#stackNodeDetail').toggleClass('max');
                    $('#stackNodeDetail .window.icon').toggleClass('maximize');
                    $('#stackNodeDetail .window.icon').toggleClass('restore');
                    $('#stackNodeDetail .content').toggleClass('text segment container');
                }
            </script>
            <i class="window maximize outline link icon" onclick="maxDetailWindow();" style="float: right;font-size: 1.1em;"></i>
        </div>
        <div class="content ui">
        </div>
    </div>
</div>
<!--覆盖率报告-->
<div class="ui tab text container segment" data-tab="coverage">
    <div id="coverageReportStatus" class="ui segment basic center aligned">
        <#if snapshot.reportStatus == 0>
            <div class="ui placeholder segment">
                <div class="ui icon header">
                    <i class="chart bar outline icon"></i>
                    尚未生成覆盖率报告
                </div>
                <div class="ui primary button" onclick="calculateCoverage()">开始生成</div>
            </div>
        <#elseif snapshot.reportStatus == 1>
            <div class="ui active inverted dimmer">
                <div class="ui text loader">报告生成中，请稍候...</div>
            </div>
            <p>正在努力计算中...</p>
        <#elseif snapshot.reportStatus == 2>
            <div class="ui success message">
                <div class="header">报告已就绪</div>
                <p>最后更新时间：${snapshot.coverageReport.createTime?datetime}</p>
            </div>
            <div class="ui grid">
                <div class="eight wide column">
                    <div class="ui label large green fluid">方法覆盖率: ${(snapshot.coverageReport.totalMethods > 0)?then(snapshot.coverageReport.coveredMethods * 100.0 / snapshot.coverageReport.totalMethods, 0)?string("0.00")}%</div>
                </div>
                <div class="eight wide column">
                    <div class="ui label large blue fluid">行覆盖率: ${(snapshot.coverageReport.totalLines > 0)?then(snapshot.coverageReport.coveredLines * 100.0 / snapshot.coverageReport.totalLines, 0)?string("0.00")}%</div>
                </div>
            </div>
            <div class="ui divider"></div>
            <a href="/p/${project.id}/${app.id}/snapshot/report/${snapshot.id}" target="_blank" class="ui primary button fluid">查看详细覆盖率报告</a>
            <div class="ui basic button tiny fluid" style="margin-top: 10px;" onclick="calculateCoverage()">重新生成报告</div>
        <#elseif snapshot.reportStatus == 3>
            <div class="ui negative message">
                <div class="header">报告生成失败</div>
                <p>请检查后台日志或尝试重新生成。</p>
            </div>
            <div class="ui primary button" onclick="calculateCoverage()">重新生成</div>
        </#if>
    </div>
</div>

<!-- 删除快照弹出框-->
<div id="delDescribeDialog" class="ui small modal">
    <div class="header">删除评论</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该评论吗？
        </div>
    </div>
    <div class="actions">
        <div id="delDescribeButton" class="ui negative button">删除</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    // 是否初始化选项卡
    var initFlow = false;
    var activeTab = '${activeTab!"definition"}';

    function getSnapshotDetailUrlWithTab(tab) {
        var url = new URL(window.location.href);
        url.searchParams.set('tab', tab);
        return url.toString();
    }

    function refreshCurrentTab() {
        window.location = getSnapshotDetailUrlWithTab(activeTab || 'definition');
    }

    $(function () {
        var defaultTab = '${activeTab!"definition"}';
        if (defaultTab) {
            activeTab = defaultTab;
        }
        // 初始化选项卡
        $('.ui.menu .item').tab({
            onVisible: function (tabPath) {
                activeTab = tabPath;
                if (tabPath == "flow" && !initFlow) {
                    buildFlow();

                }
            }
        });
        if (defaultTab) {
            $('.ui.menu .item').tab('change tab', defaultTab);
        }
        //  如果程图初始激活页 就需要先调用该方法
        // buildFlow();


        // 初始化Tree表结构
        treeTable($(".tree.table"));

        $('.ui.click.dropdown').dropdown({
            on: 'click'
        })
    });

    // 构建流程图
    function buildFlow() {
        fetch('graph/${snapshot.id}').then(function (response) {
            return response.json();
        }).then(function (datas) {
            //清除原数据
            $("#svg-canvas").children().remove();
            var g = buildTopo("svg-canvas", datas, {
                nodeClick: function (id, index, array) {
                    g.node(id);
                }
            });
            initFlow = true;
        });
    }

    // 打开堆栈节点详情
    function showTraceNodeDetail(id) {
        data = "traceId=${snapshot.traceId}&nodeId=" + id;
        // 加载 节点详情页
        $('#stackNodeDetail .content').load("/p/${project.id}/${app.id}/snapshot/node/${snapshot.id}", data, function (response, status, xhr) {
            if ("success" == status) {
                $('#stackNodeDetail').toggleClass('hidden', false);
            }
        });
    }

    //打开代码图层
    function openCodeMap(traceId) {
        let url = "/p/${project.id}/map/code?traceId=" + traceId;
        window.open(url, 'codeMapDialog', 'toolbar=no,location=no,resizable=no, height=500, width=680,,scrollbars=yes ,left=380,top=100');
    }

    function autoSave(event) {
        var target = $(event.target);
        if (event.target.type != 'hidden') {
            var currentVal = target.val();
            var oldVal = target.attr('value');
            if (currentVal == oldVal) {
                return;
            }
        }

        form = target.parents("form").first();
        form.attr('action', getSnapshotDetailUrlWithTab(activeTab || 'definition'));
        // ajax 提交
        form.addClass("loading");
        $.ajax({
            url: form.attr("action"),
            data: form.serialize(),
            success: function () {
                console.log("保存成功");
                target.attr('value', currentVal);
                form.removeClass("loading");
            }
        });
    };
    // 当输入框 失去焦点时 自动保存
    $("form.auto.form").submit(function (e) {
        e.preventDefault();
    })
    $("form.auto.form input:text").bind({
        // 回车
        keydown: function (e) {
            if (e.keyCode == 13) {
                autoSave(e);
            }
        }
    });
    $("form.auto.form textarea,:text").bind({
        blur: autoSave
    });
    $("form.auto.form .selection.dropdown").bind({
        change: autoSave
    });

    /**
     *添加评论
     */
    function doAddDescribe(id) {
        var _describeId = $('#describeId').val();
        var _id = "${snapshot.id}";
        var map = {id: _id, content: _describeId}
        var resultInform = $.post({
            url: "/p/${project.id}/${app.id}/snapshot/addDescribe",
            type: 'POST',
            data: map,
            async: false
        }).responseJSON;
        if (resultInform.result) {
            notifyToast(resultInform.message, 'success');
            // 刷新当前页 ，并传递删除成功的消息
            refreshCurrentTab();
        } else {
            notifyToast(resultInform.message, 'error');
        }
    }

    /**
     *删除评论
     */
    function doDelDescribe(id, content, dateTime) {
        $("#delDescribeButton").click(function () {
            var resultInform = $.ajax({
                url: "/p/${project.id}/${app.id}/snapshot/delDescribe?id=" + id + "&content=" + content + "&dateTime=" + dateTime,
                type: 'DELETE',
                async: false
            }).responseJSON;
            if (resultInform.result) {
                notifyToast(resultInform.message, 'success');
                // 刷新当前页 ，并传递删除成功的消息
                refreshCurrentTab();
            } else {
                notifyToast(resultInform.message, 'error');
            }
        });
        $("#delDescribeDialog").modal('show');
    }

    // 提交覆盖率计算任务
    function calculateCoverage() {
        $.post("/p/${project.id}/${app.id}/snapshot/report/calculate/${snapshot.id}", function(res) {
            if (res.result) {
                showToast("任务已启动", "info");
                window.location = getSnapshotDetailUrlWithTab('coverage');
            } else {
                showToast(res.message, "error");
            }
        });
    }

    // 如果状态是生成中，则开启轮询
    <#if snapshot.reportStatus == 1>
    var reportInterval = setInterval(function() {
        $.get("/p/${project.id}/${app.id}/snapshot/report/status/${snapshot.id}", function(res) {
            if (res.result && res.data.reportStatus != 1) {
                clearInterval(reportInterval);
                window.location = getSnapshotDetailUrlWithTab('coverage');
            }
        });
    }, 3000);
    </#if>
</script>

<#--初始化图片编辑-->
<script>
    $('#topicImage')
        .popup({
            hoverable: true,
            position: 'top right',
            offset: -20,
            delay: {
                show: 50,
                hide: 200
            }
        });
    // 弹出图片选择器
    $('#topicImageEdit button').click(function () {
        $('#topicImageEdit :file')[0].click();
    })
    $('#topicImageEdit :file')[0].addEventListener("change", function (ev1) {
        if (this.files.length == 0) {
            return;
        }
        uploadFile(this.files[0], "/resource/upload"
            , function (res) {
                var results = eval("(" + this.responseText + ")");
                var imageFilePath = results.data;
                $.ajax({
                    url: "/p/${project.id}/${app.id}/snapshot/update",
                    data: "id=${snapshot.id}&topicImage=" + imageFilePath,
                    success: function () {
                        $('#topicImage').attr('src', "/r/" + imageFilePath);
                        // form.removeClass("loading");
                    }
                });
            });
    });
</script>

</body>
</html>


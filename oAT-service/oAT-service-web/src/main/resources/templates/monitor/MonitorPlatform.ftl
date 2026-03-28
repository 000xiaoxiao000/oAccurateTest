<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>监控台</title>
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

    <script src="/js/common.js"></script>
    <script src="/js/monitorPlatform.js?v=${.now}"></script>
    <link href="/css/common.css" rel="stylesheet">
    <script src="/js/upload.js?v=${.now}"></script>
    <script src="/js/spark-md5.min.js"></script>
    <style type="text/css">
        .ui.right.aligned {
            float: right
        }

        tr.focus {
            background-color: #e1e1e1;
        }

        #monitorListBody tr {
            cursor: pointer;
        }
    </style>

</head>
<!--头部菜单 引入-->
<#assign  monitorItemActive="active">
<#include "../projectHeader.ftl">

<!--中间过滤条件-->
<div id="middleFilter" class="ui sticky top segment grid " style="margin-bottom: -8px">
    <!--右对齐-->
    <div class="thirteen wide column left aligned" style="padding: 0px">
        <form id="itemFilter" class="ui form" action="/p/${projectId}/monitor/getNodeByTime">
            <div class="inline fields" style="margin: 5px 0px 0px 5px">
                <span style="display: inline-block;margin: 6px 0px 6px 0px">
                    <i class="filter icon"></i>条件过滤：
                </span>
                <input type="hidden" name="maxSize" value="100">
                <div class="ui dropdown" style="padding: 7px;margin-bottom: -5px;min-width: 100px">
                    <#--默认值3分钟-->
                    <input type="hidden" name="upToTime" value="180">
                    <span class="text">三分钟内</span>
                    <div class="ui divider" style="margin:5px 0px 0px 0px"></div>
                    <div class="menu">
                        <div class="item" data-value="60">
                            一分钟内
                        </div>
                        <div class="item active" data-value="180">
                            三分钟内
                        </div>
                        <div class="item" data-value="300">
                            五分钟内
                        </div>
                        <div class="item" data-value="1800">
                            三十分钟内
                        </div>
                    </div>
                </div>

                <div class="ui multiple search compact selection dropdown"
                     style="border: none;margin-right: 0px;padding-right: 7px;min-width: 120px">
                    <input type="hidden" name="appIds" value="${appId!}">
                    <input class="search" autocomplete="off" tabindex="0">
                    <div class="text">应用过滤</div>
                    <div class="ui divider" style="margin: 0px"></div>
                    <div class="menu" tabindex="-1">
                        <#list apps as app >
                            <div class="item" data-value="${app.id}">${app.name}</div>
                        </#list>
                    </div>
                </div>
                <div class="ui multiple compact search selection dropdown ipFilter"
                     style="border: none;min-width: 110px;padding-right: 7px">
                    <input type="hidden" name="clientIps">
                    <!--加入该输入项即可解决网页抖动问题 -->
                    <input class="search" autocomplete="off" tabindex="0">
                    <div class=" default text">ip 过滤</div>
                    <div class="ui divider" style="margin: 0px"></div>
                    <div class="menu" tabindex="-1"></div>
                </div>
            </div>
        </form>
    </div>

    <div class="three wide column right aligned" style="padding: 0px">
        <div class="ui pointing dropdown small " tabindex="-1">
            <button class="ui primary <#--save  snapshot  icon--> button" style="margin: 5px">
                保存快照
            </button>
            <div class="menu" tabindex="1">
                <div class="item" onclick="openCreateSnapshot();">我的快照</div>
                <div class="item" onclick="openCreateSystemSnapshot();">系统快照</div>
            </div>
        </div>
        <!--保存快照弹出框-->
        <div id="snapshotDialog" class="ui modal standard  save snapshot">
            <div class="ui block header attached ">
                保存当前快照
            </div>
            <i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>

            <div class="ui content">
                <form id="newSnapshotForm" class="ui form" action="/p/${projectId}/snapshot/save">
                    <div class="required field">
                        <label>快照名称</label>
                        <input type="text" name="name" placeholder="输入快照名称">
                    </div>
                    <div class="field">
                        <label>添加标签</label>
                        <select multiple="3" class="ui search dropdown" name="labels">
                            <#list labels as lab>
                                <option value="${lab.name}">${lab.name}</option>
                            </#list>
                        </select>
                    </div>
                    <div class="field">
                        <label> 快照描述</label>
                        <div class="content">
                            <textarea rows="3" name="describe"></textarea>
                        </div>
                    </div>
                    <div class="ui error message">

                    </div>
                </form>
            </div>
            <div class="actions">
                <div class="ui black deny button">
                    取消
                </div>
                <div id="saveSnapshotButton" class="ui positive right labeled icon button save">
                    保存
                    <i class="checkmark icon"></i>
                </div>
            </div>
        </div>
        <#--保存系统快照 窗口-->
        <div id="systemSnapshotDialog" class="ui modal standard save snapshot ">


        </div>
    </div>
</div>
<div class="ui grid">
    <div class="ui four wide column" style="padding: 0px 0px 0px 10px;">
        <div class="segment">
            <div class="ui block header top attached segment">
                <div class="ui inline click dropdown">
                    <div class="text">
                        实时监控
                    </div>
                    <i class="dropdown icon"></i>
                    <div class="menu">
                        <a class="item active" href="/p/${projectId}/monitor">
                            实时监控
                        </a>
                        <a class="item" href="/p/${projectId}/snapshot/my">
                            我的快照
                        </a>
                    </div>
                </div>
                <div class="ui right aligned">
                    <a class="poping up" onclick="pullNewItem('${projectId}');" data-content="获取最新数据"
                       data-variation="tiny inverted">
                        <i class="ui refresh icon"></i>
                    </a>
                </div>
            </div>
            <div class="ui attached segment" style="padding: 0; height: calc(100vh - 190px);overflow:auto">
                <table class="ui selectable single line compact fixed table" style="border: 1px">
                    <tbody id="monitorListBody">
                    <!-- 监控列表 -->
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <div class="ui twelve wide column" style="padding: 0px 14px 0px 14px;">
        <!--欢迎提示面版-->
        <div id="emptyTip" class="ui grid middle aligned center aligned segment  "
             style="height: 100%;margin-top: 0px; background: #f7f7f7">
            <div class="column">
                <h2 class="ui header">
                    监控详情视图
                    <div class="ui sub header">
                        从左边监控列表选择您要查看的请求
                    </div>
                </h2>
            </div>
        </div>

        <!--监控详情-->
        <div id="monitorDetail" style="display: none">
            <!--表头-->
            <div class="ui block header top attached segment">
            <span class="ui">
                详情：
                <span id="monitorDetailTitle" style="color: #888888">
            </span>
            </span>
                <div class="ui right">
                    <#-- <a class="poping up" href="#" data-content="图表视图"
                           data-variation="tiny inverted" data-position="left center">
                            <i class="ui area chart icon"></i>
                        </a>
                        <a class="poping up" href="#" data-content="列表视图"
                           data-variation="tiny inverted" data-position="left center">
                            <i class="ui list icon"></i>
                        </a>
                        <a class="poping up" href="#" data-content="性能视图"
                           data-variation="tiny inverted" data-position="left center">
                            <i class="ui rocket icon"></i>
                        </a>-->
                </div>
            </div>
            <!-- 内容 -->
            <div class="ui segment attached" style="min-height: 200px;padding: 0px;">
                <svg id="svg-canvas" height="200" width="900"></svg>
                <!--节点详情-->
                <div id="nodeDetail" class="ui segment" style=" min-height: calc(100vh - 500px);padding: 0px;">
                </div>
            </div>
        </div>
    </div>
</div>


</div>
<#--界面初始化-->
<script language="JavaScript">
    $('.ui.sticky').sticky();
    $('#middleFilter .ui.dropdown').dropdown({
        onChange: function (value, text, selectedItem) {
            refreshMonitorList('${projectId}');
        }
    });
    $('.ipFilter').dropdown({
        allowAdditions: true,
        onChange: function (value, text, selectedItem) {
            refreshMonitorList('${projectId}');
        }
    });

    $('#middleFilter .search.dropdown.add.type')
        .dropdown({
            allowAdditions: true
        });
    $('#newAction').dropdown({
        on: 'click'
    });

    $('.ui.click.dropdown').dropdown({
        on: 'click'
    });

    $('.poping.up').popup();

    $(function () {
        // 初始化表单验证规则
        $('#newSnapshotForm').form({
                inline: false,
                onFailure: function (formErrors, fields) {
                    if (formErrors && formErrors.length > 0) {
                        showToast(formErrors[0], 'error');
                    }
                    return false;
                },
                fields: {
                    name: {
                        identifier: 'name',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入快照名称'
                            },
                            {
                                type: 'minLength[4]',
                                prompt: '快照名称至少包含4个字符'
                            },
                            {
                                type: 'maxLength[50]',
                                prompt: '快照名称不能超过50个字符'
                            }
                        ]
                    },
                    describe: {
                        identifier: 'describe',
                        rules: [
                            {
                                type: 'maxLength[512]',
                                prompt: '快照描述不能超过512个字符'
                            }
                        ]
                    }
                }
            });

        $('#saveSnapshotButton').on('click', function() {
            if ($('#newSnapshotForm').form('is valid')) {
                doSaveSnapshot('${projectId}');
                // 关闭当前模型
                $("#newSnapshotForm").parents(".modal").modal('hide');
            }
        });
    });
</script>

<script>
    $(function () {
        // 刷新监控列表
        refreshMonitorList('${projectId}');
    });
</script>

</body>
</html>

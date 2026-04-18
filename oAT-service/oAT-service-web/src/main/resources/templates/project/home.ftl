<!DOCTYPE html>
<html lang="en">
<!-- 项目主页 -->
<head>
    <meta charset="UTF-8">
    <title>${project.name}-项目首页</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单引入-->
<div class="include">
    <#include  "../projectHeader.ftl">
</div>

<!--内容主体-->
<div class="ui grid container" style="margin-top: 50px">
    <!-- 项目动态 -->
    <div class="ui ten wide column">
        <h3 class="ui header dividing">项目动态</h3>
        <div class="ui divided items">
            <#list logs as log>
                <div class="item">
                    <div class="content">
                        <h4 class="ui header">
                            ${log.title}
                        </h4>
                        <div class="description">
                            ${log.message!}
                        </div>
                        <div class="extra">
                            ${log.createTime?string('yyyy-MM-dd HH:mm:ss')}
                        </div>
                    </div>
                </div>
            </#list>
        </div>
    </div>
    <!--在线应用列表、我的快照-->
    <div class="ui six wide column">
        <!--在线应用列表-->
        <div class="ui block top attached header left aligned">
            应用列表
            <a class="ui" style="float: right" href="/p/${project.id}/app/list">更多</a>
        </div>
        <div class="ui attached segment" style="min-height: 100px; padding: 0px">
            <table class="ui table " style="border: none">
                <#list apps as app >
                    <tr>
                        <td><a href="/p/${project.id}/app/list?appId=${app.id}">${app.name}</a></td>
                        <td>${app.srcName!}</td>
                        <td>
                            <#-- <a class="ui poping up" data-content="在线实例" data-variation="tiny inverted"
                                href="/p/${project.id}/app/online?appId=${app.id}">${app.onlineCount!}</a>-->
                            <span class="ui poping up" data-content="在线实例" data-variation="tiny inverted">${app.onlineCount!}</span>
                        </td>
                        <td>
                            <a class="ui poping up" data-content="开始监控" data-variation="tiny inverted" href="monitor?appId=${app.id}">
                                <i class="ui play circle icon"></i>
                            </a>
                        </td>
                    </tr>
                </#list>
            </table>
        </div>

        <div class="ui block top attached header left aligned">
            我最近的快照
            <a class="ui" style="float: right" href="/p/${project.id}/snapshot/my">更多</a>
        </div>
        <div class="ui attached segment" style="min-height: 100px;padding: 0px">
            <table class="ui basic table " style="border: none">
                <#list snapshots as snap>
                    <tr>
                        <td>
                            <a href="${mySnapshotDetailHref(project.id, snap.id)}">${snap.name}</a>
                        </td>
                        <td class="right aligned" style="color: grey" title="${snap.createTimeText!'-'}">${snap.createTimeText!'-'}</td>
                    </tr>
                </#list>
            </table>
        </div>

    </div>


</div>

<script>
    /*$('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });*/
    $('.poping.up').popup();
</script>
</body>
</html>
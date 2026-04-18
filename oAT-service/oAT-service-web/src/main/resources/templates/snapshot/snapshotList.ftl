<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>系统快照-快照列表</title>
    <#include "../common.ftl">
</head>
<body>
<#assign usecaseItemActive="active">
<#include "../projectHeader.ftl">

<!--过滤条件-->
<div class="ui container">
    <div class="ui grid">
        <div class="four wide column"></div>
        <div class="ui twelve wide column" style="padding-bottom: 0px">
            <form id="filterForm" class="ui form" action="/p/${project.id}/snapshot/list">
                <div class="ui text  menu" style="margin: 7px;float:right;">
                    <div class="ui multiple dropdown item">
                        <input id="filterLabels" type="hidden" name="labels" value="${filterLabels!}">
                        <i class="tag link icon"></i>
                        <span class="text" style="margin: auto">标签过滤</span>
                        <div class="menu">
                            <div class="ui icon search input">
                                <i class="search icon"></i>
                                <input type="text" placeholder="搜索标签...">
                            </div>
                            <div class="divider"></div>
                            <div class="header">
                                <i class="tags icon"></i>
                                标签
                            </div>
                            <div class="scrolling menu">
                                <#list snapshotLabels as lab>
                                    <div class="item" data-value="${lab.name}">
                                        <div class="ui ${lab.color} empty circular label"></div>
                                        ${lab.name}
                                    </div>
                                </#list>
                            </div>
                        </div>
                    </div>
                    <div class="ui filter dropdown item" tabindex="2">
                        <input id="filterSort" type="hidden" name="sort" value="${sort!}">
                        <i class="ui sort numeric ascending link icon"> </i>
                        <span class="text">排序</span>
                        <div class="left menu transition hidden" tabindex="-1">
                            <div class="item" data-value="updateTime">更新时间</div>
                            <div class="item" data-value="name">快照名称</div>
                        </div>
                    </div>
                </div>
            </form>
        </div>
    </div>
</div>
<!--内容主体-->
<div class="ui grid attached  container">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical menu">
            <div class="header item">用例中心</div>
            <a class="item" href="/p/${project.id}/usecase/list">
                用例中心
            </a>
            <#if snapshots?? && (snapshots?size > 0) && snapshots[0].appId??>
                <a class="item" href="/p/${project.id}/${snapshots[0].appId}/snapshot/list">
                    系统快照
                </a>
                <a class="ui item" href="/p/${project.id}/app/${snapshots[0].appId}/settings">
                    设置
                </a>
            <#else>
                <a class="ui item" href="/p/${project.id}/edit">
                    设置
                </a>
            </#if>
        </div>
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <table class="ui table">
            <thead>
            <tr>
                <th>快照名称</th>
                <th class="three wide">添加时间</th>
                <th class="one wide">操作</th>
            </tr>
            </thead>
            <tbody>
            <#list snapshots as snap>
                <tr>
                    <td>
                        <a class="text" href="${mySnapshotDetailHref(project.id, snap.id)}">
                            ${snap.name}
                        </a>
                        <#if snap.labels??>
                            <#list snap.labels as lab>
                                <div class="ui small label">${lab}</div>
                            </#list>
                        </#if>
                    </td>
                    <td> ${snap.createTimeText!'-'}</td>
                    <td>
                        <div class="spanshotItem ui dropdown">
                            <i class="setting link icon"></i>
                            <div class="ui left menu">
                                <div class="ui dropdown item">
                                    <i class="share alternate icon"></i>
                                    <i class="dropdown icon"></i>
                                    共享设置
                                    <div class="menu">
                                        <div class="header">
                                            <div class="ui toggle checkbox ${snap.id}">
                                                <input type="checkbox" <#if snap.share??&&snap.share==true>
                                                    checked="checked"</#if> >
                                            </div>
                                            <script>
                                                $(function () {
                                                    $('.ui.checkbox.${snap.id}').checkbox({
                                                        onChecked: function () {
                                                            $.getJSON("/p/${project.id}/snapshot/openShare/${snap.id}", function (results) {
                                                                $(".shareUrl.${snap.id}").removeClass("disabled");
                                                            })
                                                        },
                                                        onUnchecked: function () {
                                                            $.getJSON("/p/${project.id}/snapshot/closeShare/${snap.id}", function (results) {
                                                                $(".shareUrl.${snap.id}").addClass("disabled");
                                                            })
                                                        }
                                                    });
                                                })
                                            </script>
                                        </div>
                                        <a class="item shareUrl ${snap.id} <#if snap.share??&&snap.share==true>  <#else>disabled </#if> "
                                           href="/share/snapshot/${snap.id}" target="_blank">
                                            <i class="ui linkify icon "></i>
                                            访问共享页
                                        </a>
                                    </div>
                                </div>
                                <a class="item" onclick="openSnapshotEdit('${snap.id}');"><i class="edit icon"></i>
                                    编辑
                                    <div id="snapshotEditDialog" class="ui dynamic modal standard">
                                    </div>
                                </a>
                                <div class="divider"></div>
                                <a class="item" href="/p/${project.id}/snapshot/doDelete?id=${snap.id}">
                                    <i class="remove icon red color"></i>
                                    <span class="text" style="color: red">删除</span>
                                </a>
                            </div>
                        </div>
                    </td>
                </tr>
            </#list>
            </tbody>
        </table>
    </div>
</div>

<script>
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.spanshotItem.ui.dropdown').dropdown({
        on: 'hover',
        onHide: function (e) {
            console.log(this);
        }
    });

    $('.menu .ui.multiple.dropdown.item').dropdown({
        on: 'click'
    });

    function openSnapshotEdit(id) {
        $('#snapshotEditDialog').load('/p/${project.id}/snapshot/edit?id=' + id);
        $('#snapshotEditDialog').modal('show');
    }

    $(function () {
        $("#filterLabels").change(function () {
            $("#filterForm").submit();
        });
        $("#filterSort").change(function () {
            $("#filterForm").submit();
        });
    });
</script>
</body>
</html>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-在线应用</title>
    <#include "../common.ftl">
    <style>
        .project-settings-page {
            margin-top: 18px;
            margin-bottom: 42px;
        }

        .project-settings-breadcrumb {
            margin: 6px auto 18px !important;
            color: #6b7785;
        }

        .project-settings-layout {
            display: grid;
            grid-template-columns: 280px minmax(0, 1fr);
            gap: 20px;
            align-items: start;
        }

        .project-settings-side,
        .project-settings-main {
            background: #fff;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
        }

        .project-settings-side {
            padding: 18px;
        }

        .project-settings-main {
            overflow: hidden;
        }

        .project-settings-hero {
            padding: 26px 28px;
            background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 55%, #f9fbfd 100%);
            border-bottom: 1px solid #e6eef7;
        }

        .project-settings-hero-label {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.08);
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.04em;
            margin-bottom: 14px;
        }

        .project-settings-hero-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 28px;
            font-weight: 700;
        }

        .project-settings-hero-desc {
            margin: 0;
            color: #617080;
            line-height: 1.8;
            max-width: 780px;
        }

        .project-settings-meta {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 14px;
            margin-top: 20px;
        }

        .project-settings-meta-card {
            background: rgba(255, 255, 255, 0.82);
            border: 1px solid #e4edf7;
            border-radius: 14px;
            padding: 14px 16px;
        }

        .project-settings-meta-label {
            color: #8a97a6;
            font-size: 12px;
            margin-bottom: 6px;
        }

        .project-settings-meta-value {
            color: #1f2937;
            font-size: 18px;
            font-weight: 700;
            line-height: 1.4;
            word-break: break-word;
        }

        .project-settings-content {
            padding: 28px;
        }

        .project-settings-section-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-section-desc {
            margin: 0 0 24px;
            color: #6b7785;
            line-height: 1.75;
        }

        .project-online-table-wrap {
            border: 1px solid #e7edf5;
            border-radius: 16px;
            overflow: hidden;
            background: #fff;
        }

        .project-online-table-wrap .ui.table {
            margin: 0;
            border: none;
        }

        .project-online-table-wrap .ui.table thead th {
            background: #f8fafc;
            color: #475569;
            font-weight: 700;
            border-bottom: 1px solid #e7edf5;
        }

        .project-online-table-wrap .ui.table td,
        .project-online-table-wrap .ui.table th {
            padding-top: 15px;
            padding-bottom: 15px;
            vertical-align: middle;
        }

        .project-online-detail {
            padding: 18px 22px;
            background: #fff8f8;
            border-top: 1px solid #f5d5d5;
        }

        .project-online-detail .ui.list .item {
            padding: 8px 0;
        }

        .project-online-detail-value {
            margin-top: 6px;
            color: #475569;
            word-break: break-word;
        }

        @media only screen and (max-width: 960px) {
            .project-settings-layout {
                grid-template-columns: 1fr;
            }
        }

        @media only screen and (max-width: 767px) {
            .project-settings-hero,
            .project-settings-side,
            .project-settings-content {
                padding: 22px 20px !important;
            }
        }
    </style>
</head>
<body>
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <div class="active section">在线应用</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsOnlineActive="active"/>
            <#assign onlineItemActive="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>
        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="signal icon"></i>
                    在线节点观察
                </div>
                <h1 class="project-settings-hero-title">在线应用</h1>
                <p class="project-settings-hero-desc">
                    查看当前项目下在线应用实例的接入状态、运行时信息和部署详情，帮助快速确认节点健康情况。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">在线实例数</div>
                        <div class="project-settings-meta-value">${sessions?size}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">项目编号</div>
                        <div class="project-settings-meta-value">${project.id}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">当前权限</div>
                        <div class="project-settings-meta-value"><#if loginNameRole == "visitor">访客<#else>${loginNameRole}</#if></div>
                    </div>
                </div>
            </div>

            <div class="project-settings-content">
                <h2 class="project-settings-section-title">在线实例列表</h2>
                <p class="project-settings-section-desc">点击“详情”可展开查看部署路径、进程号、JVM 版本与启动参数等运行时信息。</p>

                <div class="oat-list-toolbar js-list-control" data-table="#onlineAppTable" data-page-size="10" data-search-placeholder="搜索 IP、应用、工程、Agent 版本" data-empty-colspan="6"></div>
                <div class="project-online-table-wrap">
                    <table id="onlineAppTable" class="ui fixed table">
                        <thead>
                        <tr>
                            <th class="three wide">系统IP</th>
                            <th>应用名称</th>
                            <th>项目工程</th>
                            <th class="two wide">agent版本</th>
                            <th class="two wide">在线时长</th>
                            <th class="two wide right aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list sessions as session>
                            <tr class="js-list-row">
                                <td class="three wide">${session.clientInfo.addressIp}</td>
                                <td>${(session.application.appName)!'未定义'}</td>
                                <td>${(session.application.projectSrcName)!''}</td>
                                <td class="two wide">${session.clientInfo.agentVersion}</td>
                                <td class="two wide">${session.onlineTime}</td>
                                <td class="two wide right aligned">
                                    <a class="ui button" onclick="$('#app${session_index}').toggle();">详情</a>
                                </td>
                            </tr>
                            <tr class="js-list-detail">
                                <td id="app${session_index}" colspan="6" style="display: none; padding: 0;">
                                    <div class="project-online-detail">
                                        <div class="ui list">
                                            <div class="item">
                                                <div class="header">部署路径</div>
                                                <div class="project-online-detail-value">${session.clientInfo.systemDir}</div>
                                            </div>
                                            <div class="item">
                                                <div class="header">进程ID</div>
                                                <div class="project-online-detail-value">${session.clientInfo.pid}</div>
                                            </div>
                                            <div class="item">
                                                <div class="header">JVM版本</div>
                                                <div class="project-online-detail-value">${session.clientInfo.jvmVersion}</div>
                                            </div>
                                            <div class="item">
                                                <div class="header">JVM启动参数</div>
                                                <div class="project-online-detail-value">${(session.clientInfo.jvmOption)!''}</div>
                                            </div>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<!--初始化UI-->
<script>
    /*$('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.poping.up').popup();*/

</script>

<script>
    <!--显示节点详情-->
    function openAppDetails(appId) {
        $("#" + appId).toggle();
        // var display= $("#"+appId).css('display');
        //  if (display == 'none') {
        //      $("#" + appId).css('display', 'table-cell');
        //  } else {
        //      $("#" + appId).css('display', 'none');
        //  }
    }
</script>
</body>

</html>

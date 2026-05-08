<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>应用与代码管理</title>
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
            overflow: visible;
        }

        .project-settings-content {
            position: relative;
            z-index: 2;
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

        .project-settings-table-wrap {
            border: 1px solid #e7edf5;
            border-radius: 16px;
            overflow: visible;
            background: #fff;
            position: relative;
            z-index: 5;
        }

        .project-settings-table-wrap .ui.table {
            margin: 0;
            border: none;
            border-radius: 16px;
        }

        .project-settings-table-wrap .ui.table tbody tr:last-child td:first-child {
            border-bottom-left-radius: 16px;
        }

        .project-settings-table-wrap .ui.table tbody tr:last-child td:last-child {
            border-bottom-right-radius: 16px;
        }

        .project-settings-table-wrap .ui.table thead th {
            background: #f8fafc;
            color: #475569;
            font-weight: 700;
            border-bottom: 1px solid #e7edf5;
        }

        .project-settings-table-wrap .ui.table td,
        .project-settings-table-wrap .ui.table th {
            padding-top: 15px;
            padding-bottom: 15px;
            vertical-align: middle;
        }

        .project-app-type {
            display: inline-flex;
            align-items: center;
            padding: 6px 10px;
            border-radius: 999px;
            font-size: 12px;
            font-weight: 700;
            background: #e8f7f6;
            color: #0f766e;
        }

        .project-app-type.muted {
            background: #eef2f7;
            color: #64748b;
        }

        .project-action-menu-trigger {
            width: 34px;
            height: 34px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border-radius: 10px;
            background: #f8fafc;
            color: #475569;
            transition: all 0.2s ease;
        }

        .project-action-menu-trigger:hover {
            background: #eef5ff;
            color: #1d4ed8;
        }

        .project-settings-table-wrap,
        .project-settings-table-wrap .ui.table,
        .project-settings-table-wrap .ui.table tbody,
        .project-settings-table-wrap .ui.table tr,
        .project-settings-table-wrap .ui.table td {
            overflow: visible !important;
        }

        .project-action-dropdown.ui.dropdown {
            position: relative;
            z-index: 20;
        }

        .project-action-dropdown.ui.dropdown.active,
        .project-action-dropdown.ui.dropdown.visible {
            z-index: 1200;
        }

        .project-action-dropdown.ui.dropdown.visible .project-action-menu-trigger {
            opacity: 0;
            pointer-events: none;
        }

        .project-action-dropdown.ui.dropdown .menu {
            min-width: 172px;
            border: 1px solid #e2e8f0;
            border-radius: 12px !important;
            box-shadow: 0 16px 34px rgba(15, 23, 42, 0.16) !important;
            overflow: hidden;
            z-index: 1201 !important;
        }

        .project-action-dropdown.ui.dropdown .menu > .item {
            display: flex !important;
            align-items: center;
            gap: 8px;
            padding: 12px 14px !important;
            color: #334155 !important;
            white-space: nowrap;
        }

        .project-action-dropdown.ui.dropdown .menu > .item:hover {
            background: #f1f7ff !important;
            color: #1678c2 !important;
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
        <div class="active section">应用与代码管理</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsManageCodeActive="active"/>
            <#assign manageAppCodeActive="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>
        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="code branch icon"></i>
                    应用与代码管理
                </div>
                <h1 class="project-settings-hero-title">应用与代码管理</h1>
                <p class="project-settings-hero-desc">
                    统一管理应用关联仓库与版本列表，帮助团队将应用资产与代码资产保持一致。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">应用总数</div>
                        <div class="project-settings-meta-value">${apps?size}</div>
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

            <div id="center-content" class="project-settings-content">
                <h2 class="project-settings-section-title">应用资产入口</h2>
                <p class="project-settings-section-desc">对于主导应用，可进入仓库配置、版本列表等关键入口。</p>

                <div class="oat-list-toolbar js-list-control" data-table="#manageAppCodeTable" data-page-size="10" data-search-placeholder="搜索应用名称、工程或类型" data-empty-colspan="5"></div>
                <div class="project-settings-table-wrap">
                    <table id="manageAppCodeTable" class="ui celled table">
                        <thead>
                        <tr>
                            <th>类型</th>
                            <th>应用名称</th>
                            <th>应用工程</th>
                            <th class="center aligned">在线实例</th>
                            <th class="center aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list apps as app >
                            <#assign repositoryConfigured=(app.repoAddress?? && app.repoAddress?trim != '')>
                            <#assign repositoryConfigTip = '当前应用尚未配置代码仓库，点击可前往仓库配置完成地址与认证信息设置。'>
                            <#if repositoryConfigured>
                                <#assign repositoryConfigTip = '当前应用已配置代码仓库，点击可查看或调整仓库地址、分支与认证信息。'>
                            </#if>
                            <tr>
                                <td>
                                    <#if app.createProjectId==project.id>
                                        <span class="project-app-type">主导应用</span>
                                    <#else >
                                        <span class="project-app-type muted">参与应用</span>
                                    </#if>
                                </td>
                                <td><strong>${app.name}</strong></td>
                                <td>${app.srcName!'-'}</td>
                                <td class="center aligned">
                                    <a href="/p/${project.id}/app/online?appId=${app.id}">${app.onlineCount!0}</a>
                                </td>

                                <td class="center aligned">
                                    <#if loginNameRole != "visitor">
                                        <#if app.createProjectId==project.id>
                                            <div class="ui dropdown quickMenu project-action-dropdown">
                                                <span class="project-action-menu-trigger">
                                                    <i class="setting icon"></i>
                                                </span>
                                                <div class="left menu">
                                                    <a class="item repository-config-menu-item" href="/p/${project.id}/app/${app.id}/repository" data-content="${repositoryConfigTip?html}" data-position="left center" title="${repositoryConfigTip?html}">
                                                        <i class="edit icon"></i>仓库配置
                                                    </a>
                                                    <a class="item" href="/p/${project.id}/${app.id}/version/list">
                                                        <i class="list icon"></i>版本列表
                                                    </a>
                                                </div>
                                            </div>
                                        <#else>
                                            <span style="color: #94a3b8;">-</span>
                                        </#if>
                                    <#else>
                                        <span style="color: #94a3b8;">-</span>
                                    </#if>
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
    $('.project-settings-page .ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.project-settings-page .repository-config-menu-item').popup({
        on: 'hover'
    });


</script>
</body>

</html>

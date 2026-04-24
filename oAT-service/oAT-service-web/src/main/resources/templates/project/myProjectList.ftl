<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>我的项目列表</title>
   <#include "../common.ftl">
    <style>
        .my-projects-page {
            margin-top: 42px;
            margin-bottom: 48px;
        }

        .my-projects-hero {
            padding: 28px 32px !important;
            border-radius: 16px !important;
            background: linear-gradient(135deg, #f7fbff 0%, #eef5ff 45%, #f6f9fc 100%) !important;
            border: 1px solid #dce7f5 !important;
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06) !important;
            margin-bottom: 24px !important;
        }

        .my-projects-hero-label {
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

        .my-projects-hero-title {
            margin: 0 0 10px !important;
            font-size: 2rem !important;
            font-weight: 700 !important;
            color: #1f2937;
        }

        .my-projects-hero-desc {
            margin: 0;
            max-width: 700px;
            font-size: 15px;
            line-height: 1.75;
            color: #5b6675;
        }

        .my-projects-hero-actions {
            display: flex;
            justify-content: flex-end;
            align-items: center;
            height: 100%;
        }

        .my-projects-primary-btn.ui.button {
            border-radius: 10px;
            padding: 12px 20px;
            box-shadow: 0 8px 20px rgba(33, 133, 208, 0.18);
        }

        .my-projects-stats {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }

        .my-projects-stat-card {
            background: #fff;
            border: 1px solid #e8edf4;
            border-radius: 14px;
            padding: 18px 20px;
            box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
        }

        .my-projects-stat-label {
            color: #7b8794;
            font-size: 13px;
            margin-bottom: 10px;
        }

        .my-projects-stat-value {
            color: #1f2937;
            font-size: 28px;
            font-weight: 700;
            line-height: 1;
            margin-bottom: 8px;
        }

        .my-projects-stat-extra {
            color: #8d99a6;
            font-size: 12px;
        }

        .my-projects-toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 12px;
            margin-bottom: 18px;
        }

        .my-projects-toolbar-title {
            margin: 0 !important;
            color: #1f2937;
        }

        .my-projects-toolbar-meta {
            color: #7b8794;
            font-size: 13px;
            margin-top: 6px;
        }

        .my-projects-filter {
            min-width: 260px;
        }

        .my-projects-filter-group {
            display: flex;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
        }

        .my-projects-sort {
            min-width: 170px;
        }

        .my-projects-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
            gap: 22px;
            align-items: stretch;
        }

        .my-project-card.is-recent {
            border-color: #b7d8f4 !important;
        }

        .my-project-card.is-recent .my-project-card-header {
            background: linear-gradient(135deg, #f3f9ff 0%, #eaf4ff 100%);
        }

        .my-project-card.is-highlighted {
            border-color: #9fcaf1 !important;
            box-shadow: 0 18px 42px rgba(33, 133, 208, 0.13) !important;
        }

        .my-project-card {
            display: flex;
            flex-direction: column;
            min-height: 300px;
            border-radius: 20px !important;
            border: 1px solid #e5edf7 !important;
            box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06) !important;
            transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
            overflow: hidden;
            background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
        }

        .my-project-card .content:after,
        .my-project-card .extra:after {
            display: none !important;
        }

        .my-project-card:hover {
            transform: translateY(-4px);
            border-color: #cfe0f5 !important;
            box-shadow: 0 14px 32px rgba(15, 23, 42, 0.09) !important;
        }

        .my-project-card-content {
            display: flex;
            flex-direction: column;
            flex: 1;
            padding: 22px 22px 16px;
        }

        .my-project-card-header {
            margin: -22px -22px 18px;
            padding: 18px 22px 16px;
            background: linear-gradient(135deg, #f8fbff 0%, #eef6ff 100%);
            border-bottom: 1px solid #e6eef8;
        }

        .my-project-card-badges {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            margin-bottom: 14px;
        }

        .my-project-card-top {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 12px;
        }

        .my-project-title-wrap {
            min-width: 0;
            flex: 1;
        }

        .my-project-card-title {
            display: block;
            font-size: 22px;
            font-weight: 800;
            line-height: 1.32;
            color: #172033;
            word-break: break-word;
            overflow-wrap: anywhere;
        }

        .my-project-card-id {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            margin-top: 8px;
            color: #748294;
            font-size: 12px;
            font-weight: 600;
            max-width: 100%;
        }

        .my-project-card-id span {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .my-project-card-title:hover {
            color: #1678c2;
        }

        .my-project-role {
            flex-shrink: 0;
            max-width: 48%;
            border-radius: 999px;
            padding: 6px 11px;
            background: #eaf4ff;
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 700;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .my-project-card-description {
            color: #5f6f82;
            line-height: 1.72;
            min-height: 50px;
            margin-bottom: 18px;
            overflow: hidden;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
        }

        .my-project-card-description.is-empty {
            color: #a0a9b4;
            font-style: italic;
        }

        .my-project-card-meta {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 10px;
            margin-top: auto;
        }

        .my-project-meta-item {
            min-width: 0;
            padding: 12px 13px;
            background: #f8fafc;
            border-radius: 12px;
            border: 1px solid #edf2f7;
        }

        .my-project-meta-item--wide {
            grid-column: span 3;
        }

        .my-project-meta-item--primary {
            background: linear-gradient(135deg, #f0f8ff 0%, #f8fbff 100%);
            border-color: #d8e9fa;
        }

        .my-project-meta-value.is-positive {
            color: #16834a;
        }

        .my-project-meta-value.is-warning {
            color: #b45309;
        }

        .my-project-progress {
            height: 6px;
            margin-top: 10px;
            border-radius: 999px;
            overflow: hidden;
            background: #e7edf5;
        }

        .my-project-progress-bar {
            height: 100%;
            border-radius: inherit;
            background: linear-gradient(90deg, #21ba45 0%, #2185d0 100%);
        }

        .my-project-meta-item--hide-on-card {
            display: none;
        }

        .my-project-meta-label {
            font-size: 12px;
            color: #8b95a1;
            margin-bottom: 6px;
        }

        .my-project-meta-value {
            color: #243041;
            font-weight: 700;
            word-break: break-word;
            overflow-wrap: anywhere;
        }

        .my-project-meta-value.is-compact {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .my-project-card-actions {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 14px 22px 18px;
            border-top: 1px solid #edf2f7;
            background: #fcfdff;
        }

        .my-project-card-settings {
            color: #6b7785;
            font-weight: 500;
        }

        .my-project-quick-note {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            min-width: 0;
            padding: 6px 10px;
            border-radius: 999px;
            background: #ecfdf3;
            color: #21824a;
            font-size: 12px;
            font-weight: 700;
        }

        .my-project-quick-note-placeholder {
            min-height: 26px;
        }

        .my-project-card-settings:hover {
            color: #1678c2;
        }

        .my-project-enter-btn.ui.button {
            border-radius: 9px;
            padding-left: 16px;
            padding-right: 16px;
        }

        .my-project-create-card {
            align-items: center;
            justify-content: center;
            text-align: center;
            min-height: 250px;
            background: linear-gradient(180deg, #fbfdff 0%, #f6faff 100%);
            border: 1px dashed #b7cde5 !important;
        }

        .my-project-create-card .icon {
            color: #2185d0;
            margin-bottom: 12px;
        }

        .my-project-create-card-title {
            font-size: 18px;
            font-weight: 700;
            color: #1f2937;
            margin-bottom: 8px;
        }

        .my-project-create-card-desc {
            color: #6b7785;
            line-height: 1.7;
            max-width: 240px;
            margin: 0 auto 16px;
        }

        .my-project-empty {
            padding: 54px 24px !important;
            border-radius: 16px !important;
            text-align: center;
            border: 1px dashed #c9d7e6 !important;
            background: linear-gradient(180deg, #fbfdff 0%, #f8fbff 100%) !important;
        }

        .my-project-empty-icon {
            font-size: 42px;
            color: #2185d0;
            margin-bottom: 14px;
        }

        .my-project-empty-title {
            font-size: 22px;
            font-weight: 700;
            color: #1f2937;
            margin-bottom: 10px;
        }

        .my-project-empty-desc {
            color: #6b7785;
            max-width: 480px;
            margin: 0 auto 20px;
            line-height: 1.75;
        }

        @media only screen and (max-width: 767px) {
            .my-projects-page {
                margin-top: 24px;
            }

            .my-projects-hero {
                padding: 22px 20px !important;
            }

            .my-projects-hero-actions {
                justify-content: flex-start;
                margin-top: 18px;
            }

            .my-projects-toolbar {
                align-items: stretch;
            }

            .my-projects-filter {
                width: 100%;
            }

            .my-projects-filter-group {
                width: 100%;
            }

            .my-projects-sort {
                width: 100%;
            }

            .my-projects-grid {
                grid-template-columns: 1fr;
            }

            .my-project-card-meta {
                grid-template-columns: repeat(2, minmax(0, 1fr));
            }

            .my-project-meta-item--wide {
                grid-column: span 2;
            }

            .my-project-card-top {
                flex-direction: column;
            }

            .my-project-role {
                max-width: 100%;
            }

            .my-project-card-actions {
                flex-direction: column;
                align-items: stretch;
                gap: 10px;
            }

            .my-project-card-actions .ui.button,
            .my-project-card-actions .my-project-card-settings {
                width: 100%;
                text-align: center;
            }
        }
    </style>
</head>
<body>

<#include  "../normalHeader.ftl">

<#assign projectCount = projects?size>
<#assign describedProjectCount = 0>
<#assign totalDescriptionLength = 0>
<#assign recentProjectCount = 0>
<#list projects as p>
    <#assign currentDescription = p.describe!''>
    <#assign totalDescriptionLength = totalDescriptionLength + currentDescription?length>
    <#if currentDescription?has_content>
        <#assign describedProjectCount = describedProjectCount + 1>
    </#if>
    <#if p.updateTime??>
        <#assign recentProjectCount = recentProjectCount + 1>
    </#if>
</#list>

<div class="ui container my-projects-page">
    <div class="ui stackable grid my-projects-hero">
        <div class="ten wide column">
            <div class="my-projects-hero-label">
                <i class="folder open outline icon"></i>
                项目工作台
            </div>
            <h1 class="my-projects-hero-title">我的项目</h1>
            <p class="my-projects-hero-desc">
                在这里统一查看你参与的测试项目，快速进入项目首页、管理项目配置，并创建新的工作空间。
            </p>
        </div>
        <div class="six wide column my-projects-hero-actions">
            <a class="ui primary large button my-projects-primary-btn" href="/project/create">
                <i class="plus icon"></i>
                新建项目
            </a>
        </div>
    </div>

    <div class="my-projects-stats">
        <div class="my-projects-stat-card">
            <div class="my-projects-stat-label">项目总数</div>
            <div class="my-projects-stat-value">${projectCount}</div>
            <div class="my-projects-stat-extra">你当前可访问的全部项目</div>
        </div>
        <div class="my-projects-stat-card">
            <div class="my-projects-stat-label">有描述的项目</div>
            <div class="my-projects-stat-value">${describedProjectCount}</div>
            <div class="my-projects-stat-extra">项目说明更完整，便于协作</div>
        </div>
        <div class="my-projects-stat-card">
            <div class="my-projects-stat-label">待完善项目</div>
            <div class="my-projects-stat-value">${projectCount - describedProjectCount}</div>
            <div class="my-projects-stat-extra">建议补充项目描述与用途</div>
        </div>
        <div class="my-projects-stat-card">
            <div class="my-projects-stat-label">最近维护项目</div>
            <div class="my-projects-stat-value">${recentProjectCount}</div>
            <div class="my-projects-stat-extra">有更新时间记录的项目</div>
        </div>
    </div>

    <div class="my-projects-toolbar">
        <div>
            <h3 class="ui header my-projects-toolbar-title">项目列表</h3>
            <div class="my-projects-toolbar-meta">支持按项目名称快速筛选，并可按最近更新或创建时间排序。</div>
        </div>
        <div class="my-projects-filter-group">
            <div class="ui icon input my-projects-filter">
                <input type="text" id="projectSearch" placeholder="搜索项目名称或描述...">
                <i class="search icon"></i>
            </div>
            <select class="ui dropdown my-projects-sort" id="projectSort">
                <option value="recent">最近更新优先</option>
                <option value="created">最近创建优先</option>
                <option value="name">按名称排序</option>
            </select>
        </div>
    </div>

    <#if projectCount gt 0>
        <div class="my-projects-grid" id="projectGrid">
            <#list projects as p>
                <#assign projectDescription = p.describe!''>
                <#assign updateTimeMs = (p.updateTime?long)!0>
                <#assign createTimeMs = (p.createTime?long)!0>
                <#assign sortTimestamp = updateTimeMs>
                <#assign completionPercent = 60>
                <#if projectDescription?has_content>
                    <#assign completionPercent = completionPercent + 25>
                </#if>
                <#if p.updateTime??>
                    <#assign completionPercent = completionPercent + 15>
                </#if>
                <#assign completionStatusClass = (completionPercent >= 85)?then('is-positive', 'is-warning')>
                <#if sortTimestamp == 0>
                    <#assign sortTimestamp = createTimeMs>
                </#if>
                <div class="ui card my-project-card project-item <#if p_index == 0>is-highlighted</#if>"
                     data-project-name="${p.name?lower_case}"
                     data-project-desc="${projectDescription?lower_case}"
                     data-project-create="${createTimeMs?c}"
                     data-project-update="${updateTimeMs?c}"
                     data-project-sort="${sortTimestamp?c}">
                    <div class="my-project-card-content">
                        <div class="my-project-card-header">
                            <div class="my-project-card-badges">
                                <#if p_index == 0>
                                    <div class="my-project-quick-note">
                                        <i class="lightning icon"></i>
                                        推荐优先查看
                                    </div>
                                <#else>
                                    <div class="my-project-quick-note-placeholder"></div>
                                </#if>
                                <div class="my-project-role"><#if p.createDisplayName?? && p.createDisplayName?has_content>创建者：${p.createDisplayName}<#elseif p.create?? && p.create?has_content>创建者：${p.create}<#else>我的项目</#if></div>
                            </div>
                            <div class="my-project-card-top">
                                <div class="my-project-title-wrap">
                                    <a class="my-project-card-title" href="/p/${p.id}/home">${p.name}</a>
                                    <div class="my-project-card-id">
                                        <i class="fingerprint icon"></i>
                                        <span>${p.id}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="my-project-card-description <#if !projectDescription?has_content>is-empty</#if>">
                            <#if projectDescription?has_content>
                                ${projectDescription}
                            <#else>
                                暂无项目描述，建议补充项目目标、使用场景或协作说明。
                            </#if>
                        </div>
                        <div class="my-project-card-meta">
                            <div class="my-project-meta-item my-project-meta-item--wide my-project-meta-item--primary recent-project-slot" data-project-id="${p.id}"></div>
                            <div class="my-project-meta-item my-project-meta-item--hide-on-card">
                                <div class="my-project-meta-label">项目编号</div>
                                <div class="my-project-meta-value">${p.id}</div>
                            </div>
                            <div class="my-project-meta-item">
                                <div class="my-project-meta-label">成员数量</div>
                                <div class="my-project-meta-value">${p.memberCount!0}</div>
                            </div>
                            <div class="my-project-meta-item">
                                <div class="my-project-meta-label">创建时间</div>
                                <div class="my-project-meta-value is-compact"><#if p.createTime??>${p.createTime?string('MM-dd HH:mm')}<#else>暂无记录</#if></div>
                            </div>
                            <div class="my-project-meta-item">
                                <div class="my-project-meta-label">最近更新</div>
                                <div class="my-project-meta-value is-compact"><#if p.updateTime??>${p.updateTime?string('MM-dd HH:mm')}<#else>暂无记录</#if></div>
                            </div>
                            <div class="my-project-meta-item my-project-meta-item--wide">
                                <div class="my-project-meta-label">信息完整度</div>
                                <div class="my-project-meta-value ${completionStatusClass}">${completionPercent}%</div>
                                <div class="my-project-progress" aria-label="信息完整度 ${completionPercent}%">
                                    <div class="my-project-progress-bar" style="width: ${completionPercent}%;"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="my-project-card-actions">
                        <a class="my-project-card-settings" href="/p/${p.id}/edit">
                            <i class="setting icon"></i>
                            设置
                        </a>
                        <a class="ui primary button my-project-enter-btn" href="/p/${p.id}/home">
                            进入项目
                        </a>
                    </div>
                </div>
            </#list>
        </div>

        <div class="ui center aligned basic segment" id="projectEmptySearch" style="display: none; margin-top: 18px;">
            <div class="ui icon header" style="color: #7b8794;">
                <i class="search icon"></i>
                没有找到匹配的项目
            </div>
            <p style="color: #8d99a6;">试试更换关键词，或者直接创建一个新项目。</p>
        </div>
    <#else>
        <div class="ui segment my-project-empty">
            <div class="my-project-empty-icon">
                <i class="folder open outline icon"></i>
            </div>
            <div class="my-project-empty-title">你还没有任何项目</div>
            <div class="my-project-empty-desc">
                创建第一个项目后，你就可以在这里统一管理项目首页、应用配置和成员协作信息。
            </div>
            <a class="ui primary large button" href="/project/create">
                <i class="plus icon"></i>
                创建第一个项目
            </a>
        </div>
    </#if>
</div>

<script>
    function updateRecentProjects(projectId, projectName) {
        if (!window.localStorage || !projectId) {
            return;
        }

        var storageKey = 'oat_recent_projects';
        var list = [];

        try {
            list = JSON.parse(localStorage.getItem(storageKey) || '[]');
            if (!Array.isArray(list)) {
                list = [];
            }
        } catch (e) {
            list = [];
        }

        var nextItem = {
            id: projectId,
            name: projectName || '',
            visitedAt: Date.now()
        };

        list = $.grep(list, function (item) {
            return item && item.id !== projectId;
        });
        list.unshift(nextItem);
        localStorage.setItem(storageKey, JSON.stringify(list.slice(0, 8)));
    }

    function formatRecentTime(timestamp) {
        if (!timestamp) {
            return '刚刚访问';
        }

        var diff = Date.now() - timestamp;
        if (diff < 60 * 1000) {
            return '刚刚访问';
        }
        if (diff < 60 * 60 * 1000) {
            return Math.floor(diff / (60 * 1000)) + ' 分钟前访问';
        }
        if (diff < 24 * 60 * 60 * 1000) {
            return Math.floor(diff / (60 * 60 * 1000)) + ' 小时前访问';
        }
        return Math.floor(diff / (24 * 60 * 60 * 1000)) + ' 天前访问';
    }

    function applyProjectFilters() {
        var keyword = $.trim($('#projectSearch').val()).toLowerCase();
        var visibleCount = 0;

        $('.project-item').each(function () {
            var $item = $(this);
            var name = ($item.data('project-name') || '').toString();
            var desc = ($item.data('project-desc') || '').toString();
            var matched = !keyword || name.indexOf(keyword) !== -1 || desc.indexOf(keyword) !== -1;
            $item.toggle(matched);
            if (matched) {
                visibleCount++;
            }
        });

        $('#projectEmptySearch').toggle(visibleCount === 0);
    }

    function sortProjectCards(sortType) {
        var $grid = $('#projectGrid');
        var $cards = $grid.find('.project-item').get();
        var recentProjects = {};

        try {
            $.each(JSON.parse(localStorage.getItem('oat_recent_projects') || '[]'), function (_, item) {
                if (item && item.id) {
                    recentProjects[item.id] = item.visitedAt || 0;
                }
            });
        } catch (e) {
            recentProjects = {};
        }

        $cards.sort(function (a, b) {
            var $a = $(a);
            var $b = $(b);
            var aId = (($a.find('.recent-project-slot').data('project-id')) || '').toString();
            var bId = (($b.find('.recent-project-slot').data('project-id')) || '').toString();
            var aRecent = recentProjects[aId] || 0;
            var bRecent = recentProjects[bId] || 0;
            var aName = (($a.data('project-name') || '') + '').toLowerCase();
            var bName = (($b.data('project-name') || '') + '').toLowerCase();
            var aCreate = parseInt($a.data('project-create'), 10) || 0;
            var bCreate = parseInt($b.data('project-create'), 10) || 0;
            var aSort = parseInt($a.data('project-sort'), 10) || 0;
            var bSort = parseInt($b.data('project-sort'), 10) || 0;

            if (sortType === 'name') {
                return aName.localeCompare(bName);
            }

            if (sortType === 'created') {
                if (bCreate !== aCreate) {
                    return bCreate - aCreate;
                }
                return aName.localeCompare(bName);
            }

            if (aRecent !== bRecent) {
                return bRecent - aRecent;
            }

            if (bSort !== aSort) {
                return bSort - aSort;
            }
            return aName.localeCompare(bName);
        });

        $.each($cards, function (_, card) {
            $grid.prepend(card);
        });

        $grid.find('.project-item').removeClass('is-highlighted').find('.my-project-quick-note').remove();
        $grid.find('.project-item .my-project-card-badges').each(function () {
            var $badges = $(this);
            if (!$badges.find('.my-project-quick-note-placeholder').length) {
                $badges.prepend('<div class="my-project-quick-note-placeholder"></div>');
            }
        });
        var $firstCard = $grid.find('.project-item:visible').first();
        if ($firstCard.length) {
            $firstCard.addClass('is-highlighted');
            $firstCard.find('.my-project-quick-note-placeholder').first().replaceWith(
                $('<div class="my-project-quick-note"><i class="lightning icon"></i>推荐优先查看</div>')
            );
        }
    }

    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $(function () {
        $('#projectMenu').load('/project/projectMenu');
        $('#projectSort').dropdown();

        $('.project-item').each(function () {
            var $item = $(this);
            var projectId = (($item.find('.recent-project-slot').data('project-id')) || '').toString();
            var projectName = (($item.data('project-name') || '') + '').toString();
            var recentInfo = null;

            try {
                $.each(JSON.parse(localStorage.getItem('oat_recent_projects') || '[]'), function (_, item) {
                    if (!recentInfo && item && item.id === projectId) {
                        recentInfo = item;
                    }
                });
            } catch (e) {
                recentInfo = null;
            }

            if (recentInfo) {
                $item.addClass('is-recent');
                $item.find('.recent-project-slot').html(
                    '<div class="my-project-meta-label">最近访问</div>' +
                    '<div class="my-project-meta-value is-positive">' + formatRecentTime(recentInfo.visitedAt) + '</div>'
                );
            } else {
                $item.find('.recent-project-slot').html(
                    '<div class="my-project-meta-label">最近访问</div>' +
                    '<div class="my-project-meta-value">首次访问后会在这里显示</div>'
                );
            }

            $item.find('a[href$="/home"], a[href$="/edit"]').on('click', function () {
                updateRecentProjects(projectId, projectName);
            });
        });

        $('#projectSearch').on('input', function () {
            applyProjectFilters();
            sortProjectCards($('#projectSort').val());
        });

        $('#projectSort').on('change', function () {
            sortProjectCards($(this).val());
            applyProjectFilters();
        });

        sortProjectCards($('#projectSort').val());
        applyProjectFilters();
    });
</script>
</body>
</html>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-应用列表</title>
    <#include "../common.ftl">
    <#--属性编辑器-->
    <script src="/js/codemirror.min.js"></script>
    <script src="/js/properties.js"></script>
    <link href="/css/codemirror.min.css" rel="stylesheet">
    <style type="text/css">
        /*自动调整编辑器高度*/
        .CodeMirror {
            border: 1px solid #eee;
            height: auto;
        }

        .CodeMirror-scroll {
            overflow-y: hidden;
            overflow-x: auto;
        }

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

        .project-settings-section-head {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 16px;
            margin-bottom: 20px;
        }

        .project-settings-section-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-section-desc {
            margin: 0;
            color: #6b7785;
            line-height: 1.75;
        }

        .project-settings-section-head .ui.button {
            border-radius: 10px;
            white-space: nowrap;
        }

        .project-settings-table-wrap {
            border: 1px solid #e7edf5;
            border-radius: 16px;
            overflow: visible;
            background: #fff;
        }

        .project-settings-table-wrap .ui.table {
            border-radius: 16px;
        }

        .project-settings-table-wrap .ui.table thead th:first-child {
            border-top-left-radius: 16px;
        }

        .project-settings-table-wrap .ui.table thead th:last-child {
            border-top-right-radius: 16px;
        }

        .project-settings-table-wrap .ui.table tbody tr:last-child td:first-child {
            border-bottom-left-radius: 16px;
        }

        .project-settings-table-wrap .ui.table tbody tr:last-child td:last-child {
            border-bottom-right-radius: 16px;
        }

        .project-settings-table-wrap .ui.table {
            margin: 0;
            border: none;
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

        .project-settings-table-wrap .ui.table tbody tr:hover {
            background: #fbfdff;
        }

        .project-version-main {
            color: #1f2937;
            font-weight: 600;
        }

        .project-version-sub {
            color: #7b8794;
            font-size: 12px;
            margin-top: 4px;
            line-height: 1.5;
        }

        .project-version-sub code {
            background: #f1f5f9;
            padding: 2px 6px;
            border-radius: 6px;
            color: #0f172a;
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

        .project-settings-table-wrap .ui.dropdown .menu {
            z-index: 1200;
            min-width: 156px;
            box-shadow: 0 12px 30px rgba(15, 23, 42, 0.16);
            border-radius: 12px;
        }

        .project-settings-table-wrap .ui.dropdown .menu > .item {
            white-space: nowrap;
        }

        .app-edit-modal.ui.modal {
            display: none;
            position: fixed !important;
            top: 16px !important;
            left: 50% !important;
            bottom: 16px !important;
            margin: 0 !important;
            transform: translateX(-50%) !important;
            width: min(94vw, 1200px) !important;
            height: calc(100vh - 32px) !important;
            max-height: calc(100vh - 32px) !important;
            border-radius: 18px !important;
            overflow: hidden !important;
            box-shadow: 0 24px 70px rgba(15, 23, 42, 0.22) !important;
        }

        .app-edit-modal.ui.modal.visible,
        .app-edit-modal.ui.modal.active {
            display: flex !important;
            flex-direction: column;
        }

        .app-edit-modal .app-modal-header {
            position: relative;
            flex: 0 0 auto;
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 18px;
            padding: 22px 26px;
            border-bottom: 1px solid #e6eef7;
            background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 55%, #f9fbfd 100%);
        }

        .app-edit-modal .app-modal-label {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            margin-bottom: 8px;
            padding: 5px 11px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.08);
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.04em;
        }

        .app-edit-modal .app-modal-title {
            margin: 0 0 6px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
            line-height: 1.25;
        }

        .app-edit-modal .app-modal-desc {
            margin: 0;
            color: #617080;
            line-height: 1.65;
        }

        .app-edit-modal .app-modal-close {
            margin: 0 !important;
            color: #64748b !important;
            opacity: 1 !important;
            transition: color 0.18s ease, transform 0.18s ease;
        }

        .app-edit-modal .app-modal-close:hover {
            color: #ef4444 !important;
            transform: rotate(90deg);
        }

        .app-edit-modal .app-modal-content {
            flex: 1 1 auto;
            min-height: 0;
            height: auto;
            max-height: none;
            overflow-y: auto;
            overflow-x: hidden;
            padding: 24px 26px 18px !important;
            background: #fff;
            -webkit-overflow-scrolling: touch;
        }

        .app-edit-modal .app-modal-section {
            margin-bottom: 22px;
            padding: 18px;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            background: #fbfdff;
        }

        .app-edit-modal .app-modal-section-title {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 16px;
            color: #334155;
            font-size: 15px;
            font-weight: 700;
        }

        .app-edit-modal .app-modal-section-title .icon {
            color: #2185d0;
        }

        .app-edit-modal .ui.form .field > label,
        .app-edit-modal .ui.form .inline.fields > label {
            color: #334155;
            font-weight: 700;
        }

        .app-edit-modal .ui.form input,
        .app-edit-modal .ui.form textarea {
            border-radius: 12px !important;
            border-color: #d9e3ef !important;
        }

        .app-edit-modal .ui.form input:focus,
        .app-edit-modal .ui.form textarea:focus {
            border-color: rgba(0, 181, 173, .7) !important;
            box-shadow: 0 0 0 3px rgba(0, 181, 173, .12) !important;
        }

        .app-edit-modal textarea[name="describe"] {
            min-height: 120px;
            resize: vertical;
        }

        .app-edit-modal .CodeMirror {
            border: 1px solid #d9e3ef;
            border-radius: 12px;
            min-height: 210px;
            background: #fff;
        }

        .app-edit-modal .CodeMirror-scroll {
            min-height: 210px;
            overflow-y: auto;
        }

        .app-edit-modal .actions.app-modal-actions {
            flex: 0 0 auto;
            position: sticky;
            bottom: 0;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 12px;
            padding: 14px 26px 16px !important;
            border-top: 1px solid #e6eef7;
            background: #f8fafc;
            z-index: 2;
        }

        .app-edit-modal .actions.app-modal-actions:before,
        .app-edit-modal .actions.app-modal-actions:after {
            display: none !important;
        }

        .app-edit-modal .app-modal-actions .ui.button {
            margin: 0 !important;
            border-radius: 10px;
            min-width: 96px;
        }

        .app-edit-modal .app-version-fields .field {
            min-width: 0;
        }

        .app-edit-modal .app-alert-desc {
            margin: -4px 0 16px;
            color: #64748b;
            line-height: 1.65;
        }

        .app-edit-modal .app-alert-desc code {
            display: inline-block;
            margin-top: 4px;
            padding: 2px 6px;
            border-radius: 6px;
            background: #eef5ff;
            color: #1d4ed8;
            font-size: 12px;
            word-break: break-all;
        }

        .app-edit-modal .app-alert-switch {
            padding: 12px 14px;
            border: 1px solid #dbeafe;
            border-radius: 12px;
            background: #f8fbff;
        }

        .app-edit-modal .app-alert-events.inline.fields {
            align-items: center;
            margin-bottom: 0;
        }

        @media only screen and (max-width: 767px) {
            .app-edit-modal .app-modal-header,
            .app-edit-modal .app-modal-content,
            .app-edit-modal .actions.app-modal-actions {
                padding-left: 20px !important;
                padding-right: 20px !important;
            }

            .app-edit-modal .app-modal-section {
                padding: 16px;
            }

            .app-edit-modal.ui.modal {
                top: 8px !important;
                bottom: 8px !important;
                width: calc(100vw - 16px) !important;
                height: calc(100dvh - 16px) !important;
                max-height: calc(100dvh - 16px) !important;
                border-radius: 14px !important;
            }

            .app-edit-modal .actions.app-modal-actions {
                flex-direction: column-reverse;
            }

            .app-edit-modal .app-modal-actions .ui.button {
                width: 100%;
            }
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

            .project-settings-section-head {
                flex-direction: column;
                align-items: stretch;
            }

            .project-settings-section-head .ui.button {
                width: 100%;
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
        <a class="section" href="/p/${project.id}/edit">项目设置</a>
        <span class="divider">/</span>
        <div class="active section">应用列表</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsAppActive="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="grid layout icon"></i>
                    应用资产管理
                </div>
                <h1 class="project-settings-hero-title">应用列表</h1>
                <p class="project-settings-hero-desc">
                    管理项目下的主导应用与参与应用，查看当前版本、关联工程和在线实例，统一维护应用配置与生命周期。
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

            <div class="project-settings-content">
                <div class="project-settings-section-head">
                    <div>
                        <h2 class="project-settings-section-title">项目应用清单</h2>
                        <p class="project-settings-section-desc">查看每个应用的版本、工程来源和在线情况；如有权限，可直接新增、编辑或删除应用。</p>
                    </div>
                    <#if loginNameRole != "visitor">
                        <a class="ui primary button" href="/p/${project.id}/app/create">
                            <i class="plus icon"></i>
                            新增应用
                        </a>
                    </#if>
                </div>

                <div class="oat-list-toolbar js-list-control" data-table="#projectAppTable" data-page-size="10" data-search-placeholder="搜索应用名称、版本、工程或类型" data-empty-colspan="6"></div>
                <div class="project-settings-table-wrap">
                    <table id="projectAppTable" class="ui celled table">
                        <thead>
                        <tr>
                            <th>类型</th>
                            <th>应用名称</th>
                            <th>当前版本</th>
                            <th>应用工程</th>
                            <th class="center aligned">在线实例</th>
                            <th class="center aligned">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list apps as app >
                            <tr>
                                <td>
                                    <#if app.createProjectId==project.id>
                                        <span class="project-app-type">主导应用</span>
                                    <#else >
                                        <span class="project-app-type muted">参与应用</span>
                                    </#if>
                                </td>
                                <td>
                                    <strong>${app.name}</strong>
                                </td>
                                <td>
                                    <#if app.currentVersion??>
                                        <div class="project-version-main">${app.currentVersion}</div>
                                        <#if app.currentBranch?? && app.currentBranch != "">
                                            <div class="project-version-sub">分支：${app.currentBranch}</div>
                                        </#if>
                                        <#if app.currentCommitId?? && app.currentCommitId != "">
                                            <div class="project-version-sub">Commit：<code class="commit-id" data-content="${app.currentCommitId}" data-position="top center">${(app.currentCommitId?length > 8)?then(app.currentCommitId?substring(0,8), app.currentCommitId)}</code></div>
                                        </#if>
                                    <#else>
                                        <span style="color: #94a3b8">未设置</span>
                                    </#if>
                                </td>
                                <td>
                                    ${app.srcName!'-'}
                                </td>
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
                                                    <div class="item" onclick="openEditDialog('${app.id}');">
                                                        <i class="edit icon"></i>编辑
                                                    </div>
                                                    <a class="item" href="${app.id}/oAT.key" download=""><i class="download icon"></i>下载注册文件</a>
                                                    <div class="divider"></div>
                                                    <div class="item" onclick="openDelDialog('${app.id}');">
                                                        <i class="remove icon red color"></i>
                                                        <span class="text" style="color: red">删除</span>
                                                    </div>
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

<div id="editDialog" class="ui modal standard app-edit-modal">

</div>

<!-- 删除用例弹出框-->
<div id="deleteDialog" class="ui small modal">
    <div class="header">删除应用</div>
    <div class="ui negative message">
        <div class="header">
            你确定删除该应用吗？
        </div>
        <p id="deleteUsecaseContent"></p>
    </div>
    <div class="actions">
        <a id="deleteAppButton" class="ui negative button">删除</a>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    $('.project-settings-page .ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.commit-id').popup();

    function openEditDialog(appId) {
        var $editDialog = $("#editDialog");
        $editDialog.html('<div class="ui active centered inline loader" style="margin: 48px auto;"></div>');
        $editDialog.load('/p/${project.id}/app/edit?appId=' + appId, function() {
            $editDialog.modal({
                autofocus: false,
                observeChanges: true,
                detachable: false,
                closable: false,
                transition: 'fade',
                duration: 120,
                onVisible: function() {
                    if (typeof editor !== 'undefined' && editor) {
                        editor.refresh();
                    }
                }
            }).modal('show');
        });
    }

    function openDelDialog(appId) {
        // Set up the click handler for the confirmation button
        $("#deleteAppButton").off('click').on('click', function() {
            $.post('/p/${project.id}/app/doDelete', {appId: appId}, function(res) {
                if (res.success || res.result) {
                    showToast(res.message || '删除成功', 'success');
                    $("#deleteDialog").modal('hide');
                    setTimeout(function() {
                        location.reload();
                    }, 1000);
                } else {
                    showToast(res.message || '删除失败', 'error');
                }
            }).fail(function() {
                showToast('网络请求失败', 'error');
            });
        });

        $("#deleteDialog").modal('show');
    }
</script>
</body>
</html>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-用例详情</title>
     <#include "../common.ftl">
<#--语法高亮-->
    <link href="/css/monokai_sublime.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>

    <style>
        body {
            background: #f6f8fb;
            scroll-behavior: smooth;
        }

        .usecase-page {
            max-width: 1320px;
            margin: 0 auto 40px;
            padding: 0 20px;
        }

        .usecase-toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin-bottom: 16px;
        }

        .usecase-quick-meta {
            display: flex;
            flex-wrap: wrap;
            justify-content: flex-end;
            gap: 10px;
        }

        .usecase-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 7px 12px;
            border-radius: 999px;
            background: rgba(0, 181, 173, 0.08);
            color: #008b87;
            font-size: 12px;
            font-weight: 600;
        }

        .usecase-layout {
            display: grid;
            grid-template-columns: 240px minmax(0, 1fr);
            gap: 24px;
            align-items: start;
        }

        .usecase-side {
            position: sticky;
            top: 110px;
        }

        .usecase-side-card,
        .usecase-main-card,
        .usecase-section {
            background: #ffffff;
            border: 1px solid #e8edf4;
            border-radius: 14px;
            box-shadow: 0 10px 28px rgba(31, 45, 61, 0.05);
        }

        .usecase-side-card {
            overflow: hidden;
        }

        .usecase-side-mobile-toggle {
            display: none;
            width: 100%;
            border: none;
            background: #ffffff;
            padding: 14px 16px;
            font-size: 14px;
            font-weight: 700;
            color: #22313f;
            text-align: left;
            cursor: pointer;
        }

        .usecase-side-title {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 18px;
            border-bottom: 1px solid #eef2f7;
            font-size: 15px;
            font-weight: 700;
            color: #25313c;
        }

        .usecase-anchor-menu {
            border: none;
            box-shadow: none;
            width: 100%;
            margin: 0;
        }

        .usecase-anchor-menu .item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            padding: 14px 18px;
            color: #4c5a67;
            border-left: 3px solid transparent;
            transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
        }

        .usecase-anchor-menu .item:hover {
            background: #f7fafc;
            color: #00a5a5;
            transform: translateX(2px);
        }

        .usecase-anchor-menu .item.active {
            background: linear-gradient(90deg, rgba(0, 181, 173, 0.12) 0%, rgba(0, 181, 173, 0.03) 100%);
            color: #008b87;
            border-left-color: #00b5ad;
        }

        .usecase-anchor-menu .item.active .ui.label {
            background: #00b5ad;
            color: #ffffff;
        }

        .usecase-anchor-menu .ui.label {
            min-width: 28px;
            text-align: center;
            background: #eef3f8;
            color: #6b7a88;
        }

        .usecase-main {
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        .usecase-main-card {
            position: relative;
            overflow: hidden;
            padding: 28px 32px;
        }

        .usecase-main-card:before {
            content: '';
            position: absolute;
            inset: 0 0 auto 0;
            height: 140px;
            background: linear-gradient(135deg, rgba(0, 181, 173, 0.1) 0%, rgba(0, 181, 173, 0) 62%);
            pointer-events: none;
        }

        .usecase-title-row {
            position: relative;
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 16px;
            z-index: 1;
        }

        .usecase-title-wrap {
            min-width: 0;
        }

        .usecase-title {
            margin: 0;
            font-size: 34px;
            line-height: 1.25;
            color: #1f2d3d;
            word-break: break-word;
        }

        .usecase-labels {
            margin-top: 14px;
        }

        .usecase-meta {
            text-align: right;
            color: #7d8a97;
            font-size: 13px;
            line-height: 1.8;
            white-space: nowrap;
        }

        .usecase-summary {
            position: relative;
            z-index: 1;
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 14px;
            margin-top: 24px;
        }

        .usecase-header-divider {
            position: relative;
            z-index: 1;
            margin-top: 22px;
            padding-top: 18px;
            border-top: 1px dashed #dce5ee;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            color: #6f7e8c;
            font-size: 13px;
        }

        .summary-item {
            position: relative;
            overflow: hidden;
            padding: 16px 18px;
            border-radius: 12px;
            background: linear-gradient(180deg, #fbfdff 0%, #f4f8fc 100%);
            border: 1px solid #edf2f7;
        }

        .summary-item:after {
            content: '';
            position: absolute;
            top: 0;
            right: 0;
            width: 72px;
            height: 72px;
            background: radial-gradient(circle, rgba(0, 181, 173, 0.14) 0%, rgba(0, 181, 173, 0) 72%);
            transform: translate(22px, -22px);
        }

        .summary-label {
            font-size: 12px;
            color: #7f8c98;
            letter-spacing: 0.08em;
        }

        .summary-value {
            margin-top: 8px;
            font-size: 26px;
            font-weight: 700;
            color: #22313f;
        }

        .usecase-cover {
            position: relative;
            z-index: 1;
            margin-top: 28px;
            padding-top: 24px;
            border-top: 1px solid #eef2f7;
        }

        .usecase-cover-frame {
            position: relative;
            padding: 14px;
            border-radius: 16px;
            background: linear-gradient(180deg, #ffffff 0%, #f4f8fb 100%);
            border: 1px solid #eaf0f5;
        }

        .usecase-cover-meta {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 12px;
            color: #6c7b88;
            font-size: 13px;
        }

        .usecase-cover img {
            border-radius: 12px;
            box-shadow: 0 12px 30px rgba(31, 45, 61, 0.1);
        }

        .usecase-section {
            position: relative;
            padding: 24px 28px;
            scroll-margin-top: 100px;
        }

        .usecase-relation-grid {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 16px;
        }

        .relation-panel {
            border: 1px solid #edf2f7;
            border-radius: 12px;
            background: #fbfcfe;
            padding: 18px 20px;
        }

        .relation-panel-title {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            margin-bottom: 10px;
            font-size: 15px;
            font-weight: 700;
            color: #22313f;
        }

        .relation-panel-title .ui.label {
            background: #eef3f8;
            color: #627180;
        }

        .usecase-section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin-bottom: 18px;
        }

        .usecase-section-title {
            margin: 0;
            font-size: 22px;
            color: #1f2d3d;
        }

        .usecase-section-subtitle {
            font-size: 13px;
            color: #8b97a3;
        }

        .usecase-empty {
            margin: 0;
            padding: 14px 16px;
            border-radius: 10px;
            background: #f8fafc;
            color: #6f7e8c;
        }

        .usecase-rich-content {
            color: #2d3a45;
            line-height: 1.9;
            word-break: break-word;
            font-size: 15px;
        }

        .usecase-rich-content > :first-child {
            margin-top: 0;
        }

        .usecase-rich-content > :last-child {
            margin-bottom: 0;
        }

        .usecase-rich-content h1,
        .usecase-rich-content h2,
        .usecase-rich-content h3,
        .usecase-rich-content h4 {
            margin-top: 1.8em;
            margin-bottom: 0.75em;
            color: #1e2f3d;
            line-height: 1.35;
        }

        .usecase-rich-content h1,
        .usecase-rich-content h2 {
            padding-bottom: 0.35em;
            border-bottom: 1px solid #e8eef5;
        }

        .usecase-rich-content p,
        .usecase-rich-content ul,
        .usecase-rich-content ol,
        .usecase-rich-content blockquote,
        .usecase-rich-content table {
            margin: 0 0 1.1em;
        }

        .usecase-rich-content ul,
        .usecase-rich-content ol {
            padding-left: 1.4em;
        }

        .usecase-rich-content li + li {
            margin-top: 0.45em;
        }

        .usecase-rich-content blockquote {
            padding: 14px 16px;
            border-left: 4px solid #00b5ad;
            background: #f4fbfb;
            color: #51606d;
            border-radius: 0 10px 10px 0;
        }

        .usecase-rich-content code {
            padding: 2px 6px;
            border-radius: 6px;
            background: #f2f6fa;
            color: #0d5f6a;
            font-size: 0.92em;
        }

        .usecase-rich-content a {
            color: #008f88;
            text-decoration: none;
            border-bottom: 1px solid rgba(0, 143, 136, 0.22);
        }

        .usecase-rich-content hr {
            border: none;
            border-top: 1px dashed #dce5ee;
            margin: 2em 0;
        }

        .usecase-rich-content table {
            width: 100%;
            border-collapse: collapse;
            overflow: hidden;
            border-radius: 12px;
            border: 1px solid #eaf0f5;
        }

        .usecase-rich-content th,
        .usecase-rich-content td {
            padding: 12px 14px;
            border-bottom: 1px solid #edf2f7;
            text-align: left;
        }

        .usecase-rich-content th {
            background: #f8fafc;
            color: #536170;
        }

        .usecase-rich-content img {
            max-width: 100%;
            border-radius: 12px;
            box-shadow: 0 8px 24px rgba(31, 45, 61, 0.08);
        }

        .usecase-rich-content pre {
            overflow: auto;
            border-radius: 10px;
        }

        .relation-list .item {
            padding: 10px 0;
        }

        .relation-list .icon {
            color: #00b5ad;
        }

        .usecase-table {
            border-radius: 12px;
            overflow: hidden;
            border: 1px solid #edf2f7;
        }

        .usecase-table thead th {
            background: #f8fafc;
            color: #5e6c79;
            font-weight: 700;
        }

        .usecase-table tbody tr:hover {
            background: #fbfdff;
        }

        .sql-card-list,
        .remote-card-list {
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .sql-card,
        .remote-card {
            border: 1px solid #eaf0f5;
            border-radius: 14px;
            background: #fbfcfe;
            overflow: hidden;
        }

        .sql-card-summary,
        .remote-card-summary {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 16px;
            padding: 18px 20px;
            cursor: pointer;
        }

        .sql-card-summary::-webkit-details-marker,
        .remote-card-summary::-webkit-details-marker {
            display: none;
        }

        .sql-card-summary:after,
        .remote-card-summary:after {
            content: '展开';
            flex-shrink: 0;
            font-size: 12px;
            color: #8895a2;
        }

        .sql-card[open] .sql-card-summary:after,
        .remote-card[open] .remote-card-summary:after {
            content: '收起';
            color: #00a5a5;
        }

        .sql-card-index,
        .remote-card-index {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-width: 34px;
            height: 34px;
            border-radius: 10px;
            background: rgba(0, 181, 173, 0.1);
            color: #008b87;
            font-weight: 700;
        }

        .sql-card-main,
        .remote-card-main {
            flex: 1;
            min-width: 0;
        }

        .sql-card-title,
        .remote-card-title {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            align-items: center;
            margin-bottom: 8px;
        }

        .sql-card-title strong,
        .remote-card-title strong {
            color: #22313f;
            font-size: 15px;
        }

        .sql-card-preview,
        .remote-card-preview {
            color: #607080;
            line-height: 1.7;
            word-break: break-word;
        }

        .sql-card-body,
        .remote-card-body {
            padding: 0 20px 20px 20px;
        }

        .sql-card-body pre,
        .remote-card-body pre {
            margin: 0;
            border-radius: 12px;
            overflow: auto;
        }

        .back-to-top {
            position: fixed;
            right: 24px;
            bottom: 24px;
            width: 52px;
            height: 52px;
            border: 1px solid rgba(20, 184, 166, 0.26);
            border-radius: 16px;
            background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(240, 253, 250, 0.98) 100%);
            color: #0f766e;
            box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
            backdrop-filter: blur(10px);
            cursor: pointer;
            opacity: 0;
            visibility: hidden;
            transform: translateY(10px);
            transition: opacity 0.2s ease, transform 0.2s ease, visibility 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
            z-index: 20;
        }

        .back-to-top:hover {
            border-color: rgba(20, 184, 166, 0.42);
            box-shadow: 0 20px 42px rgba(15, 23, 42, 0.18);
        }

        .back-to-top i {
            margin: 0;
            font-size: 18px;
        }

        .back-to-top.visible {
            opacity: 1;
            visibility: visible;
            transform: translateY(0);
        }

        @media only screen and (max-width: 1100px) {
            .usecase-layout {
                grid-template-columns: 1fr;
            }

            .usecase-side {
                position: static;
            }

            .usecase-summary {
                grid-template-columns: repeat(2, minmax(0, 1fr));
            }
        }

        @media only screen and (max-width: 900px) {
            .usecase-side-mobile-toggle {
                display: block;
                border-bottom: 1px solid #eef2f7;
            }

            .usecase-anchor-menu {
                display: none;
            }

            .usecase-side-card.mobile-open .usecase-anchor-menu {
                display: block;
            }
        }

        @media only screen and (max-width: 767px) {
            .usecase-page {
                padding: 0 12px 28px;
            }

            .usecase-toolbar,
            .usecase-title-row,
            .usecase-section-header {
                flex-direction: column;
                align-items: flex-start;
            }

            .usecase-quick-meta {
                justify-content: flex-start;
            }

            .usecase-relation-grid {
                grid-template-columns: 1fr;
            }

            .usecase-main-card,
            .usecase-section {
                padding: 20px 18px;
            }

            .sql-card-summary,
            .remote-card-summary {
                padding: 16px;
            }

            .sql-card-body,
            .remote-card-body {
                padding: 0 16px 16px 16px;
            }

            .back-to-top {
                right: 16px;
                bottom: 16px;
            }

            .usecase-title {
                font-size: 28px;
            }

            .usecase-meta {
                text-align: left;
                white-space: normal;
            }

            .usecase-summary {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
<#assign usecaseItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">用例详情</div>
</div>
<div class="usecase-page">
    <div class="usecase-toolbar">
        <a class="ui tiny teal basic button" href="/p/${project.id}/usecase/list">
            <i class="home icon"></i>用例中心
        </a>
        <div class="usecase-quick-meta">
            <span class="usecase-chip"><i class="calendar alternate outline icon"></i>${usecase.updateTime?date}</span>
            <span class="usecase-chip"><i class="user outline icon"></i>${lastUpdateAuthor.name}</span>
        </div>
    </div>

    <div class="usecase-layout">
        <aside class="usecase-side">
            <div class="usecase-side-card">
                <div class="usecase-side-title">
                    页面导航
                    <span class="ui mini basic label">详情</span>
                </div>
                <button class="usecase-side-mobile-toggle" type="button">
                    <i class="list layout icon"></i> 快速跳转
                </button>
                <div class="ui vertical menu usecase-anchor-menu">
                    <a class="item active" data-anchor-target="snapshotHeader" href="#snapshotHeader">
                        <span>我的快照</span>
                        <div class="ui label">${(snapshots?size)!'0'}</div>
                    </a>
                    <a class="item" data-anchor-target="systemSnapshotHeader" href="#systemSnapshotHeader">
                        <span>系统快照</span>
                        <div class="ui label">${(systemSnapshots?size)!'0'}</div>
                    </a>
                    <a class="item" data-anchor-target="defectHeader" href="#defectHeader">
                        <span>测试缺陷</span>
                        <div class="ui label">${(defects?size)!'0'}</div>
                    </a>
                    <a class="item" data-anchor-target="prdHeader" href="#prdHeader">
                        <span>PRD需求</span>
                        <div class="ui label">${(prdRequirements?size)!'0'}</div>
                    </a>
                    <a class="item" data-anchor-target="contentHeader" href="#contentHeader">
                        <span>详情描述</span>
                    </a>
                </div>
            </div>
        </aside>

        <main class="usecase-main">
            <section class="usecase-main-card">
                <div class="usecase-title-row">
                    <div class="usecase-title-wrap">
                        <h1 class="usecase-title">${usecase.title}</h1>
                        <#if labels??>
                            <div class="usecase-labels ui tiny labels">
                                <#list labels as label >
                                    <div class="ui tiny ${label.color} label">${label.name}</div>
                                </#list>
                            </div>
                        </#if>
                    </div>
                    <div class="usecase-meta">
                        <div>
                            <a class="ui dropdown poping up" data-content="修改用例" data-variation="tiny inverted"
                               href="edit?id=${usecase.id}">
                                <i class="icon small grey link setting"></i>
                            </a>
                        </div>
                        <div>${lastUpdateAuthor.name}</div>
                        <div>最后更新于 ${usecase.updateTime?date}</div>
                    </div>
                </div>

                <div class="usecase-summary">
                    <div class="summary-item">
                        <div class="summary-label">快照数量</div>
                        <div class="summary-value">${(snapshots?size)!'0'}</div>
                    </div>
                    <div class="summary-item">
                        <div class="summary-label">系统快照</div>
                        <div class="summary-value">${(systemSnapshots?size)!'0'}</div>
                    </div>
                    <div class="summary-item">
                        <div class="summary-label">测试缺陷</div>
                        <div class="summary-value">${(defects?size)!'0'}</div>
                    </div>
                    <div class="summary-item">
                        <div class="summary-label">PRD需求</div>
                        <div class="summary-value">${(prdRequirements?size)!'0'}</div>
                    </div>
                </div>

                <div class="usecase-header-divider">
                    <span>当前页面聚合了用例说明与关联资源信息</span>
                    <span>点击右上角设置可继续编辑内容</span>
                </div>

                <#if usecase.headImage??>
                    <div class="usecase-cover">
                        <div class="usecase-cover-frame">
                            <div class="usecase-cover-meta">
                                <span><i class="image outline icon"></i> 用例主图</span>
                                <span>点击图片可放大预览</span>
                            </div>
                            <img onclick="$('#headImageDialog').modal('show');" class="ui centered large image"
                                 src="/r/${usecase.headImage}" style="cursor:pointer">
                        </div>
                    </div>

                    <div id="headImageDialog" class="ui modal">
                        <i class="close icon"></i>
                        <img class="ui fluid image" src="/r/${usecase.headImage}">
                    </div>
                </#if>
            </section>

            <section class="usecase-section" id="snapshotHeader">
                <div class="usecase-section-header">
                    <h3 class="usecase-section-title">我的快照</h3>
                    <span class="usecase-section-subtitle">关联测试快照一览</span>
                </div>
                <#if snapshots??>
                    <table class="ui compact small table usecase-table">
                        <thead>
                        <tr>
                            <th>名称</th>
                            <th>说明</th>
                            <th class="right aligned">创建时间</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list snapshots as snap>
                            <tr>
                                <td>
                                    <#if snap.appId??>
                                        <a href="/p/${project.id}/snapshot/my?snapshotId=${snap.id}">${snap.name}</a>
                                    <#else>
                                        <span>${snap.name}</span>
                                    </#if>
                                </td>
                                <td>${snap.describe!''}</td>
                                <td class="right aligned">${snap.createTime?date}</td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                <#else >
                    <p class="usecase-empty">该用例未添加任何快照</p>
                </#if>
            </section>

            <section class="usecase-section" id="systemSnapshotHeader">
                <div class="usecase-section-header">
                    <h3 class="usecase-section-title">关联信息</h3>
                    <span class="usecase-section-subtitle">系统快照、缺陷和 PRD 统一浏览</span>
                </div>
                <div class="usecase-relation-grid">
                    <div class="relation-panel">
                        <div class="relation-panel-title">
                            <span>系统快照</span>
                            <div class="ui mini basic label">${(systemSnapshots?size)!'0'}</div>
                        </div>
                        <#if systemSnapshots??>
                            <div class="ui relaxed list relation-list">
                                <#list systemSnapshots as item>
                                    <div class="item">
                                        <i class="clone outline middle aligned icon"></i>
                                        <div class="content">
                                            <#if item.url?? && item.url?length gt 0>
                                                <a href="${item.url}" <#if item.external>target="_blank" rel="noopener noreferrer"</#if>>${item.name}</a>
                                            <#else>
                                                ${item.name}
                                            </#if>
                                        </div>
                                    </div>
                                </#list>
                            </div>
                        <#else>
                            <p class="usecase-empty">该用例未关联系统快照</p>
                        </#if>
                    </div>

                    <div class="relation-panel" id="defectHeader">
                        <div class="relation-panel-title">
                            <span>测试缺陷</span>
                            <div class="ui mini basic label">${(defects?size)!'0'}</div>
                        </div>
                        <#if defects??>
                            <div class="ui relaxed list relation-list">
                                <#list defects as defect>
                                    <div class="item">
                                        <i class="bug icon"></i>
                                        <div class="content">
                                            <#if defect.url?? && defect.url?length gt 0>
                                                <a href="${defect.url}" <#if defect.external>target="_blank" rel="noopener noreferrer"</#if>>${defect.name}</a>
                                            <#else>
                                                ${defect.name}
                                            </#if>
                                        </div>
                                    </div>
                                </#list>
                            </div>
                        <#else>
                            <p class="usecase-empty">该用例未关联测试缺陷</p>
                        </#if>
                    </div>

                    <div class="relation-panel" id="prdHeader" style="grid-column: 1 / -1;">
                        <div class="relation-panel-title">
                            <span>PRD需求</span>
                            <div class="ui mini basic label">${(prdRequirements?size)!'0'}</div>
                        </div>
                        <#if prdRequirements??>
                            <div class="ui relaxed list relation-list">
                                <#list prdRequirements as item>
                                    <div class="item">
                                        <i class="clipboard list icon"></i>
                                        <div class="content">
                                            <#if item.url?? && item.url?length gt 0>
                                                <a href="${item.url}" <#if item.external>target="_blank" rel="noopener noreferrer"</#if>>${item.name}</a>
                                            <#else>
                                                ${item.name}
                                            </#if>
                                        </div>
                                    </div>
                                </#list>
                            </div>
                        <#else>
                            <p class="usecase-empty">该用例未关联PRD需求</p>
                        </#if>
                    </div>
                </div>
            </section>

            <section class="usecase-section" id="contentHeader">
                <div class="usecase-section-header">
                    <h3 class="usecase-section-title">详情描述</h3>
                    <span class="usecase-section-subtitle">用例背景、步骤与补充说明</span>
                </div>
                <div id="usecaseContent" class="usecase-rich-content">
                    ${usecaseContent!"太懒了 啥也没有写!"}
                </div>
            </section>

        </main>
    </div>
</div>
<button id="backToTop" class="back-to-top" type="button" aria-label="返回顶部" title="返回顶部">
    <i class="angle up icon"></i>
</button>
<script>
    $('.ui.accordion').accordion({
        exclusive: false
    });
    $("#usecaseContent img").attr("class", "ui centered large image");
    $('.ui.dropdown.poping.up').popup();

    var sideCard = $('.usecase-side-card');
    var mobileToggle = $('.usecase-side-mobile-toggle');
    var backToTop = $('#backToTop');
    var anchorItems = $('.usecase-anchor-menu .item');
    var anchorSections = anchorItems.map(function () {
        var targetId = $(this).data('anchor-target');
        return document.getElementById(targetId);
    }).get().filter(Boolean);

    function updateActiveAnchor() {
        var scrollTop = $(window).scrollTop() + 140;
        var currentId = anchorSections.length ? anchorSections[0].id : null;

        anchorSections.forEach(function (section) {
            if ($(section).offset().top <= scrollTop) {
                currentId = section.id;
            }
        });

        anchorItems.removeClass('active');
        anchorItems.filter('[data-anchor-target="' + currentId + '"]').addClass('active');
    }

    function updateBackToTop() {
        if ($(window).scrollTop() > 360) {
            backToTop.addClass('visible');
        } else {
            backToTop.removeClass('visible');
        }
    }

    mobileToggle.on('click', function () {
        sideCard.toggleClass('mobile-open');
    });

    anchorItems.on('click', function () {
        anchorItems.removeClass('active');
        $(this).addClass('active');
        if ($(window).width() <= 900) {
            sideCard.removeClass('mobile-open');
        }
    });

    backToTop.on('click', function () {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    $(window).on('scroll', function () {
        updateActiveAnchor();
        updateBackToTop();
    });

    updateActiveAnchor();
    updateBackToTop();
</script>


</body>
</html>

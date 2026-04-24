<!-- 基础资源引入 -->
<#include "commonFunction.ftl">
<link href="/css/semantic.min.css" rel="stylesheet">
<script src="/js/jquery.min.js"></script>
<script src="/js/semantic.min.js"></script>
<script src="/js/jquery.form.min.js"></script>
<script src="/js/common.js?version=1"></script>
<link href="/css/common.css?version=1" rel="stylesheet">
<link href="/css/theme.css?version=1" rel="stylesheet">
<style>
    .compare-empty-state {
        min-height: 180px;
    }

    .compare-record-heading {
        margin-top: 18px !important;
        margin-bottom: 0 !important;
    }

    .compare-record-segment {
        border-top: none !important;
    }

    .compare-record-stats {
        margin-top: 6px;
        font-size: 0.85em;
        color: #666;
    }

    .compare-record-meta {
        font-size: 0.9em;
        color: #666;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 12px;
        flex-wrap: wrap;
        white-space: normal;
        max-width: 100%;
        line-height: 1.6;
        vertical-align: middle;
    }

    .compare-record-meta > span {
        display: inline-flex;
        align-items: center;
    }

    .compare-record-meta-label {
        color: #999;
    }

    .compare-record-meta-label-old {
        color: #c97a1f;
    }

    .compare-record-meta-label-new {
        color: #2185d0;
    }

    .compare-record-meta .meta-chip {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 999px;
        border: 1px solid #dde3ea;
        background: #f7f8fa;
        color: #4b5563;
        line-height: 1.4;
    }

    .compare-record-meta .commit-id {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 999px;
        border: 1px solid #dde3ea;
        color: #34495e;
        font-size: 12px;
        line-height: 1.4;
    }

    .compare-record-meta .commit-id-old {
        background: #fff4e8;
        border-color: #f0d2b4;
        color: #9a5b16;
    }

    .compare-record-meta .commit-id-new {
        background: #edf6ff;
        border-color: #c8ddf4;
        color: #1f5f96;
    }

    .compare-record-action-group {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
        flex-wrap: wrap;
    }

    .compare-record-action-group .ui.button {
        margin: 0 !important;
        padding-left: 10px;
        padding-right: 10px;
    }
</style>
<#if aiLlmEnabled!true>
<link href="/css/ai-floating-widget.css?v=${.now}" rel="stylesheet">
<!-- AI 统一公共模块（两个模式共用） -->
<script src="/js/ai-common.js?v=${.now}"></script>
<!-- 浮动小窗逻辑（仅在非 Interactive 页面激活） -->
<script src="/js/ai-floating-widget.js?v=${.now}"></script>
</#if>

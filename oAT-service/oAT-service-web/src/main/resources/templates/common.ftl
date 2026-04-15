<!-- 基础资源引入 -->
<link href="/css/semantic.min.css" rel="stylesheet">
<script src="/js/jquery.min.js"></script>
<script src="/js/semantic.min.js"></script>
<script src="/js/jquery.form.min.js"></script>
<script src="/js/common.js?version=1"></script>
<link href="/css/common.css?version=1" rel="stylesheet">
<#if aiLlmEnabled!true>
<link href="/css/ai-floating-widget.css?v=${.now}" rel="stylesheet">
<!-- AI 统一公共模块（两个模式共用） -->
<script src="/js/ai-common.js?v=${.now}"></script>
<!-- 浮动小窗逻辑（仅在非 Interactive 页面激活） -->
<script src="/js/ai-floating-widget.js?v=${.now}"></script>
</#if>

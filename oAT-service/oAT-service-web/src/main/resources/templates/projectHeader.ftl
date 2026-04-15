<#include "partials/projectHeaderNav.ftl">

<#-- 浮动小窗组件：仅在启用 AI 且非 AI Interactive 工作台页面显示（工作台页面有独立全屏 UI） -->
<#if (aiLlmEnabled!true) && !(AIInteractive?? && AIInteractive == "active")>
    <#include "partials/aiFloatingWidgetFull.ftl">
</#if>

<#-- 公共函数引入 -->
<#assign sqlFormat = "com.oAT.web.control.freeMarke.SqlFormatFunction"?new() />
<#assign arrayToString = "com.oAT.web.control.freeMarke.ArrayToStringFunction"?new() />
<#assign beforeTime = "com.oAT.web.control.freeMarke.BeforeTimeFormat"?new() />

<#function relativeTimeText value fallback='-' mode='relative'>
    <#if !value??>
        <#return fallback>
    </#if>
    <#if mode == 'absolute'>
        <#return value?string('yyyy-MM-dd HH:mm:ss')>
    </#if>
    <#if beforeTime??>
        <#return beforeTime(value)>
    </#if>
    <#return fallback>
</#function>

<#macro relativeTime value fallback='-' mode='relative' showTooltip=false tooltip=''>
    <#local text = relativeTimeText(value=value fallback=fallback mode=mode)>
    <#local tooltipText = tooltip>
    <#if !tooltipText?has_content && showTooltip>
        <#if value?? && mode == 'absolute'>
            <#local tooltipText = value?string('yyyy-MM-dd HH:mm:ss')>
        <#else>
            <#local tooltipText = fallback>
        </#if>
    </#if>
    <#if tooltipText?has_content>
        <span title="${tooltipText}">${text}</span>
    <#else>
        ${text}
    </#if>
</#macro>

<#-- 公共函数引入 -->
<#assign sqlFormat = "com.oAT.web.control.freeMarke.SqlFormatFunction"?new() />
<#assign arrayToString = "com.oAT.web.control.freeMarke.ArrayToStringFunction"?new() />
<#assign beforeTime = "com.oAT.web.control.freeMarke.BeforeTimeFormat"?new() />

<#macro relativeTime value fallback='-'>
    <#if value??>
        <#if beforeTime??>
            ${beforeTime(value?datetime)}
        <#else>
            ${value?string('yyyy-MM-dd HH:mm:ss')}
        </#if>
    <#else>
        ${fallback}
    </#if>
</#macro>

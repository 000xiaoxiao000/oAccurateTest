<table class="ui single line selectable tree table" style="border:none">
    <thead>
    <tr style="font-size: 0.9em; color: rgba(0,0,0,0.4);">
        <th>应用名</th>
        <th>类型</th>
        <th>服务/方法</th>
        <th>用时</th>
    </tr>
    </thead>
    <tbody>
    <#list stacks as item >
        <tr nodeId="${item.nodeId}" parentId="${item.parentId!'root'}">
            <td class="collapsing">
                ${item.appName!}
            </td>
            <td>${item.type!}</td>
            <td>
                ${item.serverName!}
                <#if item.nodeId=="0">
                    <i class="ui icon code" onclick="event.stopPropagation(); openCodeMap('${item.traceId}'); return false;" title="代码关系图层"></i>
                </#if>
            </td>
            <td>${item.useTime!} ms</td>
        </tr>
    </#list>
    </tbody>
</table>

<div class="ui small tabular menu" style="margin: -1px 0px 0px -1px;">
    <div class="item active" data-tab="base-info-1">基本信息</div>
    <div class="item" data-tab="param-info-2">参数信息</div>
</div>
<div class="ui tab active" data-tab="base-info-1">
    <div class="ui fluid accordion">
        <div class="title active" style="font-weight:bold;">
            <i class="dropdown icon"></i>
            基本信息
        </div>
        <div class="ui list content active" style="margin:0px 0px 0px 25px">
            <div class="item">
                <span class="listHeader">路径：</span>
                ${node.requestUrl!}
            </div>
            <div class="item">
                <span class="listHeader">请求方法：</span>
                ${node.requestMethod!}
            </div>
            <div class="item">
                <span class="listHeader">状态码：</span>
                <#assign responseCode = (node.responseCode!'')?trim>
                <#if responseCode?matches('2\\d\\d')>
                    <i class="ui green circle icon" style="display: inline"></i>
                <#else>
                    <i class="ui red circle icon" style="display: inline"></i>
                </#if>
                ${node.responseCode!}
            </div>
            <div class="ui fitted divider"></div>
            <div class="item">
                <span class="listHeader">客户端IP:</span>
                ${node.clientIp!'not found'}
            </div>
            <div class="item">
                <span class="listHeader">Cookie:</span>
                ${(node.requestHeader.cookie)!'not found'}
            </div>
            <div class="item">
                <span class="listHeader">user-agent:</span>
                ${(node.requestHeader.userAgent)!'not found'}
            </div>
            <div class="item">
                <span class="listHeader">服务端IP：</span>
                ${node.serverIp}
            </div>
            <div class="ui fitted  divider"></div>
            <div class="item">
                <span class="listHeader">日期时间：</span>
                ${node.beginTime?number_to_datetime}
            </div>
            <div class="item">
                <span class="listHeader">总耗时ms：</span>
                ${node.useTime}
            </div>
        </div>
    </div>
</div>
<div class="ui tab" data-tab="param-info-2">
    <div class="ui fluid accordion">
        <div class="title active" style="font-weight:bold;">
            <i class="dropdown icon"></i>
            请求参数
        </div>
        <div class="ui list content active" style="margin:0px 0px 0px 25px">
            <#if node.requestParamNames?? && (node.requestParamNames?size > 0)>
                <#list node.requestParamNames as paramName>
                    <div class="item">
                        <span class="listHeader">${paramName}:</span>
                        ${(node.requestParamValues[paramName_index])!'null'}
                    </div>
                </#list>
            <#else>
                <div class="item">无 URL/Form 参数</div>
            </#if>
            <div class="ui fitted divider"></div>
            <div class="item">
                <span class="listHeader">请求体:</span>
            </div>
            <div class="item">
                <pre style="white-space: pre-wrap; word-break: break-all; margin: 8px 0 0 0;">${(node.requestBody)!'无请求体'}</pre>
            </div>
        </div>
    </div>
</div>
<script>
    $('.tabular.menu .item').tab();
    $('.ui.accordion').accordion({
        exclusive: false
    });
</script>

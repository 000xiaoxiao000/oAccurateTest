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
                ${node.responseCode!}
            </div>
            <div class="item">
                <span class="listHeader">客户端IP：</span>
                ${node.clientIp!}
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
            <#if params?? && (params?size > 0)>
                <#list params as param>
                    <div class="item">
                        <span class="listHeader">${param.name}:</span>
                        <#if param.value??>
                            <#if param.value?has_content>
                                ${param.value}
                            <#else>
                                <span style="color: #999;">(空值)</span>
                            </#if>
                        <#else>
                            <span style="color: #999;">null</span>
                        </#if>
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

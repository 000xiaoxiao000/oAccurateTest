<h5 class="ui header">基本信息</h5>
<div class="ui list">
    <div class="item">
        请求方法
        <div class="description">
            ${node.requestMethod!}
        </div>
    </div>
    <div class="item">
        请求URL
        <div class="description">
            ${node.requestUrl!}
        </div>
    </div>
    <div class="item">
        请求状态
        <div class="description">
            ${node.responseCode!}
        </div>
    </div>
    <div class="item">
        IP地址
        <div class="description">
            ${node.clientIp!}
        </div>
    </div>
</div>
<h5 class="ui header">请求参数</h5>
<div class="ui list">
    <#list params as param>
        <div class="item">
            ${param.name}
            <div class="description">
                ${param.value!}
            </div>
        </div>
    </#list>
</div>

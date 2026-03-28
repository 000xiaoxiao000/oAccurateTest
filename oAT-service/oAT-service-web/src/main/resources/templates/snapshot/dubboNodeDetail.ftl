<h5 class="ui header">基本信息</h5>
<div class="ui list">
    <div class="item">
        请求接口#方法
        <div class="description" style="word-break: break-all">
        ${node.serviceInterface!}#${node.serviceMethodName!}
        </div>
    </div>
    <div class="item">
        请求状态
        <div class="description">
            <#if node.error??>
                <div class="ui label small empty circular red"></div>
                <span>ERROR</span>
            <#else >
                <div class="ui label small empty circular green"></div>
             <span>OK</span>
            </#if>
        </div>
    </div>
    <div class="item">
        远程IP地址
        <div class="description">
        ${remoteIp!}
        </div>
    </div>
</div>
    <h5 class="ui header"> 请求参数 </h5>
        <pre><code class="json">${node.inParam!}</code></pre>

    <h5 class="ui header"> 返回结果 </h5>
    <#if node.outParam??>
            <pre><code class="json">${node.outParam!}</code></pre>
    <#else >
        <span style="color: #aaaaaa">无任何返回</span>
    </#if >

<#if node.error??>
        <h5 class="ui header"> 异常信息 </h5>
        <div class="ui error message">
            <div class="header">
                ${node.error.type!}
            </div>
            ${node.error.messages!}
        </div>
    <pre><code class="log">${node.errorStack!}</code></pre>
</#if>

<script>
    document.querySelectorAll('pre code.json').forEach(
            function (block) {
                json = JSON.parse(block.innerHTML);
                // console.log(JSON.stringify(json));
                block.innerHTML = JSON.stringify(json, null, 2);
                hljs.highlightBlock(block);
            });
</script>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>找不到指定页面</title>
    <#include "common.ftl">
</head>
<body>
<div class="ui text container" style="margin-top: 100px">
    <img class="ui centered image" src="/images/error.webp" alt="error">
    <div class="ui divider"></div>
    <#if errorMessage??>
        <div class="ui negative message">
            <div class="header">
                错误信息
            </div>
            <p>${errorMessage}</p>
        </div>
    <#else>
        <h4 class="ui header">操作异常</h4>
    </#if>
    <a href="#" onClick="javascript:history.back(-1);">返回上一页</a>
</div>
</body>
</html>
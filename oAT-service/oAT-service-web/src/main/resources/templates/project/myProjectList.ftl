<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>我的项目列表</title>
   <#include "../common.ftl">
</head>
<body>

<#include  "../normalHeader.ftl">
<!--内容主体-->
<div class="ui container" style="margin-top: 50px">

    <!-- 中间内容 -->

    <h3 class="ui dividing header">项目列表</h3>
    <div class="ui cards">

    <#list projects as p>
        <div class="ui card"  >
            <div class="content">
            <#-- <i class="right floated star icon"></i>-->
                <a class="header" href="/p/${p.id}/home">${p.name}</a>
                <div class="description">
                    ${p.describe!}
                </div>
            </div>
            <div class="extra content">
                <#--<span>
                    <i class="users icon"></i>
                    ${p.memberCount}个用户
                </span>-->
                <a class="right floated" href="/p/${p.id}/edit">
                    <i class="setting icon "></i>
                    设置
                </a>
            </div>
        </div>
    </#list>

        <div class="card">
            <div class="content ">
                <div class="header  center aligned ">
                    <a class="ui" href="/project/create"> <i class="add icon"></i>添加新项目</a>
                </div>
            </div>

        </div>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $(function () {
        $('#projectMenu').load('/project/projectMenu');
    });
</script>
</body>
</html>
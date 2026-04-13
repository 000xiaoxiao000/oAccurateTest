<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-应用设置</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单引入-->
<div class="include">
    <#assign settingItemActive="active">
    <#include  "../projectHeader.ftl">
</div>
<!--面包屑导航-->
<div class="ui small breadcrumb">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">设置</div>
</div>

<!--内容主体-->
<div class="ui grid attached  container" style="margin-top: 14px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical attached menu settings-nav">
            <div class="section-title item">
                <div class="ui inline click dropdown">
                    <span>${app.name}</span>
                    <i class="icon click dropdown"></i>
                    <div class="menu">
                        <div class="ui search  icon input">
                            <i class="search icon"></i>
                            <input type="text" name="search" placeholder="搜索...">
                        </div>
                        <div class="header">
                            选择应用
                        </div>
                        <div class="divider"></div>
                        <#list apps as a>
                            <a class="item" href="/p/${project.id}/app/${a.id}/settings#basicInfo">
                                ${a.name}
                            </a>
                        </#list>
                    </div>
                </div>
            </div>
            <a class="top-level item" href="/p/${project.id}/usecase/list">
                用例中心
            </a>
            <a class="top-level item" href="/p/${project.id}/${app.id}/snapshot/list">
                系统快照
            </a>
            <div class="subnav-group item">
                <div class="subnav-label">设置</div>
                <div class="subnav-menu">
                    <a class="item subnav-item active" href="/p/${project.id}/app/${app.id}/settings#basicInfo">应用设置</a>
                </div>
            </div>
        </div>
    </div>
    <script>

    </script>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <h4 id="basicInfo" class="ui header top attached block">
            基本信息
        </h4>
        <div class="ui attached segment">
            <!--比对输入区 （版本选择、开始按钮）-->
            <form class="ui form" action="/p/${project.id}/app/${app.id}/edit" method="post">

                <div class=" field">
                    <label>应用id</label>
                    <input type="text" value="${app.id}" readonly name="id">
                </div>
                <div class="two fields">
                    <div class="field">
                        <label>应用名称</label>
                        <input type="text" name="name" value="${app.name}">
                    </div>
                    <div class=" field">
                        <label>工程名称</label>
                        <input type="text" name="srcName" value="${app.srcName}">
                    </div>
                </div>
                <div class="inline fields">
                    <label>作用范围：</label>
                    <div class="field">
                        <div class="ui radio checkbox ">
                            <input type="radio" name="range" <#if app.range=='only'> checked=""</#if> tabindex="0"
                                   value="only">
                            <label>仅当前项目</label>
                        </div>
                    </div>
                    <div class="field">
                        <div class="ui radio checkbox">
                            <input type="radio" name="range" <#if app.range=='all'> checked=""</#if> tabindex="1"
                                   value="all">
                            <label>所有项目</label>
                        </div>
                    </div>
                </div>


                <div class=" field">
                    <label>应用描述</label>
                    <textarea rows="3" name="describe">${app.describe!}</textarea>
                </div>
                <div class=" field">
                    <label>应用参数</label>
                    必须重启相关应用配置才会生效
                    <textarea rows="3" name="properties">${app.properties!}</textarea>
                </div>
                <#if loginNameRole != "visitor">
                    <div class="ui button positive" type="submit">
                        保存
                    </div>
                </#if>
            </form>
        </div>

        <div id="deleteApp" style="margin-top: 24px">
            <div class="ui attached error  message">
                <div class="header">
                    删除应用
                </div>
                <p class="text left"><i class="octicon octicon-alert"></i> 删除操作会永久清除应用信息，并且 <strong>不可恢复</strong>！</p>
            </div>
            <div class="ui attached segment">
                <form class="ui form" action="/p/${project.id}/app/${app.id}/delete" method="post">
                    <div class="required field">
                        <label for="password">密码</label>
                        <input id="password" name="password" type="password" autofocus="" required="">
                    </div>
                    <#if loginNameRole != "visitor">
                        <div class="ui red button" type="submit">
                            确认删除该应用
                        </div>
                    </#if>
                </form>
            </div>
        </div>

    </div>
</div>

<script>

    $(function () {
        // $('.ui.dropdown').dropdown({
        //      on: 'hover'
        //  });
        //  $('.ui.filter.dropdown').dropdown({
        //      on: 'click'
        //  });

        $('.ui.container .click.dropdown').dropdown({
            on: 'click'
        });
        $(".ui.button[type='submit']").click(function () {
            $(this).parents("form").first().submit();
        });
    });


    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }
</script>
</body>
</html>

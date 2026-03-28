<!-- 头部导航菜单 -->
<style>
    .ui.secondary.menu .active-m.item {
        border-color: #00b5ad !important;
        color: #00b5ad !important;
        background: rgba(0, 0, 0, .05);
    }

    .subMenu {
        display: none;
        position: absolute;
        right: 10px;
        top: 50%;
        transform: translateY(-50%);
    }

    #appItem .menu > .item {
        position: relative;
        padding-right: 120px !important;
        white-space: nowrap;
    }

    .subMenu a {
        /*color: black;*/
    }
</style>
<div id="headerNavigation" class="top ui segment" style="margin: 0px -2px 10px -2px;padding: 5px">
    <div class="ui secondary menu">
        <div class="ui container">
            <a class="item" href="/p/${project.id}/home" style="padding: 0px">
                <img class="ui small image" src="/images/logo.png" style="width: 120px;">
            </a>
            <#--<a class="${searchItemActive!} teal item" href="/p/${project.id}/search">
                <i class="search link icon"></i>
                搜索
            </a>-->
            <a class="${searchItemActive!} teal item" href="/p/${project.id}/map/home">
                <i class="search link icon"></i>
                搜索
            </a>
            <a id="monitorItem" class="ui ${monitorItemActive!}-m teal primary dropdown icon item"
               href="/p/${project.id}/monitor">
                监控台
                <div class="menu">

                </div>
            </a>
            <#--<a class="${usecaseItemActive!} teal item" href="/p/${project.id}/usecase/list">
                用例中心
            </a>-->
            <#--   <a class="${versionItemActive!} teal item" href="/p/${project.id}/version/apps">
                  版本比对
              </a>-->
            <div id="appItem" class="${appCenterActive!}-m ui teal item dropdown"
                 href="/p/${project.id}/AWZNsQzclw2JLmlccYIV/snapshot/list">
                应用中心
                <div class="menu">
                    <div class="ui search icon input" style="margin: 7px 11px 5px 11px">
                        <i class="search icon"></i>
                        <input type="text" name="search" placeholder="搜索...">
                    </div>
                    <a class="item" href="/p/${project.id}/app/online">在线应用</a>
                    <div class="divider"></div>
                    <#list apps as a>
                        <div class="item" href="/p/${project.id}/${a.id}/snapshot/list">
                            <#assign displayVersion = a.currentVersion!'未设置'>
                            <#assign branch = a.currentBranch!''>
                            <#assign commitId = a.currentCommitId!''>
                            <#if displayVersion?length gt 15>
                                <#assign displayVersion = displayVersion?substring(0, 12) + "...">
                            </#if>
                            <span title="${a.name}&#10;版本: ${a.currentVersion!'未设置'}<#if branch != "">&#10;分支: ${branch}</#if><#if commitId != "">&#10;Commit: ${commitId}</#if>">
                                ${a.name} <span style="color: grey; font-size: 0.9em;">(${displayVersion}<#if branch != "">/${branch}</#if>)</span>
                            </span>
                            <div class="subMenu">
                                <a class="popup up" data-content="系统快照"
                                   href="/p/${project.id}/${a.id}/snapshot/list"
                                   data-variation="tiny inverted">
                                    <i class="ui link tiny black camera icon"></i>
                                </a>
                                <a class="popup up" data-content="版本比对"
                                   href="/p/${project.id}/${a.id}/version/compare"
                                   data-variation="tiny inverted">
                                    <i class="ui link tiny black balance scale icon"></i>
                                </a>
                                <a class="popup up" data-content="覆盖率报告"
                                   href="/p/${project.id}/${a.id}/version/report/list?tab=coverage"
                                   data-variation="tiny inverted">
                                    <i class="ui link tiny black chart bar outline icon"></i>
                                </a>
                            </div>
                        </div>
                    </#list>
                </div>
            </div>
            <#--<a class="${settingItemActive!} teal item" href="/p/${project.id}/edit">-->
            <#--项目设置-->
            <#--</a>-->

            <a id="AIInteractive" class="ui ${AIInteractive!}-m teal primary dropdown icon item"
               href="/p/${project.id}/AIInteractive">
                AI Interactive
            </a>

            <div class="right menu">
                <div class="ui dropdown icon item">
                    <i class="add icon"></i>
                    <div class="menu">
                        <a class="item" href="/project/create">创建新项目</a>
                        <a class="item" href="/p/${project.id}/app/create">添加应用</a>
                    </div>
                </div>
                <a class="icon item popup" href="/p/${project.id}/edit"
                   data-content="项目设置"
                   data-variation="mini inverted"
                   data-position="bottom center">

                    <i class="setting icon"></i>
                </a>
                <a class="ui dropdown item" href="/myProjects">
                    ${project.name}
                    <i class="dropdown icon"> </i>
                    <div id="projectMenu" class="menu"></div>
                </a>
                <div class="ui icon dropdown item">
                    <i class="user icon"></i>
                    <div class="menu">
                        <a class="item" href="/user/info">用户设置</a>
                        <div class="disabled item">管理面版</div>
                        <a class="item" href="/user/logout">注销退出</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<#assign msg = message!RequestParameters['message']!>
<#if msg?? && msg != "">
    <div class="ui success message close 3s"
         style="position: fixed;top: 30px;left: calc(80vw/2);min-width: 200px;margin: 5px;padding: 12px">
        <span><i class="check circle icon "></i>${msg}</span>
    </div>
</#if>

<#assign errMsg = errorMessage!RequestParameters['errorMessage']!>
<#if errMsg?? && errMsg != "">
    <div class="ui negative message close 3s"
         style="position: fixed;top: 30px;left: calc(80vw/2);min-width: 200px;margin: 5px;padding: 12px">
        <p><i class="warning circle icon "></i> ${errMsg}</p>
    </div>
</#if>

<script>
    $('#headerNavigation .ui.dropdown').dropdown({
        on: 'hover'
    });
    $('#headerNavigation .popup').popup();

    // 动态加载项目列表菜单
    $(function () {
        $('#projectMenu').load('/project/projectMenu');
        $('#monitorItem .menu').append('<a class="item" href="/p/${project.id}/monitor">实时监控</a>')
        $('#monitorItem .menu').append('<a class="item" href="/p/${project.id}/snapshot/my">我的快照</a>')
    });
    $('#appItem').dropdown({
        on: 'hover',
        selectOnKeydown: false,// 只有在回车时才选择
        onChange: function (value, text, choice) {
            window.location.href = choice.attr('href');
        }
    });
    $('#appItem .menu .item').bind({
        mouseenter: function () {
            $(this).find(".subMenu").css('display', 'inline-block');
        },
        mouseleave: function () {
            $(this).find(".subMenu").css('display', 'none');
        }
    });
    // 出场3秒后基于动画隐藏 消息
    setTimeout("$('.message.close.3s').transition('scale');", 3000);

</script>

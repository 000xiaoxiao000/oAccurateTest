<!--头部菜单引入-->
<div class="app-header top ui segment">
    <div class="ui secondary menu">
        <div class="ui container">
            <a class="app-logo-link item" href="#">
                <img class="app-logo-image ui small image" src="/images/logo.png">
            </a>
            <div class="right menu">
                <a class="ui dropdown item" href="/myProjects">
                    <span class="text">项目选择</span>
                    <i class="dropdown icon"> </i>
                    <div id="projectMenu" class="menu">
                    </div>
                </a>
                <div class="ui icon dropdown item">
                    <i class="user icon"></i>
                    <div class="menu">
                        <a class="item" href="/user/info">用户设置</a>
                        <div class="item">管理面版</div>
                        <a class="item" href="/user/logout">注销退出</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<#assign msg = message!RequestParameters['message']!>
<#if msg?? && msg != "">
<div class="ui success message close 3s app-toast">
    <p><i class="check circle icon "></i>${msg}</p>
</div>
</#if>

<#assign errMsg = errorMessage!RequestParameters['errorMessage']!>
<#if errMsg?? && errMsg != "">
<div class="ui negative message close 3s app-toast">
    <p><i class="warning circle icon "></i> ${errMsg}</p>
</div>
</#if>
<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    // 动态加载项目列表菜单
    $(function () {
        $('#projectMenu').load('/project/projectMenu');
    });
    // 出场3秒后隐藏 消息
    setTimeout("$('.message.close.3s').transition('scale');", 3000);
</script>

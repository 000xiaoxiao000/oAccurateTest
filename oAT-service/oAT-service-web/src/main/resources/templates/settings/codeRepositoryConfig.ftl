<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-仓库配置</title>
    <#include "../common.ftl">
</head>
<body>
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">设置</div>
</div>

<!--内容主体-->
<div class="ui grid attached container" style="margin-top: 5px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign settingsAppActive="active"/>
        <#assign coeRepositoryConfig="active"/>
        <#assign loginRole=loginNameRole />
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <h4 class="ui top attached block header">
            <i class="cog icon"></i> 仓库配置
        </h4>
        <div class="ui attached segment">
            <form class="ui form" method="post" action="/p/${project.id}/app/${app.id}/repository/save">
                <div class="field">
                    <label>仓库地址</label>
                    <input type="text" name="repoAddress" placeholder="https://github.com/username/repo.git"
                           value="${(app.repoAddress)!''}">
                </div>
                <div class="field">
                    <label>认证类型</label>
                    <div class="ui selection dropdown" id="authTypeDropdown">
                        <input type="hidden" id="authTypeInput">
                        <i class="dropdown icon"></i>
                        <div class="default text">用户名/密码</div>
                        <div class="menu">
                            <div class="item" data-value="password">用户名/密码</div>
                            <div class="item" data-value="token">Token</div>
                        </div>
                    </div>
                </div>
                <div class="two fields" id="authFieldsRow">
                    <div class="field" id="usernameField">
                        <label>用户名</label>
                        <input type="text" id="repoUserNameInput" name="repoUserName" placeholder="username"
                               value="${(app.repoUserName)!''}">
                    </div>
                    <div class="field">
                        <label id="passwordLabel">密码</label>
                        <div class="ui icon input">
                            <input type="password" name="repoPassword" placeholder="password or token"
                                   value="${(app.repoPassword)!''}">
                            <i class="eye link icon"
                               onclick="$(this).prev('input').attr('type', $(this).prev('input').attr('type')=='password'?'text':'password');"></i>
                        </div>
                    </div>
                </div>

                <button class="ui secondary button" type="button" onclick="submitRepositoryConfig()">
                    <i class="check icon"></i> 保存配置
                </button>
            </form>
        </div>
    </div>
</div>
<!--初始化UI-->
<script>
    function submitRepositoryConfig() {
        var $form = $('.ui.form');
        var action = $form.attr('action');

        $form.addClass('loading');
        $.ajax({
            type: 'POST',
            url: action,
            data: $form.serialize(),
            success: function(res) {
                $form.removeClass('loading');
                if (res.success || res.result) {
                    showToast(res.message || '保存成功', 'success');
                    setTimeout(function() {
                        window.location.href = "/p/${project.id}/manageAppCode";
                    }, 1000);
                } else {
                    showToast(res.message || '保存失败', 'error');
                }
            },
            error: function() {
                $form.removeClass('loading');
                showToast('网络请求失败', 'error');
            }
        });
    }

    $('.ui.dropdown').dropdown();
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.poping.up').popup();

    // Init Auth Type Dropdown logic
    $('#authTypeDropdown').dropdown({
        onChange: function(value) {
            if (value === 'token') {
                $('#usernameField').hide();
                $('#repoUserNameInput').prop('disabled', true);
                $('#passwordLabel').text('Token');
            } else {
                $('#usernameField').show();
                $('#repoUserNameInput').prop('disabled', false);
                $('#passwordLabel').text('密码');
            }
        }
    });

    // Determine initial state
    $(document).ready(function() {
        var userName = "${(app.repoUserName?js_string)!''}";
        // If username is empty but we have a password, assume it's a Token
        if (userName === '' && "${(app.repoPassword?js_string)!''}" !== '') {
            $('#authTypeDropdown').dropdown('set selected', 'token');
        } else {
            $('#authTypeDropdown').dropdown('set selected', 'password');
        }
    });

</script>

<script>
    <!--显示节点详情-->
    function openAppDetails(appId) {
        $("#" + appId).toggle();
        // var display= $("#"+appId).css('display');
        //  if (display == 'none') {
        //      $("#" + appId).css('display', 'table-cell');
        //  } else {
        //      $("#" + appId).css('display', 'none');
        //  }
    }
</script>
</body>

</html>

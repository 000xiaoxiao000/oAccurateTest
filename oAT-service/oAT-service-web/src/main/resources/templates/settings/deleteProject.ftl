<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-删除项目</title>
    <#include "../common.ftl">
    <style>
        .ui .warning.header {
            background-color: #F9EDBE !important;
            border-color: #F0C36D;
        }

        .ui .warning.segment {
            border-color: #F0C36D;
        }
    </style>
</head>
<body>

<!--头部菜单 引入-->
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
<div class="ui grid attached  container" style="margin-top: 5px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign settingsDeleteActive="active"/>
        <#assign deleteItemActive='active'/>
        <#assign loginRole=loginNameRole />
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide columncontent">

        <h4 class="ui top attached warning header">删除 ${project.name}</h4>
        <div class="ui attached segment">
            <div class="ui red message">
                <p class="text left"><i class="octicon octicon-alert"></i> 删除操作会永久清除您的账户信息，并且 <strong>不可恢复</strong>！</p>
            </div>
            <#if errorMessage??>
                <div class="ui error message">
                    <p>${errorMessage}</p>
                </div>
            </#if>
            <form class="ui form" id="delete-form">
                <div class="required field">
                    <label for="password">密码</label>
                    <div class="ui icon input">
                        <input id="password" name="password" type="password" autofocus="" required="">
                        <i class="eye slash icon link" id="toggle-password"></i>
                    </div>
                </div>
                <div id="error-message-box" class="ui error message" style="display: none;">
                    <p id="error-text"></p>
                </div>
                <#if loginNameRole != "visitor">
                    <div class="ui red button delete-button" id="confirm-delete-btn">
                        确认删除项目
                    </div>
                </#if>
            </form>

        </div>
    </div>
</div>

<script>
    $('#toggle-password').click(function() {
        var input = $('#password');
        if (input.attr('type') === 'password') {
            input.attr('type', 'text');
            $(this).removeClass('eye slash').addClass('eye');
        } else {
            input.attr('type', 'password');
            $(this).removeClass('eye').addClass('eye slash');
        }
    });

    $('#confirm-delete-btn').click(function() {
        var password = $('#password').val();
        if (!password) {
            $('#error-text').text('请输入密码');
            $('#error-message-box').show();
            return;
        }

        $.ajax({
            url: '/p/${project.id}/doDelete',
            type: 'POST',
            data: { password: password },
            success: function(response) {
                if (response.result || response.success) {
                    notifyToast(response.message || '项目已成功移除', 'success');

                    // 延迟 1.5 秒后跳转，确保用户看到提示
                    setTimeout(function() {
                        window.location.href = response.data || '/myProjects';
                    }, 1500);
                } else {
                    // 显示错误信息
                    $('#error-text').text(response.message || '删除失败');
                    $('#error-message-box').show();
                }
            },
            error: function() {
                $('#error-text').text('请求失败，请稍后重试');
                $('#error-message-box').show();
            }
        });
    });
</script>
</body>
</html>

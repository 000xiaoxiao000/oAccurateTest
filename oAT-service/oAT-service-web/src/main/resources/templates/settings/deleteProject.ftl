<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-删除项目</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container app-unified-page">
    <div class="ui small breadcrumb app-unified-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/edit">项目设置</a>
        <span class="divider">/</span>
        <div class="active section">删除项目</div>
    </div>

    <div class="app-unified-layout">
        <div class="app-unified-side">
            <#assign settingsDeleteActive="active"/>
            <#assign deleteItemActive='active'/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="app-unified-main">
            <div class="app-unified-header">
                <div>
                    <div class="app-unified-kicker">
                        <i class="trash alternate outline icon"></i>
                        危险操作
                    </div>
                    <h1 class="app-unified-title">删除 ${project.name}</h1>
                    <p class="app-unified-desc">删除项目会移除项目配置、关联数据和成员协作上下文，请确认不再需要后再执行。</p>
                </div>
            </div>

            <div class="app-unified-content">
                <div class="danger-zone">
                    <div class="danger-zone-header">
                        <div class="danger-zone-title">确认删除项目</div>
                        <p class="danger-zone-desc">此操作会永久清除项目数据，并且不可恢复。</p>
                    </div>
                    <div class="danger-zone-body">
                        <div class="danger-zone-warning">
                            <i class="exclamation triangle icon"></i>
                            <div>删除操作会永久清除您的项目信息，并且 <strong>不可恢复</strong>！请输入密码后确认删除。</div>
                        </div>
                        <#if errorMessage??>
                            <div class="ui error message">
                                <p>${errorMessage}</p>
                            </div>
                        </#if>
                        <form class="ui form app-unified-form" id="delete-form">
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
                                <div class="app-unified-form-actions">
                                    <a class="ui button" href="/p/${project.id}/edit">返回设置</a>
                                    <div class="ui red button delete-button" id="confirm-delete-btn">
                                        确认删除项目
                                    </div>
                                </div>
                            </#if>
                        </form>
                    </div>
                </div>
            </div>
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

<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>用户登录</title>
    <#include "../common.ftl">
</head>
<body class="auth-page">

<canvas id="login-canvas" class="auth-canvas"></canvas>

<div class="auth-shell">
    <div id="login-box" class="auth-card">
        <div class="auth-brand">
            <canvas id="logo-canvas" width="60" height="60"></canvas>
            <div>
                <h1 class="auth-title">账号登录</h1>
                <p class="auth-subtitle">欢迎回来，继续管理你的测试资产</p>
            </div>
        </div>

        <#if newUser??>
            <div id="succeed-msg" class="ui success message">
                <i class="close icon" onclick="$('#succeed-msg').hide()"></i>
                <div class="header">注册成功！</div>
                <p>现在，你可以登录你的用户名。</p>
            </div>
        </#if>

        <#if errorMessage??>
            <div id="login-error-msg" class="ui error message">
                <p><i class="warning circle icon"></i>${errorMessage}</p>
            </div>
        </#if>

        <form class="ui large form auth-form" action="/doLogin" method="post">
            <div class="ui segment">
                <input type="hidden" name="redirect" value="${redirect!''}">
                <div class="field required">
                    <label>用户名或邮箱</label>
                    <div class="ui left icon input">
                        <i class="user icon"></i>
                        <input type="text" name="nameOrEmail" placeholder="请输入用户名或邮箱地址" value="${(newUser.name)!''}" autocomplete="username" required>
                    </div>
                </div>
                <div class="field required">
                    <label>密码</label>
                    <div class="ui left icon input">
                        <i class="lock icon"></i>
                        <input type="password" name="password" id="password" placeholder="请输入密码" autocomplete="current-password" required>
                        <i class="eye slash icon link auth-password-toggle" id="toggle-password" tabindex="0" role="button" aria-label="显示或隐藏密码"></i>
                    </div>
                </div>
                <input class="ui fluid large teal submit button" type="submit" value="登录">
            </div>
        </form>

        <div class="auth-link-row">
            <span>还没有账号？</span>
            <a href="/register">立即注册</a>
        </div>
    </div>
</div>

<script src="/js/login-animation.js"></script>
<script>
    $(document).ready(function() {
        function togglePassword() {
            const passwordField = $('#password');
            const isPassword = passwordField.attr('type') === 'password';
            passwordField.attr('type', isPassword ? 'text' : 'password');
            $('#toggle-password').toggleClass('eye slash', !isPassword).toggleClass('eye', isPassword);
        }

        $('#toggle-password').on('click', togglePassword);
        $('#toggle-password').on('keydown', function(event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                togglePassword();
            }
        });

        <#if errorMessage??>
            $('#login-box').addClass('shake-animation');
            if (window.setLoginError) {
                window.setLoginError();
            }
        </#if>
    });
</script>
</body>
</html>

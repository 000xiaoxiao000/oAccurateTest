<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>注册用户</title>
    <#include "../common.ftl">
</head>
<body class="auth-page">

<canvas id="login-canvas" class="auth-canvas"></canvas>

<div class="auth-shell auth-shell-wide">
    <div class="auth-card" id="register-card">
        <div class="auth-submit-overlay">
            <div class="auth-submit-card">
                <i class="notched circle loading icon"></i>
                正在注册账号，请稍候...
            </div>
        </div>
        <div class="auth-brand">
            <canvas id="logo-canvas" width="60" height="60"></canvas>
            <div>
                <h1 class="auth-title">注册新账户</h1>
                <p class="auth-subtitle">创建账号，开始使用 oAccurate Test</p>
            </div>
        </div>

        <form id="register-form" class="ui form auth-form" method="post" action="/doRegister">
            <div class="ui segment">
                <div class="auth-form-subhint">
                    <i class="info circle icon"></i>
                    <div>请确认邮箱和密码无误；点击注册后页面会暂时锁定，避免重复提交。</div>
                </div>
                <div class="field required">
                    <label>用户名 <span class="auth-subtitle">只能包含数字、字母、下划线</span></label>
                    <div class="ui left icon input">
                        <i class="user icon"></i>
                        <input type="text" name="name" placeholder="请输入用户名" autocomplete="username" pattern="^[A-Za-z0-9_]+$" required>
                    </div>
                </div>
                <div class="field">
                    <label>自定义名称 <span class="auth-subtitle">大家熟悉你的名字</span></label>
                    <div class="ui left icon input">
                        <i class="id badge outline icon"></i>
                        <input type="text" name="nickname" placeholder="请输入昵称" autocomplete="nickname">
                    </div>
                </div>
                <div class="field required">
                    <label>邮箱</label>
                    <div class="ui left icon input">
                        <i class="mail icon"></i>
                        <input type="email" name="email" placeholder="请输入邮箱地址" autocomplete="email" required>
                    </div>
                </div>
                <div class="field required">
                    <label>密码</label>
                    <div class="ui left icon input">
                        <i class="lock icon"></i>
                        <input type="password" name="password" id="password" placeholder="请输入密码" autocomplete="new-password" minlength="6" required>
                        <i class="eye slash icon link auth-password-toggle" data-toggle-password="#password" tabindex="0" role="button" aria-label="显示或隐藏密码"></i>
                    </div>
                </div>

                <div class="field required">
                    <label>确认密码</label>
                    <div class="ui left icon input">
                        <i class="lock icon"></i>
                        <input type="password" name="againPassword" id="againPassword" placeholder="请再次输入密码" autocomplete="new-password" minlength="6" required>
                        <i class="eye slash icon link auth-password-toggle" data-toggle-password="#againPassword" tabindex="0" role="button" aria-label="显示或隐藏确认密码"></i>
                    </div>
                </div>
                <input id="registerSubmitButton" class="ui fluid large teal submit button" type="submit" value="注册">
                <a class="ui fluid large basic button auth-secondary-action" href="/login">返回登录</a>
            </div>
            <div class="ui error message"></div>
        </form>
    </div>
</div>

<script src="/js/login-animation.js"></script>
<script>
    $(document).ready(function() {
        function togglePassword(trigger) {
            const icon = $(trigger);
            const target = $(icon.data('toggle-password'));
            const isPassword = target.attr('type') === 'password';
            target.attr('type', isPassword ? 'text' : 'password');
            icon.toggleClass('eye slash', !isPassword).toggleClass('eye', isPassword);
        }

        $('[data-toggle-password]').on('click', function() {
            togglePassword(this);
        });

        $('[data-toggle-password]').on('keydown', function(event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                togglePassword(this);
            }
        });

        function setRegisterSubmitting(submitting) {
            var $form = $('#register-form');
            $('#register-card').toggleClass('auth-submitting', submitting);
            oatSetFormSubmitting($form, submitting, {
                submitButton: '#registerSubmitButton',
                keepFieldsEnabled: true,
                readonlyFields: true,
                extraControls: '.auth-secondary-action, [data-toggle-password]'
            });
        }

        $('#register-form').form({
            onSuccess: function() {
                if (oatIsFormSubmitting($('#register-form'))) {
                    return false;
                }
                setRegisterSubmitting(true);
                return true;
            },
            fields: {
                name: {
                    identifier: 'name',
                    rules: [
                        { type: 'empty', prompt: '请输入用户名' },
                        { type: 'regExp[/^[A-Za-z0-9_]+$/]', prompt: '用户名只能包含数字、字母、下划线' }
                    ]
                },
                email: {
                    identifier: 'email',
                    rules: [
                        { type: 'empty', prompt: '请输入邮箱地址' },
                        { type: 'email', prompt: '请输入有效的邮箱地址' }
                    ]
                },
                password: {
                    identifier: 'password',
                    rules: [
                        { type: 'empty', prompt: '请输入密码' },
                        { type: 'minLength[6]', prompt: '密码至少需要 6 位' }
                    ]
                },
                againPassword: {
                    identifier: 'againPassword',
                    rules: [
                        { type: 'empty', prompt: '请再次输入密码' },
                        { type: 'match[password]', prompt: '两次输入的密码不一致' }
                    ]
                }
            }
        });
    });
</script>
</body>
</html>

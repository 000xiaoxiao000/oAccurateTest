<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用户登录</title>
    <#include "../common.ftl">
    <style>
        body {
            margin: 0;
            overflow: hidden;
            background: #f4f7f6;
        }

        #login-canvas {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            z-index: -1;
        }

        .login-container {
            background: rgba(255, 255, 255, 0.9) !important;
            border-radius: 8px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            padding: 2em;
        }

        /* 抖动动画 */
        .shake-animation {
            animation: shake 0.5s cubic-bezier(.36,.07,.19,.97) both;
            transform: translate3d(0, 0, 0);
            backface-visibility: hidden;
            perspective: 1000px;
        }

        @keyframes shake {
            10%, 90% { transform: translate3d(-1px, 0, 0); }
            20%, 80% { transform: translate3d(2px, 0, 0); }
            30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
            40%, 60% { transform: translate3d(4px, 0, 0); }
        }
    </style>
</head>
<body>

<canvas id="login-canvas"></canvas>

<div class="ui middle aligned center aligned grid" style="width: 100%;height: 100%; position: relative; z-index: 1;">
    <div class="column" style="width: 500px">
        <div id="login-box" class="login-container">
            <h1 class="ui teal image header" style="display: flex; align-items: center; justify-content: center;">
                <canvas id="logo-canvas" width="60" height="60" style="margin-right: 0.5em;"></canvas>
                <div class="content">
                    账号登录
                </div>
            </h1>
            <#if newUser??>
                <div id="succeed-msg" class="ui success message">
                    <i class="close icon" onclick="$('#succeed-msg').hide()"></i>
                    <div class="header">
                        注册成功！
                    </div>
                    <p>现在，你可以登录你的用户名。</p>
                </div>
            </#if>

            <#if errorMessage??>
                <div id="succeed-msg" class="ui error message">
                    <p>${errorMessage}</p>
                </div>
            </#if>

            <form class="ui large form" action="/doLogin" method="post">
                <div class="ui segment">
                    <input type="hidden" name="redirect" value="${redirect!''}">
                    <div class="field">
                        <div class="ui left icon input">
                            <i class="user icon"></i>
                            <input type="text" name="nameOrEmail" placeholder="用户名/邮箱地址" value="${(newUser.name)!''}">
                        </div>
                    </div>
                    <div class="field">
                        <div class="ui left icon input">
                            <i class="lock icon"></i>
                            <input type="password" name="password" id="password" placeholder="密码">
                            <i class="eye slash icon link" id="toggle-password" style="position: absolute; right: 10px;
                            cursor: pointer; pointer-events: auto;"></i>
                        </div>
                    </div>
                    <input class="ui fluid large teal submit button" type="submit" value="登录">
                </div>
            </form>
        </div>

<#--        <div class="ui message">-->
<#--            新用户？ <a href="register">注册</a>-->
<#--        </div>-->
    </div>
</div>

<script src="/js/login-animation.js"></script>
<script>
    $(document).ready(function() {
        // 切换密码显示/隐藏
        $('#toggle-password').on('click', function() {
            const passwordField = $('#password');
            const isPassword = passwordField.attr('type') === 'password';
            passwordField.attr('type', isPassword ? 'text' : 'password');

            // 切换图标：如果是密码状态则显示眼，如果是明文状态则显示划掉的眼
            if (isPassword) {
                $(this).removeClass('eye slash').addClass('eye');
            } else {
                $(this).removeClass('eye').addClass('eye slash');
            }
        });

        <#if errorMessage??>
            // 触发抖动
            $('#login-box').addClass('shake-animation');
            // 触发小人表情
            if (window.setLoginError) {
                window.setLoginError();
            }
        </#if>
    });
</script>
</body>
</html>

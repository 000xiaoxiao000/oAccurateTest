<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>账户设置-修改密码</title>
      <#include "../common.ftl">
</head>
<body>

<!--头部菜单引入-->
<#include  "../normalHeader.ftl">

<!--内容主体-->
<div class="ui grid attached  container">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical menu">
            <h4 class="ui item header">账户设置</h4>
            <a class="item" href="/user/info">
                基本信息
            </a>
            <a class="item active " href="/user/password">
                更改密码
            </a>
        </div>
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <h4 class="ui top attached block header">基本信息</h4>
        <div class="ui attached segment">
            <div id="successMessage" class="ui success message" style="display: none">
                <div class="header">
                    密码修改成功！
                </div>
                <p>现在你可以用新密码登陆了</p>
            </div>
            <div id="failMessage" class="ui negative message" style="display: none">
                <div class="header">
                    密码修改失败！
                </div>
                <p class="message">输入了错误的旧密码！</p>
            </div>
            <form id="passwordForm" class="ui form">
                <div class="required field">
                    <label>原密码</label>
                    <label>
                        <input type="password" name="oldPassword" placeholder="原密码">
                    </label>
                </div>
                <div class="required field">
                    <label>新密码</label>
                    <label>
                        <input type="password" name="newPassword" placeholder="新密码">
                    </label>
                </div>
                <div class="required field">
                    <label>新密码确认</label>
                    <label>
                        <input type="password" name="newPasswordConfirm" placeholder="新密码确认">
                    </label>
                </div>
                <button class="ui button" type="button" onclick="doUpdate();">更改密码</button>
            </form>
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

    function doUpdate() {
        if (!$("#passwordForm").form('validate form')) {
           return false;
        }
        var response = $.post({
            url: "/user/doUpdatePassword",
            type: "POST",
            data: $("#passwordForm").serialize(),
            async: false
        }).responseJSON;

        if (response.results) {
            $("#successMessage").show();
            $("#failMessage").hide();
        } else {
            $("#failMessage").show();
            $("#successMessage").hide();
            $("#failMessage .message").html(response.errorMessage);
        }
    }

    $('.ui.form')
            .form({
                inline: false, // 行内显示验证异常
                onFailure: function (formErrors, fields) {
                    if (formErrors && formErrors.length > 0) {
                        showToast(formErrors[0], 'error');
                    }
                    return false;
                },
                // on     : 'blur',// 即时验证
                fields: {
                    oldPassword: {
                        identifier: 'oldPassword',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入旧密码'
                            }
                        ]
                    }, newPassword: {
                        identifier: 'newPassword',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入新密码'
                            }
                        ]
                    }, newPasswordConfirm: {
                        identifier: 'newPasswordConfirm',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请确认新密码'
                            }
                        ]
                    },
                }
            });

</script>
</body>
</html>

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
<div class="ui container app-page-shell account-settings-page page-theme" id="passwordPage">
    <div class="account-submit-mask">
        <div class="account-submit-card">
            <i class="notched circle loading icon"></i>
            正在更改密码，请稍候...
        </div>
    </div>
    <div class="account-settings-hero">
        <div>
            <div class="account-settings-eyebrow">Account Settings</div>
            <h2 class="account-settings-title">账户设置</h2>
            <p class="account-settings-desc">统一管理你的账户基础资料与安全设置，保持个人信息准确可靠。</p>
        </div>
        <i class="shield alternate icon account-settings-hero-icon"></i>
    </div>

    <div class="account-settings-layout">
        <aside class="account-settings-sidebar">
            <div class="account-settings-sidebar-title">设置菜单</div>
            <div class="account-settings-nav">
                <a class="account-settings-nav-item" href="/user/info">
                    <i class="id card outline icon"></i>
                    <span>基本信息</span>
                </a>
                <a class="account-settings-nav-item active" href="/user/password">
                    <i class="lock icon"></i>
                    <span>更改密码</span>
                </a>
            </div>
        </aside>

        <main class="account-settings-content">
            <div class="account-settings-section-head">
                <div>
                    <h3 class="account-settings-section-title">更改密码</h3>
                    <p class="account-settings-section-desc">建议使用更强的密码组合，定期更新以提升账户安全性。</p>
                </div>
            </div>
            <div id="successMessage" class="ui success message account-settings-message" style="display: none">
                <div class="header">
                    密码修改成功！
                </div>
                <p>现在你可以用新密码登陆了</p>
            </div>
            <div id="failMessage" class="ui negative message account-settings-message" style="display: none">
                <div class="header">
                    密码修改失败！
                </div>
                <p class="message">输入了错误的旧密码！</p>
            </div>
            <form id="passwordForm" class="ui form account-settings-form">
                <div class="required field">
                    <label>原密码</label>
                    <div class="ui left icon input">
                        <i class="key icon"></i>
                        <input type="password" name="oldPassword" placeholder="请输入原密码">
                    </div>
                </div>
                <div class="required field">
                    <label>新密码</label>
                    <div class="ui left icon input">
                        <i class="lock icon"></i>
                        <input type="password" name="newPassword" placeholder="请输入新密码">
                    </div>
                </div>
                <div class="required field">
                    <label>新密码确认</label>
                    <div class="ui left icon input">
                        <i class="check circle outline icon"></i>
                        <input type="password" name="newPasswordConfirm" placeholder="请再次输入新密码">
                    </div>
                </div>
                <div class="account-settings-actions">
                    <button id="passwordSubmitButton" class="ui primary button" type="button" onclick="doUpdate();">
                        <i class="sync alternate icon"></i>
                        更改密码
                    </button>
                </div>
            </form>
        </main>
    </div>
</div>

<script>
    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });

    function setPasswordSubmitting(submitting) {
        var $form = $("#passwordForm");
        $('#passwordPage').toggleClass('account-submitting', submitting);
        oatSetFormSubmitting($form, submitting, {
            submitButton: '#passwordSubmitButton',
            extraControls: '.account-settings-nav-item'
        });
    }

    function doUpdate() {
        var $form = $("#passwordForm");
        if (oatIsFormSubmitting($form)) {
            return false;
        }
        if (!$form.form('validate form')) {
           return false;
        }
        var data = $form.serialize();
        setPasswordSubmitting(true);
        $.post({
            url: "/user/doUpdatePassword",
            type: "POST",
            data: data,
            success: function(response) {
                if (response.results) {
                    $("#successMessage").show();
                    $("#failMessage").hide();
                } else {
                    $("#failMessage").show();
                    $("#successMessage").hide();
                    $("#failMessage .message").html(response.errorMessage);
                }
                setPasswordSubmitting(false);
            },
            error: function() {
                $("#failMessage").show();
                $("#successMessage").hide();
                $("#failMessage .message").html('网络请求失败');
                setPasswordSubmitting(false);
            }
        });
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

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>账户设置-基本信息</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单引入-->
<#include  "../normalHeader.ftl">

<!--内容主体-->
<div class="ui container app-page-shell account-settings-page page-theme">
    <div class="account-settings-hero">
        <div>
            <div class="account-settings-eyebrow">Account Settings</div>
            <h2 class="account-settings-title">账户设置</h2>
            <p class="account-settings-desc">统一管理你的账户基础资料与安全设置，保持个人信息准确可靠。</p>
        </div>
        <i class="user circle outline icon account-settings-hero-icon"></i>
    </div>

    <div class="account-settings-layout">
        <aside class="account-settings-sidebar">
            <div class="account-settings-sidebar-title">设置菜单</div>
            <div class="account-settings-nav">
                <a class="account-settings-nav-item active" href="/user/info">
                    <i class="id card outline icon"></i>
                    <span>基本信息</span>
                </a>
                <a class="account-settings-nav-item" href="/user/password">
                    <i class="lock icon"></i>
                    <span>更改密码</span>
                </a>
            </div>
        </aside>

        <main class="account-settings-content">
            <div class="account-settings-section-head">
                <div>
                    <h3 class="account-settings-section-title">基本信息</h3>
                    <p class="account-settings-section-desc">用于登录和展示的账户资料，更新后会立即生效。</p>
                </div>
            </div>
            <form class="ui form account-settings-form" action="/user/doUpdateInfo">
                <div class="required field">
                    <label>邮箱地址</label>
                    <div class="ui left icon input">
                        <i class="mail outline icon"></i>
                        <input type="text" name="email" value="${userInfo.email}" placeholder="请输入邮箱地址">
                    </div>
                </div>
                <div class="required field">
                    <label>用户名称</label>
                    <div class="ui left icon input">
                        <i class="user outline icon"></i>
                        <input type="text" name="name" value="${userInfo.name}" placeholder="请输入用户名">
                    </div>
                </div>
                <div class="account-settings-actions">
                    <button class="ui primary button" type="submit">
                        <i class="save outline icon"></i>
                        更新基本信息
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

    $('.ui.form')
                .form({
                    inline: false, // 关闭行内显示验证异常
                    onFailure: function (formErrors, fields) {
                        if (formErrors && formErrors.length > 0) {
                            showToast(formErrors[0], 'error');
                        }
                        return false;
                    },
                    // on     : 'blur',// 即时验证
                fields: {
                    describe: {
                        identifier: 'email',
                        rules: [
                            {
                                type: 'email',
                                prompt: '请输入合法的邮箱地址'
                            }
                        ]
                    },
                    name: {
                        identifier: 'name',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入用户名称'
                            },
                            {
                                type: 'maxLength[50]',
                                prompt: '用户名称不能超过10个字符'
                            }
                        ]
                    }
                }
            });

</script>
</body>
</html>

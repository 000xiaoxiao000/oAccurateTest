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
<div class="ui grid attached  container">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <div class="ui vertical menu">
            <h4 class="ui item header">账户设置</h4>
            <a class="item active" href="/user/info">
                基本信息
            </a>
            <a class="item  " href="/user/password">
                更改密码
            </a>
        </div>
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <h4 class="ui top attached block header">基本信息</h4>
        <div class="ui attached segment">
            <form class="ui form" action="/user/doUpdateInfo">
                <div class="required field">
                    <label>邮箱地址</label>
                    <input type="text" name="email" value="${userInfo.email}" placeholder="邮箱地址">
                </div>
                <div class="required field">
                    <label>用户名称</label>
                    <input type="text" name="name" value="${userInfo.name}" placeholder="用户名">
                </div>
                <button class="ui button" type="submit">更新基本信息</button>
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

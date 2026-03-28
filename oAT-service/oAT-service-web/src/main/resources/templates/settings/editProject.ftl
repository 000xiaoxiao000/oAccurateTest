<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>项目设置-基本信息</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<!--内容主体-->
<div class="ui grid attached  container" style="margin-top: 5px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign editItemActive="active"/>
        <#assign loginRole=loginNameRole />
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">
        <h4 class="ui top attached block header">基本信息</h4>
        <div class="ui attached segment">
            <form class="ui form" method="post" action="doEdit">
                <div class="field required">
                    <label>名称</label>
                    <input type="text" name="name" placeholder="项目名称" value="${projectInfo.name}">
                </div>
                <div class="field">
                    <label>描述</label>
                    <textarea name="describe" placeholder="项目描述">${projectInfo.describe} </textarea>
                </div>
                <#if loginNameRole != "visitor">
                    <button class="ui button" type="submit">更新基本信息</button>
                </#if>
                <div class="ui error message">

                </div>
            </form>
        </div>
    </div>
</div>

<script>
    $('.ui.form')
        .form({
            inline: false,
            onFailure: function (formErrors, fields) {
                if (formErrors && formErrors.length > 0) {
                    showToast(formErrors[0], 'error');
                }
                return false;
            },
            fields: {
                name: {
                    identifier: 'name',
                    rules: [
                        {
                            type: 'empty',
                            prompt: '请输入项目名称'
                        },
                        {
                            type: 'minLength[4]',
                            prompt: '项目名称至少包含4个字符'
                        },
                        {
                            type: 'maxLength[50]',
                            prompt: '项目名称不能超过50个字符'
                        }
                    ]
                },
                describe: {
                    identifier: 'describe',
                    rules: [
                        {
                            type: 'maxLength[512]',
                            prompt: '项目描述不能超过512个字符'
                        }
                    ]
                }
            }
        });
</script>
</body>
</html>

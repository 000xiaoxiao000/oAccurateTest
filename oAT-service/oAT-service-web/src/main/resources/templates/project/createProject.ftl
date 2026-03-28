<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>创建新项目</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单引入-->
<#include  "../normalHeader.ftl">

<!--内容主体-->
<div class="ui text container" style="margin-top: 50px">

    <!-- 中间内容 -->

    <h3 class="ui center aligned top attached block header">创建新项目</h3>
    <div class="ui attached segment">
        <form class="ui form">
            <div class="field required">
                <label class="label">项目名称:</label>
                <input type="text" name="name" placeholder="输入项目名称">
            </div>

            <div class="field ">
                <label class="label">项目描述:</label>
                <textarea rows="5" name="describe"></textarea>
            </div>
            <div class="ui" style="float: right">
                <button class="ui button positive" type="button" onclick="submitCreateProject()">创建新的项目</button>
                <button class="ui button" type="reset">重置</button>
            </div>
            <br>
            <br>
        </form>
    </div>
</div>

<script>
    function submitCreateProject() {
        var $form = $('.ui.form');
        if (!$form.form('is valid')) {
            return;
        }

        var data = $form.serialize();
        $.post('/project/doCreate', data, function(res) {
            if (res.success || res.result) {
                showToast(res.message || '项目创建成功', 'success');
                if (res.data) {
                    setTimeout(function() {
                        location.href = res.data;
                    }, 1000);
                }
            } else {
                showToast(res.message || '项目创建失败', 'error');
            }
        }).fail(function() {
            showToast('网络请求失败', 'error');
        });
    }

    $('.ui.dropdown').dropdown({
        on: 'hover'
    });
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.ui.form')
            .form({
                inline : false, // 行内显示验证异常
                // on     : 'blur',// 即时验证
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

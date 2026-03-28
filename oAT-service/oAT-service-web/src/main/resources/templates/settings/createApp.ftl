<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>${project.name}-新增应用</title>

<#--<link href="/css/font-awesome.min.css" rel="stylesheet">
<link href="https://cdn.bootcss.com/semantic-ui/2.3.1/semantic.min.css" rel="stylesheet">

<script src="https://cdn.bootcss.com/jquery/3.1.1/jquery.min.js"></script>
<script src="https://cdn.bootcss.com/semantic-ui/2.3.1/semantic.min.js"></script>
<script src="/js/d3.min.js"></script>-->

    <script src="/js/codemirror.js"></script>
    <script src="/js/properties.js"></script>
    <link href="/css/codemirror.css" rel="stylesheet">

<#include "../common.ftl">

    <style type="text/css">
        .ui.right {
            float: right
        }

        /*自动调整编辑器高度*/
        .CodeMirror {
            border: 1px solid #eee;
            height: auto;
        }

        .CodeMirror-scroll {
            overflow-y: hidden;
            overflow-x: auto;
        }

    </style>
</head>
<body>
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">
<!-- 面包屑导航 -->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section">项目设置</a>
    <span class="divider">/</span>
    <a href="list" class="section">应用列表</a>
    <span class="divider">/</span>
    <div class="active section">新增</div>
</div>


<div class="ui text container " style="margin: 20px">
    <h3 class="ui top  attached block center aligned  header">
    ${project.name}-新增应用
    </h3>
    <div class="ui attached segment">
        <form class="ui form">
            <input value="${project.id}" type="hidden" name="createProjectId">
            <div class="field required">
                <label>应用名称：</label>
                <input type="text" name="name" placeholder="输入简短的应用名称">
            </div>
            <div class="inline fields">
                <label for="fruit">作用范围：</label>
                <div class="field">
                    <div class="ui radio checkbox ">
                        <input type="radio" name="range" checked="" tabindex="0" value="only">
                        <label>仅当前项目</label>
                    </div>
                </div>
                <div class="field">
                    <div class="ui radio checkbox">
                        <input type="radio" name="range" tabindex="1" value="all">
                        <label>所有项目</label>
                    </div>
                </div>
            </div>
            <div class="field required">
                <label>工程名称：</label>
                <input type="text" name="srcName" placeholder="输入项目源码的中工程名">
            </div>

            <div class=" field">
                <label>当前版本：</label>
                <div class="three fields">
                    <div class="field">
                        <input type="text" name="currentVersion" placeholder="当前版本号">
                    </div>
                    <div class="field">
                        <input type="text" name="currentBranch" placeholder="当前分支">
                    </div>
                    <div class="field">
                        <input type="text" name="currentCommitId" placeholder="当前CommitId">
                    </div>
                </div>
            </div>

            <div class=" field">
                <label>应用描述：</label>
                <textarea placeholder="输入应用描述" name="describe"> </textarea>
            </div>

            <div class=" field">
                <label>属性配置：</label>
            <#-- textarea 赋值必须整成一行,否则显示多余的空格 -->
                <textarea id="propertiesEdit" placeholder="输入配置信息"
                          name="properties"><#include "appInitConfig.properties"></textarea>
            </div>

            <div class="ui right">
                <button class="ui button positive" type="button" onclick="submitCreateApp()">创建应用</button>
                <button class="ui button" type="reset">重置</button>
            </div>
            <br>
            <br>
        </form>
    </div>

</div>

<script>
    function submitCreateApp() {
        var $form = $('.ui.form');
        if (!$form.form('is valid')) {
            return;
        }

        var data = $form.serialize();
        $.post('/p/${project.id}/app/doCreate', data, function (res) {
            if (res.success || res.result) {
                showToast(res.message || '应用创建成功', 'success');
                if (res.data) {
                    setTimeout(function () {
                        location.href = res.data;
                    }, 1000);
                }
            } else {
                showToast(res.message || '应用创建失败', 'error');
            }
        }).fail(function () {
            showToast('异常请求', 'error');
        });
    }

    $(function () {
        // 设置表单验证规则
        $('.ui.form')
                .form({
                    inline: false, // 关闭行内显示验证异常，使用Toast
                    keyboardShortcuts: false, // 关闭回车提交（不启作用）
                    on: 'blur',// 即时验证
                    onFailure: function (formErrors, fields) {
                        // 验证失败回调
                        if (formErrors && formErrors.length > 0) {
                            // 显示第一条错误信息，或者显示所有
                            // 这里选择显示第一条，避免Toast太多
                            showToast(formErrors[0], 'error');
                        }
                        return false; // 阻止提交
                    },
                    fields: {
                        name: {
                            identifier: 'name',

                            rules: [
                                {
                                    type: 'empty',
                                    prompt: '请输入应用名称'
                                },
                                {
                                    type: 'minLength[4]',
                                    prompt: '应用名称至少包含4个字符'
                                },
                                {
                                    type: 'maxLength[50]',
                                    prompt: '应用名称不能超过50个字符'
                                }
                            ]
                        },
                        srcName: {
                            identifier: 'srcName',
                            rules: [
                                {
                                    type: 'empty',
                                    prompt: '请输入工程名称'
                                },
                                {
                                    type: 'minLength[10]',
                                    prompt: '工程名称至少包含10个字符'
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
                                    type: 'maxLength[256]',
                                    prompt: '项目描述不能超过256个字符'
                                }
                            ]
                        }
                    }
                });


    });

</script>
<#--初始化属性编辑器-->
<script>
    var editor = CodeMirror.fromTextArea(document.getElementById("propertiesEdit"), {
        lineNumbers: true
    });


</script>

</body>
</html>

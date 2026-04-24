<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-新增应用</title>

<#--<link href="/css/font-awesome.min.css" rel="stylesheet">
<link href="https://cdn.bootcss.com/semantic-ui/2.3.1/semantic.min.css" rel="stylesheet">

<script src="https://cdn.bootcss.com/jquery/3.1.1/jquery.min.js"></script>
<script src="https://cdn.bootcss.com/semantic-ui/2.3.1/semantic.min.js"></script>
<script src="/js/d3.min.js"></script>-->

    <script src="/js/codemirror.min.js"></script>
    <script src="/js/properties.js"></script>
    <link href="/css/codemirror.min.css" rel="stylesheet">

<#include "../common.ftl">

    <style type="text/css">
        .CodeMirror {
            border: 1px solid #d9e3ef;
            border-radius: 12px;
            height: auto;
        }

        .CodeMirror-scroll {
            overflow-y: hidden;
            overflow-x: auto;
        }

        .project-settings-page {
            margin-top: 18px;
            margin-bottom: 42px;
        }

        .project-settings-breadcrumb {
            margin: 6px auto 18px !important;
            color: #6b7785;
        }

        .project-settings-layout {
            display: grid;
            grid-template-columns: 280px minmax(0, 1fr);
            gap: 20px;
            align-items: start;
        }

        .project-settings-side,
        .project-settings-main {
            background: #fff;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
        }

        .project-settings-side {
            padding: 18px;
        }

        .project-settings-main {
            overflow: hidden;
        }

        .project-settings-hero {
            display: flex;
            justify-content: space-between;
            gap: 18px;
            padding: 26px 28px;
            background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 55%, #f9fbfd 100%);
            border-bottom: 1px solid #e6eef7;
        }

        .project-settings-hero-label {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.08);
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.04em;
            margin-bottom: 14px;
        }

        .project-settings-hero-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 28px;
            font-weight: 700;
        }

        .project-settings-hero-desc {
            margin: 0;
            color: #617080;
            line-height: 1.8;
            max-width: 780px;
        }

        .project-settings-hero-actions {
            flex-shrink: 0;
        }

        .project-settings-hero-actions .ui.button,
        .project-settings-actions .ui.button {
            border-radius: 10px;
        }

        .project-settings-form-wrap {
            padding: 28px;
        }

        .project-settings-form .field > label {
            margin-bottom: 8px;
            color: #334155;
            font-weight: 600;
        }

        .project-settings-form .ui.input input,
        .project-settings-form input,
        .project-settings-form textarea {
            border-radius: 12px !important;
            border-color: #d9e3ef !important;
            padding: 13px 14px !important;
            font-size: 14px;
        }

        .project-settings-form textarea {
            min-height: 150px;
            resize: vertical;
        }

        .project-settings-form .inline.fields {
            align-items: center;
        }

        .project-settings-form .three.fields > .field {
            min-width: 0;
        }

        .project-settings-actions {
            display: flex;
            justify-content: flex-end;
            gap: 12px;
            margin-top: 28px;
        }

        @media only screen and (max-width: 960px) {
            .project-settings-layout {
                grid-template-columns: 1fr;
            }
        }

        @media only screen and (max-width: 767px) {
            .project-settings-form-wrap,
            .project-settings-hero,
            .project-settings-side {
                padding: 22px 20px !important;
            }

            .project-settings-hero {
                flex-direction: column;
            }

            .project-settings-actions {
                flex-direction: column-reverse;
            }

            .project-settings-actions .ui.button,
            .project-settings-hero-actions .ui.button {
                width: 100%;
            }
        }
    </style>
</head>
<body>
<#assign settingItemActive="active">
<#assign settingsAppActive="active"/>
<#assign loginRole=loginNameRole!'visitor' />
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/edit">项目设置</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/app/list">应用列表</a>
        <span class="divider">/</span>
        <div class="active section">新增应用</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div>
                    <div class="project-settings-hero-label">
                        <i class="plus square outline icon"></i>
                        应用资产管理
                    </div>
                    <h1 class="project-settings-hero-title">新增应用</h1>
                    <p class="project-settings-hero-desc">
                        为当前项目登记新的应用资产，补充工程名称、版本信息与属性配置，便于后续快照、版本和在线实例管理。
                    </p>
                </div>
                <div class="project-settings-hero-actions">
                    <a class="ui button" href="/p/${project.id}/app/list">
                        <i class="left arrow icon"></i>
                        返回应用列表
                    </a>
                </div>
            </div>

            <div class="project-settings-form-wrap">
                <form class="ui form project-settings-form">
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

            <div class="project-settings-actions">
                <a class="ui button" href="/p/${project.id}/app/list">取消并返回</a>
                <button class="ui button" type="reset">重置</button>
                <button class="ui primary button" type="button" onclick="submitCreateApp()">
                    <i class="save outline icon"></i>
                    创建应用
                </button>
            </div>
        </form>
            </div>
        </div>
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

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-基本信息</title>
    <#include "../common.ftl">
    <style>
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

        .project-settings-meta {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 14px;
            margin-top: 20px;
        }

        .project-settings-meta-card {
            background: rgba(255, 255, 255, 0.82);
            border: 1px solid #e4edf7;
            border-radius: 14px;
            padding: 14px 16px;
        }

        .project-settings-meta-label {
            color: #8a97a6;
            font-size: 12px;
            margin-bottom: 6px;
        }

        .project-settings-meta-value {
            color: #1f2937;
            font-size: 18px;
            font-weight: 700;
            line-height: 1.4;
            word-break: break-word;
        }

        .project-settings-meta-subvalue {
            margin-top: 4px;
            color: #8a97a6;
            font-size: 12px;
            font-weight: 500;
            line-height: 1.4;
        }

        .project-settings-form-wrap {
            padding: 28px;
        }

        .project-settings-form-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-form-desc {
            margin: 0 0 24px;
            color: #6b7785;
            line-height: 1.75;
        }

        .project-settings-form .field > label {
            margin-bottom: 8px;
            color: #334155;
            font-weight: 600;
        }

        .project-settings-form .ui.input input,
        .project-settings-form textarea {
            border-radius: 12px !important;
            border-color: #d9e3ef !important;
            padding: 13px 14px !important;
            font-size: 14px;
        }

        .project-settings-form textarea {
            min-height: 170px;
            resize: vertical;
        }

        .project-settings-field-meta {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin-top: 8px;
            color: #8a97a6;
            font-size: 12px;
        }

        .project-settings-form .field {
            margin-bottom: 22px !important;
        }

        .project-settings-actions {
            display: flex;
            justify-content: flex-end;
            gap: 12px;
            margin-top: 28px;
        }

        .project-settings-actions .ui.button {
            border-radius: 10px;
            min-width: 120px;
            padding-top: 12px;
            padding-bottom: 12px;
        }

        .project-settings-lock {
            margin-top: 18px;
            padding: 14px 16px;
            border-radius: 12px;
            background: #fff8e6;
            border: 1px solid #f3dfaf;
            color: #8a6d1f;
            line-height: 1.7;
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

            .project-settings-actions {
                flex-direction: column-reverse;
            }

            .project-settings-actions .ui.button {
                width: 100%;
            }
        }
    </style>
</head>
<body>

<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/edit">项目设置</a>
        <span class="divider">/</span>
        <div class="active section">基本信息</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsBasicActive="active"/>
            <#assign editItemActive="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="settings icon"></i>
                    基本信息管理
                </div>
                <h1 class="project-settings-hero-title">${projectInfo.name}</h1>
                <p class="project-settings-hero-desc">
                    在这里更新项目名称和描述信息，让团队成员更容易识别项目目标、维护范围和协作方式。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">项目编号</div>
                        <div class="project-settings-meta-value">${projectInfo.id}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">项目创建者</div>
                        <div class="project-settings-meta-value">
                            <#if projectInfo.createDisplayName?? && projectInfo.createDisplayName?has_content>
                                ${projectInfo.createDisplayName}
                                <div class="project-settings-meta-subvalue">${projectInfo.create!'-'}</div>
                            <#else>
                                ${projectInfo.create!'-'}
                            </#if>
                        </div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">成员数量</div>
                        <div class="project-settings-meta-value">${projectInfo.memberCount!0}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">当前权限</div>
                        <div class="project-settings-meta-value"><#if loginNameRole == "visitor">访客<#else>${loginNameRole}</#if></div>
                    </div>
                </div>
            </div>

            <div class="project-settings-form-wrap">
                <h2 class="project-settings-form-title">编辑基本信息</h2>
                <p class="project-settings-form-desc">建议项目名称简洁明确，项目描述重点说明测试对象、适用场景或协作说明。</p>

                <form class="ui form project-settings-form" method="post" action="doEdit">
                    <div class="field required">
                        <label>项目名称</label>
                        <div class="ui input">
                            <input type="text" name="name" maxlength="50" placeholder="项目名称" value="${projectInfo.name}">
                        </div>
                        <div class="project-settings-field-meta">
                            <span>建议与项目列表中的命名保持一致，方便快速检索</span>
                            <span><span id="editNameCount">${(projectInfo.name!'')?length}</span>/50</span>
                        </div>
                    </div>

                    <div class="field">
                        <label>项目描述</label>
                        <textarea name="describe" maxlength="512" placeholder="项目描述">${projectInfo.describe!}</textarea>
                        <div class="project-settings-field-meta">
                            <span>建议补充目标、范围、负责人或测试场景说明</span>
                            <span><span id="editDescribeCount">${(projectInfo.describe!'')?length}</span>/512</span>
                        </div>
                    </div>

                    <#if loginNameRole != "visitor">
                        <div class="project-settings-actions">
                            <a class="ui button" href="/p/${project.id}/home">返回项目首页</a>
                            <button class="ui primary button" type="submit">
                                <i class="save outline icon"></i>
                                更新基本信息
                            </button>
                        </div>
                    <#else>
                        <div class="project-settings-lock">
                            <i class="lock icon"></i>
                            你当前是访客权限，只能查看项目基本信息，无法直接修改。
                        </div>
                    </#if>
                    <div class="ui error message"></div>
                </form>
            </div>
        </div>
    </div>
</div>

<script>
    function updateEditCounters() {
        $('#editNameCount').text(($('input[name="name"]').val() || '').length);
        $('#editDescribeCount').text(($('textarea[name="describe"]').val() || '').length);
    }

    $('.ui.form')
        .form({
            inline: false,
            onFailure: function (formErrors) {
                if (formErrors && formErrors.length > 0) {
                    showToast(formErrors[0], 'error');
                }
                return false;
            },
            onSuccess: function () {
                var $form = $(this);
                if (oatIsFormSubmitting($form)) {
                    return false;
                }
                oatSetFormSubmitting($form, true, {
                    submitButton: $form.find('.ui.primary.button').first(),
                    keepFieldsEnabled: true
                });
                return true;
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

    $(function () {
        updateEditCounters();
        $('input[name="name"], textarea[name="describe"]').on('input', updateEditCounters);
    });
</script>
</body>
</html>

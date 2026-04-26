<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-仓库配置</title>
    <#include "../common.ftl">
    <style>
        .project-settings-page {
            position: relative;
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

        .repository-submit-mask {
            position: absolute;
            inset: 0;
            z-index: 30;
            display: none;
            align-items: center;
            justify-content: center;
            border-radius: 16px;
            background: rgba(248, 251, 255, 0.72);
            backdrop-filter: blur(2px);
        }

        .repository-submit-card {
            display: inline-flex;
            align-items: center;
            gap: 12px;
            padding: 14px 18px;
            border: 1px solid #dbeafe;
            border-radius: 14px;
            background: rgba(255, 255, 255, 0.94);
            color: #1e3a8a;
            box-shadow: 0 18px 36px rgba(15, 23, 42, 0.14);
            font-weight: 700;
        }

        .project-settings-page.repository-submitting .project-settings-breadcrumb,
        .project-settings-page.repository-submitting .project-settings-layout {
            opacity: 0.56;
            filter: grayscale(0.18);
            pointer-events: none;
            user-select: none;
        }

        .project-settings-page.repository-submitting .repository-submit-mask {
            display: flex;
            pointer-events: all;
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

        .project-settings-content {
            padding: 28px;
        }

        .project-settings-section-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-section-desc {
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
        .project-settings-form input[type="text"],
        .project-settings-form input[type="password"] {
            border-radius: 12px !important;
            border-color: #d9e3ef !important;
            padding: 13px 14px !important;
            font-size: 14px;
        }

        .project-settings-form .field {
            margin-bottom: 22px !important;
        }

        .project-settings-form .ui.selection.dropdown {
            border-radius: 12px !important;
            min-height: 48px;
            display: flex;
            align-items: center;
        }

        .project-settings-form .ui.button {
            border-radius: 10px;
            padding-top: 12px;
            padding-bottom: 12px;
        }

        .repository-form-card {
            padding: 24px;
            border: 1px solid #e6eef7;
            border-radius: 16px;
            background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
        }

        .repository-help-card {
            display: flex;
            align-items: flex-start;
            gap: 10px;
            margin-bottom: 22px;
            padding: 12px 14px;
            border: 1px solid #dbeafe;
            border-radius: 12px;
            background: #eff6ff;
            color: #476582;
            line-height: 1.7;
        }

        .repository-help-card .icon {
            margin-top: 3px;
            color: #2185d0;
        }

        .repository-action-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 14px;
            margin-top: 8px;
            padding-top: 18px;
            border-top: 1px solid #edf2f7;
        }

        .repository-action-tip {
            color: #8a97a6;
            font-size: 13px;
        }

        .repository-save-button.ui.button {
            min-width: 128px;
            border-radius: 12px;
            background: linear-gradient(135deg, #16a085 0%, #2185d0 100%) !important;
            color: #fff !important;
            box-shadow: 0 10px 22px rgba(33, 133, 208, 0.22);
            transition: transform 0.2s ease, box-shadow 0.2s ease;
        }

        .repository-save-button.ui.button:hover {
            transform: translateY(-1px);
            box-shadow: 0 14px 26px rgba(33, 133, 208, 0.28);
        }

        @media only screen and (max-width: 960px) {
            .project-settings-layout {
                grid-template-columns: 1fr;
            }

            .repository-action-bar {
                align-items: stretch;
                flex-direction: column;
            }

            .repository-save-button.ui.button {
                width: 100%;
            }
        }

        @media only screen and (max-width: 767px) {
            .project-settings-hero,
            .project-settings-side,
            .project-settings-content {
                padding: 22px 20px !important;
            }
        }
    </style>
</head>
<body>
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page" id="repositoryConfigPage">
    <div class="repository-submit-mask">
        <div class="repository-submit-card">
            <i class="notched circle loading icon"></i>
            正在保存仓库配置，请稍候...
        </div>
    </div>
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <div class="active section">仓库配置</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsAppActive="active"/>
            <#assign coeRepositoryConfig="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>
        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="cog icon"></i>
                    代码仓库连接
                </div>
                <h1 class="project-settings-hero-title">仓库配置</h1>
                <p class="project-settings-hero-desc">
                    配置当前应用的代码仓库地址与认证方式，便于后续拉取源码、扫描接口以及维护版本资产。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">应用名称</div>
                        <div class="project-settings-meta-value">${app.name}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">应用工程</div>
                        <div class="project-settings-meta-value">${app.srcName!'-'}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">当前权限</div>
                        <div class="project-settings-meta-value"><#if loginNameRole == "visitor">访客<#else>${loginNameRole}</#if></div>
                    </div>
                </div>
            </div>

            <div class="project-settings-content">
                <h2 class="project-settings-section-title">仓库接入信息</h2>
                <p class="project-settings-section-desc">支持用户名/密码和 Token 两种认证方式。保存后将跳转回应用与代码管理页。</p>

                <form class="ui form project-settings-form repository-form-card" method="post" action="/p/${project.id}/app/${app.id}/repository/save">
                    <div class="repository-help-card">
                        <i class="info circle icon"></i>
                        <div>建议填写可稳定访问的仓库地址；使用 Token 时无需填写用户名，只需在密码框中填入 Token。</div>
                    </div>
                    <div class="field">
                        <label>仓库地址</label>
                        <input type="text" name="repoAddress" placeholder="https://github.com/username/repo.git" value="${(app.repoAddress)!''}">
                    </div>
                    <div class="field">
                        <label>认证类型</label>
                        <div class="ui selection dropdown" id="authTypeDropdown">
                            <input type="hidden" id="authTypeInput">
                            <i class="dropdown icon"></i>
                            <div class="default text">用户名/密码</div>
                            <div class="menu">
                                <div class="item" data-value="password">用户名/密码</div>
                                <div class="item" data-value="token">Token</div>
                            </div>
                        </div>
                    </div>
                    <div class="two fields" id="authFieldsRow">
                        <div class="field" id="usernameField">
                            <label>用户名</label>
                            <input type="text" id="repoUserNameInput" name="repoUserName" placeholder="username" value="${(app.repoUserName)!''}">
                        </div>
                        <div class="field">
                            <label id="passwordLabel">密码</label>
                            <div class="ui icon input">
                                <input type="password" name="repoPassword" placeholder="password or token" value="${(app.repoPassword)!''}">
                                <i class="eye link icon" onclick="$(this).prev('input').attr('type', $(this).prev('input').attr('type')=='password'?'text':'password');"></i>
                            </div>
                        </div>
                    </div>

                    <div class="repository-action-bar">
                        <div class="repository-action-tip">保存过程中页面会暂时锁定，避免重复提交。</div>
                        <button id="repositoryConfigSubmitButton" class="ui button repository-save-button" type="button" onclick="submitRepositoryConfig()">
                            <i class="check icon"></i> 保存配置
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<!--初始化UI-->
<script>
    function setRepositorySubmitting($form, submitting) {
        var submittingOptions = {
            submitButton: '#repositoryConfigSubmitButton',
            extraControls: '.project-settings-breadcrumb a, .project-settings-side a, .project-settings-side .item'
        };
        $('#repositoryConfigPage').toggleClass('repository-submitting', submitting);
        oatSetFormSubmitting($form, submitting, submittingOptions);
        if (!submitting) {
            syncAuthTypeFields($('#authTypeDropdown').dropdown('get value'));
        }
    }

    function submitRepositoryConfig() {
        var $form = $('.project-settings-form');
        if (oatIsFormSubmitting($form)) {
            return;
        }
        var action = $form.attr('action');
        var data = $form.serialize();

        setRepositorySubmitting($form, true);
        $.ajax({
            type: 'POST',
            url: action,
            data: data,
            success: function(res) {
                if (res.success || res.result) {
                    showToast(res.message || '保存成功', 'success');
                    setTimeout(function() {
                        window.location.href = "/p/${project.id}/manageAppCode";
                    }, 1000);
                } else {
                    setRepositorySubmitting($form, false);
                    showToast(res.message || '保存失败', 'error');
                }
            },
            error: function() {
                setRepositorySubmitting($form, false);
                showToast('网络请求失败', 'error');
            }
        });
    }

    $('.project-settings-page .ui.dropdown').dropdown();
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });
    $('.poping.up').popup();

    function syncAuthTypeFields(value) {
        if (value === 'token') {
            $('#usernameField').hide();
            $('#repoUserNameInput').prop('disabled', true);
            $('#passwordLabel').text('Token');
        } else {
            $('#usernameField').show();
            $('#repoUserNameInput').prop('disabled', false);
            $('#passwordLabel').text('密码');
        }
    }

    // Init Auth Type Dropdown logic
    $('#authTypeDropdown').dropdown({
        onChange: function(value) {
            syncAuthTypeFields(value);
        }
    });

    // Determine initial state
    $(document).ready(function() {
        var userName = "${(app.repoUserName?js_string)!''}";
        // If username is empty but we have a password, assume it's a Token
        if (userName === '' && "${(app.repoPassword?js_string)!''}" !== '') {
            $('#authTypeDropdown').dropdown('set selected', 'token');
        } else {
            $('#authTypeDropdown').dropdown('set selected', 'password');
        }
    });

</script>

<script>
    <!--显示节点详情-->
    function openAppDetails(appId) {
        $("#" + appId).toggle();
        // var display= $("#"+appId).css('display');
        //  if (display == 'none') {
        //      $("#" + appId).css('display', 'table-cell');
        //  } else {
        //      $("#" + appId).css('display', 'none');
        //  }
    }
</script>
</body>

</html>

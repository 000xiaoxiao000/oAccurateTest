<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>创建新项目</title>
    <#include "../common.ftl">
    <style>
        .create-project-page {
            margin-top: 42px;
            margin-bottom: 48px;
        }

        .create-project-hero {
            padding: 28px 32px !important;
            border-radius: 16px !important;
            background: linear-gradient(135deg, #f8fbff 0%, #eef6ff 55%, #f9fbfd 100%) !important;
            border: 1px solid #dce7f5 !important;
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06) !important;
            margin-bottom: 24px !important;
        }

        .create-project-hero-top {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 16px;
            margin-bottom: 14px;
        }

        .create-project-back.ui.button {
            flex-shrink: 0;
            border-radius: 10px;
            color: #475569 !important;
            background: rgba(255, 255, 255, 0.82) !important;
            border: 1px solid #dce7f5 !important;
            box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04) !important;
        }

        .create-project-back.ui.button:hover {
            color: #1678c2 !important;
            background: #fff !important;
            border-color: #bad6f0 !important;
        }

        .create-project-hero-label {
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

        .create-project-hero-title {
            margin: 0 0 10px !important;
            font-size: 2rem !important;
            font-weight: 700 !important;
            color: #1f2937;
        }

        .create-project-hero-desc {
            margin: 0;
            max-width: 720px;
            color: #5b6675;
            line-height: 1.8;
            font-size: 15px;
        }

        .create-project-grid {
            display: grid;
            grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
            gap: 20px;
            align-items: start;
        }

        .create-project-panel {
            background: #fff;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
        }

        .create-project-form-panel {
            padding: 28px;
        }

        .create-project-panel-title {
            margin: 0 0 8px;
            font-size: 22px;
            font-weight: 700;
            color: #1f2937;
        }

        .create-project-panel-desc {
            margin: 0 0 24px;
            color: #6b7785;
            line-height: 1.75;
        }

        .create-project-form .field > label {
            margin-bottom: 8px;
            color: #334155;
            font-weight: 600;
        }

        .create-project-form .ui.input input,
        .create-project-form textarea {
            border-radius: 12px !important;
            border-color: #d9e3ef !important;
            padding: 13px 14px !important;
            font-size: 14px;
        }

        .create-project-form textarea {
            min-height: 160px;
            resize: vertical;
        }

        .create-project-form .field {
            margin-bottom: 22px !important;
        }

        .create-project-field-meta {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin-top: 8px;
            color: #8a97a6;
            font-size: 12px;
        }

        .create-project-actions {
            display: flex;
            justify-content: flex-end;
            gap: 12px;
            margin-top: 28px;
        }

        .create-project-actions .ui.button {
            border-radius: 10px;
            min-width: 120px;
            padding-top: 12px;
            padding-bottom: 12px;
        }

        .create-project-side {
            padding: 24px;
        }

        .create-project-side-section + .create-project-side-section {
            margin-top: 20px;
            padding-top: 20px;
            border-top: 1px solid #edf2f7;
        }

        .create-project-side-title {
            font-size: 16px;
            font-weight: 700;
            color: #1f2937;
            margin-bottom: 12px;
        }

        .create-project-checklist {
            margin: 0;
            padding: 0;
            list-style: none;
        }

        .create-project-checklist li {
            display: flex;
            align-items: flex-start;
            gap: 10px;
            color: #5f6b7a;
            line-height: 1.7;
        }

        .create-project-checklist li + li {
            margin-top: 10px;
        }

        .create-project-checklist i.icon {
            color: #2185d0;
            margin-top: 3px;
        }

        .create-project-helper-card {
            background: linear-gradient(180deg, #f8fbff 0%, #f4f8fc 100%);
            border: 1px solid #e2ebf5;
            border-radius: 14px;
            padding: 16px;
        }

        .create-project-helper-kpi {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 12px;
            margin-top: 14px;
        }

        .create-project-helper-kpi > div {
            background: #fff;
            border: 1px solid #e8eef5;
            border-radius: 12px;
            padding: 14px;
        }

        .create-project-helper-kpi-label {
            color: #8894a3;
            font-size: 12px;
            margin-bottom: 6px;
        }

        .create-project-helper-kpi-value {
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
            line-height: 1;
        }

        @media only screen and (max-width: 900px) {
            .create-project-grid {
                grid-template-columns: 1fr;
            }
        }

        @media only screen and (max-width: 767px) {
            .create-project-page {
                margin-top: 24px;
            }

            .create-project-hero,
            .create-project-form-panel,
            .create-project-side {
                padding: 22px 20px !important;
            }

            .create-project-hero-top {
                flex-direction: column;
            }

            .create-project-back.ui.button {
                width: 100%;
            }

            .create-project-actions {
                flex-direction: column-reverse;
            }

            .create-project-actions .ui.button {
                width: 100%;
            }
        }
    </style>
</head>
<body>

<#include  "../normalHeader.ftl">

<div class="ui container create-project-page">
    <div class="ui segment create-project-hero">
        <div class="create-project-hero-top">
            <div class="create-project-hero-label">
                <i class="plus square outline icon"></i>
                新建工作空间
            </div>
            <button class="ui basic button create-project-back" type="button" onclick="goBackFromCreateProject()">
                <i class="arrow left icon"></i>
                返回上一页
            </button>
        </div>
        <h1 class="create-project-hero-title">创建新项目</h1>
        <p class="create-project-hero-desc">
            为新的测试任务创建独立项目空间。填写清晰的项目名称和描述后，后续成员协作、应用配置和项目检索都会更高效。
        </p>
    </div>

    <div class="create-project-grid">
        <div class="create-project-panel create-project-form-panel">
            <h2 class="create-project-panel-title">项目信息</h2>
            <p class="create-project-panel-desc">建议项目名称简洁明确，项目描述重点说明目标、使用范围和协作对象。</p>

            <form class="ui form create-project-form">
                <div class="field required">
                    <label>项目名称</label>
                    <div class="ui input">
                        <input type="text" name="name" maxlength="50" placeholder="例如：支付链路回归测试、Web 自动化冒烟测试">
                    </div>
                    <div class="create-project-field-meta">
                        <span>建议使用可快速识别的业务或测试主题命名</span>
                        <span><span id="nameCount">0</span>/50</span>
                    </div>
                </div>

                <div class="field">
                    <label>项目描述</label>
                    <textarea rows="6" name="describe" maxlength="512" placeholder="补充项目目标、覆盖范围、主要成员或使用方式，帮助后续快速理解项目用途。"></textarea>
                    <div class="create-project-field-meta">
                        <span>清晰描述可以提升项目检索和协作效率</span>
                        <span><span id="describeCount">0</span>/512</span>
                    </div>
                </div>

                <div class="create-project-actions">
                    <button class="ui basic button" type="button" onclick="goBackFromCreateProject()">
                        <i class="arrow left icon"></i>
                        返回
                    </button>
                    <button class="ui button" type="reset" id="resetCreateProject">重置</button>
                    <button class="ui primary button" type="button" onclick="submitCreateProject()">
                        <i class="check icon"></i>
                        创建项目
                    </button>
                </div>
            </form>
        </div>

        <div class="create-project-panel create-project-side">
            <div class="create-project-side-section">
                <div class="create-project-side-title">创建建议</div>
                <ul class="create-project-checklist">
                    <li><i class="check circle outline icon"></i><span>名称尽量包含业务、端或测试范围，方便列表中快速识别。</span></li>
                    <li><i class="check circle outline icon"></i><span>描述中可补充目标、场景和责任人，减少后续沟通成本。</span></li>
                    <li><i class="check circle outline icon"></i><span>建议一个项目聚焦一类任务，避免职责混杂。</span></li>
                </ul>
            </div>

            <div class="create-project-side-section">
                <div class="create-project-side-title">填写参考</div>
                <div class="create-project-helper-card">
                    <div style="color: #5f6b7a; line-height: 1.7;">
                        你可以从“测试对象 + 场景类型”入手，例如：
                        <strong>测试 web 项目</strong>、<strong>登录链路稳定性回归</strong>、<strong>接口自动化巡检</strong>。
                    </div>
                    <div class="create-project-helper-kpi">
                        <div>
                            <div class="create-project-helper-kpi-label">名称建议长度</div>
                            <div class="create-project-helper-kpi-value">8-24</div>
                        </div>
                        <div>
                            <div class="create-project-helper-kpi-label">描述建议长度</div>
                            <div class="create-project-helper-kpi-value">40+</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    function goBackFromCreateProject() {
        if (window.history.length > 1) {
            window.history.back();
            return;
        }
        window.location.href = '/myProjects';
    }

    function updateCreateCounters() {
        $('#nameCount').text(($('input[name="name"]').val() || '').length);
        $('#describeCount').text(($('textarea[name="describe"]').val() || '').length);
    }

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
                inline : false,
                onFailure: function (formErrors) {
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

    $(function () {
        updateCreateCounters();
        $('input[name="name"], textarea[name="describe"]').on('input', updateCreateCounters);
        $('#resetCreateProject').on('click', function () {
            setTimeout(updateCreateCounters, 0);
        });
    });
</script>
</body>
</html>

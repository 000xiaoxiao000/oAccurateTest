<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-新增用例</title>
    <#include "../common.ftl">
    <#--MD5加密器-->
    <script src="/js/spark-md5.min.js"></script>
    <script src="/js/upload.js?time=${.now?time}"></script>

    <!--markdown编辑器-->
    <link rel="stylesheet" href="/css/simplemde.min.css">
    <script src="/js/simplemde.min.js"></script>

    <script src="/js/common.js"></script>
    <link href="/css/common.css" rel="stylesheet">

    <style>
        body {
            background: #f6f8fb;
        }

        .usecase-edit-page {
            max-width: 1380px;
            margin: 0 auto 36px;
            padding: 0 20px;
        }

        .usecase-edit-toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin-bottom: 16px;
        }

        .usecase-edit-meta {
            display: flex;
            flex-wrap: wrap;
            justify-content: flex-end;
            gap: 10px;
        }

        .usecase-meta-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 7px 12px;
            border-radius: 999px;
            background: rgba(0, 181, 173, 0.08);
            color: #008b87;
            font-size: 12px;
            font-weight: 600;
        }

        .usecase-edit-shell {
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        .usecase-edit-hero,
        .usecase-edit-card,
        .usecase-editor-card {
            background: #ffffff;
            border: 1px solid #e8edf4;
            border-radius: 16px;
            box-shadow: 0 12px 30px rgba(31, 45, 61, 0.05);
        }

        .usecase-edit-hero {
            position: relative;
            overflow: hidden;
            padding: 28px 32px;
        }

        .usecase-edit-hero:before {
            content: '';
            position: absolute;
            inset: 0 auto auto 0;
            width: 320px;
            height: 180px;
            background: radial-gradient(circle at top left, rgba(0, 181, 173, 0.14) 0%, rgba(0, 181, 173, 0) 72%);
            pointer-events: none;
        }

        .usecase-edit-title-row {
            position: relative;
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 16px;
            z-index: 1;
        }

        .usecase-edit-title-wrap {
            flex: 1;
            min-width: 0;
        }

        .usecase-edit-title-label {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 6px 12px;
            border-radius: 999px;
            background: #eef8f8;
            color: #008b87;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.08em;
            text-transform: uppercase;
        }

        .usecase-edit-title-field {
            margin-top: 16px;
        }

        .usecase-edit-title-field input {
            border: none !important;
            box-shadow: none !important;
            background: transparent !important;
            padding: 0 !important;
            font-size: 34px !important;
            line-height: 1.25 !important;
            font-weight: 700 !important;
            color: #1f2d3d !important;
        }

        .usecase-edit-title-field input::placeholder {
            color: #9aa7b3;
        }

        .usecase-edit-side-note {
            text-align: right;
            color: #6f7d89;
            font-size: 13px;
            line-height: 1.8;
            white-space: nowrap;
        }

        .usecase-edit-summary {
            position: relative;
            z-index: 1;
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 14px;
            margin-top: 24px;
        }

        .summary-box {
            padding: 16px 18px;
            border-radius: 12px;
            background: linear-gradient(180deg, #fbfdff 0%, #f4f8fc 100%);
            border: 1px solid #edf2f7;
        }

        .summary-box-label {
            font-size: 12px;
            color: #7f8c98;
            letter-spacing: 0.08em;
        }

        .summary-box-value {
            margin-top: 8px;
            font-size: 24px;
            font-weight: 700;
            color: #22313f;
        }

        .usecase-edit-layout {
            display: grid;
            grid-template-columns: 380px minmax(0, 1fr);
            gap: 24px;
            align-items: start;
        }

        .usecase-edit-sidebar {
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        .usecase-edit-card {
            padding: 22px 22px 24px;
        }

        .card-title {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 16px;
        }

        .card-title h3 {
            margin: 0;
            font-size: 20px;
            color: #1f2d3d;
        }

        .card-subtitle {
            font-size: 12px;
            color: #8895a2;
        }

        .usecase-preview-image-wrap {
            position: relative;
            border-radius: 16px;
            overflow: hidden;
            background: linear-gradient(180deg, #f8fbfd 0%, #eef4f8 100%);
            border: 1px solid #e8edf4;
            cursor: pointer;
        }

        .usecase-preview-image-wrap img {
            width: 100%;
            height: 240px;
            object-fit: cover;
            display: block;
        }

        .usecase-preview-overlay {
            position: absolute;
            inset: auto 14px 14px 14px;
            padding: 12px 14px;
            border-radius: 12px;
            background: rgba(19, 28, 35, 0.58);
            color: #ffffff;
            backdrop-filter: blur(6px);
        }

        .usecase-preview-overlay-title {
            font-size: 14px;
            font-weight: 700;
        }

        .usecase-preview-overlay-desc {
            margin-top: 4px;
            font-size: 12px;
            color: rgba(255, 255, 255, 0.84);
        }

        .field-label {
            display: block;
            margin-bottom: 8px;
            color: #50606f;
            font-size: 13px;
            font-weight: 700;
        }

        .usecase-edit-card .field + .field {
            margin-top: 18px;
        }

        .usecase-edit-card textarea {
            min-height: 104px;
            resize: vertical;
        }

        .helper-text {
            margin-top: 6px;
            font-size: 12px;
            color: #8b97a3;
        }

        .usecase-editor-card {
            padding: 24px 24px 28px;
        }

        .editor-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin-bottom: 16px;
        }

        .editor-header h3 {
            margin: 0;
            font-size: 22px;
            color: #1f2d3d;
        }

        .editor-shell {
            border: 1px solid #e8edf4;
            border-radius: 14px;
            overflow: hidden;
            background: #ffffff;
        }

        .editor-shell .CodeMirror,
        .editor-shell .CodeMirror-scroll {
            min-height: 520px;
        }

        .editor-shell .editor-toolbar {
            border: none;
            border-bottom: 1px solid #edf2f7;
            background: #fbfcfe;
        }

        .editor-shell .CodeMirror {
            border: none;
            font-size: 15px;
            line-height: 1.8;
        }

        .editor-footer {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
            margin-top: 18px;
            flex-wrap: wrap;
        }

        .editor-tips {
            color: #748391;
            font-size: 13px;
        }

        .editor-actions {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
        }

        .editor-actions .button {
            min-width: 140px;
            border-radius: 10px;
        }

        .usecase-edit-card .ui.selection.dropdown,
        .usecase-edit-card .ui.search.selection.dropdown,
        .usecase-edit-card .ui.multiple.search.selection.dropdown,
        .usecase-edit-card textarea,
        .usecase-edit-card input[type="text"] {
            border-radius: 10px !important;
        }

        @media only screen and (max-width: 1180px) {
            .usecase-edit-layout {
                grid-template-columns: 1fr;
            }

            .usecase-edit-summary {
                grid-template-columns: repeat(2, minmax(0, 1fr));
            }
        }

        @media only screen and (max-width: 767px) {
            .usecase-edit-page {
                padding: 0 12px 28px;
            }

            .usecase-edit-toolbar,
            .usecase-edit-title-row,
            .editor-header,
            .editor-footer {
                flex-direction: column;
                align-items: flex-start;
            }

            .usecase-edit-meta {
                justify-content: flex-start;
            }

            .usecase-edit-hero,
            .usecase-edit-card,
            .usecase-editor-card {
                padding: 18px;
            }

            .usecase-edit-title-field input {
                font-size: 28px !important;
            }

            .usecase-edit-summary {
                grid-template-columns: 1fr;
            }

            .usecase-preview-image-wrap img {
                height: 200px;
            }

            .editor-shell .CodeMirror,
            .editor-shell .CodeMirror-scroll {
                min-height: 420px;
            }

            .editor-actions {
                width: 100%;
            }

            .editor-actions .button {
                flex: 1;
                min-width: 0;
            }
        }
    </style>

</head>
<body>

<!--头部菜单 引入-->
<#assign usecaseItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">新增用例</div>
</div>

<div class="usecase-edit-page">
    <div class="usecase-edit-toolbar">
        <a class="ui tiny teal basic button" href="/p/${project.id}/usecase/list">
            <i class="home icon"></i>用例中心
        </a>
        <div class="usecase-edit-meta">
            <span class="usecase-meta-chip"><i class="folder open outline icon"></i>${currentDir}</span>
            <span class="usecase-meta-chip"><i class="plus circle icon"></i>新建模式</span>
        </div>
    </div>

    <form id="usecaseForm" class="ui form usecase-edit-shell">
        <section class="usecase-edit-hero">
            <div class="usecase-edit-title-row">
                <div class="usecase-edit-title-wrap">
                    <div class="usecase-edit-title-label">
                        <i class="plus icon"></i>
                        Create Usecase
                    </div>
                    <div class="field usecase-edit-title-field">
                        <input type="text" name="title" placeholder="用例名称">
                    </div>
                </div>
                <div class="usecase-edit-side-note">
                    <div>先填写基础信息，再补充正文内容与关联资源</div>
                    <div>创建成功后将自动跳转到编辑页继续完善</div>
                </div>
            </div>

            <div class="usecase-edit-summary">
                <div class="summary-box">
                    <div class="summary-box-label">默认目录</div>
                    <div class="summary-box-value">1</div>
                </div>
                <div class="summary-box">
                    <div class="summary-box-label">快照候选</div>
                    <div class="summary-box-value">${(snapshots?size)!'0'}</div>
                </div>
                <div class="summary-box">
                    <div class="summary-box-label">系统快照</div>
                    <div class="summary-box-value">${(systemSnapshots?size)!'0'}</div>
                </div>
                <div class="summary-box">
                    <div class="summary-box-label">可选标签</div>
                    <div class="summary-box-value">${(labels?size)!'0'}</div>
                </div>
            </div>
        </section>

        <input type="hidden" name="directory" value="${currentDir}">
        <input type="file" id="headerImageFile" style="display: none">
        <input type="hidden" id="headerImagePath" name="headImage">

        <div class="usecase-edit-layout">
            <aside class="usecase-edit-sidebar">
                <section class="usecase-edit-card">
                    <div class="card-title">
                        <h3>封面与基础信息</h3>
                        <span class="card-subtitle">主图、目录与标签</span>
                    </div>
                    <div id="vikiHeadImage" class="usecase-preview-image-wrap" onclick="selectHeadFile()">
                        <img id="headerImage" src="/images/image.png">
                        <div class="usecase-preview-overlay">
                            <div class="usecase-preview-overlay-title">上传用例主图</div>
                            <div class="usecase-preview-overlay-desc">点击添加封面图片，让用例更容易识别</div>
                        </div>
                    </div>
                    <div class="helper-text">封面图会展示在后续详情页中，建议使用与业务场景相关的截图。</div>

                    <div class="field">
                        <label class="field-label">标签</label>
                        <div class="ui multiple search selection dropdown">
                            <input type="hidden" name="labels">
                            <i class="dropdown icon"></i>
                            <div class="default text">添加标签</div>
                            <div class="menu">
                                <#list labels as lab>
                                    <div class="item" data-value="${lab.name}">
                                        <div class="ui ${lab.color} empty circular label"></div>
                                        ${lab.name}
                                    </div>
                                </#list>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="usecase-edit-card">
                    <div class="card-title">
                        <h3>关联资源</h3>
                        <span class="card-subtitle">快照、缺陷与需求</span>
                    </div>

                    <div class="field">
                        <label class="field-label">快照</label>
                        <div class="ui multiple search selection dropdown">
                            <input type="hidden" name="snapshots">
                            <i class="dropdown icon"></i>
                            <div class="default text">添加快照</div>
                            <div class="menu">
                                <#list snapshots as snap>
                                    <div class="item" data-value="${snap.id}">${snap.name}</div>
                                </#list>
                            </div>
                        </div>
                    </div>

                    <div class="field">
                        <label class="field-label">系统快照</label>
                        <div class="ui multiple search selection dropdown">
                            <input type="hidden" name="systemSnapshots">
                            <i class="dropdown icon"></i>
                            <div class="default text">关联系统快照</div>
                            <div class="menu">
                                <#list systemSnapshots as item>
                                    <div class="item" data-value="${item.id}">${item.name}</div>
                                </#list>
                            </div>
                        </div>
                    </div>

                    <div class="field">
                        <label class="field-label">测试缺陷</label>
                        <textarea name="defectsText" rows="4" placeholder="每行一个缺陷编号，例如：BUG-1001"></textarea>
                        <div class="helper-text">支持逐行录入，便于后续详情页统一呈现。</div>
                    </div>

                    <div class="field">
                        <label class="field-label">PRD需求</label>
                        <textarea name="prdRequirementsText" rows="4" placeholder="每行一个PRD需求编号，例如：PRD-2026-001"></textarea>
                        <div class="helper-text">创建阶段可先补主需求编号，后续再细化补充。</div>
                    </div>
                </section>
            </aside>

            <section class="usecase-editor-card">
                <div class="editor-header">
                    <div>
                        <h3>详情编辑</h3>
                        <div class="card-subtitle">建议直接以 Markdown 记录背景、步骤、预期结果和注意事项</div>
                    </div>
                    <div class="ui tiny basic teal label">Markdown</div>
                </div>

                <div class="editor-shell">
                    <textarea id="doc-edit" name="content" style="display: none"></textarea>
                </div>

                <div class="editor-footer">
                    <div class="editor-tips">
                        推荐结构：业务背景 → 前置条件 → 操作步骤 → 断言结果 → 备注
                    </div>
                    <div class="editor-actions">
                        <div class="ui secondary button" onclick="doCancelUsecase()">取消用例</div>
                        <div class="ui primary button" onclick="doSaveUsecase()">新增用例</div>
                    </div>
                </div>

                <div class="ui error message"></div>
            </section>
        </div>
    </form>
</div>

<!-- 取消用例弹出框-->
<div id="cancelUsecaseDialog" class="ui small modal">
    <div class="header">取消用例</div>
    <div class="ui negative message">
        <div class="header">
            你确定离开该用例吗？
        </div>
    </div>
    <div class="actions">
        <div id="cancelUsecaseButton" class="ui negative button">离开</div>
        <div class="ui cancel button">不</div>
    </div>
</div>

<script>
    var simplemde = new SimpleMDE({
        element: $("#doc-edit")[0]
    });

    function doSaveUsecase() {
        if (!$('.ui.form').form('validate form')) {
            return false;
        }

        $("#doc-edit").val(simplemde.value());
        var resultInform = $.ajax({
            url: "/p/${project.id}/usecase/doSave",
            data: $("#usecaseForm").serialize(),
            async: false
        }).responseJSON;
        if (resultInform.result) {
            showToast(resultInform.message, 'success');
            // 跳转到编辑页
            setTimeout(function() {
                 window.location.href = "/p/${project.id}/usecase/edit?id=" + resultInform.data;
            }, 1000);
        } else {
            showToast(resultInform.errorMessage, 'error');
        }
    }

    function doCancelUsecase() {
        var backUrl = "/p/${project.id}/usecase/list?directory=${currentDir}";
        $("#cancelUsecaseButton").off('click').on('click', function () {
            window.location.href = backUrl;
        });
        $("#cancelUsecaseDialog").modal('show');
    }
</script>
<script>
    $('.ui.accordion').accordion({
        exclusive: false
    });
    $('.ui.dropdown.type').dropdown({
        on: 'click',
        allowAdditions: true
    });

    $('.field .ui.search.selection.dropdown').dropdown({
        on: 'click'
    });
    $('.ui.dropdown.snapshot').dropdown({
        on: 'click'
    });

    $('.ui.dropdown.label').dropdown({
        on: 'click'
    });

    $('.menu .ui.dropdown').dropdown({
        on: 'hover'
    });

    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });

    $('.tab.item').tab();

    function selectHeadFile() {
        $("#headerImageFile").trigger("click");
    }

    $("#headerImageFile").change(function (ev) {
        uploadFile(this.files[0], "/resource/upload"
            , function (ev2) {
                var results = eval("(" + this.responseText + ")");
                programFilePath = results.data;
                console.log(programFilePath);
                $("#headerImage").attr("src", "/r/" + programFilePath);
                $("#headerImagePath").val(programFilePath);
            }
            , null);
    });

    $(function () {
        // 设置表单验证规则
        $('.ui.form')
            .form({
                inline: false, // 行内显示验证异常
                keyboardShortcuts: false, // 关闭回车提交（不启作用）
                onFailure: function (formErrors, fields) {
                    if (formErrors && formErrors.length > 0) {
                        showToast(formErrors[0], 'error');
                    }
                    return false;
                },
                fields: {
                    title: {
                        identifier: 'title',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '请输入标题'
                            },
                            {
                                type: 'minLength[4]',
                                prompt: '标题至少包含4个字符'
                            },
                            {
                                type: 'maxLength[50]',
                                prompt: '标题不能超过50个字符'
                            }
                        ]
                    }
                }
            });
    });

</script>
</body>
</html>

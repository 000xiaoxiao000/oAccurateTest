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

<div class="ui container" style="margin-bottom: 10px">
    <a class="ui tiny teal basic button" href="/p/${project.id}/usecase/list">
        <i class="home icon"></i>用例中心
    </a>
</div>

<div class="ui container segment">

    <form id="usecaseForm" class="ui form">
        <h2 class="ui dividing  header">
            <div class="field">
                <input type="text" name="title" placeholder="用例名称" style="border: none">
            </div>
        </h2>
        <input type="hidden" name="directory" value="${currentDir}">
        <div class="ui grid">
            <input type="file" id="headerImageFile" style="display: none">
            <input type="hidden" id="headerImagePath" name="headImage">
            <div id="vikiHeadImage" class="ui four wide column middle aligned centered dropdown "
                 onclick="selectHeadFile()">
                <img class="ui centered small image" id="headerImage" src="/images/image.png">
                <div class="ui dimmer">
                    <div class="content">
                        <div class="center">
                            <h5 class="ui inverted icon header">
                                <i class="write icon"></i>
                                编辑图片
                            </h5>
                        </div>
                    </div>
                </div>
            </div>

            <div class="ui twelve wide column">
                <div class="field">
                    <span> 快照：</span>
                    <select multiple="" name="snapshots" class="ui dropdown search snapshot">
                        <#list snapshots as snap>
                            <option value="${snap.id}">${snap.name}</option>
                        </#list>
                    </select>
                </div>
                <div class="field">
                    <span> 标签：</span>
                    <div class="ui multiple  search selection dropdown">
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

            </div>
        </div>

        <div class="ui field grid">
            <div class="ui top attached  three menu " style="border: none">
                <a class="item active tab header  "
                   data-tab="doc-edit" <#--style="background-color: #2185d0; color: #fff"-->>
                    <i class="ui icon edit"></i>
                    详情编辑
                </a>
            </div>
            <div class="ui bottom attached tab segment active" data-tab="doc-edit" style="border: none;padding: 0px">
                <textarea id="doc-edit" name="content" style="display: none"></textarea>
            </div>
        </div>
        <div class="ui error message"></div>
        <div class="ui fluid primary button" style="margin-top: 20px" onclick="doSaveUsecase()">
            新增用例
        </div>
        <div class="ui fluid secondary button" style="margin-top: 20px" onclick="doCancelUsecase()">
            取消用例
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

    // 触发遮罩层信息显示
    $('#vikiHeadImage').dimmer({
        on: 'hover',
        opacity: 0.5
    });


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

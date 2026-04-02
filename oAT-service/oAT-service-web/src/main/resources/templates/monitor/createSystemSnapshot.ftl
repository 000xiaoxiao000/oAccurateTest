<div class="ui block header attached " xmlns="http://www.w3.org/1999/html">
    保存系统快照
</div>
<i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>
<div class="ui content">
    <form id="systemSnapshotForm" class="ui form">
        <input type="hidden" name="traceId" value="${traceId}">
        <div class="ui divided grid">
            <div class="twelve wide column">
                <div class="required field">
                    <label>名称</label>
                    <label>
                        <input type="text" placeholder="名称" name="title">
                    </label>
                </div>
                <div class="required field">
                    <label>图片</label>
                    <input type="file" id="programFileSelect" placeholder="图片" name="">
                    <input type="hidden" id="programFile" name="topicImage">
                    <div id="programProgress" class="ui bottom attached progress">
                        <div class="bar"></div>
                    </div>
                </div>
                <div class="ui field">
                    <label>描述：</label>
                    <div class="content">
                        <label>
                            <textarea rows="5" name="describe"></textarea>
                        </label>
                    </div>
                </div>
            </div>
            <div class="four wide column">
                <div class="field">
                    <label>所属应用</label>
                    <label>
                        <input readonly="" type="text" value="${app.appName}">
                    </label>
                    <input type="hidden" name="appId" value="${app.appId}">
                </div>
                <div class="field">
                    <label>目录</label>
                    <div class="ui search selection dropdown">
                        <input type="hidden" name="directory" value="root">
                        <i class="dropdown icon"></i>
                        <div class="default text">选择快照目录</div>
                        <div class="menu">
                            <div class="item" data-value="root">/root</div>
                            <#list dirs as dir>
                                <div class="item" data-value="${dir.id}">${dir.path}</div>
                            </#list>
                        </div>
                    </div>
                </div>
                <div class="field">
                    <label>版本有效周期</label>
                    <div class="ui right labeled input small">
                        <label>
                            <input type="text" value="30" name="versionCycle" placeholder="输入数字">
                        </label>
                        <div class="ui label">
                            天
                        </div>
                    </div>
                </div>
                <div class="field">
                    <label>添加标签</label>
                    <div class="ui selection  labeled multiple search dropdown  small">
                        <span class="text">选择标签</span>
                        <i class="dropdown icon"></i>
                        <input type="hidden" name="labels">
                        <div class="menu">
                            <#list labels as label>
                                <div class="item" data-value="${label.name}">
                                    <div class="ui empty  label ${label.color}"></div>
                                    ${label.name}
                                </div>
                            </#list>
                        </div>
                    </div>
                </div>
                <div class="field">
                    <label>负责人</label>
                    <div class="ui selection search multiple dropdown small ">
                        <span class="text">选择标签</span>
                        <i class="dropdown icon"></i>
                        <input type="hidden" name="principals" value="${user.id}">
                        <div class="menu">
                            <#list members as member>
                                <div class="item" data-value="${member.memberId}">
                                    ${member.memberName!}
                                </div>
                            </#list>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </form>
</div>
<div class="actions">
    <div class="ui black deny button">
        算啦
    </div>
    <div class="ui positive right labeled icon save button"
         onclick="doSaveSystemSnapshot('${projectId}')">
        是的，帮我保存
        <i class="checkmark icon"></i>
    </div>
</div>
<script>
    $("#systemSnapshotForm .dropdown").dropdown();

    // 初始化系统快照表单验证
    $('#systemSnapshotForm').form({
        inline: false,
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
                        prompt: '请输入系统快照标题'
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
            },
            describe: {
                identifier: 'describe',
                rules: [
                    {
                        type: 'maxLength[512]',
                        prompt: '描述不能超过512个字符'
                    }
                ]
            }
        }
    });

    document.getElementById("programFileSelect").addEventListener("change", function (ev1) {
        $("#programFile").val(null);
        uploadFile(this.files[0], "/resource/upload"
            , function (ev2) {
                var results = eval("(" + this.responseText + ")");
                programFilePath = results.data;
                console.log(programFilePath);
                $("#programFile").val(programFilePath);

                $('#programProgress').progress('complete');
            }
            , function (evt) {
                // 进度条
                $('#programProgress').progress('set progress', evt.loaded * 75 / 100);
                $('#programProgress').progress('set total', evt.total);
            });
    });

    function doSaveSystemSnapshot(projectid) {
        if (!$('#systemSnapshotForm').form('is valid')) {
            return false;
        }
        var resultInform = $.ajax({
            url: "/p/" + projectid + "/monitor/doSaveSystemSnapshot",
            data: $("#systemSnapshotForm").serialize(),
            async: false
        }).responseJSON;
        if (resultInform.result) {
            // 关闭当前模型
            $("#systemSnapshotForm").parents(".modal").modal('hide');
            showToast(resultInform.message, 'success');
        } else {
            showToast(resultInform.errorMessage, 'error');
        }
    }

</script>

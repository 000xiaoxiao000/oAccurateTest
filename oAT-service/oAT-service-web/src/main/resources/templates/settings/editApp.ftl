<div class="ui block header attached ">
    修改应用信息
</div>
    <i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>
    <div class="ui content">
        <form id="editAppForm" class="ui form" action="/p/${project.id}/app/doEdit">
            <div class="field required">
                <label>应用id：</label>
                <input type="text" name="id" value="${app.id}" readonly="">
            </div>
            <div class="field required">
                <label>应用名称：</label>
                <input type="text" name="name" value="${app.name}" placeholder="输入简短的应用名称">
            </div>
            <div class="inline fields">
                <label for="fruit">作用范围：</label>
                <div class="field">
                    <div class="ui radio checkbox ">
                        <input type="radio" name="range"
                        <#if app.range=='only'> checked=""</#if>
                               tabindex="0" value="only">
                        <label>仅当前项目</label>
                    </div>
                </div>
                <div class="field">
                    <div class="ui radio checkbox">
                        <input type="radio" name="range"  <#if app.range=='all'> checked=""</#if>
                               tabindex="1" value="all">
                        <label>所有项目</label>
                    </div>
                </div>
            </div>
            <div class="field required">
                <label>工程名称：</label>
                <input type="text" name="srcName" value="${app.srcName}" placeholder="输入项目源码的中工程名">
            </div>

            <div class=" field">
                <label>应用描述：</label>
                <textarea placeholder="输入应用描述"  name="describe">${app.describe!}</textarea>
            </div>
            <div class=" field">
                <label>属性配置：</label>
                <#-- textarea 赋值必须整成一行,否则显示多余的空格 -->
                <textarea id="propertiesEdit" placeholder="输入配置信息" name="properties"><#if app.properties??>${app.properties}<#else ><#include "appInitConfig.properties"></#if></textarea>
            </div>
            <div class=" field">
                <label>当前版本：</label>
                <div class="three fields">
                    <div class="field">
                        <input type="text" name="currentVersion" value="${app.currentVersion!}" placeholder="当前版本号">
                    </div>
                    <div class="field">
                        <input type="text" name="currentBranch" value="${app.currentBranch!}" placeholder="当前分支">
                    </div>
                    <div class="field">
                        <input type="text" name="currentCommitId" value="${app.currentCommitId!}" placeholder="当前CommitId">
                    </div>
                </div>
            </div>
        </form>
    </div>
    <div class="actions">
        <div class="ui black deny button">
            取消
        </div>
        <button class="ui positive right labeled icon button save"
                onclick="submitEditAppForm();">
            保存
            <i class="checkmark icon"></i>
        </button>
    </div>

<script>
    var editor = CodeMirror.fromTextArea(document.getElementById("propertiesEdit"), {
        lineNumbers: true
    });

    function submitEditAppForm() {
        // Sync CodeMirror content back to textarea before serializing
        if (editor) {
            editor.save();
        }

        var $form = $('#editAppForm');
        var action = $form.attr('action');

        $.ajax({
            type: 'POST',
            url: action,
            data: $form.serialize(),
            success: function(res) {
                if (res.success || res.result) {
                    $('#editDialog').modal('hide');
                    showToast(res.message || '修改成功', 'success');
                    setTimeout(function() {
                        location.reload();
                    }, 1000);
                } else {
                    showToast(res.message || '修改失败', 'error');
                }
            },
            error: function() {
                showToast('网络请求失败', 'error');
            }
        });
    }
</script>

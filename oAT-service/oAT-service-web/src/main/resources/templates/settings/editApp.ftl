<div class="app-modal-header">
    <div>
        <div class="app-modal-label">
            <i class="edit outline icon"></i>
            应用资产管理
        </div>
        <h3 class="app-modal-title">修改应用信息</h3>
        <p class="app-modal-desc">维护应用基础信息、版本标识与探针属性配置，保存后将同步到当前项目应用清单。</p>
    </div>
    <i class="close icon app-modal-close"></i>
</div>
<div class="ui content app-modal-content">
    <form id="editAppForm" class="ui form" action="/p/${project.id}/app/doEdit">
        <div class="app-modal-section">
            <div class="app-modal-section-title">
                <i class="info circle icon"></i>
                基础信息
            </div>
            <div class="field required">
                <label>应用 ID</label>
                <div class="ui left icon input">
                    <i class="fingerprint icon"></i>
                    <input type="text" name="id" value="${app.id}" readonly="">
                </div>
            </div>
            <div class="field required">
                <label>应用名称</label>
                <div class="ui left icon input">
                    <i class="cube icon"></i>
                    <input type="text" name="name" value="${app.name}" placeholder="输入简短的应用名称">
                </div>
            </div>
            <div class="inline fields">
                <label>作用范围</label>
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
                <label>工程名称</label>
                <div class="ui left icon input">
                    <i class="code branch icon"></i>
                    <input type="text" name="srcName" value="${app.srcName}" placeholder="输入项目源码中的工程名">
                </div>
            </div>
            <div class="field">
                <label>应用描述</label>
                <textarea placeholder="输入应用描述" name="describe">${app.describe!}</textarea>
            </div>
        </div>

        <div class="app-modal-section">
            <div class="app-modal-section-title">
                <i class="sliders horizontal icon"></i>
                属性配置
            </div>
            <div class="field">
                <label>探针配置</label>
                <#-- textarea 赋值必须整成一行,否则显示多余的空格 -->
                <textarea id="propertiesEdit" placeholder="输入配置信息" name="properties"><#if app.properties??>${app.properties}<#else ><#include "appInitConfig.properties"></#if></textarea>
            </div>
        </div>

        <div class="app-modal-section">
            <div class="app-modal-section-title">
                <i class="tag icon"></i>
                当前版本
            </div>
            <div class="three fields app-version-fields">
                <div class="field">
                    <label>版本号</label>
                    <input type="text" name="currentVersion" value="${app.currentVersion!}" placeholder="当前版本号">
                </div>
                <div class="field">
                    <label>分支</label>
                    <input type="text" name="currentBranch" value="${app.currentBranch!}" placeholder="当前分支">
                </div>
                <div class="field">
                    <label>Commit ID</label>
                    <input type="text" name="currentCommitId" value="${app.currentCommitId!}" placeholder="当前 Commit ID">
                </div>
            </div>
        </div>

        <div class="app-modal-section">
            <div class="app-modal-section-title">
                <i class="bell outline icon"></i>
                探针上下线告警
            </div>
            <p class="app-alert-desc">
                按每个探针实例独立判断上下线，超过配置阈值未收到心跳后发送通用 Webhook 通知。测试时可填当前服务内置地址：
                <code>http://127.0.0.1:8899/webhook/oat/probe-alert</code>
            </p>
            <div class="field app-alert-switch">
                <div class="ui checkbox">
                    <input type="hidden" name="probeAlertEnabled" value="false">
                    <input type="checkbox" name="probeAlertEnabled" value="true" <#if app.probeAlertEnabled?? && app.probeAlertEnabled>checked="checked"</#if>>
                    <label>启用探针实例上下线告警</label>
                </div>
            </div>
            <div class="two fields">
                <div class="field">
                    <label>下线阈值（秒）</label>
                    <input type="number" min="30" name="probeOfflineThresholdSeconds" value="${app.probeOfflineThresholdSeconds!90}" placeholder="例如：90">
                </div>
                <div class="field">
                    <label>Webhook 地址</label>
                    <input type="text" name="probeWebhookUrl" value="${app.probeWebhookUrl!}" placeholder="http://127.0.0.1:8899/webhook/oat/probe-alert">
                </div>
            </div>
            <div class="inline fields app-alert-events">
                <label>通知事件</label>
                <div class="field">
                    <div class="ui checkbox">
                        <input type="hidden" name="probeAlertOnOffline" value="false">
                        <input type="checkbox" name="probeAlertOnOffline" value="true" <#if !app.probeAlertOnOffline?? || app.probeAlertOnOffline>checked="checked"</#if>>
                        <label>下线</label>
                    </div>
                </div>
                <div class="field">
                    <div class="ui checkbox">
                        <input type="hidden" name="probeAlertOnRecovered" value="false">
                        <input type="checkbox" name="probeAlertOnRecovered" value="true" <#if !app.probeAlertOnRecovered?? || app.probeAlertOnRecovered>checked="checked"</#if>>
                        <label>恢复上线</label>
                    </div>
                </div>
                <div class="field">
                    <div class="ui checkbox">
                        <input type="hidden" name="probeAlertOnOnline" value="false">
                        <input type="checkbox" name="probeAlertOnOnline" value="true" <#if app.probeAlertOnOnline?? && app.probeAlertOnOnline>checked="checked"</#if>>
                        <label>首次上线</label>
                    </div>
                </div>
            </div>
        </div>
    </form>
</div>
<div class="actions app-modal-actions">
    <div class="ui basic deny button">
        取消
    </div>
    <button class="ui primary right labeled icon button save"
            onclick="submitEditAppForm();">
        保存
        <i class="checkmark icon"></i>
    </button>
</div>

<script>
    var editor = CodeMirror.fromTextArea(document.getElementById("propertiesEdit"), {
        lineNumbers: true
    });

    $('.app-modal-close').on('click', function() {
        $('#editDialog').modal('hide');
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

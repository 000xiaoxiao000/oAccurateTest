<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-修改应用</title>
    <script src="/js/codemirror.min.js"></script>
    <script src="/js/properties.js"></script>
    <link href="/css/codemirror.min.css" rel="stylesheet">
<#include "../common.ftl">
    <style type="text/css">
        .CodeMirror{border:1px solid #d9e3ef;border-radius:12px;height:auto}.CodeMirror-scroll{overflow-y:hidden;overflow-x:auto}
        .project-settings-page{margin-top:18px;margin-bottom:42px}.project-settings-breadcrumb{margin:6px auto 18px!important;color:#6b7785}
        .project-settings-layout{display:grid;grid-template-columns:280px minmax(0,1fr);gap:20px;align-items:start}
        .project-settings-side,.project-settings-main{background:#fff;border:1px solid #e7edf5;border-radius:16px;box-shadow:0 10px 24px rgba(15,23,42,.05)}
        .project-settings-side{padding:18px}.project-settings-main{overflow:hidden}
        .project-settings-hero{display:flex;justify-content:space-between;gap:18px;padding:26px 28px;background:linear-gradient(135deg,#f8fbff 0%,#eef5ff 55%,#f9fbfd 100%);border-bottom:1px solid #e6eef7}
        .project-settings-hero-label{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border-radius:999px;background:rgba(33,133,208,.08);color:#1d6fa5;font-size:12px;font-weight:600;letter-spacing:.04em;margin-bottom:14px}
        .project-settings-hero-title{margin:0 0 8px;color:#1f2937;font-size:28px;font-weight:700}.project-settings-hero-desc{margin:0;color:#617080;line-height:1.8;max-width:780px}.project-settings-hero-actions{flex-shrink:0}
        .project-settings-form-wrap{padding:28px}.project-settings-form .field>label,.project-settings-form .inline.fields>label{margin-bottom:8px;color:#334155;font-weight:600}
        .project-settings-form input,.project-settings-form textarea{border-radius:12px!important;border-color:#d9e3ef!important;padding:13px 14px!important;font-size:14px}.project-settings-form textarea{min-height:150px;resize:vertical}.project-settings-form textarea[name="describe"]{min-height:72px;height:86px}
        .project-settings-form .inline.fields{align-items:center}.project-settings-form .three.fields>.field,.project-settings-form .two.fields>.field{min-width:0}
        .project-settings-section{margin-bottom:24px}.project-settings-section-title{display:flex;align-items:center;gap:8px;margin-bottom:16px;color:#334155;font-size:15px;font-weight:700}.project-settings-section-title .icon{color:#2185d0}
        .project-alert-desc{margin:-4px 0 16px;color:#64748b;line-height:1.65}.project-alert-desc code{display:inline-block;margin-top:4px;padding:2px 6px;border-radius:6px;background:#eef5ff;color:#1d4ed8;font-size:12px;word-break:break-all}
        .project-alert-switch{padding:12px 14px;border:1px solid #dbeafe;border-radius:12px;background:#f8fbff}.project-alert-events.inline.fields{align-items:center;margin-bottom:0}
        .project-settings-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:28px}.project-settings-actions .ui.button,.project-settings-hero-actions .ui.button{border-radius:10px}
        @media only screen and (max-width:960px){.project-settings-layout{grid-template-columns:1fr}}
        @media only screen and (max-width:767px){.project-settings-form-wrap,.project-settings-hero,.project-settings-side{padding:22px 20px!important}.project-settings-hero{flex-direction:column}.project-settings-actions{flex-direction:column-reverse}.project-settings-actions .ui.button,.project-settings-hero-actions .ui.button{width:100%}}
    </style>
</head>
<body>
<#assign settingItemActive="active"><#assign settingsAppActive="active"/><#assign loginRole=loginNameRole!'visitor' />
<#include "../projectHeader.ftl">
<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a><span class="divider">/</span>
        <a class="section" href="/p/${project.id}/edit">项目设置</a><span class="divider">/</span>
        <a class="section" href="/p/${project.id}/app/list">应用列表</a><span class="divider">/</span><div class="active section">修改应用</div>
    </div>
    <div class="project-settings-layout">
        <div class="project-settings-side"><#include "LeftNavigationMenu.ftl"></div>
        <div class="project-settings-main">
            <div class="project-settings-hero"><div><div class="project-settings-hero-label"><i class="edit outline icon"></i>应用资产管理</div><h1 class="project-settings-hero-title">修改应用</h1><p class="project-settings-hero-desc">维护应用基础信息、版本标识与探针属性配置，保存后将同步到当前项目应用清单。</p></div><div class="project-settings-hero-actions"><a class="ui button" href="/p/${project.id}/app/list"><i class="left arrow icon"></i>返回应用列表</a></div></div>
            <div class="project-settings-form-wrap">
                <form id="editAppForm" class="ui form project-settings-form">
                    <div class="project-settings-section"><div class="project-settings-section-title"><i class="info circle icon"></i>基础信息</div>
                        <div class="field required"><label>应用 ID：</label><input type="text" name="id" value="${app.id}" readonly=""></div>
                        <div class="field required"><label>应用名称：</label><input type="text" name="name" value="${app.name}" placeholder="输入简短的应用名称"></div>
                        <div class="inline fields"><label>作用范围：</label><div class="field"><div class="ui radio checkbox"><input type="radio" name="range" <#if app.range=='only'>checked=""</#if> tabindex="0" value="only"><label>仅当前项目</label></div></div><div class="field"><div class="ui radio checkbox"><input type="radio" name="range" <#if app.range=='all'>checked=""</#if> tabindex="1" value="all"><label>所有项目</label></div></div></div>
                        <div class="field required"><label>工程名称：</label><input type="text" name="srcName" value="${app.srcName}" placeholder="输入项目源码中的工程名"></div>
                        <div class="field"><label>应用描述：</label><textarea placeholder="输入应用描述" name="describe">${app.describe!}</textarea></div>
                    </div>
                    <div class="project-settings-section"><div class="project-settings-section-title"><i class="tag icon"></i>当前版本</div><div class="three fields"><div class="field"><input type="text" name="currentVersion" value="${app.currentVersion!}" placeholder="当前版本号"></div><div class="field"><input type="text" name="currentBranch" value="${app.currentBranch!}" placeholder="当前分支"></div><div class="field"><input type="text" name="currentCommitId" value="${app.currentCommitId!}" placeholder="当前CommitId"></div></div></div>
                    <div class="project-settings-section"><div class="project-settings-section-title"><i class="sliders horizontal icon"></i>属性配置</div><div class="field"><textarea id="propertiesEdit" placeholder="输入配置信息" name="properties"><#if app.properties??>${app.properties}<#else><#include "appInitConfig.properties"></#if></textarea></div></div>
                    <div class="project-settings-section"><div class="project-settings-section-title"><i class="bell outline icon"></i>探针上下线告警</div><p class="project-alert-desc">按每个探针实例独立判断上下线，超过配置阈值未收到心跳后发送通用 Webhook 通知。下线阈值最小支持 <strong>30 秒</strong>，小于 30 秒会自动按 30 秒保存，避免频繁心跳抖动造成误报。测试时可填当前服务内置地址：<code>http://127.0.0.1:8899/webhook/oat/probe-alert</code>。</p>
                        <div class="field project-alert-switch"><div class="ui checkbox"><input type="hidden" name="probeAlertEnabled" value="false"><input type="checkbox" name="probeAlertEnabled" value="true" <#if app.probeAlertEnabled?? && app.probeAlertEnabled>checked="checked"</#if>><label>启用探针实例上下线告警</label></div></div>
                        <div class="two fields"><div class="field"><label>下线阈值（秒）</label><input type="number" min="30" name="probeOfflineThresholdSeconds" value="${app.probeOfflineThresholdSeconds!90}" placeholder="例如：90"><div class="ui pointing basic label">最小 30 秒；保存小于 30 的值时会自动调整为 30 秒。</div></div><div class="field"><label>Webhook 地址</label><input type="text" name="probeWebhookUrl" value="${app.probeWebhookUrl!}" placeholder="http://127.0.0.1:8899/webhook/oat/probe-alert"></div></div>
                        <div class="inline fields project-alert-events"><label>通知事件</label><div class="field"><div class="ui checkbox"><input type="hidden" name="probeAlertOnOffline" value="false"><input type="checkbox" name="probeAlertOnOffline" value="true" <#if !app.probeAlertOnOffline?? || app.probeAlertOnOffline>checked="checked"</#if>><label>下线</label></div></div><div class="field"><div class="ui checkbox"><input type="hidden" name="probeAlertOnRecovered" value="false"><input type="checkbox" name="probeAlertOnRecovered" value="true" <#if !app.probeAlertOnRecovered?? || app.probeAlertOnRecovered>checked="checked"</#if>><label>恢复上线</label></div></div><div class="field"><div class="ui checkbox"><input type="hidden" name="probeAlertOnOnline" value="false"><input type="checkbox" name="probeAlertOnOnline" value="true" <#if app.probeAlertOnOnline?? && app.probeAlertOnOnline>checked="checked"</#if>><label>首次上线</label></div></div></div>
                    </div>
                    <div class="project-settings-actions"><a class="ui button" href="/p/${project.id}/app/list">取消并返回</a><button class="ui button" type="reset">重置</button><button id="editAppSubmitButton" class="ui primary button" type="button" onclick="submitEditApp()"><i class="save outline icon"></i>保存应用</button></div>
                </form>
            </div>
        </div>
    </div>
</div>
<script>
    var editor=CodeMirror.fromTextArea(document.getElementById("propertiesEdit"),{lineNumbers:true});
    function submitEditApp(){var $form=$('#editAppForm');if(oatIsFormSubmitting($form)){return}if(editor){editor.save()}if(!$form.form('is valid')){return}var data=$form.serialize();oatSetFormSubmitting($form,true,{submitButton:'#editAppSubmitButton',editor:editor,extraControls:'.project-settings-hero-actions .ui.button'});$.post('/p/${project.id}/app/doEdit',data,function(res){if(res.success||res.result){showToast(res.message||'应用修改成功','success');setTimeout(function(){location.href='/p/${project.id}/app/list'},1000)}else{oatSetFormSubmitting($form,false,{submitButton:'#editAppSubmitButton',editor:editor});showToast(res.message||'应用修改失败','error')}}).fail(function(){oatSetFormSubmitting($form,false,{submitButton:'#editAppSubmitButton',editor:editor});showToast('网络请求失败','error')})}
    $(function(){$('.ui.checkbox').checkbox();$('.ui.radio.checkbox').checkbox();$('#editAppForm').form({inline:false,keyboardShortcuts:false,on:'blur',onFailure:function(formErrors){if(formErrors&&formErrors.length>0){showToast(formErrors[0],'error')}return false},fields:{name:{identifier:'name',rules:[{type:'empty',prompt:'请输入应用名称'},{type:'minLength[4]',prompt:'应用名称至少包含4个字符'},{type:'maxLength[50]',prompt:'应用名称不能超过50个字符'}]},srcName:{identifier:'srcName',rules:[{type:'empty',prompt:'请输入工程名称'},{type:'minLength[4]',prompt:'工程名称至少包含4个字符'},{type:'maxLength[50]',prompt:'项目名称不能超过50个字符'}]},describe:{identifier:'describe',rules:[{type:'maxLength[256]',prompt:'项目描述不能超过256个字符'}]}}})});
</script>
</body>
</html>

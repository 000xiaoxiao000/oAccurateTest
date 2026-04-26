<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-应用设置</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单引入-->
<div class="include">
    <#assign settingItemActive="active">
    <#include  "../projectHeader.ftl">
</div>
<!--面包屑导航-->
<div class="ui container app-unified-page">
    <div class="ui small breadcrumb app-unified-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/app/${app.id}/settings">应用设置</a>
        <span class="divider">/</span>
        <div class="active section">${app.name}</div>
    </div>

    <div class="app-unified-layout">
        <div class="app-unified-side">
            <div class="ui vertical menu settings-nav">
                <div class="section-title item">
                    <div class="ui inline click dropdown">
                        <span>${app.name}</span>
                        <i class="icon click dropdown"></i>
                        <div class="menu">
                            <div class="ui search icon input">
                                <i class="search icon"></i>
                                <input type="text" name="search" placeholder="搜索...">
                            </div>
                            <div class="header">选择应用</div>
                            <div class="divider"></div>
                            <#list apps as a>
                                <a class="item" href="/p/${project.id}/app/${a.id}/settings#basicInfo">
                                    ${a.name}
                                </a>
                            </#list>
                        </div>
                    </div>
                </div>
                <a class="top-level item" href="/p/${project.id}/usecase/list">
                    用例中心
                </a>
                <a class="top-level item" href="/p/${project.id}/${app.id}/snapshot/list">
                    系统快照
                </a>
                <div class="subnav-group item">
                    <div class="subnav-label">设置</div>
                    <div class="subnav-menu">
                        <a class="item subnav-item active" href="/p/${project.id}/app/${app.id}/settings#basicInfo">应用设置</a>
                    </div>
                </div>
            </div>
        </div>

        <div class="app-unified-main">
            <div class="app-unified-header">
                <div>
                    <div class="app-unified-kicker">
                        <i class="cog icon"></i>
                        应用设置
                    </div>
                    <h1 class="app-unified-title">${app.name}</h1>
                    <p class="app-unified-desc">统一维护应用基础信息、工程名称、作用范围和启动参数。</p>
                </div>
            </div>

            <div class="app-unified-content" id="basicInfo">
                <div class="app-unified-section-head">
                    <div>
                        <h2 class="app-unified-section-title">基本信息</h2>
                        <p class="app-unified-section-desc">建议应用名称和工程名称保持清晰一致，应用参数修改后需要重启相关应用才会生效。</p>
                    </div>
                </div>
                <form class="ui form app-unified-form" action="/p/${project.id}/app/${app.id}/edit" method="post">
                    <#if toastMessage??>
                        <input type="hidden" id="pageToastMessage" value="${toastMessage}">
                        <input type="hidden" id="pageToastMessageType" value="${toastMessageType!'success'}">
                    </#if>
                    <div class="field">
                        <label>应用id</label>
                        <input type="text" value="${app.id}" readonly name="id">
                    </div>
                    <div class="two fields">
                        <div class="field">
                            <label>应用名称</label>
                            <input type="text" name="name" value="${app.name}">
                        </div>
                        <div class="field">
                            <label>工程名称</label>
                            <input type="text" name="srcName" value="${app.srcName}">
                        </div>
                    </div>
                    <div class="inline fields">
                        <label>作用范围：</label>
                        <div class="field">
                            <div class="ui radio checkbox">
                                <input type="radio" name="range" <#if app.range=='only'> checked=""</#if> tabindex="0" value="only">
                                <label>仅当前项目</label>
                            </div>
                        </div>
                        <div class="field">
                            <div class="ui radio checkbox">
                                <input type="radio" name="range" <#if app.range=='all'> checked=""</#if> tabindex="1" value="all">
                                <label>所有项目</label>
                            </div>
                        </div>
                    </div>
                    <div class="field">
                        <label>应用描述</label>
                        <textarea rows="3" name="describe">${app.describe!}</textarea>
                    </div>
                    <div class="field">
                        <label>应用参数</label>
                        <textarea rows="4" name="properties" placeholder="必须重启相关应用配置才会生效">${app.properties!}</textarea>
                    </div>

                    <div class="ui divider"></div>
                    <h3 class="ui header">探针上下线告警</h3>
                    <p class="app-unified-section-desc">按每个探针实例独立判断上下线，超过配置阈值未收到心跳后发送通用 Webhook 通知。下线阈值最小支持 <strong>30 秒</strong>，小于 30 秒会自动按 30 秒保存，避免频繁心跳抖动造成误报。测试时可填当前服务内置地址：<code>http://127.0.0.1:8899/webhook/oat/probe-alert</code>。</p>
                    <div class="field">
                        <div class="ui checkbox">
                            <input type="checkbox" name="probeAlertEnabled" value="true" <#if app.probeAlertEnabled?? && app.probeAlertEnabled>checked="checked"</#if>>
                            <label>启用探针实例上下线告警</label>
                        </div>
                    </div>
                    <div class="two fields">
                        <div class="field">
                            <label>下线阈值（秒）</label>
                            <input type="number" min="30" name="probeOfflineThresholdSeconds" value="${app.probeOfflineThresholdSeconds!90}">
                            <div class="ui pointing basic label">最小 30 秒；保存小于 30 的值时会自动调整为 30 秒。</div>
                        </div>
                        <div class="field">
                            <label>Webhook 地址</label>
                            <input type="text" name="probeWebhookUrl" value="${app.probeWebhookUrl!}" placeholder="http://127.0.0.1:8899/webhook/oat/probe-alert">
                        </div>
                    </div>
                    <div class="inline fields">
                        <label>通知事件：</label>
                        <div class="field">
                            <div class="ui checkbox">
                                <input type="checkbox" name="probeAlertOnOffline" value="true" <#if app.probeAlertOnOffline?? && app.probeAlertOnOffline>checked="checked"</#if>>
                                <label>下线</label>
                            </div>
                        </div>
                        <div class="field">
                            <div class="ui checkbox">
                                <input type="checkbox" name="probeAlertOnRecovered" value="true" <#if app.probeAlertOnRecovered?? && app.probeAlertOnRecovered>checked="checked"</#if>>
                                <label>恢复上线</label>
                            </div>
                        </div>
                        <div class="field">
                            <div class="ui checkbox">
                                <input type="checkbox" name="probeAlertOnOnline" value="true" <#if app.probeAlertOnOnline?? && app.probeAlertOnOnline>checked="checked"</#if>>
                                <label>首次上线</label>
                            </div>
                        </div>
                    </div>
                    <div class="ui segment probe-alert-summary">
                        <div class="app-unified-section-head probe-alert-summary-head">
                            <div>
                                <h4 class="ui header">探针运行状态</h4>
                                <p class="app-unified-section-desc">这里只展示摘要，详细实例和告警记录请进入详情页查看。</p>
                            </div>
                            <a class="ui basic teal button" href="/p/${project.id}/app/${app.id}/probe-alerts">
                                查看详情
                                <i class="right arrow icon"></i>
                            </a>
                        </div>
                        <div class="ui four tiny statistics probe-alert-stats">
                            <div class="green statistic">
                                <div class="value">${probeAlertDashboard.onlineCount!0}</div>
                                <div class="label">在线探针</div>
                            </div>
                            <div class="red statistic">
                                <div class="value">${probeAlertDashboard.offlineCount!0}</div>
                                <div class="label">离线探针</div>
                            </div>
                            <div class="blue statistic">
                                <div class="value">${probeAlertDashboard.recentEventCount!0}</div>
                                <div class="label">最近告警</div>
                            </div>
                            <div class="orange statistic">
                                <div class="value">${probeAlertDashboard.failedNotifyCount!0}</div>
                                <div class="label">通知失败</div>
                            </div>
                        </div>
                        <#if probeAlertDashboard.latestEventMessage??>
                            <div class="probe-alert-latest">
                                <span class="meta-chip">最近：${probeAlertDashboard.latestEventTimeText!'-'}</span>
                                <span>${probeAlertDashboard.latestEventMessage}</span>
                            </div>
                        </#if>
                    </div>
                    <#if loginNameRole != "visitor">
                        <div class="app-unified-form-actions">
                            <div id="appSettingsSaveButton" class="ui primary button" type="submit">
                                <i class="save outline icon"></i>
                                保存
                            </div>
                        </div>
                    </#if>
                </form>
            </div>

            <div id="deleteApp" class="danger-zone danger-zone-spaced">
                <div class="danger-zone-header">
                    <div class="danger-zone-title">删除应用</div>
                    <p class="danger-zone-desc">删除操作会永久清除应用信息，并且不可恢复。</p>
                </div>
                <div class="danger-zone-body">
                    <div class="danger-zone-warning">
                        <i class="exclamation triangle icon"></i>
                        <div>删除操作会永久清除应用信息，并且 <strong>不可恢复</strong>！请输入密码后确认删除。</div>
                    </div>
                    <form class="ui form app-unified-form" action="/p/${project.id}/app/${app.id}/delete" method="post">
                        <div class="required field">
                            <label for="password">密码</label>
                            <input id="password" name="password" type="password" autofocus="" required="">
                        </div>
                        <#if loginNameRole != "visitor">
                            <div class="app-unified-form-actions">
                                <div id="appDeleteSubmitButton" class="ui red button" type="submit">
                                    确认删除该应用
                                </div>
                            </div>
                        </#if>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>

<script>

    $(function () {
        // $('.ui.dropdown').dropdown({
        //      on: 'hover'
        //  });
        //  $('.ui.filter.dropdown').dropdown({
        //      on: 'click'
        //  });

        $('.ui.container .click.dropdown').dropdown({
            on: 'click'
        });
        $('.ui.checkbox').checkbox();
        $('.app-unified-form').on('submit', function () {
            var $form = $(this);
            if (oatIsFormSubmitting($form)) {
                return false;
            }
            oatSetFormSubmitting($form, true, {
                submitButton: $form.find('.ui.button[type="submit"]').first(),
                keepFieldsEnabled: true,
                readonlyFields: true,
                message: '正在保存设置...'
            });
            return true;
        });
        var pageToastMessage = $('#pageToastMessage').val();
        if (pageToastMessage) {
            notifyToast(pageToastMessage, $('#pageToastMessageType').val() || 'success');
        }
        $(".ui.button[type='submit']").click(function () {
            $(this).parents("form").first().submit();
        });
    });


    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }
</script>
</body>
</html>

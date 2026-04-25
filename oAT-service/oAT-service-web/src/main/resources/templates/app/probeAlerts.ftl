<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>探针状态与告警记录</title>
    <#include "../common.ftl">
</head>
<body>
<div class="include">
    <#assign settingItemActive="active">
    <#include  "../projectHeader.ftl">
</div>

<div class="ui container app-unified-page">
    <div class="ui small breadcrumb app-unified-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/app/${app.id}/settings">应用设置</a>
        <span class="divider">/</span>
        <div class="active section">探针状态与告警记录</div>
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
                                <a class="item" href="/p/${project.id}/app/${a.id}/probe-alerts">
                                    ${a.name}
                                </a>
                            </#list>
                        </div>
                    </div>
                </div>
                <a class="top-level item" href="/p/${project.id}/usecase/list">用例中心</a>
                <a class="top-level item" href="/p/${project.id}/${app.id}/snapshot/list">系统快照</a>
                <div class="subnav-group item">
                    <div class="subnav-label">设置</div>
                    <div class="subnav-menu">
                        <a class="item subnav-item" href="/p/${project.id}/app/${app.id}/settings#basicInfo">应用设置</a>
                        <a class="item subnav-item active" href="/p/${project.id}/app/${app.id}/probe-alerts">探针状态与告警</a>
                    </div>
                </div>
            </div>
        </div>

        <div class="app-unified-main">
            <div class="app-unified-header">
                <div>
                    <div class="app-unified-kicker">
                        <i class="bell outline icon"></i>
                        探针告警
                    </div>
                    <h1 class="app-unified-title">探针状态与告警记录</h1>
                    <p class="app-unified-desc">查看当前探针实例在线状态、最近上下线事件以及 Webhook 通知结果。</p>
                </div>
                <div class="app-unified-header-actions">
                    <a class="ui basic button" href="/p/${project.id}/app/${app.id}/settings#basicInfo">
                        <i class="cog icon"></i>
                        返回设置
                    </a>
                    <a class="ui teal button" href="/p/${project.id}/app/${app.id}/probe-alerts">
                        <i class="sync icon"></i>
                        刷新
                    </a>
                </div>
            </div>

            <div class="app-unified-content">
                <div class="ui four tiny statistics probe-alert-stats probe-alert-detail-stats">
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

                <div class="ui top attached tabular menu probe-alert-tabs">
                    <a class="active item" data-tab="probe-status">当前探针</a>
                    <a class="item" data-tab="probe-events">告警记录</a>
                </div>

                <div class="ui bottom attached active tab segment" data-tab="probe-status">
                    <#if probeAlertDashboard.statuses?size == 0>
                        <div class="ui placeholder segment">
                            <div class="ui icon header">
                                <i class="plug icon"></i>
                                暂无探针实例状态
                            </div>
                            <p>探针启动并完成登录后会出现在这里。</p>
                        </div>
                    <#else>
                        <table class="ui very basic celled table probe-alert-table">
                            <thead>
                            <tr>
                                <th>状态</th>
                                <th>IP / PID</th>
                                <th>工作目录</th>
                                <th>Agent 版本</th>
                                <th>最后心跳</th>
                                <th>最近告警</th>
                            </tr>
                            </thead>
                            <tbody>
                            <#list probeAlertDashboard.statuses as item>
                                <tr>
                                    <td><span class="ui ${item.statusColor} label">${item.statusLabel}</span></td>
                                    <td>
                                        <div>${item.addressIp!'-'}</div>
                                        <div class="probe-alert-muted">pid=${item.pid!'-'}</div>
                                    </td>
                                    <td class="probe-alert-path">${item.systemDir!'-'}</td>
                                    <td>${item.agentVersion!'-'}</td>
                                    <td>${item.lastHeartbeatTimeText!'-'}</td>
                                    <td>
                                        <div>${item.lastAlertEventType!'-'}</div>
                                        <div class="probe-alert-muted">${item.lastAlertTimeText!'-'}</div>
                                    </td>
                                </tr>
                            </#list>
                            </tbody>
                        </table>
                    </#if>
                </div>

                <div class="ui bottom attached tab segment" data-tab="probe-events">
                    <#if probeAlertDashboard.recentEvents?size == 0>
                        <div class="ui placeholder segment">
                            <div class="ui icon header">
                                <i class="bell slash outline icon"></i>
                                暂无告警记录
                            </div>
                            <p>探针上下线事件产生后会显示在这里。</p>
                        </div>
                    <#else>
                        <table class="ui very basic celled table probe-alert-table">
                            <thead>
                            <tr>
                                <th>事件</th>
                                <th>探针</th>
                                <th>时间</th>
                                <th>通知状态</th>
                                <th>Webhook 响应</th>
                                <th>错误原因</th>
                            </tr>
                            </thead>
                            <tbody>
                            <#list probeAlertDashboard.recentEvents as event>
                                <tr>
                                    <td><span class="ui ${event.eventTypeColor} label">${event.eventTypeLabel}</span></td>
                                    <td class="probe-alert-path">${event.probeText!'-'}</td>
                                    <td>${event.eventTimeText!'-'}</td>
                                    <td><span class="ui ${event.notifyStatusColor} label">${event.notifyStatusLabel}</span></td>
                                    <td>${event.notifyResponse!'-'}</td>
                                    <td class="probe-alert-error">${event.notifyError!'-'}</td>
                                </tr>
                            </#list>
                            </tbody>
                        </table>
                    </#if>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    $(function () {
        $('.ui.container .click.dropdown').dropdown({
            on: 'click'
        });
        $('.probe-alert-tabs .item').tab();
    });
</script>
</body>
</html>

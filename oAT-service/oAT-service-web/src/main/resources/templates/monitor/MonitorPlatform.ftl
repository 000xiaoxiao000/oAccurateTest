<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>监控台</title>
    <link href="/css/font-awesome.min.css" rel="stylesheet">
    <#include "../common.ftl">
    <script src="/js/d3.min.js" charset="utf-8"></script>

    <script src="/js/dagre-d3.min.js"></script>

    <link href="/css/monokai_sublime.min.css"
          rel="stylesheet">
    <script src="/js/highlight.min.js"></script>
    <script>hljs.initHighlightingOnLoad();</script>
    <#--流程图 图标-->
    <link rel="stylesheet" href="/css/all.css"
          integrity="sha384-UHRtZLI+pbxtHCWp1t77Bi1L4ZtiqrqD80Kn4Z8NTSRyMA2Fd33n5dQ8lWUE00s/"
          crossorigin="anonymous">

    <script src="/js/common.js"></script>
    <script src="/js/monitorPlatform.js?v=${.now}"></script>
    <link href="/css/common.css" rel="stylesheet">
    <script src="/js/upload.js?v=${.now}"></script>
    <script src="/js/spark-md5.min.js"></script>
    <style type="text/css">
        .ui.right.aligned {
            float: right
        }

        #monitorListBody tr.focus .monitor-primary-text {
            color: var(--page-accent);
        }

        #monitorListBody tr {
            cursor: pointer;
        }

        .monitor-page {
            padding: 8px 12px 0;
        }

        .monitor-toolbar.ui.segment {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 14px;
            padding: 12px 16px !important;
            border-radius: 16px !important;
        }

        .monitor-toolbar-left {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
            min-width: 0;
        }

        .monitor-toolbar-right {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            flex-wrap: wrap;
            gap: 12px;
            margin-top: 0;
        }

        .monitor-autosave-inline {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 0;
            margin: 0;
            border: none;
            background: transparent;
            box-shadow: none;
        }

        .monitor-detail-content {
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .monitor-detail-title.page-detail-title {
            line-height: 1.4;
            word-break: break-word;
        }

        .monitor-graph-card {
            min-height: 220px;
        }

        .monitor-node-card {
            min-height: calc(100vh - 520px);
        }

        .monitor-panel-segment {
            height: 100%;
            display: flex;
            flex-direction: column;
        }

        .monitor-list-scroll {
            padding: 0;
            flex: 1 1 auto;
            height: auto;
            min-height: 0;
            overflow: auto;
        }

        .monitor-layout {
            height: calc(100vh - 288px);
            min-height: 420px;
        }

        .monitor-overview-grid {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 10px;
            margin-bottom: 10px;
        }

        .monitor-overview-card {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            padding: 10px 14px;
            border: 1px solid #e4edf7;
            border-radius: 18px;
            background: linear-gradient(135deg, #ffffff 0%, #f5fbff 100%);
            box-shadow: 0 14px 34px rgba(15, 23, 42, .06);
        }

        .monitor-overview-label {
            color: #718096;
            font-size: 13px;
            font-weight: 700;
        }

        .monitor-overview-value {
            margin-top: 0;
            color: #1f2937;
            font-size: 22px;
            font-weight: 800;
            line-height: 1;
        }

        .monitor-overview-sub {
            margin-top: 4px;
            color: #94a3b8;
            font-size: 12px;
        }

        .probe-dashboard-card {
            display: grid !important;
            grid-template-columns: minmax(220px, 280px) minmax(260px, 420px) minmax(160px, 1fr);
            align-items: center;
            gap: 8px 12px;
            margin-bottom: 10px !important;
            padding: 10px 14px !important;
            border-radius: 18px !important;
            border-color: #e4edf7 !important;
            box-shadow: 0 12px 32px rgba(15, 23, 42, .06) !important;
        }

        .probe-dashboard-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            padding: 0;
        }

        .probe-dashboard-title {
            color: #1f2937;
            font-weight: 800;
        }

        .probe-search {
            padding: 0;
        }

        .probe-search .ui.input > input {
            border-radius: 12px !important;
            border-color: #dbe7f3 !important;
            background: #fbfdff !important;
        }

        .probe-list-summary {
            padding: 0;
            color: #94a3b8;
            font-size: 12px;
            text-align: right;
        }

        .probe-list {
            grid-column: 1 / -1;
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
            gap: 6px;
            max-height: 136px;
            overflow: auto;
            padding: 8px 0 0;
            border-top: 1px solid #edf2f7;
        }

        .probe-card {
            display: grid;
            grid-template-columns: auto minmax(0, 1fr) auto;
            gap: 8px;
            align-items: center;
            padding: 8px 6px;
            border: 1px solid transparent;
            border-radius: 14px;
            background: #fbfdff;
            cursor: pointer;
            transition: background .18s ease, border-color .18s ease, transform .18s ease;
        }

        .probe-card:hover,
        .probe-card.active {
            background: #eefaf9;
            border-color: #bfeee9;
            transform: translateY(-1px);
        }

        .probe-status-dot {
            width: 10px;
            height: 10px;
            border-radius: 999px;
            background: #22c55e;
            box-shadow: 0 0 0 6px rgba(34, 197, 94, .12);
            animation: probePulse 1.8s ease-in-out infinite;
        }

        .probe-name {
            overflow: hidden;
            color: #1f2937;
            font-weight: 800;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .probe-meta {
            margin-top: 3px;
            overflow: hidden;
            color: #64748b;
            font-size: 12px;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .probe-badge {
            padding: 4px 8px;
            border-radius: 999px;
            background: #dcfce7;
            color: #15803d;
            font-size: 12px;
            font-weight: 800;
        }

        .probe-empty {
            grid-column: 1 / -1;
            padding: 24px 14px;
            color: #94a3b8;
            text-align: center;
        }

        .monitor-scope-state {
            display: block;
            height: 100%;
            min-height: 0;
            padding: 14px !important;
            margin-top: 0 !important;
        }

        .monitor-scope-state .monitor-scope-column {
            display: flex;
            flex-direction: column;
            width: 100% !important;
            height: 100%;
            max-width: none !important;
        }

        .oscilloscope-card {
            position: relative;
            flex: 1 1 auto;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            min-height: 240px;
            border-radius: 18px;
            background: radial-gradient(circle at top left, rgba(45, 212, 191, .16), transparent 36%), #0f172a;
            box-shadow: inset 0 0 0 1px rgba(148, 163, 184, .18), 0 18px 44px rgba(15, 23, 42, .18);
        }

        .oscilloscope-header {
            position: relative;
            z-index: 1;
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 12px;
            padding: 16px 18px 0;
            color: #dbeafe;
        }

        .oscilloscope-title {
            font-size: 16px;
            font-weight: 900;
        }

        .oscilloscope-subtitle {
            margin-top: 4px;
            color: #94a3b8;
            font-size: 12px;
        }

        .oscilloscope-actions {
            display: flex;
            align-items: flex-end;
            flex-direction: column;
            gap: 8px;
        }

        .oscilloscope-mode .button {
            background: rgba(15, 23, 42, .28) !important;
            color: #cbd5e1 !important;
            box-shadow: inset 0 0 0 1px rgba(148, 163, 184, .22) !important;
        }

        .oscilloscope-mode .button.active {
            background: rgba(20, 184, 166, .22) !important;
            color: #99f6e4 !important;
            box-shadow: inset 0 0 0 1px rgba(94, 234, 212, .46) !important;
        }

        .oscilloscope-status {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            color: #5eead4;
            font-size: 12px;
            font-weight: 800;
        }

        #oscilloscopeCanvas {
            flex: 1 1 auto;
            display: block;
            width: 100%;
            min-height: 0;
        }

        .oscilloscope-empty {
            position: absolute;
            left: 50%;
            top: 58%;
            transform: translate(-50%, -50%);
            color: #94a3b8;
            text-align: center;
            pointer-events: none;
        }

        .monitor-request-summary {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 12px;
            margin-top: 14px;
        }

        .monitor-request-card {
            padding: 14px;
            border: 1px solid #e4edf7;
            border-radius: 16px;
            background: #fff;
        }

        @keyframes probePulse {
            0%, 100% { box-shadow: 0 0 0 5px rgba(34, 197, 94, .12); }
            50% { box-shadow: 0 0 0 9px rgba(34, 197, 94, .04); }
        }

        #monitorListTable .monitor-name-cell {
            min-width: 0;
        }

        #monitorListTable .monitor-primary {
            display: flex;
            align-items: center;
            gap: 0.35em;
            width: 100%;
            min-width: 0;
        }

        #monitorListTable .monitor-primary-text {
            flex: 1 1 auto;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        #monitorListTable.show-full-name .monitor-primary-text {
            overflow: visible;
            text-overflow: unset;
        }

        @media (max-width: 960px) {
            .monitor-layout {
                flex-direction: column;
                height: auto;
                min-height: 0;
            }

            .monitor-list-panel,
            .monitor-detail-panel {
                flex: 1 1 auto;
                max-width: none;
                min-width: 0;
            }

            .monitor-resizer {
                display: none;
            }

            .monitor-list-scroll {
                height: auto;
                max-height: 45vh;
            }

            .monitor-toolbar.ui.segment {
                align-items: stretch;
                flex-direction: column;
            }

            .probe-dashboard-card {
                grid-template-columns: 1fr;
            }

            .probe-list-summary {
                text-align: left;
            }

            .monitor-overview-grid,
            .monitor-request-summary {
                grid-template-columns: 1fr;
            }
        }
    </style>

<body class="page-theme">
<!--头部菜单 引入-->
<#assign  monitorItemActive="active">
<#include "../projectHeader.ftl">

<div class="monitor-page">
<div class="monitor-overview-grid">
    <div class="monitor-overview-card">
        <div>
            <div class="monitor-overview-label">在线探针</div>
            <div class="monitor-overview-sub">当前项目已连接实例</div>
        </div>
        <div class="monitor-overview-value" id="onlineProbeCount">${onlineProbeCount!0}</div>
    </div>
    <div class="monitor-overview-card">
        <div>
            <div class="monitor-overview-label">项目应用</div>
            <div class="monitor-overview-sub">已纳入监控范围</div>
        </div>
        <div class="monitor-overview-value">${apps?size}</div>
    </div>
    <div class="monitor-overview-card">
        <div>
            <div class="monitor-overview-label">实时请求</div>
            <div class="monitor-overview-sub">当前过滤窗口内</div>
        </div>
        <div class="monitor-overview-value" id="monitorRequestCount">0</div>
    </div>
    <div class="monitor-overview-card">
        <div>
            <div class="monitor-overview-label">最后接收</div>
            <div class="monitor-overview-sub">监控数据到达时间</div>
        </div>
        <div class="monitor-overview-value" id="monitorLastReceive" style="font-size: 18px;">等待中</div>
    </div>
</div>
        <div class="ui segment probe-dashboard-card">
            <div class="probe-dashboard-header">
                <div>
                    <div class="probe-dashboard-title">探针在线状态</div>
                    <div class="ui mini grey text">点击探针可快速过滤 IP</div>
                </div>
                <button class="ui mini basic icon button" onclick="refreshProbeStatus('${projectId}')" title="刷新探针状态">
                    <i class="sync icon"></i>
                </button>
            </div>
            <div class="probe-search">
                <div class="ui fluid icon input">
                    <i class="search icon"></i>
                    <input id="probeSearchInput" type="text" placeholder="搜索应用、IP、PID、Agent">
                </div>
            </div>
            <div id="probeListSummary" class="probe-list-summary">在线 ${onlineProbeCount!0} 个探针</div>
            <div id="probeList" class="probe-list">
                <#if onlineSessions?? && (onlineSessions?size > 0)>
                    <#list onlineSessions as session>
                        <#assign client=session.clientInfo>
                        <#assign app=session.application>
                        <div class="probe-card" data-ip="${(client.addressIp)!''}" data-app-id="${(client.appKey)!''}">
                            <span class="probe-status-dot"></span>
                            <div style="min-width: 0;">
                                <div class="probe-name">${(app.appName)!'未定义应用'}</div>
                                <div class="probe-meta">${(client.addressIp)!'-'} · PID ${(client.pid)!'-'} · Agent ${(client.agentVersion)!'-'}</div>
                                <div class="probe-meta">在线 ${session.onlineTime!'-'} · 心跳 ${(session.lastHeartbeatTime?number_to_datetime?string('HH:mm:ss'))!'-'}</div>
                            </div>
                            <span class="probe-badge">在线</span>
                        </div>
                    </#list>
                <#else>
                    <div class="probe-empty">
                        <i class="plug icon"></i>
                        暂无在线探针，启动 Agent 后会显示在这里
                    </div>
                </#if>
            </div>
        </div>
<!--中间过滤条件-->
<div id="middleFilter" class="ui sticky top segment monitor-toolbar" style="margin-bottom: 14px;">
    <div class="monitor-toolbar-left">
        <form id="itemFilter" class="ui form" action="/p/${projectId}/monitor/getNodeByTime" style="margin: 0;">
            <div class="inline fields" style="margin: 0; gap: 8px; align-items: center; flex-wrap: wrap;">
                <span style="display: inline-block; margin: 0; white-space: nowrap;">
                    <i class="filter icon"></i>条件过滤：
                </span>
                <input type="hidden" name="maxSize" value="100">
                <div class="ui dropdown" style="padding: 7px; min-width: 100px">
                    <#--默认值3分钟-->
                    <input type="hidden" name="upToTime" value="180">
                    <span class="text">三分钟内</span>
                    <div class="ui divider" style="margin:5px 0px 0px 0px"></div>
                    <div class="menu">
                        <div class="item" data-value="60">
                            一分钟内
                        </div>
                        <div class="item active" data-value="180">
                            三分钟内
                        </div>
                        <div class="item" data-value="300">
                            五分钟内
                        </div>
                        <div class="item" data-value="1800">
                            三十分钟内
                        </div>
                    </div>
                </div>

                <div class="ui multiple search compact selection dropdown"
                     style="border: none; margin-right: 0px; padding-right: 7px; min-width: 120px">
                    <input type="hidden" name="appIds" value="${appId!}">
                    <input class="search" autocomplete="off" tabindex="0">
                    <div class="text">应用过滤</div>
                    <div class="ui divider" style="margin: 0px"></div>
                    <div class="menu" tabindex="-1">
                        <#list apps as app >
                            <div class="item" data-value="${app.id}">${app.name}</div>
                        </#list>
                    </div>
                </div>
                <div class="ui multiple compact search selection dropdown ipFilter"
                     style="border: none; min-width: 110px; padding-right: 7px">
                    <input type="hidden" name="clientIps">
                    <input class="search" autocomplete="off" tabindex="0">
                    <div class=" default text">ip 过滤</div>
                    <div class="ui divider" style="margin: 0px"></div>
                    <div class="menu" tabindex="-1"></div>
                </div>
            </div>
        </form>
    </div>

    <div class="monitor-toolbar-right">
        <div class="ui pointing dropdown small disabled" id="saveSnapshotDropdown" tabindex="-1">
            <button class="ui primary button disabled" id="saveSnapshotDropdownButton" style="margin: 0;">
                保存快照
            </button>
            <div class="menu" tabindex="1">
                <div class="item" onclick="openCreateSnapshot();">我的快照</div>
                <div class="item" onclick="openCreateSystemSnapshot();">系统快照</div>
            </div>
        </div>
        <div class="ui form monitor-autosave-inline">
            <div class="field" style="margin: 0;">
                <div class="ui toggle checkbox" id="autoSaveMySnapshotWrapper" data-tooltip="监控新数据进入列表后自动保存；同一条链路不会重复自动保存" data-position="top center">
                    <input type="checkbox" id="autoSaveMySnapshotToggle">
                    <label for="autoSaveMySnapshotToggle">自动保存我的快照</label>
                </div>
            </div>
            <div class="field" style="margin: 0;">
                <div class="ui toggle checkbox" id="autoSaveSystemSnapshotWrapper" data-tooltip="监控新数据进入列表后自动保存；同一条链路不会重复自动保存" data-position="top center">
                    <input type="checkbox" id="autoSaveSystemSnapshotToggle">
                    <label for="autoSaveSystemSnapshotToggle">自动保存系统快照</label>
                </div>
            </div>
            <div id="monitorActionHint" class="ui mini grey text">自动保存会在新监控数据进入列表时触发；手动保存请先选择一条记录</div>
        </div>
    </div>
        <!--保存快照弹出框-->
        <div id="snapshotDialog" class="ui modal standard  save snapshot">
            <div class="ui block header attached ">
                保存当前快照
            </div>
            <i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>

            <div class="ui content">
                <form id="newSnapshotForm" class="ui form" action="/p/${projectId}/snapshot/save">
                    <div class="required field">
                        <label>快照名称</label>
                        <input type="text" name="name" placeholder="输入快照名称">
                    </div>
                    <div class="field">
                        <label>添加标签</label>
                        <select multiple="3" class="ui search dropdown" name="labels">
                            <#list labels as lab>
                                <option value="${lab.name}">${lab.name}</option>
                            </#list>
                        </select>
                    </div>
                    <div class="field">
                        <label> 快照描述</label>
                        <div class="content">
                            <textarea rows="3" name="describe"></textarea>
                        </div>
                    </div>
                    <div class="ui error message">

                    </div>
                </form>
            </div>
            <div class="actions">
                <div class="ui black deny button">
                    取消
                </div>
                <div id="saveSnapshotButton" class="ui positive right labeled icon button save">
                    保存
                    <i class="checkmark icon"></i>
                </div>
            </div>
        </div>
        <#--保存系统快照 窗口-->
        <div id="systemSnapshotDialog" class="ui modal standard save snapshot ">


        </div>
    </div>
</div>
<div class="monitor-layout page-split-layout">
    <div id="monitorListPanel" class="monitor-list-panel page-split-list-panel">
        <div class="ui segment monitor-panel-segment page-panel-shell">
            <div class="ui block header top attached segment page-section-header">
                <div class="ui compact tiny menu page-nav-menu">
                    <a class="item page-nav-link active" href="/p/${projectId}/monitor">
                        <i class="line graph icon"></i>
                        实时监控
                    </a>
                    <a class="item page-nav-link" href="/p/${projectId}/snapshot/my">
                        <i class="copy outline icon"></i>
                        我的快照
                    </a>
                </div>
                <div class="page-section-actions">
                    <div class="ui mini basic icon button poping up" onclick="pullNewItem('${projectId}');" data-content="获取最新数据"
                         data-variation="tiny inverted" title="刷新列表">
                        <i class="refresh icon"></i>
                    </div>
                </div>
            </div>
            <div class="ui attached segment monitor-list-scroll">
                <table id="monitorListTable" class="ui selectable single line compact fixed table" style="border: 1px solid rgba(34,36,38,.08);">
                    <tbody id="monitorListBody">
                    <!-- 监控列表 -->
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <div id="monitorResizer" class="monitor-resizer split-resizer" aria-hidden="true">
        <span class="monitor-resizer-hint split-resizer-hint">拖拽调整宽度</span>
    </div>
    <div class="monitor-detail-panel page-split-detail-panel" style="padding-right: 0;">
        <!--欢迎提示面版-->
        <div id="emptyTip" class="ui segment monitor-empty-state page-empty-state monitor-scope-state">
            <div class="monitor-scope-column">
                <div class="oscilloscope-card">
                    <div class="oscilloscope-header">
                        <div>
                            <div class="oscilloscope-title">实时请求示波器</div>
                            <div id="oscilloscopeSubtitle" class="oscilloscope-subtitle">聚合全部探针：圆点 = 一次请求；折线 = 请求脉冲趋势；扫描线 = 实时监听节奏</div>
                        </div>
                        <div class="oscilloscope-actions">
                            <div class="ui mini buttons oscilloscope-mode">
                                <button type="button" class="ui button active" data-mode="aggregate">全部探针</button>
                                <button type="button" class="ui button" data-mode="single">当前探针</button>
                                <button type="button" class="ui button" data-mode="lanes">多探针泳道</button>
                            </div>
                            <div class="oscilloscope-status"><span class="probe-status-dot"></span><span id="oscilloscopeStatusText">等待请求</span></div>
                        </div>
                    </div>
                    <canvas id="oscilloscopeCanvas"></canvas>
                    <div id="oscilloscopeEmpty" class="oscilloscope-empty">
                        <i class="wave square icon"></i>
                        <div>暂无请求波形</div>
                        <div style="font-size: 12px; margin-top: 6px;">当监控列表收到请求后，每个请求会形成一个圆点</div>
                    </div>
                </div>
                <div class="monitor-request-summary">
                    <div class="monitor-request-card">
                        <div class="monitor-overview-label">最新请求</div>
                        <div id="latestTraceTitle" class="monitor-overview-sub" style="color: #1f2937; font-weight: 800;">暂无</div>
                    </div>
                    <div class="monitor-request-card">
                        <div class="monitor-overview-label">请求来源</div>
                        <div id="latestTraceSource" class="monitor-overview-sub" style="color: #1f2937; font-weight: 800;">-</div>
                    </div>
                    <div class="monitor-request-card">
                        <div class="monitor-overview-label">操作提示</div>
                        <div class="monitor-overview-sub" style="color: #1f2937; font-weight: 800;">点击左侧请求查看链路详情</div>
                    </div>
                </div>
            </div>
        </div>

        <!--监控详情-->
        <div id="monitorDetail" style="display: none">
            <div class="monitor-detail-shell page-detail-shell">
                <div class="monitor-detail-header page-detail-header-card">
                    <div class="monitor-detail-header-main page-detail-title-row">
                        <div>
                            <div class="monitor-detail-kicker page-detail-kicker">实时监控详情</div>
                            <div id="monitorDetailTitle" class="monitor-detail-title page-detail-title"></div>
                        </div>
                        <button class="ui basic teal button" onclick="showMonitorOscilloscope()">
                            <i class="wave square icon"></i>
                            返回示波器
                        </button>
                    </div>
                </div>
                <div class="monitor-detail-content">
                    <div class="monitor-graph-card page-detail-card">
                        <div class="monitor-card-title page-detail-card-title">调用链路</div>
                        <svg id="svg-canvas" height="200" width="900"></svg>
                    </div>
                    <div class="monitor-node-card page-detail-card trace-node-detail-card">
                        <div class="monitor-card-title page-detail-card-title trace-node-detail-title">节点详情</div>
                        <div id="nodeDetail" class="ui segment basic trace-node-detail-content"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>


</div>
<#--界面初始化-->
<script language="JavaScript">
    var monitorLayoutWidthController = {
        storageKey: 'monitorListWidth',
        defaultWidth: 360,
        minWidth: 280,
        maxReservedWidth: 360,
        page: null,
        listPanel: null,
        resizer: null,
        applyWidth: function (width) {
            if (!this.page || !this.listPanel) {
                return width;
            }
            var maxWidth = Math.max(this.minWidth, this.page.width() - this.maxReservedWidth);
            var nextWidth = Math.min(Math.max(width, this.minWidth), maxWidth);
            this.listPanel.css('flex-basis', nextWidth + 'px');
            return nextWidth;
        }
    };

    function syncMonitorTitleDisplay(width) {
        var table = $('#monitorListTable');
        if (!table.length) {
            return;
        }
        if (width >= 460 || window.innerWidth <= 960) {
            table.addClass('show-full-name');
        } else {
            table.removeClass('show-full-name');
        }
    }

    function resetMonitorLayoutWidth() {
        if (window.innerWidth <= 960) {
            return;
        }
        localStorage.removeItem(monitorLayoutWidthController.storageKey);
        monitorLayoutWidthController.applyWidth(monitorLayoutWidthController.defaultWidth);
        syncMonitorTitleDisplay(monitorLayoutWidthController.defaultWidth);
    }

    function initMonitorLayoutResizer() {
        var controller = monitorLayoutWidthController;
        controller.page = $('.monitor-page');
        controller.listPanel = $('#monitorListPanel');
        controller.resizer = $('#monitorResizer');
        if (!controller.page.length || !controller.listPanel.length || !controller.resizer.length) {
            return;
        }

        if (window.innerWidth <= 960) {
            controller.listPanel.css('flex-basis', 'auto');
            syncMonitorTitleDisplay(controller.defaultWidth);
            return;
        }

        var storedWidth = parseInt(localStorage.getItem(controller.storageKey), 10);
        if (!isNaN(storedWidth)) {
            controller.applyWidth(storedWidth);
            syncMonitorTitleDisplay(storedWidth);
        } else {
            controller.applyWidth(controller.defaultWidth);
            syncMonitorTitleDisplay(controller.defaultWidth);
        }

        var dragging = false;

        controller.resizer.on('dblclick', function () {
            resetMonitorLayoutWidth();
        });

        controller.resizer.on('mousedown', function (event) {
            if (window.innerWidth <= 960) {
                return;
            }
            dragging = true;
            controller.resizer.addClass('dragging');
            $('body').css('cursor', 'col-resize');
            event.preventDefault();
        });

        $(document).on('mousemove.monitorResizer', function (event) {
            if (!dragging) {
                return;
            }
            var pageOffset = controller.page.offset();
            if (!pageOffset) {
                return;
            }
            var width = event.pageX - pageOffset.left;
            var appliedWidth = controller.applyWidth(width);
            syncMonitorTitleDisplay(appliedWidth);
        });

        $(document).on('mouseup.monitorResizer', function () {
            if (!dragging) {
                return;
            }
            dragging = false;
            controller.resizer.removeClass('dragging');
            $('body').css('cursor', '');
            var currentWidth = parseInt(controller.listPanel.css('flex-basis'), 10);
            if (!isNaN(currentWidth)) {
                localStorage.setItem(controller.storageKey, currentWidth);
                syncMonitorTitleDisplay(currentWidth);
            }
        });

        $(window).on('resize.monitorResizer', function () {
            if (window.innerWidth <= 960) {
                controller.listPanel.css('flex-basis', 'auto');
                $('#monitorListTable').addClass('show-full-name');
                return;
            }
            var currentWidth = parseInt(localStorage.getItem(controller.storageKey), 10);
            if (!isNaN(currentWidth)) {
                controller.applyWidth(currentWidth);
                syncMonitorTitleDisplay(currentWidth);
            } else {
                controller.applyWidth(controller.defaultWidth);
                syncMonitorTitleDisplay(controller.defaultWidth);
            }
        });
    }

    $('.ui.sticky').sticky();
    $('#middleFilter .ui.dropdown').dropdown({
        onChange: function (value, text, selectedItem) {
            refreshMonitorList('${projectId}');
        }
    });
    $('.ipFilter').dropdown({
        allowAdditions: true,
        onChange: function (value, text, selectedItem) {
            refreshMonitorList('${projectId}');
        }
    });

    $('#middleFilter .search.dropdown.add.type')
        .dropdown({
            allowAdditions: true
        });
    $('#newAction').dropdown({
        on: 'click'
    });

    $('.ui.click.dropdown').dropdown({
        on: 'click'
    });

    $('.poping.up').popup();

    $(function () {
        initMonitorLayoutResizer();
        syncMonitorAutoSaveSwitches('${projectId}');
        $('.ui.toggle.checkbox').checkbox();
        $('#autoSaveMySnapshotToggle').on('change', function () {
            setMonitorAutoSaveEnabled('${projectId}', 'my', $(this).is(':checked'));
        });
        $('#autoSaveSystemSnapshotToggle').on('change', function () {
            setMonitorAutoSaveEnabled('${projectId}', 'system', $(this).is(':checked'));
        });
        $('#saveSnapshotDropdown .item').on('click', function (event) {
            if (!selectTraceId) {
                event.preventDefault();
                showToast('请先从左侧监控列表选择一条记录', 'warning');
            }
        });
        updateMonitorActionAvailability();
        // 初始化表单验证规则
        $('#newSnapshotForm').form({
                inline: false,
                onFailure: function (formErrors, fields) {
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
                                prompt: '请输入快照名称'
                            },
                            {
                                type: 'minLength[4]',
                                prompt: '快照名称至少包含4个字符'
                            },
                            {
                                type: 'maxLength[50]',
                                prompt: '快照名称不能超过50个字符'
                            }
                        ]
                    },
                    describe: {
                        identifier: 'describe',
                        rules: [
                            {
                                type: 'maxLength[512]',
                                prompt: '快照描述不能超过512个字符'
                            }
                        ]
                    }
                }
            });

        $('#saveSnapshotButton').on('click', function() {
            if ($('#newSnapshotForm').form('is valid')) {
                doSaveSnapshot('${projectId}', function () {
                    $("#newSnapshotForm").parents(".modal").modal('hide');
                });
            }
        });
        startMonitorOscilloscopeAnimation();
        refreshProbeStatus('${projectId}');
        setInterval(function () {
            refreshProbeStatus('${projectId}');
        }, 15000);
        $('#probeList').on('click', '.probe-card', function () {
            $('#probeList .probe-card').removeClass('active');
            $(this).addClass('active');
            var ip = $(this).data('ip');
            if (ip) {
                setMonitorScopeMode('single', ip);
                $('.ipFilter').dropdown('set selected', ip);
                refreshMonitorList('${projectId}');
            }
        });
        $('.oscilloscope-mode').on('click', '.button', function () {
            var mode = $(this).data('mode');
            setMonitorScopeMode(mode, monitorSelectedProbeIp);
            if (mode === 'aggregate' || mode === 'lanes') {
                $('#probeList .probe-card').removeClass('active');
                $('.ipFilter').dropdown('clear');
                refreshMonitorList('${projectId}');
            } else if (monitorSelectedProbeIp) {
                $('.ipFilter').dropdown('set selected', monitorSelectedProbeIp);
                refreshMonitorList('${projectId}');
            }
        });
        $('#probeSearchInput').on('input', applyProbeSearchFilter);
    });
</script>

<script>
    $(function () {
        // 刷新监控列表
        refreshMonitorList('${projectId}');
    });
</script>

</body>
</html>

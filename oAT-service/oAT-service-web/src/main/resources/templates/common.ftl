<!-- 基础资源引入 -->
<#include "commonFunction.ftl">
<link href="/css/semantic.min.css" rel="stylesheet">
<script src="/js/jquery.min.js"></script>
<script src="/js/semantic.min.js"></script>
<script src="/js/jquery.form.min.js"></script>
<script src="/js/common.js?version=1"></script>
<script src="/js/palette-colors.js?version=1"></script>
<link href="/css/common.css?version=1" rel="stylesheet">
<link href="/css/theme.css?version=1" rel="stylesheet">
<link href="/css/list-controls.css?version=1" rel="stylesheet">
<script src="/js/list-controls.js?version=1"></script>
<style>
    :root {
        --ai-common-text: var(--oat-text);
        --ai-common-text-secondary: var(--oat-text-secondary);
        --ai-common-text-muted: var(--oat-text-muted);
        --ai-common-surface-soft: var(--oat-bg-soft);
        --ai-common-border: var(--oat-border);
        --ai-common-accent: var(--oat-accent);
        --ai-common-warning: var(--oat-warning);
        --ai-common-accent-rgb: var(--oat-accent-rgb, 33, 133, 208);
        --ai-common-warning-rgb: var(--oat-warning-rgb, 242, 192, 55);
        --ai-common-accent-soft: rgba(var(--ai-common-accent-rgb), .08);
        --ai-common-accent-border: rgba(var(--ai-common-accent-rgb), .20);
        --ai-common-warning-soft: rgba(var(--ai-common-warning-rgb), .12);
        --ai-common-warning-border: rgba(var(--ai-common-warning-rgb), .26);
    }

    .compare-empty-state {
        min-height: 180px;
    }

    .compare-record-heading {
        margin-top: 18px !important;
        margin-bottom: 0 !important;
    }

    .compare-record-segment {
        border-top: none !important;
    }

    .compare-record-stats {
        margin-top: 6px;
        font-size: 0.85em;
        color: var(--ai-common-text-secondary);
    }

    .compare-record-meta {
        font-size: 0.9em;
        color: var(--ai-common-text-secondary);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 12px;
        flex-wrap: wrap;
        white-space: normal;
        max-width: 100%;
        line-height: 1.6;
        vertical-align: middle;
    }

    .compare-record-meta > span {
        display: inline-flex;
        align-items: center;
    }

    .compare-record-meta-label {
        color: var(--ai-common-text-muted);
    }

    .compare-record-meta-label-old {
        color: var(--ai-common-warning);
    }

    .compare-record-meta-label-new {
        color: var(--ai-common-accent);
    }

    .compare-record-meta .meta-chip {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 999px;
        border: 1px solid var(--ai-common-border);
        background: var(--ai-common-surface-soft);
        color: var(--ai-common-text-secondary);
        line-height: 1.4;
    }

    .compare-record-meta .commit-id {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 999px;
        border: 1px solid var(--ai-common-border);
        color: var(--ai-common-text);
        font-size: 12px;
        line-height: 1.4;
    }

    .compare-record-meta .commit-id-old {
        background: var(--ai-common-warning-soft);
        border-color: var(--ai-common-warning-border);
        color: var(--ai-common-warning);
    }

    .compare-record-meta .commit-id-new {
        background: var(--ai-common-accent-soft);
        border-color: var(--ai-common-accent-border);
        color: var(--ai-common-accent);
    }

    .compare-record-action-group {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
        flex-wrap: wrap;
    }

    .compare-record-action-group .ui.button {
        margin: 0 !important;
        padding-left: 10px;
        padding-right: 10px;
    }
</style>
<script>
    (function (window, document) {
        var palette = window.OatPalette || {};
        var root = document.documentElement;
        if (!root || !palette.primary) {
            return;
        }

        function hexToRgb(hex) {
            var normalized = String(hex || '').replace('#', '');
            if (normalized.length === 3) {
                normalized = normalized.split('').map(function (part) { return part + part; }).join('');
            }
            if (normalized.length !== 6) {
                return '';
            }
            return [
                parseInt(normalized.slice(0, 2), 16),
                parseInt(normalized.slice(2, 4), 16),
                parseInt(normalized.slice(4, 6), 16)
            ].join(', ');
        }

        function setThemeColor(name, color) {
            var rgb = hexToRgb(color);
            if (!color || !rgb) {
                return;
            }
            root.style.setProperty('--ai-common-' + name, color);
            root.style.setProperty('--ai-common-' + name + '-rgb', rgb);
            root.style.setProperty('--oat-' + name, color);
            root.style.setProperty('--oat-' + name + '-rgb', rgb);
        }

        setThemeColor('primary', palette.primary[0]);
        setThemeColor('accent', palette.primary[4] || palette.accent[0]);
        setThemeColor('success', palette.primary[9]);
        setThemeColor('warning', palette.primary[10]);
        setThemeColor('danger', palette.primary[3]);
    })(window, document);
</script>
<#if aiLlmEnabled!true>
<link href="/css/ai-floating-widget.css?v=${.now}" rel="stylesheet">
<!-- AI 统一公共模块（两个模式共用） -->
<script src="/js/ai-common.js?v=${.now}"></script>
<!-- 浮动小窗逻辑（仅在非 Interactive 页面激活） -->
<script src="/js/ai-floating-widget.js?v=${.now}"></script>
</#if>

<#if project?? && project.id??>
	<script>
	    (function () {
	        var projectId = '${project.id}';
	        if (!projectId || !window.EventSource || window.__probeAlertSseStarted) {
	            return;
	        }
	        window.__probeAlertSseStarted = true;
	        var storageKey = 'oat.probeAlert.lastSeen.' + projectId;
	        var endpoint = '/p/' + projectId + '/app/probe-alerts/stream';
	        var toastDuration = 12000;
	        var source = null;
	        var reconnectTimer = null;
	        var reconnectDelay = 30000;
	        var maxReconnectDelay = 300000;
	        var closedByPage = false;

        function eventTimestamp(event) {
            return event && event.eventTimeText ? event.eventTimeText : '';
        }

        function eventIdentity(event) {
            return [event.id || '', eventTimestamp(event), event.eventType || '', event.probeText || ''].join('|');
        }

        function buildToastMessage(event) {
            var appName = event.appName || '探针';
            var typeText = event.eventTypeLabel || event.eventType || '状态变化';
            var probe = event.probeText || '-';
            var time = event.eventTimeText || '-';
            return appName + ' ' + typeText + '<br><span class="probe-alert-global-toast-meta">' + probe + '<br>' + time + '</span>';
        }

        function toastType(event) {
            return event.eventType === 'OFFLINE' ? 'warning' : 'success';
        }

	        function scheduleReconnect() {
	            if (closedByPage || reconnectTimer) {
	                return;
	            }
	            reconnectTimer = setTimeout(function () {
	                reconnectTimer = null;
	                connect();
	            }, reconnectDelay);
	            reconnectDelay = Math.min(reconnectDelay * 2, maxReconnectDelay);
	        }

	        function connect() {
	            if (closedByPage) {
	                return;
	            }
	            if (source) {
	                source.close();
	            }
	            source = new EventSource(endpoint);
	            source.addEventListener('connected', function () {
	                reconnectDelay = 30000;
	            });
	            source.addEventListener('probe-alert', function (message) {
	                var event;
	                try {
	                    event = JSON.parse(message.data || '{}');
	                } catch (e) {
	                    if (window.console) {
	                        console.debug && console.debug('probe alert sse data parse failed:', message.data, e);
	                    }
	                    return;
	                }
	                var identity = eventIdentity(event);
	                if (!identity || identity === localStorage.getItem(storageKey)) {
	                    return;
	                }
	                localStorage.setItem(storageKey, identity);
	                notifyToast(buildToastMessage(event), toastType(event), toastDuration);
	            });
	            source.onerror = function () {
	                source.close();
	                source = null;
	                scheduleReconnect();
	            };
	        }

	        connect();

	        $(window).on('beforeunload', function () {
	            closedByPage = true;
	            if (reconnectTimer) {
	                clearTimeout(reconnectTimer);
	            }
	            if (source) {
	                source.close();
	            }
	        });
	    })();
	</script>
</#if>

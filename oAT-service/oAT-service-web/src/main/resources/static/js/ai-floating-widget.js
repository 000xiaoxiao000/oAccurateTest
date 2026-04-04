(function ($) {
    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatMessage(value) {
        return escapeHtml(value).replace(/\n/g, '<br>');
    }

    $(function () {
        var $root = $('#aiFloatingWidget');
        if ($root.length === 0) {
            return;
        }

        var projectId = $root.data('project-id');
        var projectName = $root.data('project-name') || '当前项目';
        var askUrl = $root.data('ask-url');
        var mascotPrimary = $root.data('mascot-primary') || '#00b5ad';
        var storagePrefix = 'ai-floating-widget:' + projectId;
        var historyKey = storagePrefix + ':history';
        var hiddenKey = storagePrefix + ':hidden';
        var panelKey = storagePrefix + ':panel';
        var lastPageKey = storagePrefix + ':last-page';
        var positionKey = storagePrefix + ':position';
        var restoreBtnKey = storagePrefix + ':restore-btn-pos';

        var $launcher = $('#aiFloatingLauncher');
        var $panel = $('#aiFloatingPanel');
        var $restore = $('#aiFloatingRestore');
        var $messageList = $('#aiFloatingMessageList');
        var $contextStatus = $('#aiFloatingContextStatus');
        var $quickLinks = $('#aiFloatingQuickLinkList');
        var $starterList = $('#aiFloatingStarterList');
        var $questionInput = $('#aiFloatingQuestionInput');
        var $sendButton = $('#aiFloatingSendButton');
        var $state = $('#aiFloatingState');
        var $panelEyebrow = $('#aiFloatingPanelEyebrow');
        var $panelTitle = $('#aiFloatingPanelTitle');
        var mouseX = window.innerWidth / 2;
        var mouseY = window.innerHeight / 2;
        var dragState = null;
        var currentContext = detectPageContext(window.location.pathname);
        var $hoveredRow = null;
        var $selectedRow = null;
        var liveSignals = {
            filters: [],
            tableSelection: '',
            tableHover: ''
        };

        function readJSON(key, fallback) {
            try {
                return JSON.parse(sessionStorage.getItem(key) || JSON.stringify(fallback));
            } catch (e) {
                return fallback;
            }
        }

        function writeJSON(key, value) {
            sessionStorage.setItem(key, JSON.stringify(value));
        }

        function readLocalJSON(key, fallback) {
            try {
                return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
            } catch (e) {
                return fallback;
            }
        }

        function writeLocalJSON(key, value) {
            localStorage.setItem(key, JSON.stringify(value));
        }

        function setHidden(hidden) {
            $root.toggleClass('is-mascot-hidden', hidden);
            sessionStorage.setItem(hiddenKey, hidden ? '1' : '0');
            if (hidden) {
                initRestoreBtnPosition();
            }
        }

        function setPanelOpen(open) {
            var wasOpen = $root.hasClass('is-panel-open');
            // 关闭面板前，先保存对话框的右下角位置
            var savedDialogRect = null;
            if (wasOpen && !open) {
                savedDialogRect = $root[0].getBoundingClientRect();
            }
            $root.toggleClass('is-panel-open', open);
            sessionStorage.setItem(panelKey, open ? '1' : '0');
            window.requestAnimationFrame(function () {
                if (savedDialogRect) {
                    // 关闭面板时，使用保存的对话框位置将 launcher 锚定在右下角
                    var launcherWidth = 96;
                    var launcherHeight = 112;
                    var targetLeft = savedDialogRect.right - launcherWidth;
                    var targetTop = savedDialogRect.bottom - launcherHeight;
                    applyPosition(clampPosition({ left: targetLeft, top: targetTop }));
                } else {
                    applyPosition(getCurrentPosition());
                }
                writeLocalJSON(positionKey, getCurrentPosition());
            });
        }

        function anchorLauncherPosition() {
            var rect = $launcher[0].getBoundingClientRect();
            // 目标:让 launcher 关闭后面板消失后,launcher 仍停留在当前屏幕坐标
            // widget position:fixed, left/top 控制其左上角
            // 关闭后 widget 宽度 ≈ launcher 宽度(96px),所以 left = launcherRight - widgetWidth
            var targetLeft = rect.right - $root.outerWidth();
            var targetTop = rect.top;
            applyPosition(clampPosition({ left: targetLeft, top: targetTop }));
            writeLocalJSON(positionKey, getCurrentPosition());
        }
        
        function anchorLauncherToBottomRight() {
            // 获取当前对话框的尺寸和位置
            var rootRect = $root[0].getBoundingClientRect();
                    
            // 关闭面板后,widget 只包含 launcher(约96px宽)
            // 我们需要让 launcher 定位在原来对话框的右下角位置
            // 对话框右下角的屏幕坐标
            var dialogRight = rootRect.right;
            var dialogBottom = rootRect.bottom;
                    
            // 计算新的 left/top,使得 launcher 的右下角对齐到对话框的右下角
            var launcherWidth = 96; // launcher 的宽度
            var launcherHeight = 112; // launcher 的高度
            var targetLeft = dialogRight - launcherWidth;
            var targetTop = dialogBottom - launcherHeight;
                    
            applyPosition(clampPosition({ left: targetLeft, top: targetTop }));
            // 不在这里保存位置，让 setPanelOpen 中的 requestAnimationFrame 统一保存
        }

        function scrollToBottom() {
            $messageList.scrollTop($messageList[0].scrollHeight);
        }

        function scrollToTop() {
            $messageList.scrollTop(0);
        }

        function setState(text, disabled) {
            $state.text(text);
            $sendButton.prop('disabled', disabled);
            $questionInput.prop('disabled', disabled);
        }

        function detectPageContext(pathname) {
            var projectBase = '/p/' + projectId;
            if (/\/monitor/.test(pathname)) {
                return {
                    eyebrow: 'Monitor View',
                    title: '监控页助手',
                    placeholder: '例如：当前监控页应该先看哪些请求',
                    welcome: '你现在在监控台，我可以帮你判断先看实时请求、应用过滤还是链路节点。',
                    starters: [
                        '当前监控页应该先看哪些请求',
                        '如果线上有异常，排查顺序是什么',
                        '帮我解释监控台里的关键入口'
                    ],
                    quickLinks: [
                        {title: '实时监控', description: '优先查看最近几分钟请求链路', url: projectBase + '/monitor', icon: 'chart line', priority: 96},
                        {title: '我的快照', description: '把关键现场沉淀下来', url: projectBase + '/snapshot/my', icon: 'camera retro', priority: 72},
                        {title: '在线应用', description: '锁定当前在线服务范围', url: projectBase + '/app/online', icon: 'server', priority: 64}
                    ]
                };
            }
            if (/\/snapshot/.test(pathname)) {
                return {
                    eyebrow: 'Snapshot View',
                    title: '快照页助手',
                    placeholder: '例如：这个快照页我应该重点看什么',
                    welcome: '你现在在快照相关页面，我可以帮你判断应该先看标签、链路细节还是复盘入口。',
                    starters: [
                        '这个快照页我应该重点看什么',
                        '快照适合解决什么问题',
                        '如何从监控进入快照'
                    ],
                    quickLinks: [
                        {title: '快照列表', description: '浏览项目沉淀的全部快照', url: projectBase + '/snapshot/list', icon: 'camera retro', priority: 94},
                        {title: '我的快照', description: '查看个人沉淀的问题现场', url: projectBase + '/snapshot/my', icon: 'bookmark', priority: 76},
                        {title: '监控台', description: '从实时请求继续生成快照', url: projectBase + '/monitor', icon: 'chart line', priority: 70}
                    ]
                };
            }
            if (/\/search/.test(pathname)) {
                return {
                    eyebrow: 'Search View',
                    title: '搜索页助手',
                    placeholder: '例如：这个搜索页适合先搜什么',
                    welcome: '你现在在搜索页，我可以帮你判断该搜关键词、链路线索，还是直接跳到地图和监控页。',
                    starters: [
                        '这个搜索页适合先搜什么',
                        '搜索和监控怎么配合',
                        '我现在应该先看哪些数据'
                    ],
                    quickLinks: [
                        {title: '搜索入口', description: '按关键词追踪数据线索', url: projectBase + '/search', icon: 'search', priority: 95},
                        {title: '项目地图', description: '从结构视角缩小排查范围', url: projectBase + '/map/home', icon: 'map signs', priority: 78},
                        {title: '监控台', description: '回到实时请求继续排查', url: projectBase + '/monitor', icon: 'chart line', priority: 72}
                    ]
                };
            }
            if (/\/map/.test(pathname)) {
                return {
                    eyebrow: 'Map View',
                    title: '地图页助手',
                    placeholder: '例如：这张地图应该从哪里开始看',
                    welcome: '你现在在地图页，我可以帮你从应用结构、调用关系和热点视角切入。',
                    starters: [
                        '这张地图应该从哪里开始看',
                        '地图和监控怎么配合',
                        '帮我解释地图上的关键节点'
                    ],
                    quickLinks: [
                        {title: '项目地图', description: '回到项目级全景视角', url: projectBase + '/map/home', icon: 'map signs', priority: 94},
                        {title: '应用列表', description: '先确认接入应用与配置', url: projectBase + '/app/list', icon: 'grid layout', priority: 70},
                        {title: '搜索入口', description: '结合关键词进一步定位', url: projectBase + '/search', icon: 'search', priority: 74}
                    ]
                };
            }
            if (/\/app\//.test(pathname)) {
                return {
                    eyebrow: 'Application View',
                    title: '应用页助手',
                    placeholder: '例如：这个应用页应该先看哪些信息',
                    welcome: '你现在在应用相关页面，我可以帮你判断当前更适合看接入状态、在线实例还是配置入口。',
                    starters: [
                        '这个应用页应该先看哪些信息',
                        '帮我看看当前应用接入情况',
                        '哪些应用值得先关注'
                    ],
                    quickLinks: [
                        {title: '应用列表', description: '查看项目接入应用与状态', url: projectBase + '/app/list', icon: 'grid layout', priority: 92},
                        {title: '在线应用', description: '查看当前活跃实例', url: projectBase + '/app/online', icon: 'server', priority: 84},
                        {title: '项目主页', description: '回到整体项目上下文', url: projectBase + '/home', icon: 'home', priority: 58}
                    ]
                };
            }
            if (/\/version/.test(pathname)) {
                return {
                    eyebrow: 'Version View',
                    title: '版本页助手',
                    placeholder: '例如：版本比对先看哪些差异',
                    welcome: '你现在在版本相关页面，我可以帮你判断该先看版本差异、覆盖率还是代码报告。',
                    starters: [
                        '版本比对先看哪些差异',
                        '帮我解释这个版本页面的入口',
                        '如果有回归风险应该先查什么'
                    ],
                    quickLinks: [
                        {title: '版本列表', description: '浏览应用版本与比对入口', url: projectBase + '/version/apps', icon: 'code branch', priority: 94},
                        {title: '覆盖率报告', description: '结合覆盖率判断变更风险', url: projectBase + '/app/list', icon: 'chart bar', priority: 76},
                        {title: '快照列表', description: '回看历史问题现场', url: projectBase + '/snapshot/list', icon: 'camera retro', priority: 66}
                    ]
                };
            }
            if (/\/edit|\/member|\/label/.test(pathname)) {
                return {
                    eyebrow: 'Settings View',
                    title: '设置页助手',
                    placeholder: '例如：这个设置页应该先改什么',
                    welcome: '你现在在设置页，我可以帮你梳理项目成员、标签、应用和基础配置入口。',
                    starters: [
                        '这个设置页应该先改什么',
                        '成员和权限该怎么调整',
                        '哪些配置会影响后续数据分析'
                    ],
                    quickLinks: [
                        {title: '项目设置', description: '继续查看项目基础配置', url: projectBase + '/edit', icon: 'setting', priority: 92},
                        {title: '成员管理', description: '查看项目成员与权限', url: projectBase + '/member/list', icon: 'users', priority: 76},
                        {title: '标签管理', description: '整理快照和用例标签', url: projectBase + '/label', icon: 'tags', priority: 70}
                    ]
                };
            }
            return {
                eyebrow: 'Project View',
                title: '项目页助手',
                placeholder: '例如：当前页面我应该先看什么数据',
                welcome: '我会跟随在项目 ' + projectName + ' 的各个页面里，你可以随时问我当前页面该看什么、下一步该去哪。',
                starters: [
                    '当前页面我应该先看什么数据',
                    '帮我总结一下当前项目概况',
                    '我现在应该先看哪些数据'
                ],
                quickLinks: [
                    {title: '项目主页', description: '回到项目整体概况', url: projectBase + '/home', icon: 'home', priority: 82},
                    {title: '监控台', description: '查看实时请求与调用链', url: projectBase + '/monitor', icon: 'chart line', priority: 88},
                    {title: 'AI 工作台', description: '进入完整 AI Interactive 页面', url: projectBase + '/AIInteractive', icon: 'magic', priority: 90}
                ]
            };
        }

        function collectDomSignals() {
            var signals = [];
            $('h1, h2, h3, .ui.header, .ui.block.header, .ui.top.attached.header, label, .text.menu .item .text').each(function () {
                var text = $.trim($(this).text().replace(/\s+/g, ' '));
                if (!text || text.length < 2) {
                    return;
                }
                if (signals.indexOf(text) !== -1) {
                    return;
                }
                signals.push(text);
                if (signals.length >= 8) {
                    return false;
                }
            });
            return signals;
        }

        function collectTexts(selector, limit) {
            var results = [];
            $(selector).each(function () {
                var rawText;
                if ($(this).is('input, textarea')) {
                    rawText = $(this).attr('placeholder') || '';
                } else {
                    rawText = $(this).text();
                }
                var text = $.trim(String(rawText || '').replace(/\s+/g, ' '));
                if (!text || text.length < 2 || text.length > 32) {
                    return;
                }
                if (results.indexOf(text) !== -1) {
                    return;
                }
                results.push(text);
                if (results.length >= limit) {
                    return false;
                }
            });
            return results;
        }

        function buildPageContextPayload() {
            var signals = collectDomSignals();
            var tableHeaders = collectTexts('th, .ui.table thead th', 6);
            var filterLabels = collectTexts('input[placeholder], textarea[placeholder], .ui.dropdown .text, .search.icon.input input[placeholder]', 6);
            var actionTexts = collectTexts('button, .ui.button, a.item, .menu .item', 8);
            var parts = [];
            if (currentContext && currentContext.title) {
                parts.push('页面类型:' + currentContext.title);
            }
            if (signals.length) {
                parts.push('页面关键词:' + signals.join('、'));
            }
            if (tableHeaders.length) {
                parts.push('表格字段:' + tableHeaders.join('、'));
            }
            if (filterLabels.length) {
                parts.push('筛选线索:' + filterLabels.join('、'));
            }
            if (actionTexts.length) {
                parts.push('可操作项:' + actionTexts.join('、'));
            }
            if (liveSignals.filters.length) {
                parts.push('当前筛选:' + liveSignals.filters.join('、'));
            }
            if (liveSignals.tableSelection) {
                parts.push('当前选中行:' + liveSignals.tableSelection);
            }
            if (liveSignals.tableHover) {
                parts.push('当前悬停行:' + liveSignals.tableHover);
            }
            return parts.join('；');
        }

        function renderContextStatus() {
            var html = '';
            if (liveSignals.filters.length) {
                html += '<div class="ai-floating-context-chip">当前筛选：' + escapeHtml(liveSignals.filters.join('、')) + '</div>';
            }
            if (liveSignals.tableHover) {
                html += '<div class="ai-floating-context-chip">当前悬停：' + escapeHtml(liveSignals.tableHover) + '</div>';
            }
            if (liveSignals.tableSelection) {
                html += '<div class="ai-floating-context-chip">当前选中：' + escapeHtml(liveSignals.tableSelection) + '</div>';
            }
            $contextStatus.html(html);
            renderStarters(buildAdaptiveStarters(currentContext));
        }

        function collectLiveFilterState() {
            var filters = [];
            $('input[type="text"], input[type="search"], input:not([type]), select, textarea').each(function () {
                var $el = $(this);
                var value = $.trim($el.val());
                var placeholder = $.trim($el.attr('placeholder') || $el.attr('name') || '');
                if (!value || value.length > 24) {
                    return;
                }
                var item = placeholder ? (placeholder + '=' + value) : value;
                if (filters.indexOf(item) === -1) {
                    filters.push(item);
                }
                if (filters.length >= 4) {
                    return false;
                }
            });
            liveSignals.filters = filters;
            renderContextStatus();
        }

        function summarizeRow($row) {
            var cells = [];
            $row.find('td').each(function () {
                var text = $.trim($(this).text().replace(/\s+/g, ' '));
                if (text && cells.indexOf(text) === -1) {
                    cells.push(text);
                }
                if (cells.length >= 4) {
                    return false;
                }
            });
            return cells.join(' | ');
        }

        function bindLivePageSignals() {
            $(document).on('input.aiFloatingSignals change.aiFloatingSignals', 'input, select, textarea', function () {
                collectLiveFilterState();
            });

            $(document).on('mouseenter.aiFloatingSignals', '.ui.table tbody tr, table tbody tr', function () {
                if ($hoveredRow && $hoveredRow.length && (!$selectedRow || !$hoveredRow.is($selectedRow))) {
                    $hoveredRow.removeClass('ai-floating-row-hover');
                }
                $hoveredRow = $(this);
                if (!$selectedRow || !$hoveredRow.is($selectedRow)) {
                    $hoveredRow.addClass('ai-floating-row-hover');
                }
                liveSignals.tableHover = summarizeRow($hoveredRow);
                renderContextStatus();
            });

            $(document).on('mouseleave.aiFloatingSignals', '.ui.table tbody tr, table tbody tr', function () {
                var $row = $(this);
                if (!$selectedRow || !$row.is($selectedRow)) {
                    $row.removeClass('ai-floating-row-hover');
                }
                if ($hoveredRow && $hoveredRow.is($row)) {
                    liveSignals.tableHover = '';
                    renderContextStatus();
                }
            });

            $(document).on('click.aiFloatingSignals', '.ui.table tbody tr, table tbody tr', function () {
                if ($selectedRow && $selectedRow.length) {
                    $selectedRow.removeClass('ai-floating-row-selected ai-floating-row-hover');
                }
                $selectedRow = $(this);
                $selectedRow.addClass('ai-floating-row-selected');
                liveSignals.tableSelection = summarizeRow($selectedRow);
                renderContextStatus();
            });

            collectLiveFilterState();
        }

        function renderStarters(starters) {
            if (!starters || !starters.length) {
                $starterList.html('');
                $('#aiFloatingStarterSection').hide();
                return;
            }
            var html = '';
            $.each(starters || [], function (_, starter) {
                html += '<button class="ai-floating-starter" type="button" data-question="' + escapeHtml(starter) + '">' + escapeHtml(starter) + '</button>';
            });
            $starterList.html(html);
            $('#aiFloatingStarterSection').show();
        }

        function buildLinkMeta(link) {
            var url = link && link.url ? link.url : '';
            var title = link && link.title ? link.title : '';
            var icon = link && link.icon ? link.icon : 'external';
            var priority = link && typeof link.priority === 'number' ? link.priority : 40;

            if (/\/monitor/.test(url) || /监控/.test(title)) {
                icon = 'chart line';
                priority = Math.max(priority, 85);
            } else if (/\/search/.test(url) || /搜索|排查/.test(title)) {
                icon = 'search';
                priority = Math.max(priority, 78);
            } else if (/\/snapshot/.test(url) || /快照/.test(title)) {
                icon = 'camera retro';
                priority = Math.max(priority, 68);
            } else if (/\/map\//.test(url) || /地图/.test(title)) {
                icon = 'map signs';
                priority = Math.max(priority, 62);
            } else if (/\/app\//.test(url) || /应用/.test(title)) {
                icon = 'grid layout';
                priority = Math.max(priority, 52);
            } else if (/\/home/.test(url) || /首页/.test(title)) {
                icon = 'home';
                priority = Math.max(priority, 45);
            }

            return {
                icon: icon,
                priority: priority
            };
        }

        function normalizeQuickLinks(links) {
            var unique = [];
            $.each(links || [], function (_, link) {
                if (!link) {
                    return;
                }
                var meta = buildLinkMeta(link);
                var normalized = $.extend({}, link, meta);
                var key = (normalized.title || '') + '|' + (normalized.url || '');
                var matched = false;
                $.each(unique, function (index, item) {
                    if (((item.title || '') + '|' + (item.url || '')) === key) {
                        if ((normalized.priority || 0) > (item.priority || 0)) {
                            unique[index] = normalized;
                        }
                        matched = true;
                        return false;
                    }
                });
                if (!matched) {
                    unique.push(normalized);
                }
            });
            unique.sort(function (a, b) {
                return (b.priority || 0) - (a.priority || 0);
            });
            return unique.slice(0, 4);
        }

        function buildAdaptiveQuickLinks(context) {
            var links = (context.quickLinks || []).slice(0);
            if (liveSignals.tableSelection) {
                links.unshift({
                    title: '围绕选中数据继续搜索',
                    description: '带着当前选中行到搜索入口继续定位：' + liveSignals.tableSelection,
                    url: '/p/' + projectId + '/search',
                    priority: 98
                });
                links.unshift({
                    title: '围绕选中数据进入监控台',
                    description: '结合当前选中行到监控台查看实时请求',
                    url: '/p/' + projectId + '/monitor',
                    priority: 100
                });
            } else if (liveSignals.tableHover) {
                links.unshift({
                    title: '围绕悬停数据继续搜索',
                    description: '以当前悬停行为线索继续排查：' + liveSignals.tableHover,
                    url: '/p/' + projectId + '/search',
                    priority: 92
                });
                links.unshift({
                    title: '围绕悬停数据查看快照',
                    description: '把当前悬停数据映射到历史快照入口',
                    url: '/p/' + projectId + '/snapshot/list',
                    priority: 88
                });
            }
            if (liveSignals.filters.length) {
                links.push({
                    title: '保留当前筛选去项目地图',
                    description: '先带着当前筛选条件切到地图页继续缩小范围',
                    url: '/p/' + projectId + '/map/home',
                    priority: 80
                });
            }
            return normalizeQuickLinks(links);
        }

        function buildAdaptiveStarters(context) {
            var starters = (context.starters || []).slice(0, 3);
            if (liveSignals.tableSelection) {
                starters.unshift('这条选中数据我该重点看什么');
                starters.unshift('帮我分析这条数据：' + liveSignals.tableSelection);
            } else if (liveSignals.tableHover) {
                starters.unshift('这条悬停数据下一步应该怎么排查');
                starters.unshift('帮我看看这条悬停数据代表什么');
            }
            if (liveSignals.filters.length) {
                starters.push('结合当前筛选条件，建议我下一步操作');
            }

            var unique = [];
            $.each(starters, function (_, item) {
                if (unique.indexOf(item) === -1) {
                    unique.push(item);
                }
            });
            return unique.slice(0, 5);
        }

        function syncContextUI(context) {
            $panelEyebrow.text(context.eyebrow || 'AI Interactive');
            $panelTitle.text(context.title || '项目悬浮助手');
            $questionInput.attr('placeholder', context.placeholder || '随时提问');
            renderStarters(buildAdaptiveStarters(context));
            renderQuickLinks(buildAdaptiveQuickLinks(context));
        }

        function buildContextWelcome(context) {
            var welcome = context.welcome || '我会跟随你处理当前项目中的数据问题。';
            var signals = collectDomSignals();
            var headers = collectTexts('th, .ui.table thead th', 4);
            if (signals.length) {
                welcome += '\n我还看到了当前页面的关键词：' + signals.slice(0, 4).join('、') + '。';
            }
            if (headers.length) {
                welcome += '\n当前页里比较明显的表格字段有：' + headers.join('、') + '。';
            }
            return welcome;
        }

        function clampPosition(position) {
            var widgetWidth = $root.outerWidth();
            var widgetHeight = $root.outerHeight();
            var minLeft = 8;
            var minTop = 8;
            var maxLeft = Math.max(minLeft, window.innerWidth - widgetWidth - 8);
            var maxTop = Math.max(minTop, window.innerHeight - widgetHeight - 8);
            return {
                left: Math.min(Math.max(position.left, minLeft), maxLeft),
                top: Math.min(Math.max(position.top, minTop), maxTop)
            };
        }

        function defaultPosition() {
            return clampPosition({
                left: window.innerWidth - $root.outerWidth() - 22,
                top: window.innerHeight - $root.outerHeight() - 24
            });
        }

        function snapToBottomRight() {
            var position = defaultPosition();
            applyPosition(position);
            writeLocalJSON(positionKey, position);
        }

        function getCurrentPosition() {
            var rect = $root[0].getBoundingClientRect();
            return {
                left: rect.left,
                top: rect.top
            };
        }

        function applyPosition(position) {
            var safePosition = clampPosition(position);
            $root.css({
                left: safePosition.left + 'px',
                top: safePosition.top + 'px',
                right: 'auto',
                bottom: 'auto'
            });
        }

        function restorePosition() {
            localStorage.removeItem(positionKey);
            snapToBottomRight();
        }

        function initPosition() {
            var saved = readLocalJSON(positionKey, null);
            if (saved) {
                applyPosition(saved);
            } else {
                snapToBottomRight();
            }
        }

        function defaultRestoreBtnPosition() {
            return {
                right: 22,
                bottom: 24
            };
        }

        function applyRestoreBtnPosition(position) {
            $restore.css({
                right: position.right + 'px',
                bottom: position.bottom + 'px',
                left: 'auto',
                top: 'auto'
            });
        }

        function initRestoreBtnPosition() {
            var saved = readLocalJSON(restoreBtnKey, null);
            if (saved) {
                applyRestoreBtnPosition(saved);
            } else {
                applyRestoreBtnPosition(defaultRestoreBtnPosition());
            }
        }

        var restoreDragState = null;

        function beginRestoreDrag(event) {
            if (event.which && event.which !== 1) return;
            var rect = $restore[0].getBoundingClientRect();
            restoreDragState = {
                startX: event.clientX,
                startY: event.clientY,
                originRight: window.innerWidth - rect.right,
                originBottom: window.innerHeight - rect.bottom,
                dragging: false
            };
            $(document).on('mousemove.aiFloatingRestoreDrag', onRestoreDragMove);
            $(document).on('mouseup.aiFloatingRestoreDrag', onRestoreDragEnd);
            event.preventDefault();
        }

        function onRestoreDragMove(event) {
            if (!restoreDragState) return;
            var deltaX = event.clientX - restoreDragState.startX;
            var deltaY = event.clientY - restoreDragState.startY;
            if (!restoreDragState.dragging && (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4)) {
                restoreDragState.dragging = true;
                $restore.addClass('is-dragging');
            }
            if (!restoreDragState.dragging) return;
            applyRestoreBtnPosition({
                right: restoreDragState.originRight - deltaX,
                bottom: restoreDragState.originBottom - deltaY
            });
        }

        function onRestoreDragEnd() {
            if (!restoreDragState) return;
            $(document).off('.aiFloatingRestoreDrag');
            $restore.removeClass('is-dragging');
            if (restoreDragState.dragging) {
                var rect = $restore[0].getBoundingClientRect();
                writeLocalJSON(restoreBtnKey, {
                    right: Math.max(8, window.innerWidth - rect.right),
                    bottom: Math.max(8, window.innerHeight - rect.bottom)
                });
            }
            restoreDragState = null;
        }

        function appendMessage(role, title, message, actions, options) {
            options = options || {};
            var avatar = role === 'assistant' ? 'AI' : '我';
            var html = ''
                + '<div class="ai-floating-message ' + role + '">'
                + '  <div class="ai-floating-avatar">' + escapeHtml(avatar) + '</div>'
                + '  <div class="ai-floating-message-body">'
                + '      <div class="ai-floating-message-name">' + escapeHtml(title) + '</div>'
                + '      <div class="ai-floating-message-card">' + formatMessage(message);

            if (actions && actions.length) {
                html += '<div class="ai-floating-message-actions">';
                $.each(actions, function (_, action) {
                    html += '<button class="ai-floating-message-action" type="button" data-question="' + escapeHtml(action) + '">' + escapeHtml(action) + '</button>';
                });
                html += '</div>';
            }

            html += '      </div>'
                + '  </div>'
                + '</div>';

            $messageList.append(html);
            $('#aiFloatingMessageSection').removeClass('is-collapsed');
            if (options.scrollTop) {
                scrollToTop();
                return;
            }
            if (options.scroll === 'none') {
                return;
            }
            scrollToBottom();
        }

        function renderQuickLinks(links) {
            if (!links || !links.length) {
                $quickLinks.empty();
                $('#aiFloatingQuickLinkSection').hide();
                return;
            }

            var html = '';
            $.each(normalizeQuickLinks(links), function (_, link) {
                var priorityClass = link.priority >= 85 ? 'priority-high' : (link.priority >= 60 ? 'priority-medium' : 'priority-normal');
                html += '<a class="ai-floating-quick-link ' + priorityClass + '" href="' + escapeHtml(link.url || '#') + '">'
                    + '<div class="ai-floating-quick-link-head">'
                    + '<span class="ai-floating-quick-link-icon"><i class="' + escapeHtml(link.icon || 'external') + ' icon"></i></span>'
                    + '<div class="ai-floating-quick-link-title">' + escapeHtml(link.title || '推荐入口') + '</div>'
                    + '</div>'
                    + '<div class="ai-floating-quick-link-desc">' + escapeHtml(link.description || '') + '</div>'
                    + '</a>';
            });
            $quickLinks.html(html);
            $('#aiFloatingQuickLinkSection').show();
        }

        function saveHistory(entry) {
            var history = readJSON(historyKey, []);
            history.push(entry);
            writeJSON(historyKey, history);
        }

        function initHistory() {
            var history = readJSON(historyKey, []);
            syncContextUI(currentContext);
            if (history.length) {
                $.each(history, function (_, item) {
                    appendMessage(item.role, item.title, item.message, item.actions || [], {scroll: 'none'});
                    if (item.quickLinks && item.quickLinks.length) {
                        renderQuickLinks(item.quickLinks);
                    }
                });
                renderQuickLinks(currentContext.quickLinks || []);
                scrollToBottom();
            } else {
                appendMessage('assistant', 'AI 助手', buildContextWelcome(currentContext), currentContext.starters || [], {scrollTop: true});
                writeJSON(historyKey, [{
                    role: 'assistant',
                    title: 'AI 助手',
                    message: buildContextWelcome(currentContext),
                    actions: currentContext.starters || [],
                    quickLinks: currentContext.quickLinks || []
                }]);
            }

            if (sessionStorage.getItem(lastPageKey) !== window.location.pathname) {
                if (history.length) {
                    appendMessage('assistant', 'AI 助手', buildContextWelcome(currentContext), currentContext.starters || []);
                    saveHistory({
                        role: 'assistant',
                        title: 'AI 助手',
                        message: buildContextWelcome(currentContext),
                        actions: currentContext.starters || [],
                        quickLinks: currentContext.quickLinks || []
                    });
                }
                sessionStorage.setItem(lastPageKey, window.location.pathname);
            }
        }

        function applyMascotTheme() {
            var primary = mascotPrimary || '#00b5ad';
            var r = parseInt(primary.slice(1, 3), 16) || 0;
            var g = parseInt(primary.slice(3, 5), 16) || 181;
            var b = parseInt(primary.slice(5, 7), 16) || 173;
            // 计算一个稍暗的主色作为文字/按钮强调色
            var darkR = Math.max(0, Math.round(r * 0.55));
            var darkG = Math.max(0, Math.round(g * 0.55));
            var darkB = Math.max(0, Math.round(b * 0.55));

            // 设置 CSS 变量
            $root[0].style.setProperty('--mascot-primary', primary);
            $root[0].style.setProperty('--mascot-dark', 'rgb(' + darkR + ',' + darkG + ',' + darkB + ')');

            // 统一设置所有 UI 强调色，使悬浮小人与 AIInteractive 页面颜色一致
            var styleBlock = document.getElementById('aiFloatingThemeStyle');
            if (!styleBlock) {
                styleBlock = document.createElement('style');
                styleBlock.id = 'aiFloatingThemeStyle';
                document.head.appendChild(styleBlock);
            }
            styleBlock.textContent =
                '.ai-floating-launcher-text { background: rgba(' + r + ',' + g + ',' + b + ',0.12) !important; color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-link { color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-section-toggle:hover { color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-context-chip { background: rgba(' + r + ',' + g + ',' + b + ',0.06) !important; border-color: rgba(' + r + ',' + g + ',' + b + ',0.22) !important; color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-row-hover { outline-color: rgba(' + r + ',' + g + ',' + b + ',0.2) !important; background: rgba(' + r + ',' + g + ',' + b + ',0.06) !important; }' +
                '.ai-floating-row-selected { outline-color: rgba(' + r + ',' + g + ',' + b + ',0.5) !important; background: rgba(' + r + ',' + g + ',' + b + ',0.15) !important; }' +
                '.ai-floating-avatar { background: rgba(' + r + ',' + g + ',' + b + ',0.1) !important; }' +
                '.ai-floating-message-action { border-color: rgba(' + darkR + ',' + darkG + ',' + darkB + ',0.25) !important; color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-quick-link.priority-high { border-color: rgba(' + r + ',' + g + ',' + b + ',0.36) !important; background: rgba(' + r + ',' + g + ',' + b + ',0.06) !important; }' +
                '.ai-floating-quick-link-icon { background: rgba(' + r + ',' + g + ',' + b + ',0.12) !important; color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-starter { border-color: rgba(' + r + ',' + g + ',' + b + ',0.22) !important; background: rgba(' + r + ',' + g + ',' + b + ',0.06) !important; color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; }' +
                '.ai-floating-compose textarea:focus { border-color: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; box-shadow: 0 0 0 3px rgba(' + r + ',' + g + ',' + b + ',0.12) !important; }' +
                '.ai-floating-restore { background: rgb(' + darkR + ',' + darkG + ',' + darkB + ') !important; box-shadow: 0 12px 26px rgba(' + darkR + ',' + darkG + ',' + darkB + ',0.24) !important; }';
        }

        function initMascot() {
            var canvas = document.getElementById('aiFloatingMascotCanvas');
            if (!canvas) {
                return;
            }
            var ctx = canvas.getContext('2d');
            var width = canvas.width;
            var height = canvas.height;
            var mascot = {
                x: width / 2,
                y: height / 2 - 5,
                radius: 26,
                color: mascotPrimary
            };
            // Parse mascotPrimary to RGB for particle accents
            var mRgb = {
                r: parseInt(mascotPrimary.slice(1,3), 16) || 0,
                g: parseInt(mascotPrimary.slice(3,5), 16) || 181,
                b: parseInt(mascotPrimary.slice(5,7), 16) || 173
            };
            var particles = [];

            // Orbiting particles (scaled down from workbench mascot)
            for (var i = 0; i < 8; i++) {
                particles.push({
                    angle: (Math.PI * 2 / 8) * i,
                    radius: 34 + Math.random() * 10,
                    size: 1 + Math.random() * 1.5,
                    speed: 0.003 + Math.random() * 0.002
                });
            }

            $(window).on('mousemove.aiFloatingMascot', function (event) {
                mouseX = event.clientX;
                mouseY = event.clientY;
            });

            function draw() {
                ctx.clearRect(0, 0, width, height);

                var rect = canvas.getBoundingClientRect();
                var localMouseX = mouseX - (rect.left + width / 2);
                var localMouseY = mouseY - (rect.top + height / 2);
                var lookAngle = Math.atan2(localMouseY, localMouseX);

                ctx.save();
                ctx.translate(mascot.x, mascot.y);
                ctx.translate(0, Math.sin(Date.now() / 500) * 1.5);

                // Dashed orbital rings
                ctx.save();
                ctx.strokeStyle = 'rgba(148, 163, 184, 0.25)';
                ctx.setLineDash([4, 4]);
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.arc(0, 0, 34, 0, Math.PI * 2);
                ctx.stroke();
                ctx.beginPath();
                ctx.arc(0, 0, 42, 0, Math.PI * 2);
                ctx.stroke();
                ctx.restore();

                // Orbiting particles
                particles.forEach(function (particle, index) {
                    var angle = particle.angle + Date.now() * particle.speed * (index % 2 === 0 ? 1 : -1);
                    var pxOrbit = Math.cos(angle) * particle.radius;
                    var pyOrbit = Math.sin(angle) * (particle.radius * 0.45);
                    ctx.beginPath();
                    ctx.fillStyle = index % 2 === 0 ? 'rgba(255,255,255,0.9)' : 'rgba(' + mRgb.r + ',' + mRgb.g + ',' + mRgb.b + ',0.22)';
                    ctx.arc(pxOrbit, pyOrbit, particle.size, 0, Math.PI * 2);
                    ctx.fill();
                });

                // Shadow platform
                ctx.beginPath();
                ctx.ellipse(0, 30, 28, 6, 0, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(15, 23, 42, 0.07)';
                ctx.fill();

                // Body
                ctx.beginPath();
                ctx.ellipse(0, 0, mascot.radius, mascot.radius * 0.9, 0, 0, Math.PI * 2);
                ctx.fillStyle = mascot.color;
                ctx.fill();

                // Face highlight
                ctx.beginPath();
                ctx.arc(-mascot.radius * 0.48, -mascot.radius * 0.52, mascot.radius * 0.16, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255,255,255,0.2)';
                ctx.fill();

                // Eyes
                var eyeOffsetX = mascot.radius * 0.35;
                var eyeOffsetY = -mascot.radius * 0.2;
                var eyeSize = mascot.radius * 0.3;

                ctx.fillStyle = 'white';
                ctx.beginPath();
                ctx.arc(-eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2);
                ctx.fill();
                ctx.beginPath();
                ctx.arc(eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2);
                ctx.fill();

                // Pupils follow mouse
                var px = Math.cos(lookAngle) * eyeSize * 0.4;
                var py = Math.sin(lookAngle) * eyeSize * 0.4;
                ctx.fillStyle = 'black';
                ctx.beginPath();
                ctx.arc(-eyeOffsetX + px, eyeOffsetY + py, eyeSize * 0.5, 0, Math.PI * 2);
                ctx.fill();
                ctx.beginPath();
                ctx.arc(eyeOffsetX + px, eyeOffsetY + py, eyeSize * 0.5, 0, Math.PI * 2);
                ctx.fill();

                // Mouth
                ctx.strokeStyle = 'rgba(0,0,0,0.38)';
                ctx.lineWidth = 1.8;
                ctx.lineCap = 'round';
                ctx.beginPath();
                ctx.arc(0, 4, 7, 0.2, Math.PI - 0.2);
                ctx.stroke();

                // Legs
                ctx.beginPath();
                ctx.moveTo(-7, 24);
                ctx.lineTo(-3, 31);
                ctx.lineTo(-1, 24);
                ctx.moveTo(7, 24);
                ctx.lineTo(3, 31);
                ctx.lineTo(1, 24);
                ctx.strokeStyle = 'rgba(15,23,42,0.18)';
                ctx.lineWidth = 2.8;
                ctx.lineCap = 'round';
                ctx.stroke();

                ctx.restore();
                window.requestAnimationFrame(draw);
            }

            draw();
        }

        function beginDrag(event) {
            if (event.which && event.which !== 1) {
                return;
            }

            var rect = $root[0].getBoundingClientRect();
            dragState = {
                startX: event.clientX,
                startY: event.clientY,
                originLeft: rect.left,
                originTop: rect.top,
                dragging: false
            };

            $(document).on('mousemove.aiFloatingDrag', onDragMove);
            $(document).on('mouseup.aiFloatingDrag', onDragEnd);
            event.preventDefault();
        }

        function onDragMove(event) {
            if (!dragState) {
                return;
            }

            var deltaX = event.clientX - dragState.startX;
            var deltaY = event.clientY - dragState.startY;
            if (!dragState.dragging && (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4)) {
                dragState.dragging = true;
                $root.addClass('is-dragging');
            }

            if (!dragState.dragging) {
                return;
            }

            applyPosition({
                left: dragState.originLeft + deltaX,
                top: dragState.originTop + deltaY
            });
        }

        function onDragEnd() {
            if (!dragState) {
                return;
            }

            var wasDragging = dragState.dragging;
            $(document).off('.aiFloatingDrag');
            $root.removeClass('is-dragging');
            dragState = null;

            if (wasDragging) {
                writeLocalJSON(positionKey, getCurrentPosition());
                return;
            }

            setHidden(false);
            setPanelOpen(!$root.hasClass('is-panel-open'));
        }

        function sendQuestion(questionText) {
            var question = $.trim(questionText || $questionInput.val());
            if (!question) {
                $questionInput.focus();
                return;
            }

            setHidden(false);
            setPanelOpen(true);
            appendMessage('user', '你', question, []);
            saveHistory({
                role: 'user',
                title: '你',
                message: question,
                actions: []
            });

            $questionInput.val('');
            setState('思考中...', true);

            $.ajax({
                url: askUrl,
                type: 'POST',
                dataType: 'json',
                data: {
                    question: question,
                    pageContext: buildPageContextPayload()
                }
            }).done(function (response) {
                if (!response || response.success === false || response.result === false) {
                    appendMessage('assistant', 'AI 助手', (response && response.message) || '当前无法完成分析，请稍后重试。', []);
                    saveHistory({
                        role: 'assistant',
                        title: 'AI 助手',
                        message: (response && response.message) || '当前无法完成分析，请稍后重试。',
                        actions: []
                    });
                    return;
                }

                var data = response.data || {};
                var answer = data.answer || response.message || '已收到你的问题。';
                var suggestions = data.suggestions || [];
                var links = data.quickLinks || [];
                appendMessage('assistant', 'AI 助手', answer, suggestions);
                renderQuickLinks(links);
                saveHistory({
                    role: 'assistant',
                    title: 'AI 助手',
                    message: answer,
                    actions: suggestions,
                    quickLinks: links
                });
            }).fail(function () {
                appendMessage('assistant', 'AI 助手', '请求失败了，请稍后再试，或者换一个更具体的问题。', []);
                saveHistory({
                    role: 'assistant',
                    title: 'AI 助手',
                    message: '请求失败了，请稍后再试，或者换一个更具体的问题。',
                    actions: []
                });
            }).always(function () {
                setState('就绪', false);
            });
        }

        initMascot();
        applyMascotTheme();
        setHidden(sessionStorage.getItem(hiddenKey) === '1');
        setPanelOpen(sessionStorage.getItem(panelKey) === '1' && sessionStorage.getItem(hiddenKey) !== '1');
        initPosition();
        initRestoreBtnPosition();
        bindLivePageSignals();
        initHistory();

        $launcher.on('mousedown', beginDrag);

        $('#aiFloatingCollapse').on('click', function () {
            setPanelOpen(false);
            snapToBottomRight();
        });

        $('#aiFloatingHideMascot').on('click', function () {
            setPanelOpen(false);
            setHidden(true);
        });

        $restore.on('click', function () {
            setHidden(false);
            applyRestoreBtnPosition(defaultRestoreBtnPosition());
            writeLocalJSON(restoreBtnKey, defaultRestoreBtnPosition());
            snapToBottomRight();
        });

        $restore.on('mousedown', beginRestoreDrag);

        $sendButton.on('click', function () {
            sendQuestion();
        });

        $questionInput.on('keydown', function (event) {
            if (event.keyCode === 13 && !event.shiftKey) {
                event.preventDefault();
                sendQuestion();
            }
        });

        $(window).on('resize', function () {
            applyPosition(getCurrentPosition());
            writeLocalJSON(positionKey, getCurrentPosition());
        });

        $(document).on('click', '.ai-floating-starter, .ai-floating-message-action', function () {
            sendQuestion($(this).data('question'));
        });

        $(document).on('click', '.ai-floating-section-header', function () {
            $(this).closest('.ai-floating-section').toggleClass('is-collapsed');
        });
    });
})(jQuery);

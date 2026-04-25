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
        var aiTimeout = ($root.data('ai-timeout') || 300) * 1000;
        var storagePrefix = 'ai-floating-widget:' + projectId;
        var historyKey = storagePrefix + ':history';
        var hiddenKey = storagePrefix + ':hidden';
        var panelKey = storagePrefix + ':panel';
        var panelSizeKey = storagePrefix + ':panel-size';
        var panelLayoutKey = storagePrefix + ':panel-layout';
        var layoutLockedKey = storagePrefix + ':layout-locked';
        var panelLayoutUndoKey = storagePrefix + ':panel-layout-undo';
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
        var $messageSection = $('#aiFloatingMessageSection');
        var $contextSection = $('#aiFloatingContextStatus');
        var $quickSection = $('#aiFloatingQuickLinkSection');
        var $starterSection = $('#aiFloatingStarterSection');
        var $compose = $panel.find('.ai-floating-compose');
        var fwCurrentAjaxRequest = null;
        var fwPendingQuestion = '';
        var fwUploadedImageData = null;  // Floating widget 图片数据
        var $panelEyebrow = $('#aiFloatingPanelEyebrow');
        var $panelTitle = $('#aiFloatingPanelTitle');
        var mouseX = window.innerWidth / 2;
        var mouseY = window.innerHeight / 2;
        var dragState = null;
        var resizeState = null;
        var layoutDragState = null;
        var layoutResizeState = null;
        var $layoutGuides = null;
        var $layoutPreview = null;
        var minPanelSize = null;
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
                if (open) {
                    compactQuickLinkLayout();
                }
            });
        }

        function anchorLauncherPosition() {
            var rect = $launcher[0].getBoundingClientRect();
            // 让 launcher 关闭后面板消失后,launcher 仍停留在当前屏幕坐标
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
                    {title: '监控台', description: '查看实时请求与调用链', url: projectBase + '/monitor', icon: 'chart line', priority: 88}
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
                html += '<div class="ai-floating-context-chip">当前筛选：<span>' + escapeHtml(liveSignals.filters.join('、')) + '</span><button class="ai-floating-chip-close" type="button" title="清除"><i class="close icon"></i></button></div>';
            }
            if (liveSignals.tableHover) {
                html += '<div class="ai-floating-context-chip">当前悬停：<span>' + escapeHtml(liveSignals.tableHover) + '</span><button class="ai-floating-chip-close" type="button" title="清除"><i class="close icon"></i></button></div>';
            }
            if (liveSignals.tableSelection) {
                html += '<div class="ai-floating-context-chip">当前选中：<span>' + escapeHtml(liveSignals.tableSelection) + '</span><button class="ai-floating-chip-close" type="button" title="清除"><i class="close icon"></i></button></div>';
            }
            $contextStatus.html(html);
            // 无内容时隐藏整个区域，避免占位影响布局
            if (!html) {
                $contextStatus.hide();
            } else {
                $contextStatus.show();
            }
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

        function getPanelMinimumSize() {
            if (!minPanelSize) {
                minPanelSize = {
                    width: $panel.outerWidth(),
                    height: $panel.outerHeight()
                };
            }
            return minPanelSize;
        }

        function getLayoutItems() {
            return [
                { id: 'message', $el: $messageSection, minWidth: 180, minHeight: 120, preferredHeight: 190 },
                { id: 'context', $el: $contextSection, minWidth: 160, minHeight: 48, preferredHeight: 68 },
                { id: 'quick', $el: $quickSection, minWidth: 180, minHeight: 92, preferredHeight: 150 },
                { id: 'starter', $el: $starterSection, minWidth: 180, minHeight: 86, preferredHeight: 114 },
                { id: 'compose', $el: $compose, minWidth: 220, minHeight: 122, preferredHeight: 144 }
            ];
        }

        function getPanelInnerBounds() {
            var $header = $panel.find('.ai-floating-panel-header');
            var panelWidth = $panel.innerWidth();
            var panelHeight = $panel.innerHeight();
            var headerBottom = $header.length ? ($header.position().top + $header.outerHeight()) : 0;
            var padding = 10;
            var topOffset = headerBottom + 8;
            var bottomOffset = padding;
            return {
                width: Math.max(0, panelWidth - padding * 2),
                height: Math.max(0, panelHeight),
                leftOffset: padding,
                topOffset: topOffset,
                bottomOffset: bottomOffset,
                usableHeight: Math.max(0, panelHeight - topOffset - bottomOffset)
            };
        }

        function rectsOverlap(a, b) {
            return !(a.left + a.width <= b.left || b.left + b.width <= a.left || a.top + a.height <= b.top || b.top + b.height <= a.top);
        }

        function updateUndoLayoutButton() {
            var hasUndo = !!readLocalJSON(panelLayoutUndoKey, null);
            $('#aiFloatingUndoLayout').prop('disabled', !hasUndo).toggleClass('is-disabled', !hasUndo);
        }

        function snapshotPanelLayoutForUndo() {
            writeLocalJSON(panelLayoutUndoKey, collectLayoutMap());
            updateUndoLayoutButton();
        }

        function restoreUndoPanelLayout() {
            var undoLayout = readLocalJSON(panelLayoutUndoKey, null);
            if (!undoLayout) {
                return;
            }
            writeLocalJSON(panelLayoutUndoKey, null);
            applyLayoutMap(undoLayout);
            savePanelLayout();
            updateUndoLayoutButton();
        }

        function setLayoutLocked(locked) {
            $panel.toggleClass('is-layout-locked', locked);
            localStorage.setItem(layoutLockedKey, locked ? '1' : '0');
            var $btn = $('#aiFloatingToggleLayoutLock');
            $btn.text(locked ? '🔒' : '🔓');
            $btn.attr('title', locked ? '解锁内部布局，允许拖动和缩放' : '锁定内部布局，防止误拖动');
        }

        function isLayoutLocked() {
            return $panel.hasClass('is-layout-locked');
        }

        function snapValue(value, candidates, threshold) {
            var snapped = value;
            var minDelta = threshold + 1;
            var snappedTo = null;
            $.each(candidates, function (_, candidate) {
                var delta = Math.abs(candidate - value);
                if (delta <= threshold && delta < minDelta) {
                    snapped = candidate;
                    minDelta = delta;
                    snappedTo = candidate;
                }
            });
            return {
                value: snapped,
                guide: snappedTo
            };
        }

        function ensureLayoutGuides() {
            if ($layoutGuides && $layoutGuides.length) {
                return;
            }
            $layoutGuides = $('<div class="ai-floating-layout-guide ai-floating-layout-guide-x"></div><div class="ai-floating-layout-guide ai-floating-layout-guide-y"></div>');
            $panel.append($layoutGuides);
        }

        function ensureLayoutPreview() {
            if ($layoutPreview && $layoutPreview.length) {
                return;
            }
            $layoutPreview = $('<div class="ai-floating-layout-preview"></div>');
            $panel.append($layoutPreview);
        }

        function hideLayoutPreview() {
            ensureLayoutPreview();
            $layoutPreview.removeClass('is-visible');
        }

        function highlightLayoutTargets(candidate, movingId) {
            $.each(getLayoutItems(), function (_, item) {
                item.$el.removeClass('is-layout-target');
                if (item.id === movingId) {
                    return;
                }
                var rect = {
                    left: parseFloat(item.$el[0].style.left) || item.$el.position().left,
                    top: parseFloat(item.$el[0].style.top) || item.$el.position().top,
                    width: parseFloat(item.$el[0].style.width) || item.$el.outerWidth(),
                    height: parseFloat(item.$el[0].style.height) || item.$el.outerHeight()
                };
                var gapThreshold = 12;
                var nearHorizontal = Math.abs((candidate.left + candidate.width) - rect.left) <= gapThreshold || Math.abs(candidate.left - (rect.left + rect.width)) <= gapThreshold;
                var overlapVertical = !(candidate.top + candidate.height < rect.top || rect.top + rect.height < candidate.top);
                var nearVertical = Math.abs((candidate.top + candidate.height) - rect.top) <= gapThreshold || Math.abs(candidate.top - (rect.top + rect.height)) <= gapThreshold;
                var overlapHorizontal = !(candidate.left + candidate.width < rect.left || rect.left + rect.width < candidate.left);
                if ((nearHorizontal && overlapVertical) || (nearVertical && overlapHorizontal)) {
                    item.$el.addClass('is-layout-target');
                }
            });
        }

        function clearLayoutTargetHighlights() {
            $.each(getLayoutItems(), function (_, item) {
                item.$el.removeClass('is-layout-target');
            });
        }

        function showLayoutPreview(rect) {
            ensureLayoutPreview();
            $layoutPreview.css({
                left: rect.left + 'px',
                top: rect.top + 'px',
                width: rect.width + 'px',
                height: rect.height + 'px'
            }).addClass('is-visible');
        }

        function hideLayoutGuides() {
            ensureLayoutGuides();
            $panel.find('.ai-floating-layout-guide').removeClass('is-visible');
        }

        function showLayoutGuides(guides, rect) {
            ensureLayoutGuides();
            var bounds = getPanelInnerBounds();
            var $guideX = $panel.find('.ai-floating-layout-guide-x');
            var $guideY = $panel.find('.ai-floating-layout-guide-y');
            if (guides.x !== null && guides.x !== undefined) {
                $guideX.css({ left: guides.x + 'px', top: bounds.topOffset + 'px', height: Math.max(0, $panel.innerHeight() - bounds.topOffset - bounds.bottomOffset) + 'px' }).addClass('is-visible');
            } else {
                $guideX.removeClass('is-visible');
            }
            if (guides.y !== null && guides.y !== undefined) {
                $guideY.css({ top: guides.y + 'px', left: bounds.leftOffset + 'px', width: Math.max(0, bounds.width) + 'px' }).addClass('is-visible');
            } else {
                $guideY.removeClass('is-visible');
            }
        }

        function applyLayoutSnap(rect, movingId) {
            var snapped = $.extend({}, rect);
            var threshold = 10;
            var bounds = getPanelInnerBounds();
            var itemMeta = getLayoutItems().filter(function (item) { return item.id === movingId; })[0];
            var horizontalCandidates = [bounds.leftOffset, bounds.leftOffset + 10, Math.max(bounds.leftOffset, bounds.leftOffset + bounds.width - rect.width)];
            var verticalCandidates = [bounds.topOffset, Math.max(bounds.topOffset, bounds.topOffset + bounds.usableHeight - rect.height)];

            $.each(collectLayoutMap(), function (id, otherRect) {
                if (id === movingId) {
                    return;
                }
                horizontalCandidates.push(otherRect.left);
                horizontalCandidates.push(otherRect.left + otherRect.width);
                horizontalCandidates.push(otherRect.left - rect.width);
                horizontalCandidates.push(otherRect.left + otherRect.width - rect.width);
                verticalCandidates.push(otherRect.top);
                verticalCandidates.push(otherRect.top + otherRect.height);
                verticalCandidates.push(otherRect.top - rect.height);
                verticalCandidates.push(otherRect.top + otherRect.height - rect.height);
            });

            var snappedLeft = snapValue(snapped.left, horizontalCandidates, threshold);
            var snappedTop = snapValue(snapped.top, verticalCandidates, threshold);
            snapped.left = snappedLeft.value;
            snapped.top = snappedTop.value;
            snapped = clampLayoutRect({ left: snapped.left, top: snapped.top, width: snapped.width, height: snapped.height }, itemMeta);
            snapped.guides = {
                x: snappedLeft.guide,
                y: snappedTop.guide
            };
            return snapped;
        }

        function getDefaultLayoutMap() {
            var bounds = getPanelInnerBounds();
            var top = bounds.topOffset;
            var gap = 10;
            var sideWidth = Math.max(180, Math.round(bounds.width * 0.34));
            var mainWidth = Math.max(200, bounds.width - sideWidth - gap);
            var messageHeight = Math.max(160, Math.round(bounds.usableHeight * 0.48));
            var contextHeight = 68;
            var quickHeight = 136;
            var starterHeight = 104;
            var composeHeight = 142;
            var sideHeight = Math.max(220, bounds.usableHeight - composeHeight - gap);
            var quickActualHeight = Math.min(quickHeight, Math.max(92, sideHeight - starterHeight - gap));
            var starterActualHeight = Math.max(86, sideHeight - quickActualHeight - gap);
            return {
                message: { left: bounds.leftOffset, top: top, width: mainWidth, height: messageHeight },
                context: { left: bounds.leftOffset, top: top + messageHeight + gap, width: mainWidth, height: contextHeight },
                quick: { left: bounds.leftOffset + mainWidth + gap, top: top, width: sideWidth, height: quickActualHeight },
                starter: { left: bounds.leftOffset + mainWidth + gap, top: top + quickActualHeight + gap, width: sideWidth, height: starterActualHeight },
                compose: { left: bounds.leftOffset, top: bounds.topOffset + bounds.usableHeight - composeHeight, width: bounds.width, height: composeHeight }
            };
        }

        function compactQuickLinkLayout() {
            if (!$quickSection.is(':visible') || !$quickLinks.length || !$quickLinks.children().length) {
                return;
            }

            var quickItem = getLayoutItems().filter(function (item) { return item.id === 'quick'; })[0];
            if (!quickItem || !$quickSection.hasClass('ai-floating-layout-item')) {
                return;
            }

            var current = collectLayoutMap();
            var quickRect = current.quick;
            var starterRect = current.starter;
            if (!quickRect) {
                return;
            }

            var headerHeight = $quickSection.find('.ai-floating-section-header').outerHeight(true) || 0;
            var bodyHeight = $quickLinks[0].scrollHeight || $quickLinks.outerHeight(true) || 0;
            var desiredHeight = Math.ceil(headerHeight + bodyHeight + 4);
            desiredHeight = Math.max(quickItem.minHeight, desiredHeight);

            var shrinkBy = Math.floor(quickRect.height - desiredHeight);
            if (shrinkBy <= 24) {
                return;
            }

            current.quick = $.extend({}, quickRect, { height: desiredHeight });
            if (starterRect) {
                var sameColumn = starterRect.left < quickRect.left + quickRect.width && quickRect.left < starterRect.left + starterRect.width;
                var belowQuick = starterRect.top >= quickRect.top + quickRect.height - 2;
                if (sameColumn && belowQuick) {
                    current.starter = $.extend({}, starterRect, { top: Math.max(quickRect.top + desiredHeight + 10, starterRect.top - shrinkBy) });
                }
            }

            applyLayoutMap(current);
        }

        function clampLayoutRect(rect, item) {
            var bounds = getPanelInnerBounds();
            var width = Math.min(Math.max(rect.width, item.minWidth), bounds.width);
            var height = Math.min(Math.max(rect.height, item.minHeight), Math.max(item.minHeight, bounds.usableHeight));
            return {
                left: Math.min(Math.max(rect.left, bounds.leftOffset), Math.max(bounds.leftOffset, bounds.leftOffset + bounds.width - width)),
                top: Math.min(Math.max(rect.top, bounds.topOffset), Math.max(bounds.topOffset, bounds.topOffset + bounds.usableHeight - height)),
                width: width,
                height: height
            };
        }

        function collectLayoutMap() {
            var map = {};
            $.each(getLayoutItems(), function (_, item) {
                var style = item.$el[0].style;
                map[item.id] = {
                    left: parseFloat(style.left) || item.$el.position().left,
                    top: parseFloat(style.top) || item.$el.position().top,
                    width: parseFloat(style.width) || item.$el.outerWidth(),
                    height: parseFloat(style.height) || item.$el.outerHeight()
                };
            });
            return map;
        }

        function applyLayoutMap(layoutMap) {
            $.each(getLayoutItems(), function (_, item) {
                var rect = layoutMap[item.id];
                if (!rect) {
                    return;
                }
                var safeRect = clampLayoutRect(rect, item);
                item.$el.css({
                    left: safeRect.left + 'px',
                    top: safeRect.top + 'px',
                    width: safeRect.width + 'px',
                    height: safeRect.height + 'px'
                });
            });
        }

        function hasLayoutCollision(layoutMap, movingId) {
            var ids = [];
            $.each(getLayoutItems(), function (_, item) {
                if (layoutMap[item.id]) {
                    ids.push(item.id);
                }
            });
            for (var i = 0; i < ids.length; i++) {
                for (var j = i + 1; j < ids.length; j++) {
                    var idA = ids[i];
                    var idB = ids[j];
                    if (movingId && idA !== movingId && idB !== movingId) {
                        continue;
                    }
                    if (rectsOverlap(layoutMap[idA], layoutMap[idB])) {
                        return true;
                    }
                }
            }
            return false;
        }

        function resolveSoftLayoutReflow(layoutMap, movingId) {
            var resolved = $.extend(true, {}, layoutMap);
            var bounds = getPanelInnerBounds();
            var gap = 10;
            var items = getLayoutItems();
            var movingRect = resolved[movingId];
            if (!movingRect) {
                return resolved;
            }

            var sorted = items.slice(0).sort(function (a, b) {
                if (a.id === movingId) {
                    return -1;
                }
                if (b.id === movingId) {
                    return 1;
                }
                return (resolved[a.id].top - resolved[b.id].top) || (resolved[a.id].left - resolved[b.id].left);
            });

            $.each(sorted, function (_, itemMeta) {
                var rect = resolved[itemMeta.id];
                if (!rect) {
                    return;
                }
                rect = clampLayoutRect(rect, itemMeta);
                if (itemMeta.id === movingId) {
                    resolved[itemMeta.id] = rect;
                    return;
                }

                var guard = 0;
                while (rectsOverlap(rect, movingRect) && guard < 24) {
                    var nextTop = movingRect.top + movingRect.height + gap;
                    var maxTop = Math.max(bounds.topOffset, bounds.topOffset + bounds.usableHeight - rect.height);
                    if (nextTop <= maxTop) {
                        rect.top = nextTop;
                    } else {
                        var nextLeft = movingRect.left + movingRect.width + gap;
                        var maxLeft = Math.max(bounds.leftOffset, bounds.leftOffset + bounds.width - rect.width);
                        if (nextLeft <= maxLeft) {
                            rect.left = nextLeft;
                            rect.top = Math.min(Math.max(movingRect.top, bounds.topOffset), maxTop);
                        } else {
                            rect.top = Math.max(bounds.topOffset, movingRect.top - rect.height - gap);
                            rect.left = Math.min(Math.max(rect.left, bounds.leftOffset), maxLeft);
                        }
                    }
                    rect = clampLayoutRect(rect, itemMeta);
                    guard += 1;
                }
                resolved[itemMeta.id] = rect;
                movingRect = resolved[movingId];
            });

            return resolved;
        }

        function findAutoAvoidRect(layoutMap, movingId, baseRect, item, mode) {
            var bounds = getPanelInnerBounds();
            var gap = 10;
            var attempts = [];
            var topMin = bounds.topOffset;
            var topMax = Math.max(bounds.topOffset, bounds.topOffset + bounds.usableHeight - baseRect.height);
            var leftMax = Math.max(bounds.leftOffset, bounds.leftOffset + bounds.width - baseRect.width);

            $.each(layoutMap, function (id, otherRect) {
                if (id === movingId) {
                    return;
                }
                attempts.push({ left: baseRect.left, top: otherRect.top - baseRect.height - gap, width: baseRect.width, height: baseRect.height });
                attempts.push({ left: baseRect.left, top: otherRect.top + otherRect.height + gap, width: baseRect.width, height: baseRect.height });
                attempts.push({ left: otherRect.left - baseRect.width - gap, top: baseRect.top, width: baseRect.width, height: baseRect.height });
                attempts.push({ left: otherRect.left + otherRect.width + gap, top: baseRect.top, width: baseRect.width, height: baseRect.height });
            });

            attempts.push({ left: baseRect.left, top: topMin, width: baseRect.width, height: baseRect.height });
            attempts.push({ left: baseRect.left, top: topMax, width: baseRect.width, height: baseRect.height });
            attempts.push({ left: bounds.leftOffset, top: baseRect.top, width: baseRect.width, height: baseRect.height });
            attempts.push({ left: leftMax, top: baseRect.top, width: baseRect.width, height: baseRect.height });

            var best = null;
            var bestScore = Number.MAX_VALUE;

            $.each(attempts, function (_, attempt) {
                var candidate = clampLayoutRect(attempt, item);
                candidate = applyLayoutSnap(candidate, movingId);
                var candidateMap = $.extend({}, layoutMap);
                candidateMap[movingId] = candidate;
                if (hasLayoutCollision(candidateMap, movingId)) {
                    return;
                }
                var score = Math.abs(candidate.left - baseRect.left) + Math.abs(candidate.top - baseRect.top);
                if (mode === 'resize') {
                    score += Math.abs(candidate.width - baseRect.width) + Math.abs(candidate.height - baseRect.height);
                }
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            });

            return best;
        }

        function scalePanelLayout(previousBounds, nextBounds) {
            if (!previousBounds || !nextBounds) {
                restorePanelLayout();
                return;
            }

            var prevUsableWidth = Math.max(1, previousBounds.width);
            var prevUsableHeight = Math.max(1, previousBounds.usableHeight);
            var nextUsableWidth = Math.max(1, nextBounds.width);
            var nextUsableHeight = Math.max(1, nextBounds.usableHeight);
            var widthRatio = nextUsableWidth / prevUsableWidth;
            var heightRatio = nextUsableHeight / prevUsableHeight;
            var scaled = {};

            $.each(getLayoutItems(), function (_, item) {
                var style = item.$el[0].style;
                var left = parseFloat(style.left) || item.$el.position().left;
                var top = parseFloat(style.top) || item.$el.position().top;
                var width = parseFloat(style.width) || item.$el.outerWidth();
                var height = parseFloat(style.height) || item.$el.outerHeight();
                var relativeLeft = left - previousBounds.leftOffset;
                var relativeTop = top - previousBounds.topOffset;

                scaled[item.id] = {
                    left: nextBounds.leftOffset + relativeLeft * widthRatio,
                    top: nextBounds.topOffset + relativeTop * heightRatio,
                    width: width * widthRatio,
                    height: height * heightRatio
                };
            });

            applyLayoutMap(scaled);
            savePanelLayout();
        }

        function savePanelLayout() {
            writeLocalJSON(panelLayoutKey, collectLayoutMap());
            updateUndoLayoutButton();
        }

        function restorePanelLayout() {
            var layout = readLocalJSON(panelLayoutKey, null) || getDefaultLayoutMap();
            applyLayoutMap(layout);
            if (hasLayoutCollision(collectLayoutMap())) {
                applyLayoutMap(getDefaultLayoutMap());
                savePanelLayout();
            }
        }

        function resetPanelLayout() {
            snapshotPanelLayoutForUndo();
            applyLayoutMap(getDefaultLayoutMap());
            clearLayoutTargetHighlights();
            hideLayoutGuides();
            hideLayoutPreview();
            savePanelLayout();
        }

        function bringLayoutItemToFront($item) {
            var maxZ = 1;
            $.each(getLayoutItems(), function (_, item) {
                maxZ = Math.max(maxZ, parseInt(item.$el.css('z-index'), 10) || 1);
            });
            $item.css('z-index', maxZ + 1);
        }

        function beginLayoutDrag(event) {
            if (event.which && event.which !== 1) {
                return;
            }
            if (isLayoutLocked()) {
                return;
            }
            if ($(event.target).closest('button, a, textarea, input, .ai-floating-section-toggle, .ai-floating-layout-resizer').length) {
                return;
            }
            var $item = $(event.currentTarget);
            var item = $item.data('layout-item');
            if (!item) {
                return;
            }
            var position = $item.position();
            layoutDragState = {
                id: item.id,
                $el: $item,
                startX: event.clientX,
                startY: event.clientY,
                originLeft: position.left,
                originTop: position.top
            };
            bringLayoutItemToFront($item);
            snapshotPanelLayoutForUndo();
            $(document).on('mousemove.aiFloatingLayoutDrag', onLayoutDragMove);
            $(document).on('mouseup.aiFloatingLayoutDrag', onLayoutDragEnd);
            event.preventDefault();
            event.stopPropagation();
        }

        function onLayoutDragMove(event) {
            if (!layoutDragState) {
                return;
            }
            var item = layoutDragState.$el.data('layout-item');
            var current = collectLayoutMap();
            var candidate = clampLayoutRect({
                left: layoutDragState.originLeft + (event.clientX - layoutDragState.startX),
                top: layoutDragState.originTop + (event.clientY - layoutDragState.startY),
                width: current[layoutDragState.id].width,
                height: current[layoutDragState.id].height
            }, item);
            candidate = applyLayoutSnap(candidate, layoutDragState.id);
            current[layoutDragState.id] = candidate;
            if (hasLayoutCollision(current, layoutDragState.id)) {
                var reflowed = resolveSoftLayoutReflow(current, layoutDragState.id);
                if (!hasLayoutCollision(reflowed, layoutDragState.id)) {
                    current = reflowed;
                    candidate = current[layoutDragState.id];
                    applyLayoutMap(current);
                } else {
                    var autoAvoidCandidate = findAutoAvoidRect(current, layoutDragState.id, candidate, item, 'drag');
                    if (!autoAvoidCandidate) {
                        clearLayoutTargetHighlights();
                        hideLayoutGuides();
                        hideLayoutPreview();
                        return;
                    }
                    candidate = autoAvoidCandidate;
                    current[layoutDragState.id] = candidate;
                }
            }
            highlightLayoutTargets(candidate, layoutDragState.id);
            showLayoutGuides(candidate.guides || {}, candidate);
            showLayoutPreview(candidate);
            layoutDragState.$el.css({ left: candidate.left + 'px', top: candidate.top + 'px' });
        }

        function onLayoutDragEnd() {
            if (!layoutDragState) {
                return;
            }
            $(document).off('.aiFloatingLayoutDrag');
            clearLayoutTargetHighlights();
            hideLayoutGuides();
            hideLayoutPreview();
            layoutDragState = null;
            savePanelLayout();
        }

        function detectInnerResizeDirection($item, event) {
            var rect = $item[0].getBoundingClientRect();
            var edgeSize = 10;
            var horizontal = '';
            var vertical = '';

            if (event.clientX <= rect.left + edgeSize) {
                horizontal = 'w';
            } else if (event.clientX >= rect.right - edgeSize) {
                horizontal = 'e';
            }

            if (event.clientY <= rect.top + edgeSize) {
                vertical = 'n';
            } else if (event.clientY >= rect.bottom - edgeSize) {
                vertical = 's';
            }

            return vertical + horizontal;
        }

        function beginLayoutResize(event) {
            if (event.which && event.which !== 1) {
                return;
            }
            if (isLayoutLocked()) {
                return;
            }
            var $item = $(event.target).closest('.ai-floating-layout-item');
            if ($item.length === 0) {
                return;
            }
            var direction = $(event.target).closest('.ai-floating-layout-resizer').length ? 'se' : detectInnerResizeDirection($item, event);
            if (!direction) {
                return;
            }
            var item = $item.data('layout-item');
            if (!item) {
                return;
            }
            var position = $item.position();
            layoutResizeState = {
                id: item.id,
                $el: $item,
                startX: event.clientX,
                startY: event.clientY,
                originLeft: position.left,
                originTop: position.top,
                originWidth: $item.outerWidth(),
                originHeight: $item.outerHeight(),
                direction: direction
            };
            bringLayoutItemToFront($item);
            snapshotPanelLayoutForUndo();
            $(document).on('mousemove.aiFloatingLayoutResize', onLayoutResizeMove);
            $(document).on('mouseup.aiFloatingLayoutResize', onLayoutResizeEnd);
            event.preventDefault();
            event.stopPropagation();
        }

        function onLayoutResizeMove(event) {
            if (!layoutResizeState) {
                return;
            }
            var item = layoutResizeState.$el.data('layout-item');
            var current = collectLayoutMap();
            var deltaX = event.clientX - layoutResizeState.startX;
            var deltaY = event.clientY - layoutResizeState.startY;
            var candidate = {
                left: layoutResizeState.originLeft,
                top: layoutResizeState.originTop,
                width: layoutResizeState.originWidth,
                height: layoutResizeState.originHeight
            };

            if (layoutResizeState.direction.indexOf('e') !== -1) {
                candidate.width = layoutResizeState.originWidth + deltaX;
            }
            if (layoutResizeState.direction.indexOf('s') !== -1) {
                candidate.height = layoutResizeState.originHeight + deltaY;
            }
            if (layoutResizeState.direction.indexOf('w') !== -1) {
                candidate.width = layoutResizeState.originWidth - deltaX;
                candidate.left = layoutResizeState.originLeft + deltaX;
            }
            if (layoutResizeState.direction.indexOf('n') !== -1) {
                candidate.height = layoutResizeState.originHeight - deltaY;
                candidate.top = layoutResizeState.originTop + deltaY;
            }

            candidate = clampLayoutRect(candidate, item);
            candidate = applyLayoutSnap(candidate, layoutResizeState.id);
            current[layoutResizeState.id] = candidate;
            if (hasLayoutCollision(current, layoutResizeState.id)) {
                var reflowed = resolveSoftLayoutReflow(current, layoutResizeState.id);
                if (!hasLayoutCollision(reflowed, layoutResizeState.id)) {
                    current = reflowed;
                    candidate = current[layoutResizeState.id];
                    applyLayoutMap(current);
                } else {
                    var autoAvoidCandidate = findAutoAvoidRect(current, layoutResizeState.id, candidate, item, 'resize');
                    if (!autoAvoidCandidate) {
                        clearLayoutTargetHighlights();
                        hideLayoutGuides();
                        hideLayoutPreview();
                        return;
                    }
                    candidate = autoAvoidCandidate;
                    current[layoutResizeState.id] = candidate;
                }
            }
            highlightLayoutTargets(candidate, layoutResizeState.id);
            showLayoutGuides(candidate.guides || {}, candidate);
            showLayoutPreview(candidate);
            layoutResizeState.$el.css({
                left: candidate.left + 'px',
                top: candidate.top + 'px',
                width: candidate.width + 'px',
                height: candidate.height + 'px'
            });
        }

        function onLayoutResizeEnd() {
            if (!layoutResizeState) {
                return;
            }
            $(document).off('.aiFloatingLayoutResize');
            layoutResizeState.$el.removeClass('resize-n resize-s resize-e resize-w resize-ne resize-nw resize-se resize-sw');
            clearLayoutTargetHighlights();
            hideLayoutGuides();
            hideLayoutPreview();
            layoutResizeState = null;
            savePanelLayout();
        }

        function initLayoutCustomizer() {
            $.each(getLayoutItems(), function (_, item) {
                item.$el.addClass('ai-floating-layout-item');
                item.$el.data('layout-item', item);
                if (item.$el.find('.ai-floating-layout-resizer').length === 0) {
                    item.$el.append('<span class="ai-floating-layout-resizer" title="拖拽调整大小"></span>');
                }
                if (item.$el.find('.ai-floating-layout-edge-hint').length === 0) {
                    item.$el.append('<span class="ai-floating-layout-edge-hint top"></span><span class="ai-floating-layout-edge-hint right"></span><span class="ai-floating-layout-edge-hint bottom"></span><span class="ai-floating-layout-edge-hint left"></span>');
                }
            });
            restorePanelLayout();
        }

        function getPanelSize() {
            return {
                width: $panel.outerWidth(),
                height: $panel.outerHeight()
            };
        }

        function savePanelSize() {
            writeLocalJSON(panelSizeKey, getPanelSize());
        }

        function getViewportResizeBounds() {
            var minSize = getPanelMinimumSize();
            var launcherWidth = $launcher.outerWidth() || 96;
            var viewportPadding = 8;
            return {
                minWidth: minSize.width,
                minHeight: minSize.height,
                maxWidth: Math.max(minSize.width, window.innerWidth - launcherWidth - viewportPadding * 3),
                maxHeight: Math.max(minSize.height, window.innerHeight - viewportPadding * 2)
            };
        }

        function applyPanelSize(size) {
            var bounds = getViewportResizeBounds();
            var safeWidth = Math.min(Math.max(size.width, bounds.minWidth), bounds.maxWidth);
            var safeHeight = Math.min(Math.max(size.height, bounds.minHeight), bounds.maxHeight);

            $panel.css({
                width: safeWidth + 'px',
                height: safeHeight + 'px'
            });
        }

        function restorePanelSize() {
            var saved = readLocalJSON(panelSizeKey, null);
            if (!saved) {
                return;
            }
            applyPanelSize(saved);
        }

        function resetPanelSize() {
            var minSize = getPanelMinimumSize();
            $panel.css({
                width: '',
                height: ''
            });
            applyPanelSize(minSize);
            savePanelSize();
            applyPosition(getCurrentPosition());
            writeLocalJSON(positionKey, getCurrentPosition());
        }

        function detectResizeDirection(event) {
            var rect = $panel[0].getBoundingClientRect();
            var edgeSize = 10;
            var horizontal = '';
            var vertical = '';

            if (event.clientX <= rect.left + edgeSize) {
                horizontal = 'w';
            } else if (event.clientX >= rect.right - edgeSize) {
                horizontal = 'e';
            }

            if (event.clientY <= rect.top + edgeSize) {
                vertical = 'n';
            } else if (event.clientY >= rect.bottom - edgeSize) {
                vertical = 's';
            }

            return vertical + horizontal;
        }

        function getResizeCursor(direction) {
            var cursors = {
                n: 'ns-resize',
                s: 'ns-resize',
                e: 'ew-resize',
                w: 'ew-resize',
                ne: 'nesw-resize',
                sw: 'nesw-resize',
                nw: 'nwse-resize',
                se: 'nwse-resize'
            };
            return cursors[direction] || '';
        }

        function beginResize(event) {
            if (event.which && event.which !== 1) {
                return;
            }

            var direction = $(event.target).hasClass('ai-floating-resize-handle') ? 'se' : detectResizeDirection(event);
            if (!direction) {
                return;
            }

            var panelRect = $panel[0].getBoundingClientRect();
            var rootRect = $root[0].getBoundingClientRect();
            resizeState = {
                startX: event.clientX,
                startY: event.clientY,
                originWidth: panelRect.width,
                originHeight: panelRect.height,
                originPanelLeft: panelRect.left,
                originPanelTop: panelRect.top,
                originRootLeft: rootRect.left,
                originRootTop: rootRect.top,
                direction: direction
            };

            $(document).on('mousemove.aiFloatingResize', onResizeMove);
            $(document).on('mouseup.aiFloatingResize', onResizeEnd);
            $root.addClass('is-resizing');
            $root[0].style.setProperty('--ai-floating-resize-cursor', getResizeCursor(direction) || 'nwse-resize');
            event.preventDefault();
            event.stopPropagation();
        }

        function onResizeMove(event) {
            if (!resizeState) {
                return;
            }

            var previousBounds = getPanelInnerBounds();
            var deltaX = event.clientX - resizeState.startX;
            var deltaY = event.clientY - resizeState.startY;
            var bounds = getViewportResizeBounds();
            var width = resizeState.originWidth;
            var height = resizeState.originHeight;
            var left = resizeState.originRootLeft;
            var top = resizeState.originRootTop;

            if (resizeState.direction.indexOf('e') !== -1) {
                width = resizeState.originWidth + deltaX;
            }
            if (resizeState.direction.indexOf('s') !== -1) {
                height = resizeState.originHeight + deltaY;
            }
            if (resizeState.direction.indexOf('w') !== -1) {
                width = resizeState.originWidth - deltaX;
            }
            if (resizeState.direction.indexOf('n') !== -1) {
                height = resizeState.originHeight - deltaY;
            }

            width = Math.min(Math.max(width, bounds.minWidth), bounds.maxWidth);
            height = Math.min(Math.max(height, bounds.minHeight), bounds.maxHeight);

            if (resizeState.direction.indexOf('w') !== -1) {
                left = resizeState.originRootLeft + (resizeState.originWidth - width);
            }
            if (resizeState.direction.indexOf('n') !== -1) {
                top = resizeState.originRootTop + (resizeState.originHeight - height);
            }

            applyPanelSize({ width: width, height: height });
            applyPosition({ left: left, top: top });
            if (!resizeState.hasSnapshot) {
                snapshotPanelLayoutForUndo();
                resizeState.hasSnapshot = true;
            }
            scalePanelLayout(previousBounds, getPanelInnerBounds());
        }

        function onResizeEnd() {
            if (!resizeState) {
                return;
            }

            $(document).off('.aiFloatingResize');
            $root.removeClass('is-resizing');
            $root[0].style.setProperty('--ai-floating-resize-cursor', 'nwse-resize');
            resizeState = null;
            savePanelSize();
            writeLocalJSON(positionKey, getCurrentPosition());
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

        function refreshResizeConstraints() {
            var previousBounds = getPanelInnerBounds();
            applyPanelSize(getPanelSize());
            applyPosition(getCurrentPosition());
            scalePanelLayout(previousBounds, getPanelInnerBounds());
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
            var contentHtml = options.isHtml ? message : formatMessage(message);
            var html = ''
                + '<div class="ai-floating-message ' + role + '">'
                + '  <div class="ai-floating-avatar">' + escapeHtml(avatar) + '</div>'
                + '  <div class="ai-floating-message-body">'
                + '      <div class="ai-floating-message-name">' + escapeHtml(title) + '</div>'
                + '      <div class="ai-floating-message-card" style="position:relative">' + contentHtml
                + '<button class="ai-floating-copy-btn" title="复制"><i class="copy icon"></i></button>';

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
            // 绑定复制按钮
            bindCopyButton($messageList.find('.ai-floating-copy-btn').last(), message);
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

        /* ===== Stop / Resume Generation (Floating) ===== */
        function fwSetSendButtonToStop() {
            $sendButton.text('停止').addClass('ai-floating-stop-btn').prop('disabled', false);
        }

        function fwResetSendButton() {
            $sendButton.text('发送').removeClass('ai-floating-stop-btn');
        }

        function fwStopGeneration() {
            if (fwCurrentAjaxRequest) {
                fwCurrentAjaxRequest.abort();
                fwCurrentAjaxRequest = null;
            }
        }

        function fwShowStoppedMessage(questionText) {
            hideLoading();
            var html = ''
                + '<div class="ai-floating-message assistant">'
                + '  <div class="ai-floating-avatar">AI</div>'
                + '  <div class="ai-floating-message-body">'
                + '    <div class="ai-floating-message-name">AI 助手</div>'
                + '    <div class="ai-floating-stopped-card">'
                + '      <span><i class="pause circle icon"></i> 已停止生成</span>'
                + '      <button class="ai-resume-btn" data-question="' + escapeHtml(questionText) + '">重新发送</button>'
                + '    </div>'
                + '  </div>'
                + '</div>';
            $messageList.append(html);
            $('#aiFloatingMessageSection').removeClass('is-collapsed');
            scrollToBottom();
            saveHistory({
                role: 'assistant',
                title: 'AI 助手',
                message: '[已停止生成] 原问题: ' + questionText,
                actions: []
            });
            fwResetSendButton();
            setState('就绪', false);
        }

        /* ===== Image Upload & Voice Recording (Floating) ===== */
        $('#aiFloatingImageUploadBtn').on('click', function (e) {
            // 有图片时点击按钮只预览图片，不触发文件选择
            if ($(this).hasClass('has-image')) {
                e.preventDefault();
                var src = $(this).find('.ai-floating-image-preview').attr('src');
                if (src) {
                    $(this).find('.ai-floating-image-preview').trigger('click');
                }
                return;
            }
            $('#aiFloatingImageInput').trigger('click');
        });

        $('#aiFloatingImageInput').on('change', function () {
            var file = this.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                appendMessage('assistant', 'AI 助手', '仅支持图片文件', [], { scrollTop: true });
                return;
            }
            fwHandleImageFile(file);
            this.value = '';
        });

        /**
         * Floating widget 统一图片处理入口（文件选择 / 粘贴共用）
         */
        function fwHandleImageFile(file) {
            if (file.size > 10 * 1024 * 1024) {
                appendMessage('assistant', 'AI 助手', '图片不能超过 10MB', [], { scrollTop: true });
                return;
            }
            var reader = new FileReader();
            reader.onload = function (e) {
                fwUploadedImageData = e.target.result;
                var $btn = $('#aiFloatingImageUploadBtn');
                $btn.addClass('has-image');
                $btn.find('.ai-floating-image-preview').attr('src', fwUploadedImageData).show();
            };
            reader.readAsDataURL(file);
        }

        // 点击预览图 → 打开全屏预览（不触发文件选择）
        $(document).on('click', '.ai-floating-image-preview', function (e) {
            e.stopPropagation();
            e.preventDefault();
            var src = $(this).attr('src');
            if (!src) return;
            var $overlay = $('<div class="ai-lightbox-overlay" style="position:fixed;top:0;left:0;right:0;bottom:0;z-index:99999;background:rgba(0,0,0,0.75);display:flex;align-items:center;justify-content:center;cursor:pointer;opacity:0;transition:opacity 0.2s ease;">'
                + '<img src="' + escapeHtml(src) + '" style="max-width:90vw;max-height:90vh;border-radius:12px;box-shadow:0 16px 48px rgba(0,0,0,0.4);object-fit:contain;" alt="图片预览">'
                + '<div style="position:absolute;top:16px;right:16px;">'
                + '<span class="ai-lightbox-hint" style="color:rgba(255,255,255,0.6);font-size:12px;background:rgba(0,0,0,0.5);padding:4px 10px;border-radius:999px;pointer-events:none;">点击任意处关闭</span>'
                + '</div></div>');
            $('body').append($overlay);
            requestAnimationFrame(function () { $overlay.css({opacity: '1'}); });
            $overlay.on('click', function () {
                $overlay.css({opacity: '0'});
                setTimeout(function () { $overlay.remove(); }, 200);
            });
        });

        // 点击关闭按钮 → 移除图片
        $(document).on('click', '.ai-image-remove-btn', function (e) {
            e.stopPropagation();
            e.preventDefault();
            fwUploadedImageData = null;
            $('#aiFloatingImageUploadBtn').removeClass('has-image')
                .find('.ai-floating-image-preview').hide().attr('src', '');
        });

        // Floating voice recording
        var fwMediaRecorder = null;
        var fwVoiceChunks = [];
        var fwIsRecording = false;
        var fwRecordStartTime = 0;
        var fwRecordTimerInterval = null;

        $('#aiFloatingVoiceRecordBtn').on('click', function () {
            var $btn = $(this);
            if (fwIsRecording) {
                fwIsRecording = false;
                $btn.removeClass('recording').find('i').removeClass('stop').addClass('microphone');
                $btn.attr('title', '语音输入');
                if (fwRecordTimerInterval) { clearInterval(fwRecordTimerInterval); fwRecordTimerInterval = null; }
                if (fwMediaRecorder && fwMediaRecorder.state !== 'inactive') { fwMediaRecorder.stop(); }
            } else {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    appendMessage('assistant', 'AI 助手', '当前浏览器不支持语音输入', [], { scrollTop: true });
                    return;
                }
                navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
                    fwMediaRecorder = new MediaRecorder(stream);
                    fwVoiceChunks = [];
                    fwIsRecording = true;
                    fwRecordStartTime = Date.now();
                    $btn.addClass('recording').find('i').removeClass('microphone').addClass('stop');

                    fwMediaRecorder.ondataavailable = function (e) { fwVoiceChunks.push(e.data); };
                    fwMediaRecorder.onstop = function () {
                        stream.getTracks().forEach(function (t) { t.stop(); });
                        if (fwVoiceChunks.length > 0) {
                            var currentVal = $.trim($questionInput.val());
                            var prefix = currentVal ? (currentVal + '\n') : '';
                            var sec = Math.round((Date.now() - fwRecordStartTime) / 1000);
                            var m = Math.floor(sec / 60);
                            var s = sec % 60;
                            var durStr = String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
                            $questionInput.val(prefix + '[语音片段 ' + durStr + '] （语音转文字功能待后端接入ASR）');
                        }
                        fwVoiceChunks = [];
                    };
                    fwMediaRecorder.start();
                    fwRecordTimerInterval = setInterval(function () {
                        var sec = Math.floor((Date.now() - fwRecordStartTime) / 1000);
                        $btn.attr('title', '录音中... ' + String(Math.floor(sec / 60)).padStart(2, '0') + ':' + String(sec % 60).padStart(2, '0'));
                    }, 500);
                }).catch(function (err) {
                    appendMessage('assistant', 'AI 助手', '无法访问麦克风：' + (err.message || '权限被拒绝'), [], { scrollTop: true });
                });
            }
        });

        /* ===== Loading Indicator (Floating) ===== */
        var fwLoadingTimerInterval = null;
        var fwLoadingStartTime = 0;

        function showLoading() {
            hideLoading();
            fwLoadingStartTime = Date.now();
            var $loading = $('<div class="ai-floating-loading" id="aiFloatingLoading">'
                + '<div style="display:flex;align-items:center;gap:10px;padding:14px;border-radius:12px;background:#f8fafc;border:1px solid #e2e8f0;">'
                + '<span class="fw-loading-dots"><i class="spinner loading icon" style="color:#14b8a6;font-size:16px;"></i></span>'
                + '<span class="fw-loading-text" style="font-size:13px;color:#64748b;">正在思考中...</span>'
                + '<span class="fw-loading-timer" style="font-size:11px;color:#94a3b8;margin-left:auto;white-space:nowrap;"></span>'
                + '</div></div>');
            $messageList.append($loading);
            $('#aiFloatingMessageSection').removeClass('is-collapsed');
            scrollToBottom();
            fwLoadingTimerInterval = setInterval(function () {
                var elapsed = Math.round((Date.now() - fwLoadingStartTime) / 1000);
                $loading.find('.fw-loading-timer').text(elapsed + 's');
                if (elapsed >= 30 && elapsed % 10 === 0) {
                    $loading.find('.fw-loading-text').text('AI 正在深入分析，请稍候...');
                }
            }, 1000);
        }

        function hideLoading() {
            if (fwLoadingTimerInterval) {
                clearInterval(fwLoadingTimerInterval);
                fwLoadingTimerInterval = null;
            }
            $('#aiFloatingLoading').remove();
        }

        function showTimeoutMessage() {
            var elapsed = Math.round((Date.now() - fwLoadingStartTime) / 1000);
            var timeoutSeconds = aiTimeout / 1000;
            hideLoading();
            appendMessage('assistant', 'AI 助手',
                '抱歉，AI 响应超时（已等待 ' + elapsed + ' 秒，超时阈值 ' + timeoutSeconds + ' 秒）。可能原因：\n\n'
                + '1. 大模型服务负载较高或网络延迟较大\n'
                + '2. 问题涉及大量数据查询需要更长时间\n'
                + '3. 服务端处理出现异常\n\n'
                + '建议：可以稍后重试，或换一个更具体的问题。', [], {scrollTop: true});
            saveHistory({
                role: 'assistant',
                title: 'AI 助手',
                message: '[请求超时] 等待 ' + elapsed + 's / 阈值 ' + timeoutSeconds + 's',
                actions: []
            });
        }

        /* ===== Copy Button ===== */
        function bindCopyButton($btn, text) {
            $btn.on('click', function (e) {
                e.stopPropagation();
                var plainText = (text || '').replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '').trim();
                if (!plainText) return;
                if (navigator.clipboard !== undefined) {
                    navigator.clipboard.writeText(plainText).then(function () {
                        $(this).css({color:'#16a34a','border-color':'rgba(22,163,77,0.4)'}).html('<i class="check icon"></i>');
                        setTimeout(function () { $(this).css({'color':'#94a3b8','border-color':'rgba(203,213,225,0.6)'}).html('<i class="copy icon"></i>'); }, 1500);
                    }.bind(this)).catch(function () {});
                } else {
                    var ta = document.createElement('textarea');
                    ta.value = plainText;
                    document.body.appendChild(ta); ta.select();
                    try { document.execCommand('copy'); } catch(e2) {}
                    document.body.removeChild(ta);
                    $(this).css({color:'#16a34a','border-color':'rgba(22,163,77,0.4)'}).html('<i class="check icon"></i>');
                    setTimeout(function () { $(this).css({'color':'#94a3b8','border-color':'rgba(203,213,225,0.6)'}).html('<i class="copy icon"></i>'); }, 1500);
                }
            });
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
            compactQuickLinkLayout();
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
            // 允许纯图片发送
            if (!question && !fwUploadedImageData) {
                $questionInput.focus();
                return;
            }

            fwPendingQuestion = question || '[图片提问]';

            setHidden(false);
            setPanelOpen(true);

            // 构建用户消息（含图片）
            var userMsgHtml = question;
            var saveMsg = question;
            if (fwUploadedImageData) {
                userMsgHtml += (question ? '<br>' : '') + '<img src="' + escapeHtml(fwUploadedImageData)
                    + '" style="max-width:280px;max-height:200px;border-radius:10px;margin-top:6px;" alt="上传的图片">';
                saveMsg += (saveMsg ? ' [附图]' : '[图片]');
            }
            appendMessage('user', '你', userMsgHtml, [], { isHtml: true });
            saveHistory({
                role: 'user',
                title: '你',
                message: saveMsg,
                actions: []
            });

            $questionInput.val('');
            setState('思考中...', true);

            // 构建请求数据
            var requestData = { question: question || '[图片提问]', pageContext: buildPageContextPayload() };
            if (fwUploadedImageData) {
                requestData.imageData = fwUploadedImageData;
            }

            // 清除已上传的图片
            fwUploadedImageData = null;
            $('#aiFloatingImageUploadBtn').removeClass('has-image')
                .find('.ai-floating-image-preview').hide().attr('src', '');

            fwSetSendButtonToStop();
            showLoading();

            fwCurrentAjaxRequest = $.ajax({
                url: askUrl,
                type: 'POST',
                dataType: 'json',
                timeout: aiTimeout, // 超时时间（毫秒），与后端 ai.llm.timeout 统一
                data: requestData
            }).done(function (response) {
                hideLoading();
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
            }).fail(function (jqXHR, textStatus) {
                hideLoading();
                if (textStatus === 'abort') {
                    fwShowStoppedMessage(fwPendingQuestion);
                    return;
                }
                if (textStatus === 'timeout') {
                    showTimeoutMessage();
                } else {
                    appendMessage('assistant', 'AI 助手', '请求失败了（' + textStatus + '），请稍后再试，或者换一个更具体的问题。', []);
                    saveHistory({
                        role: 'assistant',
                        title: 'AI 助手',
                        message: '请求失败 - ' + textStatus,
                        actions: []
                    });
                }
            }).always(function () {
                fwCurrentAjaxRequest = null;
                fwResetSendButton();
                setState('就绪', false);
            });
        }

        initMascot();
        applyMascotTheme();
        updateUndoLayoutButton();
        setLayoutLocked(localStorage.getItem(layoutLockedKey) === '1');
        setHidden(sessionStorage.getItem(hiddenKey) === '1');
        setPanelOpen(sessionStorage.getItem(panelKey) === '1' && sessionStorage.getItem(hiddenKey) !== '1');
        getPanelMinimumSize();
        restorePanelSize();
        initLayoutCustomizer();
        initPosition();
        initRestoreBtnPosition();
        bindLivePageSignals();
        initHistory();

        $launcher.on('mousedown', beginDrag);
        $panel.on('mousedown', beginResize);
        $panel.on('mousedown', '.ai-floating-section-header, .ai-floating-context-status, .ai-floating-compose', beginLayoutDrag);
        $panel.on('mousedown', '.ai-floating-layout-item', function (event) {
            if ($(event.target).closest('.ai-floating-section-header, .ai-floating-context-status, .ai-floating-compose, button, a, textarea, input').length) {
                return;
            }
            beginLayoutResize(event);
        });
        $panel.on('mousedown', '.ai-floating-layout-resizer', function (event) {
            beginLayoutResize(event);
        });
        $panel.on('mousemove', '.ai-floating-layout-item', function (event) {
            if (isLayoutLocked() || layoutResizeState) {
                return;
            }
            var $item = $(this);
            $item.removeClass('resize-n resize-s resize-e resize-w resize-ne resize-nw resize-se resize-sw');
            var direction = detectInnerResizeDirection($item, event);
            if (direction) {
                $item.addClass('resize-' + direction);
            }
        });
        $panel.on('mouseleave', '.ai-floating-layout-item', function () {
            if (!layoutResizeState) {
                $(this).removeClass('resize-n resize-s resize-e resize-w resize-ne resize-nw resize-se resize-sw');
            }
        });
        $panel.on('dblclick', function (event) {
            if ($(event.target).closest('.ai-floating-panel-tools, textarea, button, a, input, .ai-floating-section-body').length) {
                return;
            }
            resetPanelSize();
        });
        $panel.on('mousemove', function (event) {
            if ($root.hasClass('is-resizing')) {
                return;
            }
            var cursor = getResizeCursor(detectResizeDirection(event));
            $panel.css('cursor', cursor || 'default');
        });
        $panel.on('mouseleave', function () {
            if (!$root.hasClass('is-resizing')) {
                $panel.css('cursor', 'default');
            }
        });
        $(window).on('resize.aiFloatingPanel', refreshResizeConstraints);

        $('#aiFloatingResetLayout').on('click', function () {
            resetPanelLayout();
        });

        $('#aiFloatingUndoLayout').on('click', function () {
            restoreUndoPanelLayout();
        });

        $('#aiFloatingToggleLayoutLock').on('click', function () {
            setLayoutLocked(!isLayoutLocked());
        });

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
            if ($sendButton.hasClass('ai-floating-stop-btn')) {
                fwStopGeneration();
                return;
            }
            sendQuestion();
        });

        $questionInput.on('keydown', function (event) {
            if (event.keyCode === 13 && !event.shiftKey) {
                event.preventDefault();
                sendQuestion();
            }
        });

        // ===== 粘贴图片支持 (Floating) =====
        $questionInput.on('paste', function (e) {
            var clipboardData = e.originalEvent.clipboardData || window.clipboardData;
            if (!clipboardData) return;
            var items = clipboardData.items;
            if (!items) return;
            for (var i = 0; i < items.length; i++) {
                if (items[i].type.indexOf('image') !== -1) {
                    e.preventDefault();
                    var file = items[i].getAsFile();
                    if (file) fwHandleImageFile(file);
                    return;
                }
            }
        });

        $(window).on('resize', function () {
            applyPosition(getCurrentPosition());
            writeLocalJSON(positionKey, getCurrentPosition());
        });

        $(document).on('click', '.ai-floating-starter, .ai-floating-message-action, .ai-resume-btn', function () {
            sendQuestion($(this).data('question'));
        });

        $(document).on('click', '.ai-floating-chip-close', function (e) {
            e.stopPropagation();
            var $chip = $(this).closest('.ai-floating-context-chip');
            if ($chip.text().indexOf('当前筛选') !== -1) {
                liveSignals.filters = [];
            } else if ($chip.text().indexOf('当前悬停') !== -1) {
                liveSignals.tableHover = '';
            } else if ($chip.text().indexOf('当前选中') !== -1) {
                liveSignals.tableSelection = '';
                if ($selectedRow && $selectedRow.length) {
                    $selectedRow.removeClass('ai-floating-row-selected');
                    $selectedRow = null;
                }
            }
            renderContextStatus();
        });

        $(document).on('click', '.ai-floating-section-header', function () {
            $(this).closest('.ai-floating-section').toggleClass('is-collapsed');
        });
    });
})(jQuery);

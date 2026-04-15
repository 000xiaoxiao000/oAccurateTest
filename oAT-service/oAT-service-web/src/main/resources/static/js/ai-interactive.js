/**
 * AI Interactive Workbench — 交互式工作台页面逻辑
 *
 * 仅在 #aiInteractivePage 存在时激活（通过 DOM 检测自动判断）。
 * 公共工具函数统一使用 window.AiUtils (ai-common.js)。
 *
 * 本文件仅保留工作台独有功能：
 *   - 多会话管理（CRUD/排序/搜索）
 *   - 打字机动画效果
 *   - 三灯信号系统 + 时间线
 *   - 能力卡片渲染
 *   - 工作台粒子/环境光特效
 */
(function ($) {
    var U = window.AiUtils; // 引用公共模块
    if (!U) { console.warn('[AI] ai-common.js 未加载'); return; }

    $(function () {
        var $root = $('#aiInteractivePage');
        if ($root.length === 0) { return; }

        /* ===== 配置 ===== */
        var askUrl = $root.data('ask-url');
        var assistantName = $root.data('assistant-name') || 'AI';
        var mascotPrimary = $root.data('mascot-primary') || '#00b5ad';
        var aiTimeout = ($root.data('ai-timeout') || 300) * 1000;
        var projectId = $root.data('project-id') || 'default';
        var projectName = $root.data('project-name') || '当前项目';

        /* ===== 存储 Key ===== */
        var legacyHistoryKey = 'ai-interactive-history:' + projectId;
        var sessionsKey = 'ai-interactive-sessions:' + projectId;
        var activeSessionKey = 'ai-interactive-active-session:' + projectId;
        var sessionSortKey = 'ai-interactive-session-sort:' + projectId;
        var timelineExpandedKey = 'ai-interactive-timeline-expanded:' + projectId;
        var sessionMigrationMarkerKey = 'ai-interactive-session-migrated:' + projectId;
        var sessionStateUrl = askUrl.replace(/\/ask$/, '/sessionState');
        var sessionStateSyncInFlight = false;
        var serverSessionState = $root.attr('data-session-state') || '';

        /* ===== DOM 引用 ===== */
        var $messageList = $('#aiMessageList');
        var $chatAnchorsList = $('#aiChatAnchorsList');
        var $floatingAnchors = $('#aiFloatingAnchors');
        var $floatingAnchorsList = $('#aiFloatingAnchorsList');
        var $floatingAnchorTooltip = $('#aiFloatingAnchorTooltip');
        var $anchorSearchInput = $('#aiAnchorSearchInput');
        var $anchorSearchClear = $('#aiAnchorSearchClear');
        var $anchorSearchMeta = $('#aiAnchorSearchMeta');
        var $questionInput = $('#aiQuestionInput');
        var $sendButton = $('#aiSendButton');
        var $requestState = $('#aiRequestState');
        var currentAjaxRequest = null;
        var pendingQuestion = '';
        var $followUpList = $('#aiFollowUpList');
        var $quickLinkList = $('#aiQuickLinkList');
        var $signalLights = $('#aiSignalLights');
        var $timelineList = $('#aiTimelineList');
        var $timelinePanel = $('#aiTimelinePanel');
        var $timelineSummary = $('#aiTimelineSummary');
        var $timelineToggleButton = $('#aiTimelineToggleButton');
        var $sessionList = $('#aiSessionList');
        var $newSessionButton = $('#aiNewSessionButton');
        var $sessionSearchInput = $('#aiSessionSearchInput');
        var $sessionSortSelect = $('#aiSessionSortSelect');

        /* ===== 状态 ===== */
        var sessions = [];
        var activeSessionId = null;
        var loadingTimerInterval = null;
        var loadingStartTime = 0;
        var uploadedImageData = null;
        var timelineState = {
            expanded: false,
            hasUserPreference: false
        };
        var anchorFilterMode = 'all';
        var anchorSearchKeyword = '';
        var anchorKeyboardIndex = -1;
        var expandedAnchorTextMap = {};
        var anchorScrollState = {
            programmatic: false,
            currentAnchorId: null,
            suppressUntil: 0
        };

        /* ============================================================
         *  初始化
         * ============================================================ */

        // Mascot Canvas（使用公共工厂，支持状态动画）
        var mascotCanvas = U.createMascotCanvas({
            canvas: document.getElementById('aiMascotCanvas'),
            primaryColor: mascotPrimary,
            particleCount: 10,
            orbitRadius: 60
        });
        mascotCanvas.start();

        // 全局 Lightbox（两种预览图选择器都绑定）
        U.initGlobalLightbox($, '.ai-image-preview, .ai-floating-image-preview');

        // 图片上传处理器
        var imgHandler = U.createImageHandler({
            $btn: $('#aiImageUploadBtn'),
            inputId: 'aiImageInput',
            previewClass: '.ai-image-preview',
            onLoaded: function (dataUrl, file) { uploadedImageData = dataUrl; U.showToast('图片已添加：' + (file.name || '粘贴图片'), 'success'); },
            onRemoved: function () { uploadedImageData = null; }
        });
        imgHandler.init($);

        // 语音录制处理器
        U.createVoiceRecorder({ $btn: $('#aiVoiceRecordBtn'), $questionInput: $questionInput }).init($);

        // 粘贴图片支持
        U.enablePasteImage($questionInput, function (file) { imgHandler.handleFile(file); });

        // 加载会话数据
        loadSessions();
        renderActiveSession();
        setTimelineExpanded(readTimelineExpanded());
        initWorkbenchAnimation();

        /* ============================================================
         *  工作台独有：环境光 / 粒子 / 波纹特效
         * ============================================================ */

        function initWorkbenchAnimation() {
            var $shell = $('.ai-workbench-shell');
            if (!$shell.length) return;

            var $ambient = $('<div class="ai-workbench-ambient"></div>');
            $shell.prepend($ambient);
            $ambient.css({
                position: 'absolute', width: '400px', height: '400px', borderRadius: '50%',
                background: 'radial-gradient(circle, rgba(' + U.hexToRgb(mascotPrimary) + ',0.08) 0%, transparent 70%)',
                pointerEvents: 'none', zIndex: 0, opacity: '0',
                transition: 'opacity 0.4s ease, left 0.15s ease-out, top 0.15s ease-out'
            });

            $(document).on('mousemove.aiAmbient', function (e) {
                var off = $shell.offset();
                var x = e.clientX - off.left - 200;
                var y = e.clientY - off.top - 200;
                if (e.clientX >= off.left && e.clientX <= off.left + $shell.outerWidth() &&
                    e.clientY >= off.top && e.clientY <= off.top + $shell.outerHeight()) {
                    $ambient.css({ left: x, top: y, opacity: 1 });
                } else {
                    $ambient.css({ opacity: 0 });
                }
            });

            $sendButton.on('mousedown', createRipple);
            $(document).on('mouseenter', '.ai-dock-chip', function () {
                $(this).css({
                    boxShadow: '0 0 20px ' + U.hexToRgba(mascotPrimary, 0.25) + ', inset 0 0 12px ' + U.hexToRgba(mascotPrimary, 0.06),
                    borderColor: U.hexToRgba(mascotPrimary, 0.35)
                });
            }).on('mouseleave', '.ai-dock-chip', function () {
                $(this).css({ boxShadow: '', borderColor: '' });
            });
        }

        function createRipple() {
            var ripple = $('<span class="ai-btn-ripple"></span>');
            var rect = $sendButton[0].getBoundingClientRect();
            var size = Math.max(rect.width, rect.height) * 2;
            var clientEvent = window.event || event;
            var x = clientEvent.clientX - rect.left - size / 2;
            var y = clientEvent.clientY - rect.top - size / 2;
            ripple.css({
                position: 'absolute', width: size + 'px', height: size + 'px',
                borderRadius: '50%', background: 'rgba(' + U.hexToRgb(mascotPrimary) + ',0.25)',
                transform: 'scale(0)', left: x + 'px', top: y + 'px',
                pointerEvents: 'none',
                animation: 'aiBtnRipple 0.5s ease-out forwards'
            });
            $sendButton.css({ position: 'relative', overflow: 'hidden' }).append(ripple);
            setTimeout(function () { ripple.remove(); }, 500);
        }

        /* ============================================================
         *  工作台独有：三灯信号系统
         * ============================================================ */

        function setSignalState(state) {
            $signalLights.find('.ai-signal-light').removeClass('active');
            var $target = $signalLights.find('[data-state="' + state + '"]');
            if ($target.length) {
                $target.addClass('active');
                $target.find('.dot').css({ transform: 'scale(1.6)', transition: 'transform 0.2s ease' });
                setTimeout(function () {
                    $target.find('.dot').css({ transform: 'scale(1)', transition: 'transform 0.3s ease' });
                }, 200);
            }
        }

        /* ============================================================
         *  工作台独有：请求状态控制
         * ============================================================ */

        function setRequestState(text, disabled) {
            $requestState.text(text);
            $sendButton.prop('disabled', disabled);
            $questionInput.prop('disabled', disabled);
        }

        function setSendButtonToStop() {
            $sendButton.text('停止').addClass('ai-stop-btn').removeClass('teal').prop('disabled', false);
        }

        function resetSendButton() {
            $sendButton.text('发送').removeClass('ai-stop-btn').addClass('teal');
        }

        /* ============================================================
         *  工作台独有：打字机效果
         * ============================================================ */

        function getScrollContainer() {
            return null;
        }

        function markScrollContainerDebug(container) {
            return container;
        }

        function scrollMessageListToBottom(force) {
            var container = getScrollContainer();
            if (container) {
                markScrollContainerDebug(container);
                if (!force && anchorScrollState.programmatic) return;
                container.scrollTop = container.scrollHeight;
                return;
            }
            $('#aiMessageList, #aiChatPanel').removeClass('ai-scroll-debug-target');
            if (!force && anchorScrollState.programmatic) return;
            var messageListNode = $messageList[0];
            if (messageListNode) {
                messageListNode.scrollIntoView({ block: 'end', behavior: 'auto' });
            }
            window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'auto' });
        }

        function typewriterText($target, message, done) {
            var safeMessage = U.escapeHtml(message || '');
            var index = 0;
            function tick() {
                index = Math.min(index + 2, safeMessage.length);
                $target.html(safeMessage.substring(0, index).replace(/\n/g, '<br>') + '<span class="ai-typing-cursor"></span>');
                scrollMessageListToBottom(false);
                if (index >= safeMessage.length) {
                    $target.html(safeMessage.replace(/\n/g, '<br>'));
                    if (done) done();
                    return;
                }
                setTimeout(tick, 12);
            }
            tick();
        }

        /* ============================================================
         *  工作台独有：消息渲染（含打字机动画）
         * ============================================================ */

        function appendMessage(role, title, message, actions, options) {
            options = options || {};
            var avatar = role === 'assistant' ? assistantName.substring(0, 1) : '我';
            var shouldPulseLatest = !!options.pulseLatest;
            var cardContentClass = 'ai-message-content' + (options.collapsible ? ' collapsible expanded' : '');
            var contentHtml = options.animate
                ? '<div class="ai-message-text"></div>'
                : (options.isHtml ? message : U.formatMessage(message));
            var toggleHtml = options.collapsible
                ? '<button type="button" class="ai-message-toggle" aria-expanded="true">收起</button>'
                : '';
            var anchorAttrs = options.anchorId ? ' id="' + U.escapeHtml(options.anchorId) + '" data-anchor-id="' + U.escapeHtml(options.anchorId) + '"' : '';
            var sectionClass = 'ai-message-section' + (options.isAnchorTarget ? ' ai-message-section-anchor' : '');
            var qaGroupStartClass = options.startQaGroup ? ' ai-qa-group ai-qa-group-start' : '';
            var qaGroupEndClass = options.endQaGroup ? ' ai-qa-group-end' : '';
            var $node = $('<div class="' + sectionClass + qaGroupStartClass + qaGroupEndClass + '"' + anchorAttrs + '>'
                + '<div class="ai-message ' + role + '">'
                + '<div class="ai-message-avatar">' + U.escapeHtml(avatar) + '</div>'
                + '<div class="ai-message-body">'
                + '<div class="ai-message-head">'
                + '<div class="ai-message-name">' + U.escapeHtml(title) + '</div>'
                + toggleHtml
                + '</div>'
                + '<div class="ai-message-card" style="position:relative">'
                + '<div class="' + cardContentClass + '">' + contentHtml + '</div>'
                + (options.animate ? '' : '<button class="ai-message-copy" title="复制"><i class="copy icon"></i></button>' + renderActions(actions))
                + '</div></div></div></div>');
            $messageList.append($node);
            if (!options.animate) {
                U.bindCopyButton($node.find('.ai-message-copy'), message);
            }
            scrollMessageListToBottom(false);
            if (options.animate) {
                var $card = $node.find('.ai-message-card');
                var $content = $card.find('.ai-message-content');
                typewriterText($content.find('.ai-message-text'), message, function () {
                    var extra = actions && actions.length
                        ? ('<button class="ai-message-copy" title="复制"><i class="copy icon"></i></button>' + renderActions(actions))
                        : '<button class="ai-message-copy" title="复制"><i class="copy icon"></i></button>';
                    $card.append(extra);
                    U.bindCopyButton($card.find('.ai-message-copy'), message);
                    collapseHistoricalQaMessages({ scrollLatest: true });
                    pulseLatestQaFocus();
                });
                return;
            }
            collapseHistoricalQaMessages({ scrollLatest: false });
        }

        function renderActions(actions) {
            if (!actions || !actions.length) return '';
            var html = '<div class="ai-message-actions">';
            $.each(actions, function (_, action) {
                html += '<button class="ai-action-btn" data-question="' + U.escapeHtml(action) + '">' + U.escapeHtml(action) + '</button>';
            });
            return html + '</div>';
        }

        function createQuestionAnchor(question, index, meta) {
            var normalizedIndex = typeof index === 'number' && index >= 0
                ? index
                : (meta && typeof meta.index === 'number' ? meta.index : 0);
            return {
                id: 'ai-question-anchor-' + (normalizedIndex + 1),
                label: 'Q' + (normalizedIndex + 1),
                question: question || '未命名提问',
                answered: !!(meta && meta.answered),
                responseTime: meta && meta.responseTime ? meta.responseTime : 0,
                shareUrl: window.location.pathname + '#'+ ('ai-question-anchor-' + (normalizedIndex + 1))
            };
        }

        function formatResponseTime(ms) {
            if (!ms || ms <= 0) return '';
            if (ms < 1000) return ms + 'ms';
            return (ms / 1000).toFixed(ms >= 10000 ? 0 : 1) + 's';
        }

        function getQuestionAnchors(session) {
            var anchors = [];
            var count = 0;
            var pendingUser = null;
            $.each((session && session.history) || [], function (_, item) {
                if (item.role === 'user') {
                    pendingUser = item;
                    anchors.push(createQuestionAnchor(item.message, count, { index: count, answered: false, responseTime: 0 }));
                    count++;
                    return;
                }
                if (item.role === 'assistant' && pendingUser && anchors.length) {
                    anchors[anchors.length - 1].answered = true;
                    anchors[anchors.length - 1].responseTime = item.responseTime || 0;
                    pendingUser = null;
                }
            });
            return anchors;
        }

        function renderFloatingAnchors(anchors) {
            if (!$floatingAnchorsList.length) return;
            if (!anchors || !anchors.length) {
                $floatingAnchors.removeClass('visible');
                $floatingAnchorsList.empty();
                $floatingAnchorTooltip.removeClass('visible').attr('aria-hidden', 'true').empty();
                return;
            }
            var trackHeight = Math.max(180, Math.min(window.innerHeight - 220, 420));
            var maxTop = Math.max(0, trackHeight - 16);
            var anchorPositions = [];
            var lastTop = -18;
            $.each(anchors, function (_, anchor) {
                var $target = $('#' + anchor.id);
                var ratio = anchors.length <= 1 ? 0 : (_ / (anchors.length - 1));
                if ($target.length) {
                    var targetRect = $target[0].getBoundingClientRect();
                    var absoluteTop = (window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0) + targetRect.top;
                    var pageHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, window.innerHeight);
                    ratio = Math.max(0, Math.min(1, absoluteTop / Math.max(1, pageHeight - window.innerHeight)));
                }
                var top = Math.round(maxTop * ratio);
                if (top - lastTop < 18) {
                    top = Math.min(maxTop, lastTop + 18);
                }
                lastTop = top;
                anchorPositions.push({ anchor: anchor, top: top, ratio: ratio });
            });
            if (anchorPositions.length > 1) {
                for (var i = anchorPositions.length - 2; i >= 0; i--) {
                    if (anchorPositions[i + 1].top - anchorPositions[i].top < 18) {
                        anchorPositions[i].top = Math.max(0, anchorPositions[i + 1].top - 18);
                    }
                }
            }
            var html = '';
            $.each(anchorPositions, function (_, item) {
                var anchor = item.anchor;
                var statusClass = anchor.answered ? 'answered' : 'pending';
                var durationText = formatResponseTime(anchor.responseTime) || '等待回复中';
                html += '<button type="button" class="ai-floating-anchor-dot ' + statusClass + '" style="top:' + item.top + 'px" data-anchor-ratio="' + item.ratio + '" data-anchor-id="' + U.escapeHtml(anchor.id) + '" data-anchor-label="' + U.escapeHtml(anchor.label) + '" data-anchor-question="' + U.escapeHtml(anchor.question) + '" data-anchor-status="' + U.escapeHtml(anchor.answered ? '已回复' : '待回复') + '" data-anchor-duration="' + U.escapeHtml(durationText) + '" aria-label="' + U.escapeHtml(anchor.label + ' ' + anchor.question) + '"></button>';
            });
            $floatingAnchorsList.css('height', trackHeight + 'px').html(html);
            $floatingAnchors.addClass('visible');
        }

        function findClosestFloatingAnchorId(pageY) {
            if (!$floatingAnchorsList.length) return null;
            var $dots = $floatingAnchorsList.find('.ai-floating-anchor-dot');
            if (!$dots.length) return null;
            var listRect = $floatingAnchorsList[0].getBoundingClientRect();
            var relativeY = pageY - (window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0) - listRect.top;
            var closestId = null;
            var minDistance = Infinity;
            $dots.each(function () {
                var $dot = $(this);
                var dotTop = parseFloat($dot.css('top')) || 0;
                var dotCenter = dotTop + ($dot.outerHeight() || 28) / 2;
                var distance = Math.abs(dotCenter - relativeY);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestId = $dot.data('anchor-id');
                }
            });
            return closestId;
        }

        function previewFloatingAnchor(anchorId) {
            if (!anchorId) return;
            applyAnchorPreview(anchorId);
            setTimeout(function () {
                clearAnchorPreview();
            }, 220);
        }

        function positionFloatingAnchorTooltip($dot) {
            if (!$dot || !$dot.length || !$floatingAnchorTooltip.length) return;
            var dotRect = $dot[0].getBoundingClientRect();
            var hostRect = $floatingAnchors[0].getBoundingClientRect();
            var tooltipHeight = $floatingAnchorTooltip.outerHeight() || 0;
            var tooltipTop = dotRect.top - hostRect.top - (tooltipHeight / 2) + (dotRect.height / 2);
            var trackHeight = $floatingAnchorsList.outerHeight() || 0;
            tooltipTop = Math.max(0, Math.min(trackHeight - tooltipHeight, tooltipTop));
            $floatingAnchorTooltip.css({ top: tooltipTop + 'px' });
        }

        function highlightSearchKeyword(text, keyword) {
            var safeText = U.escapeHtml(text || '');
            if (!keyword) return safeText;
            var escapedKeyword = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            return safeText.replace(new RegExp(escapedKeyword, 'ig'), function (match) {
                return '<mark class="ai-anchor-keyword-mark">' + match + '</mark>';
            });
        }

        function renderChatAnchors(session, options) {
            options = options || {};
            if (!$chatAnchorsList.length) return;
            var previousStates = {};
            $chatAnchorsList.find('.ai-chat-anchor-item').each(function () {
                previousStates[$(this).data('anchor-id')] = {
                    answered: $(this).hasClass('answered')
                };
            });
            var anchors = getQuestionAnchors(session);
            var visibleAnchors = anchors.filter(function (anchor) {
                var matchesFilter = anchorFilterMode === 'all' || !anchor.answered;
                var matchesSearch = !anchorSearchKeyword || (anchor.question || '').toLowerCase().indexOf(anchorSearchKeyword) !== -1;
                return matchesFilter && matchesSearch;
            });
            if ($anchorSearchMeta.length) {
                $anchorSearchMeta.text(anchorSearchKeyword ? ('匹配 ' + visibleAnchors.length + ' 条') : ('共 ' + anchors.length + ' 条'));
            }
            if ($anchorSearchClear.length) {
                $anchorSearchClear.toggleClass('visible', !!anchorSearchKeyword);
            }
            if (!visibleAnchors.length) {
                anchorKeyboardIndex = -1;
                renderFloatingAnchors([]);
                $chatAnchorsList.html('<div class="ai-chat-anchor-empty">' + (anchorFilterMode === 'pending' ? '当前没有待回复问题。' : anchorSearchKeyword ? '没有匹配的提问锚点。' : '当前还没有提问，先发起一个问题吧。') + '</div>');
                return;
            }
            var html = '';
            $.each(visibleAnchors, function (_, anchor) {
                var statusClass = anchor.answered ? 'answered' : 'pending';
                var statusText = anchor.answered ? '已回复' : '待回复';
                var durationText = formatResponseTime(anchor.responseTime);
                var expandedText = !!expandedAnchorTextMap[anchor.id];
                html += '<div class="ai-chat-anchor-entry">'
                    + '<button type="button" class="ai-chat-anchor-item ' + statusClass + (expandedText ? ' expanded-text' : '') + '" data-anchor-id="' + U.escapeHtml(anchor.id) + '" title="' + U.escapeHtml(anchor.question) + '">'
                    + '<div class="ai-chat-anchor-top">'
                    + '<span class="ai-chat-anchor-index">' + U.escapeHtml(anchor.label) + '</span>'
                    + '<span class="ai-chat-anchor-status ' + statusClass + '"><span class="ai-chat-anchor-status-dot ' + statusClass + '"></span>' + U.escapeHtml(statusText) + '</span>'
                    + '</div>'
                    + '<span class="ai-chat-anchor-text">' + highlightSearchKeyword(anchor.question, anchorSearchKeyword) + '</span>'
                    + '<span class="ai-chat-anchor-duration">' + U.escapeHtml(durationText || '等待回复中') + '</span>'
                    + '</button>'
                    + '<div class="ai-chat-anchor-actions">'
                    + '<button type="button" class="ai-chat-anchor-text-toggle" data-anchor-id="' + U.escapeHtml(anchor.id) + '">' + (expandedText ? '收起全文' : '展开全文') + '</button>'
                    + '<button type="button" class="ai-chat-anchor-link-btn" data-anchor-link="' + U.escapeHtml(anchor.shareUrl) + '" title="复制直达链接">复制链接</button>'
                    + '</div>'
                    + '</div>';
            });
            $chatAnchorsList.html(html);
            renderFloatingAnchors(visibleAnchors);
            anchorKeyboardIndex = visibleAnchors.length ? Math.min(anchorKeyboardIndex, visibleAnchors.length - 1) : -1;
            if (anchorKeyboardIndex >= 0) {
                $chatAnchorsList.find('.ai-chat-anchor-item').eq(anchorKeyboardIndex).addClass('keyboard-active');
            }
            $.each(visibleAnchors, function (_, anchor) {
                var prev = previousStates[anchor.id];
                if (anchor.answered && prev && !prev.answered) {
                    $chatAnchorsList.find('.ai-chat-anchor-item[data-anchor-id="' + anchor.id + '"]').addClass('just-answered');
                }
            });
            // 仅在非静默模式下同步滚动状态
            if (!options.silent) {
                syncActiveAnchorByScroll();
            }
        }

        function centerAnchorItemInView($item, behavior) {
            if (!$item || !$item.length || !$chatAnchorsList.length) return;
            var container = $chatAnchorsList[0];
            var item = $item[0];
            var targetLeft = item.offsetLeft - (container.clientWidth - item.offsetWidth) / 2;
            var maxLeft = Math.max(0, container.scrollWidth - container.clientWidth);
            var nextLeft = Math.max(0, Math.min(maxLeft, Math.round(targetLeft)));
            if (Math.abs(container.scrollLeft - nextLeft) <= 2) return;
            container.scrollTo({ left: nextLeft, behavior: behavior || 'smooth' });
        }

        function setActiveAnchor(anchorId) {
            $('.ai-chat-anchor-item.active').removeClass('active');
            $('.ai-floating-anchor-dot.active').removeClass('active');
            $('.ai-message-section-anchor.is-active').removeClass('is-active');
            if (anchorScrollState.currentAnchorId !== anchorId) {
                clearAnchorPreview();
            }
            anchorScrollState.currentAnchorId = anchorId || null;
            if (!anchorId) return;
            var $active = $chatAnchorsList.find('.ai-chat-anchor-item[data-anchor-id="' + anchorId + '"]');
            $active.addClass('active');
            $floatingAnchorsList.find('.ai-floating-anchor-dot[data-anchor-id="' + anchorId + '"]').addClass('active');
            $('#'+ anchorId).addClass('is-active');
            centerAnchorItemInView($active, 'smooth');
        }

        function syncActiveAnchorByScroll() {
            var container = getScrollContainer();
            if (anchorScrollState.programmatic && Date.now() < anchorScrollState.suppressUntil) return;
            var activeAnchorId = null;

            if (container) {
                var containerRect = container.getBoundingClientRect();
                var anchorActivationLine = containerRect.top + Math.max(24, Math.round(container.clientHeight * 0.18));
                $messageList.find('.ai-message-section-anchor').each(function () {
                    var $section = $(this);
                    var $card = $section.find('.ai-message-card').first();
                    var targetElement = $card.length ? $card[0] : this;
                    var targetRect = targetElement.getBoundingClientRect();
                    if (targetRect.top <= anchorActivationLine) {
                        activeAnchorId = $section.data('anchor-id');
                    }
                });
            } else {
                var viewportLine = Math.max(72, Math.round(window.innerHeight * 0.16));
                $messageList.find('.ai-message-section-anchor').each(function () {
                    var $section = $(this);
                    var $card = $section.find('.ai-message-card').first();
                    var targetElement = $card.length ? $card[0] : this;
                    var targetRect = targetElement.getBoundingClientRect();
                    if (targetRect.top <= viewportLine) {
                        activeAnchorId = $section.data('anchor-id');
                    }
                });
            }

            if (!activeAnchorId) {
                var $first = $messageList.find('.ai-message-section-anchor').first();
                activeAnchorId = $first.data('anchor-id');
            }
            if (anchorFilterMode === 'pending' && activeAnchorId) {
                var $filtered = $chatAnchorsList.find('.ai-chat-anchor-item[data-anchor-id="' + activeAnchorId + '"]');
                if (!$filtered.length) {
                    setActiveAnchor(null);
                    return;
                }
            }
            setActiveAnchor(activeAnchorId);
        }

        function clearAnchorPreview() {
            $('.ai-message-section-anchor.is-preview').removeClass('is-preview');
            $('.ai-chat-anchor-item.preview').removeClass('preview');
            $floatingAnchorsList.find('.ai-floating-anchor-dot.preview').removeClass('preview');
        }

        function applyAnchorPreview(anchorId) {
            if (!anchorId) return;
            var $target = $('#' + anchorId);
            clearAnchorPreview();
            if ($target.length) {
                $target.addClass('is-preview');
            }
            $chatAnchorsList.find('.ai-chat-anchor-item[data-anchor-id="' + anchorId + '"]').addClass('preview');
            $floatingAnchorsList.find('.ai-floating-anchor-dot[data-anchor-id="' + anchorId + '"]').addClass('preview');
        }

        function clearAnchorSearch() {
            if (!$anchorSearchInput.length) return;
            $anchorSearchInput.val('');
            anchorSearchKeyword = '';
            anchorKeyboardIndex = -1;
            clearAnchorPreview();
            renderChatAnchors(getActiveSession());
            $anchorSearchInput.focus();
        }

        function previewAnchorSelection(anchorId) {
            applyAnchorPreview(anchorId);
        }

        function moveAnchorKeyboardSelection(step) {
            var $items = $chatAnchorsList.find('.ai-chat-anchor-item');
            if (!$items.length) return;
            anchorKeyboardIndex = anchorKeyboardIndex < 0 ? 0 : Math.max(0, Math.min($items.length - 1, anchorKeyboardIndex + step));
            $items.removeClass('keyboard-active');
            var $active = $items.eq(anchorKeyboardIndex).addClass('keyboard-active');
            if ($active.length) {
                centerAnchorItemInView($active, 'smooth');
                previewAnchorSelection($active.data('anchor-id'));
            }
        }

        function copyAnchorLink(link) {
            if (!link) return;
            navigator.clipboard.writeText(window.location.origin + link).then(function () {
                U.showToast('直达链接已复制', 'success');
            }, function () {
                U.showToast('复制失败，请稍后重试', 'warning');
            });
        }

        function getAnchorScrollTop(anchorElement) {
            var container = getScrollContainer();
            var $card = $(anchorElement).find('.ai-message-card').first();
            var targetElement = $card.length ? $card[0] : anchorElement;
            var targetRect = targetElement.getBoundingClientRect();

            if (container) {
                var containerRect = container.getBoundingClientRect();
                var viewportOffset = Math.max(20, Math.round(container.clientHeight * 0.12));
                var nextTop = container.scrollTop + (targetRect.top - containerRect.top) - viewportOffset;
                return Math.max(0, Math.round(nextTop));
            }

            var pageTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
            var pageOffset = Math.max(72, Math.round(window.innerHeight * 0.1));
            return Math.max(0, Math.round(pageTop + targetRect.top - pageOffset));
        }

        function expandAnchorFromHash() {
            var hash = window.location.hash || '';
            if (!hash || hash.indexOf('#ai-question-anchor-') !== 0) return;
            var anchorId = hash.substring(1);
            if (anchorFilterMode === 'pending') {
                anchorFilterMode = 'all';
                $('.ai-anchor-filter-btn.active').removeClass('active');
                $('.ai-anchor-filter-btn[data-filter="all"]').addClass('active');
                renderChatAnchors(getActiveSession(), { silent: true });
            }
            scrollToAnchor(anchorId, { immediate: true });
        }

        function highlightAnchorTarget(anchorId) {
            var $target = $('#' + anchorId);
            if (!$target.length) return;
            $('.ai-message-section-anchor.is-preview').removeClass('is-preview');
            $('.ai-message-section-anchor.is-target').removeClass('is-target');
            $target.addClass('is-target');
            setTimeout(function () { $target.removeClass('is-target'); }, 1800);
        }

        function scrollToAnchor(anchorId, options) {
            options = options || {};
            var $target = $('#' + anchorId);
            if (!$target.length) return;
            var container = getScrollContainer();

            anchorScrollState.programmatic = true;
            anchorScrollState.currentAnchorId = anchorId;
            anchorScrollState.suppressUntil = Date.now() + (options.immediate ? 120 : 480);
            setActiveAnchor(anchorId);

            var $content = $target.find('.ai-message-content');
            if ($content.length && $content.hasClass('collapsed')) {
                updateMessageCollapseState($target.find('.ai-message'), true);
            }

            var nextTop = Math.round(getAnchorScrollTop($target[0]));

            if (container && container._aiAnchorScrollFrame) {
                cancelAnimationFrame(container._aiAnchorScrollFrame);
                container._aiAnchorScrollFrame = null;
            }

            var finishScroll = function () {
                anchorScrollState.programmatic = false;
                anchorScrollState.suppressUntil = 0;
                setActiveAnchor(anchorId);
                highlightAnchorTarget(anchorId);
            };

            if (!container) {
                window.scrollTo({ top: nextTop, behavior: options.immediate ? 'auto' : 'smooth' });
                updateLocationHash(anchorId);
                setTimeout(finishScroll, options.immediate ? 80 : 360);
                return;
            }

            var maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
            nextTop = Math.max(0, Math.min(maxScrollTop, nextTop));

            if (options.immediate) {
                container.scrollTop = nextTop;
                updateLocationHash(anchorId);
                finishScroll();
                return;
            }

            var startTop = container.scrollTop;
            var distance = nextTop - startTop;

            if (Math.abs(distance) <= 3) {
                container.scrollTop = nextTop;
                updateLocationHash(anchorId);
                finishScroll();
                return;
            }

            var duration = Math.min(220, Math.max(120, Math.abs(distance) * 0.2));
            var startAt = performance.now();
            var easeOutCubic = function (t) { return 1 - Math.pow(1 - t, 3); };

            var step = function (now) {
                var elapsed = now - startAt;
                var progress = Math.min(1, elapsed / duration);
                var eased = easeOutCubic(progress);

                container.scrollTop = Math.round(startTop + distance * eased);

                if (progress < 1) {
                    container._aiAnchorScrollFrame = requestAnimationFrame(step);
                } else {
                    container._aiAnchorScrollFrame = null;
                    container.scrollTop = nextTop;
                    finishScroll();
                }
            };

            container._aiAnchorScrollFrame = requestAnimationFrame(step);
            updateLocationHash(anchorId);
        }

        function updateLocationHash(anchorId) {
            if (window.location.hash !== '#' + anchorId) {
                if (window.history && window.history.replaceState) {
                    window.history.replaceState(null, '', '#' + anchorId);
                } else {
                    window.location.hash = anchorId;
                }
            }
        }

        function shouldCollapseMessage(message) {
            var text = $.trim((message || '').replace(/<[^>]*>/g, ''));
            return text.length > 140 || (message || '').indexOf('\n') !== -1;
        }

        function updateMessageCollapseState($message, expanded) {
            var $content = $message.find('.ai-message-content');
            var $button = $message.find('.ai-message-toggle');
            if (!$content.length || !$button.length) return;
            $content.toggleClass('expanded', expanded).toggleClass('collapsed', !expanded);
            $button.attr('aria-expanded', expanded ? 'true' : 'false').text(expanded ? '收起' : '展开');
        }

        function pulseLatestQaFocus() {
            var $latestStart = $messageList.find('.ai-message-section.ai-qa-group-start').last();
            if (!$latestStart.length) return;
            var $qaGroup = $latestStart.nextUntil('.ai-message-section.ai-qa-group-start').addBack();
            var $assistantMessage = $qaGroup.find('.ai-message.assistant').last();
            $messageList.find('.ai-message-section.is-latest-focus').removeClass('is-latest-focus');
            $messageList.find('.ai-message.assistant.is-current-assistant').removeClass('is-current-assistant');
            $qaGroup.addClass('is-latest-focus');
            if ($assistantMessage.length) {
                $assistantMessage.addClass('is-current-assistant');
                setTimeout(function () {
                    $assistantMessage.removeClass('is-current-assistant');
                }, 2200);
            }
            setTimeout(function () {
                $qaGroup.removeClass('is-latest-focus');
            }, 2600);
        }

        function collapseHistoricalQaMessages(options) {
            options = options || {};
            var $qaStarts = $messageList.find('.ai-message-section.ai-qa-group-start');
            if (!$qaStarts.length) return;
            var latestStartIndex = $qaStarts.length - 1;
            $qaStarts.each(function (index) {
                var $start = $(this);
                var $end = $start.nextUntil('.ai-message-section.ai-qa-group-start').addBack();
                var shouldExpand = index === latestStartIndex;
                $end.find('.ai-message').each(function () {
                    var $message = $(this);
                    if (!$message.find('.ai-message-toggle').length) return;
                    updateMessageCollapseState($message, shouldExpand);
                });
            });
            if (options.scrollLatest !== false) {
                scrollMessageListToBottom(false);
            }
        }

        function readTimelineExpanded() {
            var stored = sessionStorage.getItem(timelineExpandedKey);
            timelineState.hasUserPreference = stored !== null;
            return stored === 'true';
        }

        function persistTimelineState() {
            sessionStorage.setItem(timelineExpandedKey, timelineState.expanded ? 'true' : 'false');
        }

        function updateTimelineSummary(session) {
            var timeline = (session && session.timeline) || [];
            if (!timeline.length) {
                $timelineSummary.html('<span class="ai-timeline-summary-empty">暂无时间线记录</span>');
                return;
            }
            var latest = timeline[timeline.length - 1] || {};
            $timelineSummary.html('<div class="ai-timeline-summary-time">' + U.escapeHtml(latest.time || '') + '</div>'
                + '<div class="ai-timeline-summary-body">'
                + '<div class="ai-timeline-summary-title">' + U.escapeHtml(latest.title || '最近动态') + '</div>'
                + '<div class="ai-timeline-summary-desc">' + U.escapeHtml(latest.desc || '') + '</div>'
                + '</div>');
        }

        function setTimelineExpanded(expanded, options) {
            options = options || {};
            timelineState.expanded = expanded;
            if (options.persist !== false) {
                timelineState.hasUserPreference = true;
            }
            $timelinePanel.toggleClass('collapsed', !expanded);
            $timelineToggleButton.attr('aria-expanded', expanded ? 'true' : 'false').text(expanded ? '收起' : '展开');
            $timelineSummary.attr('aria-hidden', expanded ? 'true' : 'false');
            if (options.persist !== false) {
                persistTimelineState();
            }
        }

        /* ============================================================
         *  停止 / 恢复生成
         * ============================================================ */

        function stopGeneration() {
            if (currentAjaxRequest) { currentAjaxRequest.abort(); currentAjaxRequest = null; }
        }

        function showStoppedMessage(questionText) {
            hideLoading();
            var $node = $('<div class="ai-message assistant">'
                + '<div class="ai-message-avatar">' + U.escapeHtml(assistantName.substring(0, 1)) + '</div>'
                + '<div class="ai-message-body">'
                + '<div class="ai-message-name">' + U.escapeHtml(assistantName) + '</div>'
                + '<div class="ai-stopped-card">'
                + '<span><i class="pause circle icon"></i> 已停止生成，等待 ' + Math.round((Date.now() - loadingStartTime) / 1000) + 's 后手动中断</span>'
                + '<button class="ai-resume-btn" data-question="' + U.escapeHtml(questionText) + '">重新发送</button>'
                + '</div></div></div>');
            $messageList.append($node); scrollMessageListToBottom(false);
            saveMessage({ role: 'assistant', title: assistantName, message: '[已停止生成] 原问题: ' + questionText, actions: [], responseTime: 0 });
            addTimeline('用户中断', '手动停止了 AI 回复生成');
            resetSendButton(); setRequestState('就绪', false); setSignalState('online');
            var s = getActiveSession(); if (s) { s.updatedAt = Date.now(); persistSessions(); renderSessionList(); }
        }

        $(document).on('click', '.ai-pending-banner-toggle', function () {
            var $banner = $(this).closest('.ai-pending-banner');
            updatePendingBannerExpandedState($banner, !$banner.hasClass('is-expanded'));
        });

        $(document).on('click', '.ai-pending-banner-dismiss', function () {
            var $banner = $(this).closest('.ai-pending-banner');
            persistPendingBannerState({ dismissed: true });
            $banner.slideUp(180, function () {
                $(this).remove();
            });
        });

        $(document).on('click', '.ai-pending-banner-retry', function () {
            var question = $(this).data('question') || '';
            if (!question) return;
            sendQuestion(question);
        });

        $(document).on('click', '.ai-pending-banner-toggle', function () {
            var $banner = $(this).closest('.ai-pending-banner');
            updatePendingBannerExpandedState($banner, !$banner.hasClass('is-expanded'));
        });

        $(document).on('click', '.ai-pending-banner-dismiss', function () {
            var $banner = $(this).closest('.ai-pending-banner');
            persistPendingBannerState({ dismissed: true });
            $banner.slideUp(180, function () {
                $(this).remove();
            });
        });

        $(document).on('click', '.ai-pending-banner-retry', function () {
            var question = $(this).data('question') || '';
            if (!question) return;
            sendQuestion(question);
        });

        $(document).on('click', '.ai-resume-btn', function () {
            var q = $(this).data('question') || pendingQuestion;
            if (!q) return;
            $(this).closest('.ai-message').remove(); sendQuestion(q);
        });

        $sendButton.on('click', function () {
            if ($(this).hasClass('ai-stop-btn')) { stopGeneration(); return; }
            sendQuestion();
        });

        /* ============================================================
         *  Loading 指示器
         * ============================================================ */

        function showLoading() {
            hideLoading(); loadingStartTime = Date.now();
            var $loading = $('<div class="ai-loading-indicator" id="aiLoadingIndicator">'
                + '<div class="loading-avatar" style="width:38px;height:38px;border-radius:14px;background:#dff7f5;display:flex;align-items:center;justify-content:center;font-weight:700;flex-shrink:0;color:#0f172a;">' + assistantName.substring(0, 1) + '</div>'
                + '<div class="ai-loading-dots"><span class="ai-loading-dot"></span><span class="ai-loading-dot"></span><span class="ai-loading-dot"></span></div>'
                + '<span class="ai-loading-text">正在思考中...</span><span class="ai-loading-timer">0s</span></div>');
            $messageList.append($loading); scrollMessageListToBottom(false);
            loadingTimerInterval = setInterval(function () {
                var elapsed = Math.round((Date.now() - loadingStartTime) / 1000);
                $loading.find('.ai-loading-timer').text(elapsed + 's');
                if (elapsed >= 30 && elapsed % 10 === 0) $loading.find('.ai-loading-text').text('AI 正在深入分析，请稍候...');
                if (elapsed >= 60) { $loading.find('.ai-loading-text').text('响应时间较长，AI 可能遇到了复杂问题...'); $loading.find('.ai-loading-timer').css('color', '#d97706'); }
            }, 1000);
        }

        function hideLoading() {
            if (loadingTimerInterval) { clearInterval(loadingTimerInterval); loadingTimerInterval = null; }
            $('#aiLoadingIndicator').remove();
        }

        function showTimeoutMessage() {
            var elapsed = Math.round((Date.now() - loadingStartTime) / 1000);
            var timeoutSeconds = aiTimeout / 1000;
            hideLoading();
            appendMessage('assistant', assistantName,
                '抱歉，AI 响应超时（已等待 ' + elapsed + ' 秒，超时阈值 ' + timeoutSeconds + ' 秒）。可能原因：\n\n'
                + '1. 大模型服务负载较高或网络延迟较大\n'
                + '2. 问题涉及大量数据查询需要更长时间\n'
                + '3. 服务端处理出现异常\n\n'
                + '建议：可以稍后重试，或换一个更具体的问题。', [], { animate: true, collapsible: true });
            saveMessage({ role: 'assistant', title: assistantName, message: '[请求超时] 等待 ' + elapsed + 's / 阈值 ' + timeoutSeconds + 's', actions: [], responseTime: elapsed * 1000 });
            addTimeline('请求超时', '等待 ' + elapsed + 's 超过阈值 ' + timeoutSeconds + 's');
        }

        /* ============================================================
         *  快捷链接 & 推荐提问 渲染
         * ============================================================ */

        function renderQuickLinks(links) {
            if (!links || !links.length) { $quickLinkList.empty(); return; }
            var html = ''; $.each(links, function (_, link) {
                html += '<a class="ai-quick-link-card" href="' + U.escapeHtml(link.url || '#') + '">'
                    + '<div class="ai-quick-link-title">' + U.escapeHtml(link.title || '推荐入口') + '</div>'
                    + '<div class="ai-quick-link-desc">' + U.escapeHtml(link.description || '') + '</div></a>';
            }); $quickLinkList.html(html);
        }

        function renderFollowUps(actions) {
            if (!actions || !actions.length) { $followUpList.empty(); return; }
            var html = ''; $.each(actions, function (_, action) {
                html += '<button class="ui button basic fluid small starter-question" data-question="' + U.escapeHtml(action) + '" title="' + U.escapeHtml(action) + '"><span class="ai-follow-up-text">' + U.escapeHtml(action) + '</span></button>';
            }); $followUpList.html(html);
        }

        /* ============================================================
         *  多会话管理系统（工作台独有）
         * ============================================================ */

        function createBaseSession() {
            var welcomeMessage = $('#aiWelcomeMessage').val();
            return {
                id: U.createId(), title: '新会话', pinned: false, updatedAt: Date.now(),
                quickLinks: [], suggestions: [], pendingRequest: null,
                history: [{ role: 'assistant', title: assistantName, message: welcomeMessage, actions: [] }],
                timeline: [
                    { time: U.nowText(), title: '工作台上线', desc: '动态工作台已连接当前项目上下文' },
                    { time: U.nowText(), title: '项目信号已载入', desc: projectName }
                ]
            };
        }

        function migrateLegacyHistory() {
            var legacy = U.readJSON(legacyHistoryKey, []);
            if (legacy.length) {
                var session = createBaseSession(); session.history = legacy; session.title = '历史会话'; session.updatedAt = Date.now();
                sessions = [session]; activeSessionId = session.id; persistSessions(); sessionStorage.removeItem(legacyHistoryKey);
                return;
            }
            var fresh = createBaseSession(); sessions = [fresh]; activeSessionId = fresh.id; persistSessions();
        }

        function loadSessions() {
            var localSessions = U.readJSON(sessionsKey, []);
            var serverState = parseServerSessionState();
            var preferLocalMigration = shouldPreferLocalMigration(serverState, localSessions);
            maybeShowRecoveredToast(serverState, localSessions, preferLocalMigration);
            sessions = preferLocalMigration ? localSessions : (serverState && serverState.sessions && serverState.sessions.length ? serverState.sessions : localSessions);
            activeSessionId = preferLocalMigration
                ? (sessionStorage.getItem(activeSessionKey) || (localSessions[0] && localSessions[0].id))
                : (serverState && serverState.activeSessionId ? serverState.activeSessionId : sessionStorage.getItem(activeSessionKey));
            $sessionSortSelect.val(getSessionSortMode(preferLocalMigration ? null : serverState));
            $.each(sessions, function (_, s) {
                s.pinned = !!s.pinned;
                if (!s.pendingRequest) {
                    s.pendingRequest = null;
                    return;
                }
                if (typeof s.pendingRequest.bannerExpanded !== 'boolean') {
                    s.pendingRequest.bannerExpanded = s.pendingRequest.status === 'pending';
                }
                if (typeof s.pendingRequest.dismissed !== 'boolean') {
                    s.pendingRequest.dismissed = false;
                }
            });
            if (serverState && typeof serverState.timelineExpanded === 'boolean' && !preferLocalMigration) {
                timelineState.hasUserPreference = true;
                timelineState.expanded = serverState.timelineExpanded;
                sessionStorage.setItem(timelineExpandedKey, serverState.timelineExpanded ? 'true' : 'false');
            }
            if (!sessions.length) { migrateLegacyHistory(); return; }
            if (!activeSessionId || !findSession(activeSessionId)) { activeSessionId = sessions[0].id; sessionStorage.setItem(activeSessionKey, activeSessionId); }
            persistSessions();
            if (preferLocalMigration) {
                syncSessionStateToServer({ silent: true });
                localStorage.setItem(sessionMigrationMarkerKey, 'done');
            }
        }

        function persistSessions() { U.writeJSON(sessionsKey, sessions); sessionStorage.setItem(activeSessionKey, activeSessionId); serverSessionState = JSON.stringify(buildServerSessionPayload()); }

        function parseServerSessionState() {
            if (!serverSessionState) return null;
            try {
                return JSON.parse(serverSessionState);
            } catch (e) {
                console.warn('[AI] 解析服务端会话状态失败', e);
                return null;
            }
        }

        function buildServerSessionPayload() {
            return {
                sessions: sessions,
                activeSessionId: activeSessionId,
                sessionSortMode: getSessionSortMode(),
                timelineExpanded: timelineState.expanded
            };
        }

        function syncSessionStateToServer(options) {
            options = options || {};
            var payload = JSON.stringify(buildServerSessionPayload());
            serverSessionState = payload;
            sessionStateSyncInFlight = true;
            return $.ajax({
                url: sessionStateUrl,
                type: 'POST',
                dataType: 'json',
                timeout: Math.min(aiTimeout, 15000),
                data: { sessionState: payload }
            }).done(function (response) {
                if (response && response.success !== false && response.data) {
                    serverSessionState = response.data;
                    normalizeServerSessionState();
                    persistSessions();
                    if (options.toastMessage) {
                        U.showToast(options.toastMessage, 'success');
                    }
                }
            }).fail(function () {
                if (options.silent !== true) {
                    U.showToast('会话同步失败，但本地记录仍已保留', 'warning');
                }
            }).always(function () {
                sessionStateSyncInFlight = false;
            });
        }

        function syncSessionStateOnPageLeave() {
            if (sessionStateSyncInFlight || !window.navigator || typeof window.navigator.sendBeacon !== 'function') return;
            var payload = JSON.stringify(buildServerSessionPayload());
            serverSessionState = payload;
            var formData = new FormData();
            formData.append('sessionState', payload);
            window.navigator.sendBeacon(sessionStateUrl, formData);
        }

        function maybeShowRecoveredToast(serverState, localSessions, preferLocalMigration) {
            if (preferLocalMigration) {
                U.showToast('已从浏览器本地恢复历史会话，并同步到服务端', 'success');
                return;
            }
            if (!serverState || !serverState.sessions || !serverState.sessions.length) return;
            if (localSessions && localSessions.length) return;
            U.showToast('已自动恢复上次会话', 'success');
        }

        function maybeShowPendingRecoveryHint(session) {
            if (!session || !session.pendingRequest || session.pendingRequest.status !== 'pending') return;
            var question = session.pendingRequest.question || '上一轮问题';
            U.showToast('检测到上一轮请求可能未完成：' + question.substring(0, 18), 'warning');
        }

        function renderPendingRequestBanner(session) {
            if (!session || !session.pendingRequest || session.pendingRequest.dismissed) return;
            var pending = session.pendingRequest;
            var status = pending.status || 'pending';
            var statusLabelMap = {
                pending: '上一轮请求仍未完成',
                completed: '上一轮请求已完成',
                failed: '上一轮请求处理失败',
                timeout: '上一轮请求已超时',
                aborted: '上一轮请求已手动停止',
                error: '上一轮请求发生异常'
            };
            var statusMetaMap = {
                pending: { icon: 'history', bannerClass: 'is-pending' },
                completed: { icon: 'check circle', bannerClass: 'is-completed' },
                failed: { icon: 'times circle', bannerClass: 'is-failed' },
                timeout: { icon: 'clock outline', bannerClass: 'is-timeout' },
                aborted: { icon: 'pause circle', bannerClass: 'is-aborted' },
                error: { icon: 'warning circle', bannerClass: 'is-error' }
            };
            var question = pending.question || '未记录问题';
            var detail = pending.detail || '';
            var startedAt = pending.startedAt ? new Date(pending.startedAt).toLocaleString() : '';
            var statusMeta = statusMetaMap[status] || statusMetaMap.pending;
            var bannerClass = statusMeta.bannerClass;
            var isExpanded = typeof pending.bannerExpanded === 'boolean' ? pending.bannerExpanded : status === 'pending';
            var retryButton = status === 'pending'
                ? '<button type="button" class="ai-pending-banner-retry" data-question="' + U.escapeHtml(question) + '">重新发送</button>'
                : '';
            var dismissButton = status !== 'pending'
                ? '<button type="button" class="ai-pending-banner-dismiss" aria-label="关闭提示" title="关闭提示"><i class="close icon"></i></button>'
                : '';
            var metaText = startedAt + (detail ? ' · ' + detail : '');
            var collapsedSummary = question.length > 42 ? question.substring(0, 42) + '…' : question;
            var toggleText = isExpanded ? '收起' : '展开';
            var html = '<div class="ai-pending-banner ' + bannerClass + (isExpanded ? ' is-expanded' : '') + '">'
                + '<div class="ai-pending-banner-icon"><i class="' + U.escapeHtml(statusMeta.icon) + ' icon"></i></div>'
                + '<div class="ai-pending-banner-body">'
                + '<div class="ai-pending-banner-title-row">'
                + '<button type="button" class="ai-pending-banner-toggle" aria-expanded="' + (isExpanded ? 'true' : 'false') + '">'
                + '<span class="ai-pending-banner-toggle-main">'
                + '<span class="ai-pending-banner-title">' + U.escapeHtml(statusLabelMap[status] || '上一轮请求状态未知') + '</span>'
                + '<span class="ai-pending-banner-summary' + (isExpanded ? '' : ' visible') + '">' + U.escapeHtml(collapsedSummary) + '</span>'
                + '</span>'
                + '<span class="ai-pending-banner-toggle-side">'
                + '<span class="ai-pending-banner-toggle-text">' + toggleText + '</span>'
                + '<span class="ai-pending-banner-chevron" aria-hidden="true"></span>'
                + '</span>'
                + '</button>'
                + retryButton
                + dismissButton
                + '</div>'
                + '<div class="ai-pending-banner-details"' + (isExpanded ? '' : ' hidden') + '>'
                + '<div class="ai-pending-banner-details-inner">'
                + '<div class="ai-pending-banner-question">' + U.escapeHtml(question) + '</div>'
                + '<div class="ai-pending-banner-meta">' + U.escapeHtml(metaText) + '</div>'
                + '</div>'
                + '</div>'
                + '</div></div>';
            $messageList.append(html);
        }

        function updatePendingBannerExpandedState($banner, expanded) {
            if (!$banner || !$banner.length) return;
            var $toggle = $banner.find('.ai-pending-banner-toggle');
            var $details = $banner.find('.ai-pending-banner-details');
            var $toggleText = $banner.find('.ai-pending-banner-toggle-text');
            var $summary = $banner.find('.ai-pending-banner-summary');
            if (!$toggle.length || !$details.length) return;

            persistPendingBannerState({ bannerExpanded: expanded });

            $banner.toggleClass('is-expanded', expanded);
            $toggle.attr('aria-expanded', expanded ? 'true' : 'false');
            if ($toggleText.length) {
                $toggleText.text(expanded ? '收起' : '展开');
            }
            if ($summary.length) {
                $summary.toggleClass('visible', !expanded);
            }

            if (expanded) {
                $details.prop('hidden', false);
                var targetHeight = $details[0].scrollHeight;
                $details.css('max-height', '0px');
                requestAnimationFrame(function () {
                    $details.addClass('is-animating').css('max-height', targetHeight + 'px');
                });
                setTimeout(function () {
                    if ($banner.hasClass('is-expanded')) {
                        $details.removeClass('is-animating').css('max-height', 'none');
                    }
                }, 240);
                return;
            }

            var currentHeight = $details[0].scrollHeight;
            $details.addClass('is-animating').css('max-height', currentHeight + 'px');
            requestAnimationFrame(function () {
                $details.css('max-height', '0px');
            });
            setTimeout(function () {
                if (!$banner.hasClass('is-expanded')) {
                    $details.removeClass('is-animating').prop('hidden', true).css('max-height', '');
                }
            }, 240);
        }

        function persistPendingBannerState(patch) {
            var activeSession = getActiveSession();
            if (!activeSession || !activeSession.pendingRequest) return;
            $.extend(activeSession.pendingRequest, patch || {});
            persistSessions();
        }

        function shouldPreferLocalMigration(serverState, localSessions) {
            if (!localSessions || !localSessions.length) return false;
            var serverSessions = serverState && serverState.sessions ? serverState.sessions : [];
            return !serverSessions.length;
        }

        function normalizeServerSessionState() {
            var serverState = parseServerSessionState();
            if (!serverState) return;
            if (serverState.sessions && serverState.sessions.length) {
                sessions = serverState.sessions;
            }
            if (serverState.activeSessionId) {
                activeSessionId = serverState.activeSessionId;
            }
            if (typeof serverState.timelineExpanded === 'boolean') {
                timelineState.expanded = serverState.timelineExpanded;
                timelineState.hasUserPreference = true;
                sessionStorage.setItem(timelineExpandedKey, serverState.timelineExpanded ? 'true' : 'false');
            }
            if (serverState.sessionSortMode) {
                sessionStorage.setItem(sessionSortKey, serverState.sessionSortMode);
                $sessionSortSelect.val(serverState.sessionSortMode);
            }
        }

        function clearPendingRequest(session, status, detail) {
            if (!session || !session.pendingRequest) return;
            session.pendingRequest.status = status || 'done';
            session.pendingRequest.finishedAt = Date.now();
            if (detail) session.pendingRequest.detail = detail;
            persistSessions();
        }

        function findSession(id) { for (var i = 0; i < sessions.length; i++) { if (sessions[i].id === id) return sessions[i]; } return null; }
        function getActiveSession() { return findSession(activeSessionId); }

        function updateSessionTitle(session, q) {
            if (!session || session.title !== '新会话' && session.title !== '历史会话') return;
            session.title = (q || '新会话').substring(0, 18);
        }

        function getSessionSortMode(serverState) {
            var fromServer = serverState && serverState.sessionSortMode;
            var m = fromServer || sessionStorage.getItem(sessionSortKey) || 'recent';
            return ['recent', 'oldest', 'name'].indexOf(m) !== -1 ? m : 'recent';
        }

        function getSessionPreview(session) {
            for (var i = (session.history || []).length - 1; i >= 0; i--) {
                if (session.history[i].role === 'assistant') return session.history[i].message || '';
            } return '';
        }

        function sortSessions(list) {
            var mode = getSessionSortMode();
            return list.slice().sort(function (a, b) {
                if (!!a.pinned !== !!b.pinned) return a.pinned ? -1 : 1;
                if (mode === 'oldest') return (a.updatedAt || 0) - (b.updatedAt || 0);
                if (mode === 'name') { var c = (a.title || '新会话').localeCompare(b.title || '新会话', 'zh-Hans-CN'); return c !== 0 ? c : (b.updatedAt || 0) - (a.updatedAt || 0); }
                return (b.updatedAt || 0) - (a.updatedAt || 0);
            });
        }

        function matchKeyword(session, keyword) {
            if (!keyword) return true;
            var h = (session.title || '') + ' ' + getSessionPreview(session);
            $.each(session.history || [], function (_, item) { if (item.message) h += ' ' + item.message; });
            return h.toLowerCase().indexOf(keyword) > -1;
        }

        function buildSessionItemHtml(session) {
            var preview = getSessionPreview(session);
            var t = U.escapeHtml(new Date(session.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
            return '<div class="ai-session-item ' + (session.id === activeSessionId ? 'active' : '') + '" data-session-id="' + U.escapeHtml(session.id) + '">'
                + '<div class="ai-session-top"><div class="ai-session-title" title="' + U.escapeHtml(session.title || '新会话') + '">' + U.escapeHtml(session.title || '新会话') + '</div>'
                + '<div class="ai-session-actions"><span class="ai-session-time">' + t + '</span>'
                + '<button class="ai-session-action pin ' + (session.pinned ? 'pinned' : '') + '" type="button" data-session-id="' + U.escapeHtml(session.id) + '" title="置顶"><i class="thumbtack icon"></i></button>'
                + '<button class="ai-session-action rename" type="button" data-session-id="' + U.escapeHtml(session.id) + '" title="重命名"><i class="edit outline icon"></i></button>'
                + '<button class="ai-session-action delete" type="button" data-session-id="' + U.escapeHtml(session.id) + '" title="删除"><i class="trash alternate outline icon"></i></button></div></div>'
                + '<div class="ai-session-preview">' + U.escapeHtml((preview || '从这里继续你的 AI 交互工作台').substring(0, 80)) + '</div></div>';
        }

        function renderSection(label, items) {
            if (!items.length) return;
            var $sec = $('<div class="ai-session-section"></div>');
            $sec.append('<div class="ai-session-section-label"><span>' + U.escapeHtml(label) + '</span><span class="ai-session-section-count">' + items.length + '</span></div>');
            $.each(items, function (_, s) { $sec.append(buildSessionItemHtml(s)); });
            $sessionList.append($sec);
        }

        function renderSessionList() {
            $sessionList.empty();
            var kw = $.trim($sessionSearchInput.val() || '').toLowerCase();
            var sorted = sortSessions(sessions).filter(function (s) { return matchKeyword(s, kw); });
            if (!sorted.length) { $sessionList.html('<div class="ai-session-empty">没有匹配的会话，换个关键词试试。</div>'); return; }
            renderSection('置顶会话', sorted.filter(function (s) { return s.pinned; }));
            renderSection('最近会话', sorted.filter(function (s) { return !s.pinned; }));
        }

        function renderActiveSession() {
            var s = getActiveSession(); if (!s) return;
            $messageList.empty();
            renderPendingRequestBanner(s);
            var anchors = getQuestionAnchors(s);
            var anchorCursor = 0;
            $.each(s.history || [], function (index, item) {
                var nextItem = (s.history || [])[index + 1] || null;
                var messageOptions = {
                    animate: false,
                    collapsible: item.role === 'assistant' && shouldCollapseMessage(item.message),
                    isHtml: false,
                    startQaGroup: item.role === 'user',
                    endQaGroup: item.role === 'assistant' && (!nextItem || nextItem.role === 'user')
                };
                if (item.role === 'user') {
                    var anchor = anchors[anchorCursor] || createQuestionAnchor(item.message, anchorCursor, { index: anchorCursor });
                    messageOptions.anchorId = anchor.id;
                    messageOptions.isAnchorTarget = true;
                    anchorCursor++;
                }
                appendMessage(item.role, item.title, item.message, item.actions || [], messageOptions);
            });
            renderChatAnchors(s, { silent: true });
            collapseHistoricalQaMessages({ scrollLatest: false });
            syncActiveAnchorByScroll();
            renderQuickLinks(s.quickLinks || []); renderFollowUps(s.suggestions || []); renderTimeline(s); updateTimelineSummary(s);
            if (!timelineState.hasUserPreference) {
                var firstSessionId = sortSessions(sessions)[0] ? sortSessions(sessions)[0].id : null;
                setTimelineExpanded(s.id === firstSessionId, { persist: false });
            }
            renderSessionList(); setSignalState('online'); $questionInput.focus();
            maybeShowPendingRecoveryHint(s);
            expandAnchorFromHash();
        }

        function addTimeline(title, description) {
            var s = getActiveSession(); if (!s) return;
            s.timeline = s.timeline || [];
            s.timeline.push({ time: U.nowText(), title: title, desc: description });
            if (s.timeline.length > 8) s.timeline = s.timeline.slice(-8);
            renderTimeline(s);
        }

        function saveMessage(entry) {
            var s = getActiveSession(); if (!s) return;
            s.history.push(entry); s.updatedAt = Date.now(); persistSessions(); renderSessionList();
            renderChatAnchors(s, { silent: true });
            renderFloatingAnchors(getQuestionAnchors(s).filter(function (anchor) {
                var matchesFilter = anchorFilterMode === 'all' || !anchor.answered;
                var matchesSearch = !anchorSearchKeyword || (anchor.question || '').toLowerCase().indexOf(anchorSearchKeyword) !== -1;
                return matchesFilter && matchesSearch;
            }));
            syncActiveAnchorByScroll();
        }

        function createNewSession() {
            var s = createBaseSession(); sessions.unshift(s); activeSessionId = s.id; persistSessions(); renderActiveSession();
            syncSessionStateToServer({ silent: true });
        }

        function renameSession(sid) {
            var s = findSession(sid); if (!s) return;
            var $item = $('.ai-session-item[data-session-id="' + sid + '"]'); var $titleEl = $item.find('.ai-session-title');
            var old = s.title || '新会话';
            var $input = $('<input type="text" class="ai-session-rename-input" value="' + U.escapeHtml(old) + '" maxlength="24" />');
            $titleEl.empty().append($input).css({ width: '100%', border: '1px solid #0f766e', borderRadius: '6px', padding: '2px 6px', fontSize: 'inherit', outline: 'none', boxSizing: 'border-box' }).focus().select();

            function finish() {
                var n = $.trim($input.val());
                if (n && n !== old) {
                    s.title = n.substring(0, 24); s.updatedAt = Date.now(); persistSessions(); renderSessionList();
                    syncSessionStateToServer({ toastMessage: '会话已重命名为"' + U.escapeHtml(s.title) + '"' });
                    return;
                }
                renderSessionList();
            }
            $input.on('keydown', function (e) { if (e.keyCode === 13) { e.preventDefault(); finish(); } else if (e.keyCode === 27) { renderSessionList(); } }).on('blur', finish);
        }

        function togglePinSession(sid) {
            var s = findSession(sid); if (!s) return;
            s.pinned = !s.pinned; s.updatedAt = Date.now(); persistSessions(); renderSessionList();
            syncSessionStateToServer({ silent: true });
        }

        function deleteSession(sid) {
            var s = findSession(sid); if (!s) return;
            U.showConfirm('确认删除会话 "' + (s.title || '新会话') + '" 吗？', function () {
                sessions = sessions.filter(function (x) { return x.id !== sid; });
                if (!sessions.length) {
                    var ns = createBaseSession(); sessions = [ns]; activeSessionId = ns.id; persistSessions(); renderActiveSession();
                    syncSessionStateToServer({ toastMessage: '会话已删除' });
                    return;
                }
                if (activeSessionId === sid) activeSessionId = sortSessions(sessions)[0].id;
                persistSessions(); renderActiveSession();
                syncSessionStateToServer({ toastMessage: '会话已删除' });
            });
        }

        /* ============================================================
         *  时间线渲染（工作台独有）
         * ============================================================ */

        function renderTimeline(session) {
            $timelineList.empty();
            $.each((session.timeline || []).slice().reverse(), function (_, item) {
                $timelineList.append('<div class="ai-timeline-item"><div class="ai-timeline-time">' + U.escapeHtml(item.time) + '</div>'
                    + '<div class="ai-timeline-title">' + U.escapeHtml(item.title) + '</div>'
                    + '<div class="ai-timeline-desc">' + U.escapeHtml(item.desc) + '</div></div>');
            });
            updateTimelineSummary(session);
        }

        /* ============================================================
         *  发送提问（核心 AJAX 逻辑）
         * ============================================================ */

        function sendQuestion(rawQuestion) {
            var question = $.trim(rawQuestion || $questionInput.val());
            if (!question && !uploadedImageData) { $questionInput.focus(); return; }
            var session = getActiveSession();
            if (!session) { createNewSession(); session = getActiveSession(); }

            pendingQuestion = question || '[图片提问]';
            var userMsgHtml = question;
            var saveMsgText = question;
            if (uploadedImageData) {
                userMsgHtml += (question ? '<br>' : '') + '<img src="' + U.escapeHtml(uploadedImageData) + '" style="max-width:280px;max-height:200px;border-radius:10px;margin-top:6px;" alt="上传的图片">';
                saveMsgText += (saveMsgText ? ' [附图]' : '[图片]');
            }
            appendMessage('user', '你', userMsgHtml, [], {
                isHtml: true,
                anchorId: createQuestionAnchor(saveMsgText || pendingQuestion, getQuestionAnchors(session).length).id,
                isAnchorTarget: true
            });
            renderChatAnchors({ history: session.history.concat([{ role: 'user', message: saveMsgText || pendingQuestion }]) });
            updateSessionTitle(session, question || '[图片提问]');
            saveMessage({ role: 'user', title: '你', message: saveMsgText, actions: [] });
            session.pendingRequest = {
                status: 'pending',
                question: saveMsgText || pendingQuestion,
                startedAt: Date.now(),
                hasImage: !!uploadedImageData,
                bannerExpanded: true,
                dismissed: false
            };
            persistSessions();
            syncSessionStateToServer({ silent: true });
            addTimeline('收到提问', pendingQuestion);
            $questionInput.val('');
            imgHandler.clear(); uploadedImageData = null;
            setSignalState('thinking'); setRequestState('思考中...', true); setSendButtonToStop(); showLoading();
            mascotCanvas.setState('thinking');

            currentAjaxRequest = $.ajax({
                url: askUrl, type: 'POST', dataType: 'json', timeout: aiTimeout,
                data: {
                    question: question,
                    sessionState: JSON.stringify(buildServerSessionPayload()),
                    activeSessionId: activeSessionId,
                    sessionSortMode: getSessionSortMode(),
                    timelineExpanded: timelineState.expanded
                }
            }).done(function (response) {
                hideLoading();
                if (!response || response.success === false || response.result === false) {
                    var fail = (response && response.message) || '当前无法完成分析，请稍后重试。';
                    appendMessage('assistant', assistantName, fail, [], { animate: true, collapsible: shouldCollapseMessage(fail) });
                    saveMessage({ role: 'assistant', title: assistantName, message: fail, actions: [], responseTime: 0 });
                    clearPendingRequest(session, 'failed', fail);
                    syncSessionStateToServer({ silent: true });
                    addTimeline('分析失败', fail); setSignalState('online'); mascotCanvas.setState('idle'); return;
                }
                var d = response.data || {};
                var reply = d.answer || response.message || '已收到你的问题。';
                appendMessage('assistant', assistantName, reply, d.suggestions || [], { animate: true, collapsible: shouldCollapseMessage(reply) });
                session.quickLinks = d.quickLinks || []; session.suggestions = d.suggestions || [];
                if (d.sessionState) {
                    serverSessionState = d.sessionState;
                    normalizeServerSessionState();
                    session = getActiveSession() || session;
                }
                renderQuickLinks(session.quickLinks); renderFollowUps(session.suggestions);
                saveMessage({ role: 'assistant', title: assistantName, message: reply, actions: d.suggestions || [], quickLinks: d.quickLinks || [], responseTime: d.metadata && d.metadata.responseTime ? d.metadata.responseTime : 0 });
                clearPendingRequest(session, 'completed', reply);
                syncSessionStateToServer({ silent: true });
                addTimeline('生成回复', d.topic || 'general'); setSignalState('reply');
                mascotCanvas.setState('done');
            }).fail(function (jqXHR, textStatus) {
                hideLoading();
                if (textStatus === 'abort') { showStoppedMessage(pendingQuestion); clearPendingRequest(session, 'aborted', '用户手动停止'); syncSessionStateToServer({ silent: true }); mascotCanvas.setState('idle'); return; }
                if (textStatus === 'timeout') { showTimeoutMessage(); clearPendingRequest(session, 'timeout', '请求超时'); syncSessionStateToServer({ silent: true }); mascotCanvas.setState('idle'); return; }
                var err = '请求失败了（' + textStatus + '），请稍后再试，或者换一个更具体的问题。';
                appendMessage('assistant', assistantName, err, [], { animate: true });
                saveMessage({ role: 'assistant', title: assistantName, message: err, actions: [], responseTime: 0 });
                clearPendingRequest(session, 'error', err);
                syncSessionStateToServer({ silent: true });
                addTimeline('请求异常', textStatus + ' - 请稍后重试或更换问题描述'); setSignalState('online'); mascotCanvas.setState('idle');
            }).always(function () {
                currentAjaxRequest = null; var s = getActiveSession(); if (s) { s.updatedAt = Date.now(); persistSessions(); renderSessionList(); }
                resetSendButton(); setRequestState('就绪', false);
                setTimeout(function () { setSignalState('online'); }, 900); $questionInput.focus();
            });
        }

        /* ============================================================
         *  事件绑定
         * ============================================================ */

        $(window).on('pagehide beforeunload', function () {
            syncSessionStateOnPageLeave();
        });
        $(document).on('visibilitychange', function () {
            if (document.visibilityState === 'hidden') {
                syncSessionStateOnPageLeave();
            }
        });

        $newSessionButton.on('click', createNewSession);
        $sessionSearchInput.on('input', renderSessionList);
        $sessionSortSelect.on('change', function () { sessionStorage.setItem(sessionSortKey, $(this).val()); persistSessions(); renderSessionList(); syncSessionStateToServer({ silent: true }); });

        $questionInput.on('keydown', function (e) {
            if (e.keyCode === 13 && !e.shiftKey) { e.preventDefault(); sendQuestion(); }
        });

        $(document).on('click', '.starter-question, .ai-action-btn', function () { sendQuestion($(this).data('question')); });
        $(document).on('mousedown', '.ai-floating-anchor-dot', function (event) {
            event.preventDefault();
            event.stopPropagation();
            scrollToAnchor($(this).data('anchor-id'));
        });
        $floatingAnchorsList.on('mousedown', function (event) {
            if ($(event.target).closest('.ai-floating-anchor-dot').length) return;
            event.preventDefault();
            event.stopPropagation();
            var anchorId = findClosestFloatingAnchorId(event.pageY);
            if (anchorId) {
                previewFloatingAnchor(anchorId);
                setTimeout(function () {
                    scrollToAnchor(anchorId);
                }, 70);
            }
        });
        $(document).on('click', '.ai-chat-anchor-item, .ai-floating-anchor-dot', function (event) {
            event.preventDefault();
            event.stopPropagation();
            scrollToAnchor($(this).data('anchor-id'));
        });
        $(document).on('mouseenter focus', '.ai-floating-anchor-dot', function () {
            var $dot = $(this);
            var question = $dot.data('anchor-question') || '';
            var status = $dot.data('anchor-status') || '';
            var duration = $dot.data('anchor-duration') || '';
            $floatingAnchorTooltip.html('<div class="ai-floating-anchor-tooltip-label">' + U.escapeHtml($dot.data('anchor-label') || '') + '</div>'
                + '<div class="ai-floating-anchor-tooltip-question">' + U.escapeHtml(question) + '</div>'
                + '<div class="ai-floating-anchor-tooltip-meta">' + U.escapeHtml(status + ' · ' + duration) + '</div>')
                .addClass('visible').attr('aria-hidden', 'false');
            positionFloatingAnchorTooltip($dot);
        });
        $(document).on('mouseleave blur', '.ai-floating-anchor-dot', function () {
            $floatingAnchorTooltip.removeClass('visible').attr('aria-hidden', 'true').empty();
        });
        $(document).on('click', '.ai-chat-anchor-link-btn', function (event) { event.stopPropagation(); copyAnchorLink($(this).data('anchor-link')); });
        $(document).on('click', '.ai-chat-anchor-text-toggle', function (event) {
            event.stopPropagation();
            var anchorId = $(this).data('anchor-id');
            expandedAnchorTextMap[anchorId] = !expandedAnchorTextMap[anchorId];
            renderChatAnchors(getActiveSession());
        });
        $(document).on('click', '.ai-anchor-filter-btn', function () {
            anchorFilterMode = $(this).data('filter') || 'all';
            $('.ai-anchor-filter-btn.active').removeClass('active');
            $(this).addClass('active');
            renderChatAnchors(getActiveSession());
        });
        $anchorSearchInput.on('input', function () {
            anchorSearchKeyword = $.trim($(this).val() || '').toLowerCase();
            anchorKeyboardIndex = -1;
            renderChatAnchors(getActiveSession());
        }).on('keydown', function (event) {
            if (event.keyCode === 13) {
                event.preventDefault();
                var $firstMatch = $chatAnchorsList.find('.ai-chat-anchor-item').eq(anchorKeyboardIndex >= 0 ? anchorKeyboardIndex : 0);
                if ($firstMatch.length) {
                    scrollToAnchor($firstMatch.data('anchor-id'));
                }
            } else if (event.keyCode === 27) {
                event.preventDefault();
                clearAnchorSearch();
            } else if (event.keyCode === 40) {
                event.preventDefault();
                moveAnchorKeyboardSelection(1);
            } else if (event.keyCode === 38) {
                event.preventDefault();
                moveAnchorKeyboardSelection(-1);
            }
        });
        $anchorSearchClear.on('click', function () { clearAnchorSearch(); });
        $(window).on('hashchange', function () { expandAnchorFromHash(); });
        $(window).on('resize', function () {
            renderFloatingAnchors(getQuestionAnchors(getActiveSession()).filter(function (anchor) {
                var matchesFilter = anchorFilterMode === 'all' || !anchor.answered;
                var matchesSearch = !anchorSearchKeyword || (anchor.question || '').toLowerCase().indexOf(anchorSearchKeyword) !== -1;
                return matchesFilter && matchesSearch;
            }));
            var $hoveredDot = $floatingAnchorsList.find('.ai-floating-anchor-dot:hover').first();
            if ($hoveredDot.length && $floatingAnchorTooltip.hasClass('visible')) {
                positionFloatingAnchorTooltip($hoveredDot);
            }
        });
        $messageList.on('scroll', function () { dismissCurrentAssistantHighlight(); syncActiveAnchorByScroll(); });
        $('#aiChatPanel').on('scroll', function () { dismissCurrentAssistantHighlight(); syncActiveAnchorByScroll(); });
        $(window).on('scroll', function () { dismissCurrentAssistantHighlight(); syncActiveAnchorByScroll(); });
        $(document).on('click', '.ai-message-toggle', function () {
            var $message = $(this).closest('.ai-message');
            var shouldExpand = $(this).attr('aria-expanded') !== 'true';
            if (shouldExpand) {
                collapseHistoricalQaMessages({ scrollLatest: false });
            }
            updateMessageCollapseState($message, shouldExpand);
        });
        $timelineToggleButton.on('click', function () { setTimelineExpanded(!timelineState.expanded); });
        $timelineSummary.on('click keydown', function (event) {
            if (event.type === 'click' || event.keyCode === 13 || event.keyCode === 32) {
                if (event.type === 'keydown') event.preventDefault();
                setTimelineExpanded(true);
            }
        });
        $(document).on('click', '.ai-session-item', function () {
            var nextSessionId = $(this).data('session-id');
            if (!nextSessionId || nextSessionId === activeSessionId) return;
            activeSessionId = nextSessionId;
            persistSessions();
            renderActiveSession();
            syncSessionStateToServer({ silent: true });
        });
        $(document).on('click', '.ai-session-action.rename', function (e) { e.stopPropagation(); renameSession($(this).data('session-id')); });
        $(document).on('click', '.ai-session-action.pin', function (e) { e.stopPropagation(); togglePinSession($(this).data('session-id')); });
        $(document).on('click', '.ai-session-action.delete', function (e) { e.stopPropagation(); deleteSession($(this).data('session-id')); });
    });
})(jQuery);

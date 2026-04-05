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
        var aiTimeout = ($root.data('ai-timeout') || 1800) * 1000;
        var projectId = $root.data('project-id') || 'default';
        var projectName = $root.data('project-name') || '当前项目';

        /* ===== 存储 Key ===== */
        var legacyHistoryKey = 'ai-interactive-history:' + projectId;
        var sessionsKey = 'ai-interactive-sessions:' + projectId;
        var activeSessionKey = 'ai-interactive-active-session:' + projectId;
        var sessionSortKey = 'ai-interactive-session-sort:' + projectId;

        /* ===== DOM 引用 ===== */
        var $messageList = $('#aiMessageList');
        var $questionInput = $('#aiQuestionInput');
        var $sendButton = $('#aiSendButton');
        var $requestState = $('#aiRequestState');
        var currentAjaxRequest = null;
        var pendingQuestion = '';
        var $followUpList = $('#aiFollowUpList');
        var $quickLinkList = $('#aiQuickLinkList');
        var $signalLights = $('#aiSignalLights');
        var $timelineList = $('#aiTimelineList');
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
            var x = event.clientX - rect.left - size / 2;
            var y = event.clientY - rect.top - size / 2;
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

        function typewriterText($target, message, done) {
            var safeMessage = U.escapeHtml(message || '');
            var index = 0;
            function tick() {
                index = Math.min(index + 2, safeMessage.length);
                $target.html(safeMessage.substring(0, index).replace(/\n/g, '<br>') + '<span class="ai-typing-cursor"></span>');
                $messageList.scrollTop($messageList[0].scrollHeight);
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
            var contentHtml = options.animate
                ? '<div class="ai-message-text"></div>'
                : (options.isHtml ? message : U.formatMessage(message));
            var $node = $('<div class="ai-message ' + role + '">'
                + '<div class="ai-message-avatar">' + U.escapeHtml(avatar) + '</div>'
                + '<div class="ai-message-body">'
                + '<div class="ai-message-name">' + U.escapeHtml(title) + '</div>'
                + '<div class="ai-message-card" style="position:relative">' + contentHtml
                + (options.animate ? '' : '<button class="ai-message-copy" title="复制"><i class="copy icon"></i></button>' + renderActions(actions))
                + '</div></div></div>');
            $messageList.append($node);
            if (!options.animate) {
                U.bindCopyButton($node.find('.ai-message-copy'), message);
            }
            $messageList.scrollTop($messageList[0].scrollHeight);
            if (options.animate) {
                var $card = $node.find('.ai-message-card');
                typewriterText($card.find('.ai-message-text'), message, function () {
                    var extra = actions && actions.length
                        ? ('<button class="ai-message-copy" title="复制"><i class="copy icon"></i></button>' + renderActions(actions))
                        : '<button class="ai-message-copy" title="复制"><i class="copy icon"></i></button>';
                    $card.append(extra);
                    U.bindCopyButton($card.find('.ai-message-copy'), message);
                });
            }
        }

        function renderActions(actions) {
            if (!actions || !actions.length) return '';
            var html = '<div class="ai-message-actions">';
            $.each(actions, function (_, action) {
                html += '<button class="ai-action-btn" data-question="' + U.escapeHtml(action) + '">' + U.escapeHtml(action) + '</button>';
            });
            return html + '</div>';
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
            $messageList.append($node); $messageList.scrollTop($messageList[0].scrollHeight);
            saveMessage({ role: 'assistant', title: assistantName, message: '[已停止生成] 原问题: ' + questionText, actions: [] });
            addTimeline('用户中断', '手动停止了 AI 回复生成');
            resetSendButton(); setRequestState('就绪', false); setSignalState('online');
            var s = getActiveSession(); if (s) { s.updatedAt = Date.now(); persistSessions(); renderSessionList(); }
        }

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
            $messageList.append($loading); $messageList.scrollTop($messageList[0].scrollHeight);
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
                + '建议：可以稍后重试，或换一个更具体的问题。', [], { animate: true });
            saveMessage({ role: 'assistant', title: assistantName, message: '[请求超时] 等待 ' + elapsed + 's / 阈值 ' + timeoutSeconds + 's', actions: [] });
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
                    + '<div class="ai-quick-link-desc">' + U.escapeHtml(link.description || '') + '</a>';
            }); $quickLinkList.html(html);
        }

        function renderFollowUps(actions) {
            if (!actions || !actions.length) { $followUpList.empty(); return; }
            var html = ''; $.each(actions, function (_, action) {
                html += '<button class="ui button basic fluid small starter-question" data-question="' + U.escapeHtml(action) + '">' + U.escapeHtml(action) + '</button>';
            }); $followUpList.html(html);
        }

        /* ============================================================
         *  多会话管理系统（工作台独有）
         * ============================================================ */

        function createBaseSession() {
            var welcomeMessage = $('#aiWelcomeMessage').val();
            return {
                id: U.createId(), title: '新会话', pinned: false, updatedAt: Date.now(),
                quickLinks: [], suggestions: [],
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
            sessions = U.readJSON(sessionsKey, []);
            activeSessionId = sessionStorage.getItem(activeSessionKey);
            $sessionSortSelect.val(getSessionSortMode());
            $.each(sessions, function (_, s) { s.pinned = !!s.pinned; });
            if (!sessions.length) { migrateLegacyHistory(); return; }
            if (!activeSessionId || !findSession(activeSessionId)) { activeSessionId = sessions[0].id; sessionStorage.setItem(activeSessionKey, activeSessionId); }
        }

        function persistSessions() { U.writeJSON(sessionsKey, sessions); sessionStorage.setItem(activeSessionKey, activeSessionId); }

        function findSession(id) { for (var i = 0; i < sessions.length; i++) { if (sessions[i].id === id) return sessions[i]; } return null; }
        function getActiveSession() { return findSession(activeSessionId); }

        function updateSessionTitle(session, q) {
            if (!session || session.title !== '新会话' && session.title !== '历史会话') return;
            session.title = (q || '新会话').substring(0, 18);
        }

        function getSessionSortMode() {
            var m = sessionStorage.getItem(sessionSortKey) || 'recent';
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
            $.each(s.history || [], function (_, item) { appendMessage(item.role, item.title, item.message, item.actions || [], { animate: false }); });
            renderQuickLinks(s.quickLinks || []); renderFollowUps(s.suggestions || []); renderTimeline(s);
            renderSessionList(); setSignalState('online'); $questionInput.focus();
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
        }

        function createNewSession() {
            var s = createBaseSession(); sessions.unshift(s); activeSessionId = s.id; persistSessions(); renderActiveSession();
        }

        function renameSession(sid) {
            var s = findSession(sid); if (!s) return;
            var $item = $('.ai-session-item[data-session-id="' + sid + '"]'); var $titleEl = $item.find('.ai-session-title');
            var old = s.title || '新会话';
            var $input = $('<input type="text" class="ai-session-rename-input" value="' + U.escapeHtml(old) + '" maxlength="24" />');
            $titleEl.empty().append($input).css({ width: '100%', border: '1px solid #0f766e', borderRadius: '6px', padding: '2px 6px', fontSize: 'inherit', outline: 'none', boxSizing: 'border-box' }).focus().select();

            function finish() {
                var n = $.trim($input.val());
                if (n && n !== old) { s.title = n.substring(0, 24); s.updatedAt = Date.now(); persistSessions(); U.showToast('会话已重命名为"' + U.escapeHtml(s.title) + '"', 'success'); }
                renderSessionList();
            }
            $input.on('keydown', function (e) { if (e.keyCode === 13) { e.preventDefault(); finish(); } else if (e.keyCode === 27) { renderSessionList(); } }).on('blur', finish);
        }

        function togglePinSession(sid) { var s = findSession(sid); if (!s) return; s.pinned = !s.pinned; s.updatedAt = Date.now(); persistSessions(); renderSessionList(); }

        function deleteSession(sid) {
            var s = findSession(sid); if (!s) return;
            U.showConfirm('确认删除会话 "' + (s.title || '新会话') + '" 吗？', function () {
                sessions = sessions.filter(function (x) { return x.id !== sid; });
                if (!sessions.length) { var ns = createBaseSession(); sessions = [ns]; activeSessionId = ns.id; persistSessions(); renderActiveSession(); return; }
                if (activeSessionId === sid) activeSessionId = sortSessions(sessions)[0].id;
                persistSessions(); renderActiveSession(); U.showToast('会话已删除', 'success');
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
            appendMessage('user', '你', userMsgHtml, [], { isHtml: true });
            updateSessionTitle(session, question || '[图片提问]');
            saveMessage({ role: 'user', title: '你', message: saveMsgText, actions: [] });
            addTimeline('收到提问', pendingQuestion);
            $questionInput.val('');
            imgHandler.clear(); uploadedImageData = null;
            setSignalState('thinking'); setRequestState('思考中...', true); setSendButtonToStop(); showLoading();
            mascotCanvas.setState('thinking');

            currentAjaxRequest = $.ajax({
                url: askUrl, type: 'POST', dataType: 'json', timeout: aiTimeout,
                data: { question: question }
            }).done(function (response) {
                hideLoading();
                if (!response || response.success === false || response.result === false) {
                    var fail = (response && response.message) || '当前无法完成分析，请稍后重试。';
                    appendMessage('assistant', assistantName, fail, [], { animate: true });
                    saveMessage({ role: 'assistant', title: assistantName, message: fail, actions: [] }); addTimeline('分析失败', fail); setSignalState('online'); mascotCanvas.setState('idle'); return;
                }
                var d = response.data || {};
                var reply = d.answer || response.message || '已收到你的问题。';
                appendMessage('assistant', assistantName, reply, d.suggestions || [], { animate: true });
                session.quickLinks = d.quickLinks || []; session.suggestions = d.suggestions || [];
                renderQuickLinks(session.quickLinks); renderFollowUps(session.suggestions);
                saveMessage({ role: 'assistant', title: assistantName, message: reply, actions: d.suggestions || [], quickLinks: d.quickLinks || [] });
                addTimeline('生成回复', d.topic || 'general'); setSignalState('reply');
                mascotCanvas.setState('done');
            }).fail(function (jqXHR, textStatus) {
                hideLoading();
                if (textStatus === 'abort') { showStoppedMessage(pendingQuestion); mascotCanvas.setState('idle'); return; }
                if (textStatus === 'timeout') { showTimeoutMessage(); mascotCanvas.setState('idle'); return; }
                var err = '请求失败了（' + textStatus + '），请稍后再试，或者换一个更具体的问题。';
                appendMessage('assistant', assistantName, err, [], { animate: true });
                saveMessage({ role: 'assistant', title: assistantName, message: err, actions: [] });
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

        $newSessionButton.on('click', createNewSession);
        $sessionSearchInput.on('input', renderSessionList);
        $sessionSortSelect.on('change', function () { sessionStorage.setItem(sessionSortKey, $(this).val()); renderSessionList(); });

        $questionInput.on('keydown', function (e) {
            if (e.keyCode === 13 && !e.shiftKey) { e.preventDefault(); sendQuestion(); }
        });

        $(document).on('click', '.starter-question, .ai-action-btn', function () { sendQuestion($(this).data('question')); });
        $(document).on('click', '.ai-session-item', function () { activeSessionId = $(this).data('session-id'); persistSessions(); renderActiveSession(); });
        $(document).on('click', '.ai-session-action.rename', function (e) { e.stopPropagation(); renameSession($(this).data('session-id')); });
        $(document).on('click', '.ai-session-action.pin', function (e) { e.stopPropagation(); togglePinSession($(this).data('session-id')); });
        $(document).on('click', '.ai-session-action.delete', function (e) { e.stopPropagation(); deleteSession($(this).data('session-id')); });
    });
})(jQuery);

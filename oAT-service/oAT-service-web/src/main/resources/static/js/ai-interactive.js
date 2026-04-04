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

    function nowText() {
        var now = new Date();
        return String(now.getHours()).padStart(2, '0') + ':'
            + String(now.getMinutes()).padStart(2, '0') + ':'
            + String(now.getSeconds()).padStart(2, '0');
    }

    function createId() {
        return 'session-' + Date.now() + '-' + Math.floor(Math.random() * 10000);
    }

    $(function () {
        var $root = $('#aiInteractivePage');
        if ($root.length === 0) {
            return;
        }

        var askUrl = $root.data('ask-url');
        var assistantName = $root.data('assistant-name') || 'AI';
        var mascotPrimary = $root.data('mascot-primary') || '#00b5ad';
        var projectId = $root.data('project-id') || 'default';
        var projectName = $root.data('project-name') || '当前项目';
        var legacyHistoryKey = 'ai-interactive-history:' + projectId;
        var sessionsKey = 'ai-interactive-sessions:' + projectId;
        var activeSessionKey = 'ai-interactive-active-session:' + projectId;
        var sessionSortKey = 'ai-interactive-session-sort:' + projectId;

        var $messageList = $('#aiMessageList');
        var $questionInput = $('#aiQuestionInput');
        var $sendButton = $('#aiSendButton');
        var $requestState = $('#aiRequestState');
        var $followUpList = $('#aiFollowUpList');
        var $quickLinkList = $('#aiQuickLinkList');
        var $signalLights = $('#aiSignalLights');
        var $timelineList = $('#aiTimelineList');
        var $sessionList = $('#aiSessionList');
        var $newSessionButton = $('#aiNewSessionButton');
        var $sessionSearchInput = $('#aiSessionSearchInput');
        var $sessionSortSelect = $('#aiSessionSortSelect');
        var mouseX = window.innerWidth / 2;
        var mouseY = window.innerHeight / 2;
        var sessions = [];
        var activeSessionId = null;

        function initMascotCanvas() {
            var canvas = document.getElementById('aiMascotCanvas');
            if (!canvas) {
                return;
            }

            var ctx = canvas.getContext('2d');
            var width = canvas.width;
            var height = canvas.height;
            var mascot = {
                x: width / 2,
                y: height / 2 - 4,
                radius: 72,
                color: mascotPrimary
            };
            // Parse mascotPrimary to RGB for particle accents (consistent with floating widget)
            var mRgb = {
                r: parseInt(mascotPrimary.slice(1,3), 16) || 0,
                g: parseInt(mascotPrimary.slice(3,5), 16) || 181,
                b: parseInt(mascotPrimary.slice(5,7), 16) || 173
            };
            var particles = [];

            for (var i = 0; i < 18; i++) {
                particles.push({
                    angle: (Math.PI * 2 / 18) * i,
                    radius: 92 + Math.random() * 30,
                    size: 2 + Math.random() * 3,
                    speed: 0.002 + Math.random() * 0.002
                });
            }

            $(window).on('mousemove.aiMascot', function (event) {
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
                ctx.translate(0, Math.sin(Date.now() / 500) * 3);

                ctx.save();
                ctx.strokeStyle = 'rgba(148, 163, 184, 0.25)';
                ctx.setLineDash([6, 6]);
                ctx.beginPath();
                ctx.arc(0, 0, 96, 0, Math.PI * 2);
                ctx.stroke();
                ctx.beginPath();
                ctx.arc(0, 0, 118, 0, Math.PI * 2);
                ctx.stroke();
                ctx.restore();

                particles.forEach(function (particle, index) {
                    var angle = particle.angle + Date.now() * particle.speed * (index % 2 === 0 ? 1 : -1);
                    var pxOrbit = Math.cos(angle) * particle.radius;
                    var pyOrbit = Math.sin(angle) * (particle.radius * 0.45);
                    ctx.beginPath();
                    ctx.fillStyle = index % 2 === 0 ? 'rgba(255,255,255,0.95)' : 'rgba(' + mRgb.r + ',' + mRgb.g + ',' + mRgb.b + ',0.22)';
                    ctx.arc(pxOrbit, pyOrbit, particle.size, 0, Math.PI * 2);
                    ctx.fill();
                });

                ctx.beginPath();
                ctx.ellipse(0, 86, 78, 16, 0, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(15, 23, 42, 0.08)';
                ctx.fill();

                ctx.beginPath();
                ctx.ellipse(0, 0, mascot.radius, mascot.radius * 0.9, 0, 0, Math.PI * 2);
                ctx.fillStyle = mascot.color;
                ctx.fill();

                ctx.beginPath();
                ctx.arc(-mascot.radius * 0.48, -mascot.radius * 0.52, mascot.radius * 0.16, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255,255,255,0.2)';
                ctx.fill();

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

                ctx.fillStyle = 'black';
                var pupilSize = eyeSize * 0.5;
                var px = Math.cos(lookAngle) * eyeSize * 0.4;
                var py = Math.sin(lookAngle) * eyeSize * 0.4;

                ctx.beginPath();
                ctx.arc(-eyeOffsetX + px, eyeOffsetY + py, pupilSize, 0, Math.PI * 2);
                ctx.fill();
                ctx.beginPath();
                ctx.arc(eyeOffsetX + px, eyeOffsetY + py, pupilSize, 0, Math.PI * 2);
                ctx.fill();

                ctx.strokeStyle = 'rgba(0,0,0,0.4)';
                ctx.lineWidth = 3;
                ctx.beginPath();
                ctx.arc(0, 10, 18, 0.2, Math.PI - 0.2);
                ctx.stroke();

                ctx.beginPath();
                ctx.moveTo(-18, 70);
                ctx.lineTo(-8, 88);
                ctx.lineTo(-2, 70);
                ctx.moveTo(18, 70);
                ctx.lineTo(8, 88);
                ctx.lineTo(2, 70);
                ctx.strokeStyle = 'rgba(15,23,42,0.22)';
                ctx.lineWidth = 4;
                ctx.lineCap = 'round';
                ctx.stroke();

                ctx.restore();
                window.requestAnimationFrame(draw);
            }

            draw();
        }

        function setSignalState(state) {
            $signalLights.find('.ai-signal-light').removeClass('active');
            var $target = $signalLights.find('[data-state="' + state + '"]');
            if ($target.length) {
                $target.addClass('active');
                // Pulse animation on state change
                $target.find('.dot').css({transform: 'scale(1.6)', transition: 'transform 0.2s ease'});
                setTimeout(function () {
                    $target.find('.dot').css({transform: 'scale(1)', transition: 'transform 0.3s ease'});
                }, 200);
            }
        }

        function initWorkbenchAnimation() {
            var $shell = $('.ai-workbench-shell');
            if (!$shell.length) return;

            // Mouse-following ambient light on the shell
            var $ambient = $('<div class="ai-workbench-ambient"></div>');
            $shell.prepend($ambient);
            $ambient.css({
                position: 'absolute',
                width: '400px',
                height: '400px',
                borderRadius: '50%',
                background: 'radial-gradient(circle, rgba(' + hexToRgb(mascotPrimary) + ',0.08) 0%, transparent 70%)',
                pointerEvents: 'none',
                zIndex: 0,
                opacity: 0,
                transition: 'opacity 0.4s ease, left 0.15s ease-out, top 0.15s ease-out'
            });

            $(document).on('mousemove.aiAmbient', function (e) {
                var offset = $shell.offset();
                var x = e.clientX - offset.left - 200;
                var y = e.clientY - offset.top - 200;
                // Only show when mouse is inside the shell area
                if (e.clientX >= offset.left && e.clientX <= offset.left + $shell.outerWidth() &&
                    e.clientY >= offset.top && e.clientY <= offset.top + $shell.outerHeight()) {
                    $ambient.css({left: x, top: y, opacity: 1});
                } else {
                    $ambient.css({opacity: 0});
                }
            });

            // Ripple effect on send button click
            $sendButton.on('mousedown', function () {
                createRipple($(this), mascotPrimary);
            });

            // Dock chip hover glow
            $(document).on('mouseenter', '.ai-dock-chip', function () {
                $(this).css({
                    boxShadow: '0 0 20px ' + hexToRgba(mascotPrimary, 0.25) + ', inset 0 0 12px ' + hexToRgba(mascotPrimary, 0.06),
                    borderColor: hexToRgba(mascotPrimary, 0.35)
                });
            }).on('mouseleave', '.ai-dock-chip', function () {
                $(this).css({boxShadow: '', borderColor: ''});
            });
        }

        function hexToRgb(hex) {
            hex = String(hex || '#14b8a6').replace('#', '');
            if (hex.length === 3) hex = hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2];
            var r = parseInt(hex.substring(0, 2), 16) || 20;
            var g = parseInt(hex.substring(2, 4), 16) || 184;
            var b = parseInt(hex.substring(4, 6), 16) || 166;
            return r + ',' + g + ',' + b;
        }

        function hexToRgba(hex, alpha) {
            return 'rgba(' + hexToRgb(hex) + ',' + alpha + ')';
        }

        function createRipple($el, color) {
            var ripple = $('<span class="ai-btn-ripple"></span>');
            var rect = $el[0].getBoundingClientRect();
            var size = Math.max(rect.width, rect.height) * 2;
            var x = event.clientX - rect.left - size / 2;
            var y = event.clientY - rect.top - size / 2;
            ripple.css({
                position: 'absolute',
                width: size + 'px',
                height: size + 'px',
                borderRadius: '50%',
                background: 'rgba(' + hexToRgb(color) + ',0.25)',
                transform: 'scale(0)',
                left: x + 'px',
                top: y + 'px',
                pointerEvents: 'none',
                animation: 'aiBtnRipple 0.5s ease-out forwards'
            });
            $el.css({position: 'relative', overflow: 'hidden'}).append(ripple);
            setTimeout(function () { ripple.remove(); }, 500);
        }

        function setRequestState(text, disabled) {
            $requestState.text(text);
            $sendButton.prop('disabled', disabled);
            $questionInput.prop('disabled', disabled);
        }

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

        function createBaseSession() {
            var welcomeMessage = $('#aiWelcomeMessage').val();
            return {
                id: createId(),
                title: '新会话',
                pinned: false,
                updatedAt: Date.now(),
                quickLinks: [],
                suggestions: [],
                history: [{
                    role: 'assistant',
                    title: assistantName,
                    message: welcomeMessage,
                    actions: []
                }],
                timeline: [
                    {time: nowText(), title: '工作台上线', desc: '动态工作台已连接当前项目上下文'},
                    {time: nowText(), title: '项目信号已载入', desc: projectName}
                ]
            };
        }

        function migrateLegacyHistory() {
            var legacyHistory = readJSON(legacyHistoryKey, []);
            if (legacyHistory.length) {
                var session = createBaseSession();
                session.history = legacyHistory;
                session.title = '历史会话';
                session.updatedAt = Date.now();
                sessions = [session];
                activeSessionId = session.id;
                persistSessions();
                sessionStorage.removeItem(legacyHistoryKey);
                return;
            }
            var freshSession = createBaseSession();
            sessions = [freshSession];
            activeSessionId = freshSession.id;
            persistSessions();
        }

        function loadSessions() {
            sessions = readJSON(sessionsKey, []);
            activeSessionId = sessionStorage.getItem(activeSessionKey);
            $sessionSortSelect.val(getSessionSortMode());
            $.each(sessions, function (_, session) {
                session.pinned = !!session.pinned;
            });
            if (!sessions.length) {
                migrateLegacyHistory();
                return;
            }
            if (!activeSessionId || !findSession(activeSessionId)) {
                activeSessionId = sessions[0].id;
                sessionStorage.setItem(activeSessionKey, activeSessionId);
            }
        }

        function persistSessions() {
            writeJSON(sessionsKey, sessions);
            sessionStorage.setItem(activeSessionKey, activeSessionId);
        }

        function findSession(id) {
            for (var i = 0; i < sessions.length; i++) {
                if (sessions[i].id === id) {
                    return sessions[i];
                }
            }
            return null;
        }

        function getActiveSession() {
            return findSession(activeSessionId);
        }

        function updateSessionTitle(session, fallbackQuestion) {
            if (!session) {
                return;
            }
            if (session.title === '新会话' || session.title === '历史会话') {
                session.title = (fallbackQuestion || '新会话').substring(0, 18);
            }
        }

        function renderActions(actions) {
            if (!actions || !actions.length) {
                return '';
            }
            var html = '<div class="ai-message-actions">';
            $.each(actions, function (_, action) {
                html += '<button class="ai-action-btn" data-question="' + escapeHtml(action) + '">' + escapeHtml(action) + '</button>';
            });
            html += '</div>';
            return html;
        }

        function typewriterText($target, message, done) {
            var safeMessage = escapeHtml(message || '');
            var index = 0;
            function tick() {
                index = Math.min(index + 2, safeMessage.length);
                $target.html(safeMessage.substring(0, index).replace(/\n/g, '<br>') + '<span class="ai-typing-cursor"></span>');
                $messageList.scrollTop($messageList[0].scrollHeight);
                if (index >= safeMessage.length) {
                    $target.html(safeMessage.replace(/\n/g, '<br>'));
                    if (done) {
                        done();
                    }
                    return;
                }
                window.setTimeout(tick, 12);
            }
            tick();
        }

        function appendMessage(role, title, message, actions, options) {
            options = options || {};
            var avatar = role === 'assistant' ? assistantName.substring(0, 1) : '我';
            var contentHtml = options.animate ? '<div class="ai-message-text"></div>' : formatMessage(message);
            var $node = $('<div class="ai-message ' + role + '">'
                + '<div class="ai-message-avatar">' + escapeHtml(avatar) + '</div>'
                + '<div class="ai-message-body">'
                + '<div class="ai-message-name">' + escapeHtml(title) + '</div>'
                + '<div class="ai-message-card">' + contentHtml + (options.animate ? '' : renderActions(actions)) + '</div>'
                + '</div>'
                + '</div>');
            $messageList.append($node);
            $messageList.scrollTop($messageList[0].scrollHeight);
            if (options.animate) {
                var $card = $node.find('.ai-message-card');
                typewriterText($card.find('.ai-message-text'), message, function () {
                    if (actions && actions.length) {
                        $card.append(renderActions(actions));
                    }
                });
            }
        }

        function renderQuickLinks(links) {
            if (!links || !links.length) {
                $quickLinkList.empty();
                return;
            }
            var html = '';
            $.each(links, function (_, link) {
                html += '<a class="ai-quick-link-card" href="' + escapeHtml(link.url || '#') + '">'
                    + '<div class="ai-quick-link-title">' + escapeHtml(link.title || '推荐入口') + '</div>'
                    + '<div class="ai-quick-link-desc">' + escapeHtml(link.description || '') + '</div>'
                    + '</a>';
            });
            $quickLinkList.html(html);
        }

        function renderFollowUps(actions) {
            if (!actions || !actions.length) {
                $followUpList.empty();
                return;
            }
            var html = '';
            $.each(actions, function (_, action) {
                html += '<button class="ui button basic fluid small starter-question" data-question="' + escapeHtml(action) + '">' + escapeHtml(action) + '</button>';
            });
            $followUpList.html(html);
        }

        function renderTimeline(session) {
            $timelineList.empty();
            $.each((session.timeline || []).slice().reverse(), function (_, item) {
                $timelineList.append('<div class="ai-timeline-item">'
                    + '<div class="ai-timeline-time">' + escapeHtml(item.time) + '</div>'
                    + '<div class="ai-timeline-title">' + escapeHtml(item.title) + '</div>'
                    + '<div class="ai-timeline-desc">' + escapeHtml(item.desc) + '</div>'
                    + '</div>');
            });
        }

        function getSessionSortMode() {
            var mode = sessionStorage.getItem(sessionSortKey) || 'recent';
            if (['recent', 'oldest', 'name'].indexOf(mode) === -1) {
                return 'recent';
            }
            return mode;
        }

        function getSessionPreview(session) {
            for (var i = (session.history || []).length - 1; i >= 0; i--) {
                if (session.history[i].role === 'assistant') {
                    return session.history[i].message || '';
                }
            }
            return '';
        }

        function sortSessions(list) {
            var sortMode = getSessionSortMode();
            return list.slice().sort(function (a, b) {
                if (!!a.pinned !== !!b.pinned) {
                    return a.pinned ? -1 : 1;
                }
                if (sortMode === 'oldest') {
                    return (a.updatedAt || 0) - (b.updatedAt || 0);
                }
                if (sortMode === 'name') {
                    var compare = (a.title || '新会话').localeCompare(b.title || '新会话', 'zh-Hans-CN');
                    return compare !== 0 ? compare : (b.updatedAt || 0) - (a.updatedAt || 0);
                }
                return (b.updatedAt || 0) - (a.updatedAt || 0);
            });
        }

        function matchSessionKeyword(session, keyword) {
            if (!keyword) {
                return true;
            }
            var haystack = (session.title || '') + ' ' + getSessionPreview(session);
            $.each(session.history || [], function (_, item) {
                if (item && item.message) {
                    haystack += ' ' + item.message;
                }
            });
            return haystack.toLowerCase().indexOf(keyword) > -1;
        }

        function buildSessionItemHtml(session) {
            var preview = getSessionPreview(session);
            var timeStr = escapeHtml(new Date(session.updatedAt).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'}));
            return '<div class="ai-session-item ' + (session.id === activeSessionId ? 'active' : '') + '" data-session-id="' + escapeHtml(session.id) + '">'
                + '<div class="ai-session-top">'
                + '<div class="ai-session-title" title="' + escapeHtml(session.title || '新会话') + '">' + escapeHtml(session.title || '新会话') + '</div>'
                + '<div class="ai-session-actions">'
                + '<span class="ai-session-time">' + timeStr + '</span>'
                + '<button class="ai-session-action pin ' + (session.pinned ? 'pinned' : '') + '" type="button" data-session-id="' + escapeHtml(session.id) + '" title="置顶"><i class="thumbtack icon"></i></button>'
                + '<button class="ai-session-action rename" type="button" data-session-id="' + escapeHtml(session.id) + '" title="重命名"><i class="edit outline icon"></i></button>'
                + '<button class="ai-session-action delete" type="button" data-session-id="' + escapeHtml(session.id) + '" title="删除"><i class="trash alternate outline icon"></i></button>'
                + '</div>'
                + '</div>'
                + '<div class="ai-session-preview">' + escapeHtml((preview || '从这里继续你的 AI 交互工作台').substring(0, 80)) + '</div>'
                + '</div>';
        }

        function renderSessionSection(label, items) {
            if (!items.length) {
                return;
            }
            var $section = $('<div class="ai-session-section"></div>');
            $section.append('<div class="ai-session-section-label"><span>' + escapeHtml(label) + '</span><span class="ai-session-section-count">' + items.length + '</span></div>');
            $.each(items, function (_, session) {
                $section.append(buildSessionItemHtml(session));
            });
            $sessionList.append($section);
        }

        function renderSessionList() {
            $sessionList.empty();
            var keyword = $.trim($sessionSearchInput.val() || '').toLowerCase();
            var sortedSessions = sortSessions(sessions).filter(function (session) {
                return matchSessionKeyword(session, keyword);
            });
            if (!sortedSessions.length) {
                $sessionList.html('<div class="ai-session-empty">没有匹配的会话，换个关键词试试。</div>');
                return;
            }
            renderSessionSection('置顶会话', sortedSessions.filter(function (session) {
                return session.pinned;
            }));
            renderSessionSection('最近会话', sortedSessions.filter(function (session) {
                return !session.pinned;
            }));
        }

        function renderActiveSession() {
            var session = getActiveSession();
            if (!session) {
                return;
            }
            $messageList.empty();
            $.each(session.history || [], function (_, item) {
                appendMessage(item.role, item.title, item.message, item.actions || [], {animate: false});
            });
            renderQuickLinks(session.quickLinks || []);
            renderFollowUps(session.suggestions || []);
            renderTimeline(session);
            renderSessionList();
            setSignalState('online');
            $questionInput.focus();
        }

        function addTimeline(title, description) {
            var session = getActiveSession();
            if (!session) {
                return;
            }
            session.timeline = session.timeline || [];
            session.timeline.push({
                time: nowText(),
                title: title,
                desc: description
            });
            if (session.timeline.length > 8) {
                session.timeline = session.timeline.slice(session.timeline.length - 8);
            }
            renderTimeline(session);
        }

        function saveMessage(entry) {
            var session = getActiveSession();
            if (!session) {
                return;
            }
            session.history.push(entry);
            session.updatedAt = Date.now();
            persistSessions();
            renderSessionList();
        }

        function createNewSession() {
            var session = createBaseSession();
            sessions.unshift(session);
            activeSessionId = session.id;
            persistSessions();
            renderActiveSession();
        }

        function showToast(message, type) {
            type = type || 'info';
            var iconMap = {info: 'info circle', success: 'check circle', warning: 'warning', error: 'times circle'};
            var colorMap = {info: '#0f766e', success: '#16a34a', warning: '#d97706', error: '#dc2626'};
            var $toast = $('<div class="ai-toast ai-toast-' + type + '">'
                + '<i class="' + (iconMap[type] || iconMap.info) + ' icon"></i>'
                + '<span>' + escapeHtml(message) + '</span>'
                + '</div>');
            $toast.css({
                position: 'fixed',
                top: '20px',
                left: '50%',
                transform: 'translateX(-50%)',
                zIndex: 99999,
                padding: '10px 20px',
                borderRadius: '10px',
                background: colorMap[type] || colorMap.info,
                color: '#ffffff',
                fontSize: '14px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
                opacity: '0',
                transition: 'opacity 0.25s ease, transform 0.25s ease',
                maxWidth: '360px'
            });
            $('body').append($toast);
            requestAnimationFrame(function () {
                $toast.css({opacity: '1', transform: 'translateX(-50%) translateY(0)'});
            });
            setTimeout(function () {
                $toast.css({opacity: '0', transform: 'translateX(-50%) translateY(-8px)'});
                setTimeout(function () { $toast.remove(); }, 280);
            }, 2500);
        }

        function showConfirmToast(message, onConfirm) {
            var $overlay = $('<div class="ai-confirm-overlay"></div>');
            $overlay.css({
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                zIndex: 99998,
                background: 'rgba(0,0,0,0.2)'
            });
            var $toast = $('<div class="ai-confirm-toast">'
                + '<div class="ai-confirm-message">' + escapeHtml(message) + '</div>'
                + '<div class="ai-confirm-actions">'
                + '<button class="ai-confirm-btn ai-confirm-cancel" type="button">取消</button>'
                + '<button class="ai-confirm-btn ai-confirm-ok" type="button">确认</button>'
                + '</div>'
                + '</div>');
            $toast.css({
                position: 'fixed',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                zIndex: 99999,
                padding: '20px 24px',
                borderRadius: '14px',
                background: '#ffffff',
                boxShadow: '0 16px 48px rgba(0,0,0,0.18)',
                minWidth: '280px',
                textAlign: 'center'
            });
            $('body').append($overlay).append($toast);
            function close(confirm) {
                $overlay.remove();
                $toast.remove();
                if (confirm && onConfirm) {
                    onConfirm();
                }
            }
            $toast.find('.ai-confirm-cancel').on('click', function () { close(false); });
            $toast.find('.ai-confirm-ok').on('click', function () { close(true); });
            $overlay.on('click', function () { close(false); });
        }

        function renameSession(sessionId) {
            var session = findSession(sessionId);
            if (!session) {
                return;
            }
            var $item = $('.ai-session-item[data-session-id="' + sessionId + '"]');
            var $titleEl = $item.find('.ai-session-title');
            var oldTitle = session.title || '新会话';
            var $input = $('<input type="text" class="ai-session-rename-input" value="' + escapeHtml(oldTitle) + '" maxlength="24" />');
            $titleEl.empty().append($input);
            $input.css({
                width: '100%',
                border: '1px solid #0f766e',
                borderRadius: '6px',
                padding: '2px 6px',
                fontSize: 'inherit',
                outline: 'none',
                boxSizing: 'border-box'
            }).focus().select();

            function finishRename() {
                var newName = $.trim($input.val());
                if (newName && newName !== oldTitle) {
                    session.title = newName.substring(0, 24);
                    session.updatedAt = Date.now();
                    persistSessions();
                    showToast('会话已重命名为"' + escapeHtml(session.title) + '"', 'success');
                }
                renderSessionList();
            }

            $input.on('keydown', function (event) {
                if (event.keyCode === 13) {
                    event.preventDefault();
                    finishRename();
                } else if (event.keyCode === 27) {
                    renderSessionList();
                }
            });
            $input.on('blur', finishRename);
        }

        function togglePinSession(sessionId) {
            var session = findSession(sessionId);
            if (!session) {
                return;
            }
            session.pinned = !session.pinned;
            session.updatedAt = Date.now();
            persistSessions();
            renderSessionList();
        }

        function deleteSession(sessionId) {
            var session = findSession(sessionId);
            if (!session) {
                return;
            }
            showConfirmToast('确认删除会话 "' + (session.title || '新会话') + '" 吗？', function () {
                sessions = sessions.filter(function (s) {
                    return s.id !== sessionId;
                });
                if (!sessions.length) {
                    var freshSession = createBaseSession();
                    sessions = [freshSession];
                    activeSessionId = freshSession.id;
                    persistSessions();
                    renderActiveSession();
                    return;
                }
                if (activeSessionId === sessionId) {
                    activeSessionId = sortSessions(sessions)[0].id;
                }
                persistSessions();
                renderActiveSession();
                showToast('会话已删除', 'success');
            });
        }

        function sendQuestion(rawQuestion) {
            var question = $.trim(rawQuestion || $questionInput.val());
            if (!question) {
                $questionInput.focus();
                return;
            }
            var session = getActiveSession();
            if (!session) {
                createNewSession();
                session = getActiveSession();
            }

            appendMessage('user', '你', question);
            updateSessionTitle(session, question);
            saveMessage({
                role: 'user',
                title: '你',
                message: question,
                actions: []
            });
            addTimeline('收到提问', question);
            $questionInput.val('');
            setSignalState('thinking');
            setRequestState('思考中...', true);

            $.ajax({
                url: askUrl,
                type: 'POST',
                dataType: 'json',
                data: {
                    question: question
                }
            }).done(function (response) {
                if (!response || response.success === false || response.result === false) {
                    var failMessage = (response && response.message) || '当前无法完成分析，请稍后重试。';
                    appendMessage('assistant', assistantName, failMessage, [], {animate: true});
                    saveMessage({
                        role: 'assistant',
                        title: assistantName,
                        message: failMessage,
                        actions: []
                    });
                    addTimeline('分析失败', failMessage);
                    setSignalState('online');
                    return;
                }

                var data = response.data || {};
                var replyMessage = data.answer || response.message || '已收到你的问题。';
                appendMessage('assistant', assistantName, replyMessage, data.suggestions || [], {animate: true});
                session.quickLinks = data.quickLinks || [];
                session.suggestions = data.suggestions || [];
                renderQuickLinks(session.quickLinks);
                renderFollowUps(session.suggestions);
                saveMessage({
                    role: 'assistant',
                    title: assistantName,
                    message: replyMessage,
                    actions: data.suggestions || [],
                    quickLinks: data.quickLinks || []
                });
                addTimeline('生成回复', data.topic || 'general');
                setSignalState('reply');
            }).fail(function () {
                var errorMessage = '请求失败了，请稍后再试，或者换一个更具体的问题。';
                appendMessage('assistant', assistantName, errorMessage, [], {animate: true});
                saveMessage({
                    role: 'assistant',
                    title: assistantName,
                    message: errorMessage,
                    actions: []
                });
                addTimeline('请求异常', '请稍后重试或更换问题描述');
                setSignalState('online');
            }).always(function () {
                session.updatedAt = Date.now();
                persistSessions();
                renderSessionList();
                setRequestState('就绪', false);
                window.setTimeout(function () {
                    setSignalState('online');
                }, 900);
                $questionInput.focus();
            });
        }

        initMascotCanvas();
        loadSessions();
        renderActiveSession();
        initWorkbenchAnimation();

        $sendButton.on('click', function () {
            sendQuestion();
        });

        $newSessionButton.on('click', function () {
            createNewSession();
        });

        $sessionSearchInput.on('input', function () {
            renderSessionList();
        });

        $sessionSortSelect.on('change', function () {
            sessionStorage.setItem(sessionSortKey, $(this).val());
            renderSessionList();
        });

        $questionInput.on('keydown', function (event) {
            if (event.keyCode === 13 && !event.shiftKey) {
                event.preventDefault();
                sendQuestion();
            }
        });

        $(document).on('click', '.starter-question, .ai-action-btn', function () {
            sendQuestion($(this).data('question'));
        });

        $(document).on('click', '.ai-session-item', function () {
            activeSessionId = $(this).data('session-id');
            persistSessions();
            renderActiveSession();
        });

        $(document).on('click', '.ai-session-action.rename', function (event) {
            event.stopPropagation();
            renameSession($(this).data('session-id'));
        });

        $(document).on('click', '.ai-session-action.pin', function (event) {
            event.stopPropagation();
            togglePinSession($(this).data('session-id'));
        });

        $(document).on('click', '.ai-session-action.delete', function (event) {
            event.stopPropagation();
            deleteSession($(this).data('session-id'));
        });
    });
})(jQuery);

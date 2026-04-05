/**
 * AI Unified Component — Shared Utilities
 *
 * 全局公共模块，被 ai-floating-widget.js 和 ai-interactive.js 共用。
 * 通过 window.AiUtils 暴露接口（兼容传统 <script> 加载方式）。
 *
 * 设计原则：
 *   - 工厂函数模式：createImageHandler(cfg)、createVoiceRecorder(cfg) 通过配置对象适配两种模式的 DOM 选择器差异
 *   - 无 jQuery 依赖的核心工具放在最前面
 *   - 需要 jQuery 的功能在文档就绪后延迟初始化
 */
(function (window) {
    'use strict';

    // ============================================================
    //  1. 纯函数工具（无依赖）
    // ============================================================

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
        var n = new Date();
        return [n.getHours(), n.getMinutes(), n.getSeconds()]
            .map(function (v) { return String(v).padStart(2, '0'); })
            .join(':');
    }

    function createId() {
        return 'ai-' + Date.now() + '-' + Math.floor(Math.random() * 10000);
    }

    /**
     * hex → "r,g,b" 字符串
     * @param {string} hex 如 "#14b8a6" 或 "#abc"
     */
    function hexToRgb(hex) {
        hex = String(hex || '#14b8a6').replace('#', '');
        if (hex.length === 3) hex = hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2];
        var r = parseInt(hex.substring(0, 2), 16) || 20;
        var g = parseInt(hex.substring(2, 4), 16) || 184;
        var b = parseInt(hex.substring(4, 6), 16) || 166;
        return r + ',' + g + ',' + b;
    }

    /** hex + alpha → rgba(...) */
    function hexToRgba(hex, alpha) {
        return 'rgba(' + hexToRgb(hex) + ',' + alpha + ')';
    }

    function formatDuration(totalSec) {
        var m = Math.floor(totalSec / 60);
        var s = totalSec % 60;
        return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
    }

    // ============================================================
    //  2. 存储工具
    // ============================================================

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

    // ============================================================
    //  3. 图片上传处理器（工厂）
    //
    //  用法:
    //    var imgHandler = AiUtils.createImageHandler({
    //        $btn: $('#myImageUploadBtn'),
    //        inputId: 'myImageInput',
    //        previewClass: '.my-image-preview',   // 相对于 $btn 查找
    //        maxSizeMB: 10,
    //        onLoaded: function(dataUrl) {},       // 可选回调
    //        onRemoved: function() {}              // 可选回调
   //    });
    //    imgHandler.init();
    //    imgHandler.getData() → dataUrl | null
    //    imgHandler.clear()
    // ============================================================

    function createImageHandler(cfg) {
        cfg = cfg || {};
        var $btn = cfg.$btn;
        var inputSelector = '#' + (cfg.inputId || 'aiImageInput');
        var previewSelector = cfg.previewClass || '.ai-image-preview';
        var maxSize = (cfg.maxSizeMB || 10) * 1024 * 1024;
        var currentData = null;

        function getData() { return currentData; }
        function setData(url) { currentData = url; }
        function clear() {
            currentData = null;
            if ($btn && $btn.length) {
                $btn.removeClass('has-image').find(previewSelector).hide().attr('src', '');
            }
        }

        function handleFile(file) {
            if (!file || file.size > maxSize) {
                if (typeof cfg.showToast === 'function') {
                    showToast('图片不能超过 ' + (maxSize / 1024 / 1024) + 'MB', 'warning');
                }
                return;
            }
            var reader = new FileReader();
            reader.onload = function (e) {
                currentData = e.target.result;
                if ($btn && $btn.length) {
                    $btn.addClass('has-image');
                    $btn.find(previewSelector).attr('src', currentData).show();
                }
                if (typeof cfg.onLoaded === 'function') {
                    cfg.onLoaded(currentData, file);
                }
            };
            reader.readAsDataURL(file);
        }

        function init($) {
            // 点击按钮：有图片→预览；无图片→打开文件选择
            $btn.on('click', function (e) {
                if ($btn.hasClass('has-image')) {
                    e.preventDefault();
                    var src = $btn.find(previewSelector).attr('src');
                    if (src) { $btn.find(previewSelector).trigger('click'); }
                    return;
                }
                $(inputSelector).trigger('click');
            });

            // 文件选择变化
            $(inputSelector).on('change', function () {
                var f = this.files[0];
                if (!f) return;
                if (!f.type.startsWith('image/')) {
                    showToast('仅支持图片文件', 'warning');
                    return;
                }
                handleFile(f);
                this.value = '';
            });

            // 关闭按钮移除图片
            $(document).on('click', $btn.find('.ai-image-remove-btn').selector || '.ai-image-remove-btn', function (e) {
                e.stopPropagation(); e.preventDefault();
                clear();
                if (typeof cfg.onRemoved === 'function') { cfg.onRemoved(); }
            });
        }

        return { init: init, getData: getData, setData: setData, clear: clear, handleFile: handleFile };
    }

    // ============================================================
    //  4. 语音录制处理器（工厂）
    // ============================================================

    function createVoiceRecorder(cfg) {
        cfg = cfg || {};
        var $btn = cfg.$btn;
        var $questionInput = cfg.$questionInput;
        var isRecording = false;
        var mediaRecorder = null;
        var chunks = [];
        var startTime = 0;
        var timerInterval = null;

        function start() {
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                showToast('当前浏览器不支持语音输入', 'error');
                return;
            }
            navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
                mediaRecorder = new MediaRecorder(stream);
                chunks = [];
                isRecording = true;
                startTime = Date.now();
                $btn.addClass('recording').find('i').removeClass('microphone').addClass('stop');

                mediaRecorder.ondataavailable = function (e) { chunks.push(e); };
                mediaRecorder.onstop = function () {
                    stream.getTracks().forEach(function (t) { t.stop(); });
                    if (chunks.length > 0) {
                        var prefix = ($questionInput ? $.trim($questionInput.val()) : '') || '';
                        $questionInput.val(prefix + (prefix ? '\n' : '') +
                            '[语音片段 ' + formatDuration(Math.round((Date.now() - startTime) / 1000)) +
                            '] （语音转文字功能待后端接入ASR）');
                        if ($questionInput) { $questionInput.focus(); }
                        showToast('语音已录制，当前为模拟模式。完整语音识别需后端支持。', 'info');
                    }
                    chunks = [];
                };

                mediaRecorder.start();

                timerInterval = setInterval(function () {
                    $btn.attr('title', '录音中... ' + formatDuration(Math.round((Date.now() - startTime) / 1000)) + ' (点击结束)');
                }, 500);

            }).catch(function (err) {
                showToast('无法访问麦克风：' + (err.message || '权限被拒绝'), 'error');
            });
        }

        function stop() {
            isRecording = false;
            $btn.removeClass('recording').find('i').removeClass('stop').addClass('microphone');
            $btn.attr('title', '语音输入');
            if (timerInterval) { clearInterval(timerInterval); timerInterval = null; }
            if (mediaRecorder && mediaRecorder.state !== 'inactive') { mediaRecorder.stop(); }
        }

        function toggle() {
            isRecording ? stop() : start();
        }

        return { init: function () {
            $btn.on('click', function () { toggle(); });
        }, toggle: toggle, stop: stop, isRecording: function () { return isRecording; } };
    }

    // ============================================================
    //  5. 全屏预览弹窗 (Lightbox)
    // ============================================================

    function openLightbox(src) {
        if (!src) return;
        var $overlay = $('<div class="ai-lightbox-overlay" style="position:fixed;top:0;left:0;right:0;bottom:0;z-index:99999;background:rgba(0,0,0,0.75);display:flex;align-items:center;justify-content:center;cursor:pointer;opacity:0;transition:opacity 0.2s ease;">'
            + '<img src="' + escapeHtml(src) + '" style="max-width:90vw;max-height:90vh;border-radius:12px;box-shadow:0 16px 48px rgba(0,0,0,0.4);object-fit:contain;" alt="图片预览">'
            + '<div style="position:absolute;top:16px;right:16px;display:flex;gap:8px;">'
            + '<span class="ai-lightbox-hint" style="color:rgba(255,255,255,0.6);font-size:12px;background:rgba(0,0,0,0.5);padding:4px 10px;border-radius:999px;pointer-events:none;">点击任意处关闭</span>'
            + '</div></div>');
        $('body').append($overlay);
        requestAnimationFrame(function () { $overlay.css({ opacity: '1' }); });
        $overlay.on('click', function () {
            $overlay.css({ opacity: '0' });
            setTimeout(function () { $overlay.remove(); }, 200);
        });
    }

    /**
     * 初始化全局 lightbox 事件委托（只需调用一次）
     * @param {jQuery} $
     * @param {string} [selector='.ai-image-preview,.ai-floating-image-preview']
     */
    function initGlobalLightbox($, selector) {
        selector = selector || '.ai-image-preview, .ai-floating-image-preview';
        $(document).on('click.aiLightbox', selector, function (e) {
            e.stopPropagation();
            e.preventDefault();
            openLightbox($(this).attr('src'));
        });
    }

    // ============================================================
    //  6. 复制到剪贴板
    // ============================================================

    function bindCopyButton($btn, text) {
        $btn.on('click', function (e) {
            e.stopPropagation();
            var rawText = text || '';
            var plainText = rawText.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '').trim();
            if (!plainText || navigator.clipboard === undefined) {
                showToast('无法复制内容', 'warning');
                return;
            }
            navigator.clipboard.writeText(plainText).then(function () {
                var $b = $(this);
                $b.addClass('copied').html('<i class="check icon"></i>');
                showToast('已复制到剪贴板', 'success');
                setTimeout(function () { $b.removeClass('copied').html('<i class="copy icon"></i>'); }, 1800);
            }.bind(this)).catch(function () {
                var ta = document.createElement('textarea');
                ta.value = plainText;
                document.body.appendChild(ta);
                ta.select();
                try { document.execCommand('copy'); } catch(e2) {}
                document.body.removeChild(ta);
                $(this).addClass('copied').html('<i class="check icon"></i>');
                setTimeout(function () { $(this).removeClass('copied').html('<i class="copy icon"></i>'); }.bind(this), 1800);
            }.bind(this));
        });
    }

    // ============================================================
    //  7. Toast 通知系统
    // ============================================================

    var _iconMap = { info: 'info circle', success: 'check circle', warning: 'warning', error: 'times circle' };
    var _colorMap = { info: '#0f766e', success: '#16a34a', warning: '#d97706', error: '#dc2626' };

    function showToast(message, type) {
        type = type || 'info';
        var $toast = $('<div class="ai-toast ai-toast-' + type + '">'
            + '<i class="' + (_iconMap[type] || _iconMap.info) + ' icon"></i>'
            + '<span>' + escapeHtml(message) + '</span>'
            + '</div>');
        $toast.css({
            position: 'fixed', top: '20px', left: '50%', transform: 'translateX(-50%)',
            zIndex: 99999, padding: '10px 20px', borderRadius: '10px',
            background: _colorMap[type] || _colorMap.info, color: '#ffffff',
            fontSize: '14px', display: 'flex', alignItems: 'center', gap: '8px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
            opacity: '0', transition: 'opacity 0.25s ease, transform 0.25s ease',
            maxWidth: '360px'
        });
        $('body').append($toast);
        requestAnimationFrame(function () {
            $toast.css({ opacity: '1', transform: 'translateX(-50%) translateY(0)' });
        });
        setTimeout(function () {
            $toast.css({ opacity: '0', transform: 'translateX(-50%) translateY(-8px)' });
            setTimeout(function () { $toast.remove(); }, 280);
        }, 2500);
    }

    // ============================================================
    //  8. 确认对话框
    // ============================================================

    function showConfirm(message, onConfirm) {
        var $overlay = $('<div class="ai-confirm-overlay" style="position:fixed;top:0;left:0;right:0;bottom:0;z-index:99998;background:rgba(0,0,0,0.2)"></div>');
        var $toast = $('<div class="ai-confirm-toast">'
            + '<div class="ai-confirm-message">' + escapeHtml(message) + '</div>'
            + '<div class="ai-confirm-actions">'
            + '<button class="ai-confirm-btn ai-confirm-cancel" type="button">取消</button>'
            + '<button class="ai-confirm-btn ai-confirm-ok" type="button">确认</button>'
            + '</div></div>');
        $toast.css({
            position: 'fixed', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
            zIndex: 99999, padding: '20px 24px', borderRadius: '14px',
            background: '#ffffff', boxShadow: '0 16px 48px rgba(0,0,0,0.18)',
            minWidth: '280px', textAlign: 'center'
        });
        $('body').append($overlay).append($toast);
        function close(confirm) {
            $overlay.remove(); $toast.remove();
            if (confirm && onConfirm) { onConfirm(); }
        }
        $toast.find('.ai-confirm-cancel').on('click', function () { close(false); });
        $toast.find('.ai-confirm-ok').on('click', function () { close(true); });
        $overlay.on('click', function () { close(false); });
    }

    // ============================================================
    //  9. Mascot Canvas 渲染器（参数化尺寸）
    // ============================================================

    /**
     * @param {Object} opts
     * @param {HTMLElement|string} opts.canvas - canvas 元素或 ID
     * @param {string} opts.primaryColor - 主色调如 "#00b5ad"
     * @param {number} [opts.particleCount=8] - 轨道粒子数（浮动版8, 工作台18）
     * @param {number} [opts.orbitRadius=52] - 轨道半径基准（浮动版52, 工作台92）
     * @returns {{start:function, stop:function, setState:function}} 控制句柄
     */
    function createMascotCanvas(opts) {
        opts = opts || {};
        var el = typeof opts.canvas === 'string' ? document.getElementById(opts.canvas) : opts.canvas;
        if (!el) { console.warn('[Mascot] Canvas element not found'); return { start: function () {}, stop: function () {}, setState: function () {} }; }

        var ctx = el.getContext('2d');
        if (!ctx) { console.warn('[Mascot] Cannot get 2D context from canvas', el); return { start: function () {}, stop: function () {}, setState: function () {} }; }

        // 防御：canvas 尺寸可能为 0（DOM 未就绪或 CSS 隐藏），使用属性值或回退到默认尺寸
        var w = el.width || parseInt(el.getAttribute('width'), 10) || 180;
        var h = el.height || parseInt(el.getAttribute('height'), 10) || 180;
        if (w <= 0 || h <= 0) { w = 180; h = 180; }
        var radius = Math.min(w, h) * 0.42;
        var primaryColor = opts.primaryColor || '#00b5ad';
        var particleCount = opts.particleCount || 8;
        var orbitRadius = opts.orbitRadius || 52;
        var rgb = {
            r: parseInt(primaryColor.slice(1, 3), 16) || 0,
            g: parseInt(primaryColor.slice(3, 5), 16) || 181,
            b: parseInt(primaryColor.slice(5, 7), 16) || 173
        };
        var particles = [];
        for (var i = 0; i < particleCount; i++) {
            particles.push({
                angle: (Math.PI * 2 / particleCount) * i,
                radius: orbitRadius + Math.random() * 30,
                size: 2 + Math.random() * 3,
                speed: 0.002 + Math.random() * 0.002
            });
        }
        var mx = window.innerWidth / 2, my = window.innerHeight / 2;
        var rafId = null;

        /* ===== 状态机 ===== */
        var STATE_IDLE = 'idle';
        var STATE_THINKING = 'thinking';
        var STATE_DONE = 'done';
        var currentState = STATE_IDLE;
        var stateEnterTime = Date.now();
        // done 弹跳控制
        var doneBounceCount = 0;
        var doneBounceMax = 3;

        /* ===== 庆祝特效粒子 ===== */
        var celebrationParticles = [];
        var hearts = [];

        $(window).on('mousemove.aiMascot' + w, function (e) { mx = e.clientX; my = e.clientY; });

        /**
         * 设置小人状态
         * @param {'idle'|'thinking'|'done'} state
         */
        function setState(state) {
            if (currentState === state && state !== STATE_DONE) return;
            currentState = state;
            stateEnterTime = Date.now();
            if (state === STATE_DONE) {
                doneBounceCount = 0;
                spawnCelebrationParticles();
                spawnHearts();
            }
        }

        /** 生成庆祝星星粒子 */
        function spawnCelebrationParticles() {
            celebrationParticles = [];
            var starColors = ['#fbbf24', '#f472b6', '#60a5fa', '#34d399', '#a78bfa', '#fb923c'];
            for (var i = 0; i < 14; i++) {
                var angle = (Math.PI * 2 / 14) * i + Math.random() * 0.5;
                var speed = 1.8 + Math.random() * 2.5;
                celebrationParticles.push({
                    x: 0, y: 0,
                    vx: Math.cos(angle) * speed,
                    vy: Math.sin(angle) * speed - 1.5,
                    size: 4 + Math.random() * 5,
                    rotation: Math.random() * Math.PI * 2,
                    rotSpeed: (Math.random() - 0.5) * 0.25,
                    color: starColors[i % starColors.length],
                    life: 1,
                    decay: 0.008 + Math.random() * 0.01,
                    type: 'star'
                });
            }
        }

        /** 生成爱心 */
        function spawnHearts() {
            hearts = [];
            for (var i = 0; i < 5; i++) {
                setTimeout(function () {
                    if (currentState !== STATE_DONE) return;
                    hearts.push({
                        x: -20 + Math.random() * 40,
                        y: -radius * 0.4,
                        vy: -1.2 - Math.random() * 0.8,
                        vx: (Math.random() - 0.5) * 1.2,
                        size: 8 + Math.random() * 7,
                        rotation: Math.random() * 0.4 - 0.2,
                        life: 1,
                        decay: 0.007 + Math.random() * 0.005
                    });
                }, i * 150);
            }
        }

        /** 绘制五角星形状（用于庆祝粒子） */
        function drawStar(cx, cy, size, rot) {
            ctx.save();
            ctx.translate(cx, cy);
            ctx.rotate(rot);
            ctx.beginPath();
            for (var i = 0; i < 5; i++) {
                var ang = (Math.PI * 2 / 5) * i - Math.PI / 2;
                var x = Math.cos(ang) * size;
                var y = Math.sin(ang) * size;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
                var ang2 = ang + Math.PI / 5;
                var x2 = Math.cos(ang2) * size * 0.42;
                var y2 = Math.sin(ang2) * size * 0.42;
                ctx.lineTo(x2, y2);
            }
            ctx.closePath();
            ctx.fill();
            ctx.restore();
        }

        /** 绘制爱心 */
        function drawHeart(x, y, sz, rot, alpha) {
            ctx.save();
            ctx.globalAlpha = alpha;
            ctx.translate(x, y);
            ctx.rotate(rot);
            ctx.scale(sz / 18, sz / 18);
            ctx.fillStyle = '#f472b6';
            ctx.beginPath();
            ctx.moveTo(0, 4);
            ctx.bezierCurveTo(0, 2, -6, 0, -9, -3);
            ctx.bezierCurveTo(-12, -6, -12, -10, -6, -13);
            ctx.bezierCurveTo(-2, -15, 0, -14, 0, -11);
            ctx.bezierCurveTo(0, -14, 2, -15, 6, -13);
            ctx.bezierCurveTo(12, -10, 12, -6, 9, -3);
            ctx.bezierCurveTo(6, 0, 0, 2, 0, 4);
            ctx.fill();
            ctx.restore();
        }

        /** 绘制嘴巴 — 根据状态返回不同的形状参数 */
        function getMouthParams(state, t) {
            if (state === STATE_THINKING) {
                // O 型嘴 — 思考中，带微微张合
                var openAmount = 0.15 + Math.sin(t / 200) * 0.06;
                return { type: 'o', openness: openAmount };
            } else if (state === STATE_DONE) {
                // 大笑 — 完成后的开心表情
                return { type: 'smile', width: 0.38, depth: 0.36 };
            }
            return { type: 'smile', width: 0.25, depth: 0.20 }; // idle 微笑
        }

        /** 绘制眼睛 — 根据状态返回不同的形状参数 */
        function getEyeParams(state) {
            if (state === STATE_THINKING) {
                // 正常圆眼 — 思考中，停止眯眼，保持自然注视
                return { type: 'open', scale: 1.0, pupilScale: 0.5 };
            } else if (state === STATE_DONE) {
                // 超级大眼 — 开心到眼睛都亮了
                return { type: 'open', scale: 1.18, pupilScale: 0.48, sparkle: true };
            }
            return { type: 'open', scale: 1.0, pupilScale: 0.5 }; // idle 正常
        }

        function draw() {
            rafId = window.requestAnimationFrame(draw);
            try {
                // 防御：canvas 可能已被销毁或脱离 DOM
                if (!el || el.parentNode === null || !ctx) { return; }

                // 动态同步尺寸
                var curW = el.width || w;
                var curH = el.height || h;
                if (curW !== w || curH !== h) { w = curW; h = curH; }

                ctx.clearRect(0, 0, w, h);
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 && rect.height === 0) { return; }

                var lmx = mx - (rect.left + w / 2);
                var lmy = my - (rect.top + h / 2);
                var lookAngle = Math.atan2(lmy, lmx);
                var now = Date.now();
                var t = now;
                var stateElapsed = now - stateEnterTime;

                // ===== 身体偏移量 =====
                var bodyOffsetY = 0;
                var bodyScaleY = 1;
                if (currentState === STATE_THINKING) {
                    bodyOffsetY = Math.sin(now / 110) * 5;
                    bodyScaleY = 1 + Math.sin(now / 170) * 0.025;
                } else if (currentState === STATE_DONE) {
                    if (doneBounceCount < doneBounceMax) {
                        var bounceProgress = stateElapsed % 350;
                        if (bounceProgress < 175) {
                            bodyOffsetY = -Math.sin(bounceProgress / 175 * Math.PI) * (12 - doneBounceCount * 3);
                        } else {
                            bodyOffsetY = 0;
                            if (bounceProgress > 280) { doneBounceCount++; }
                        }
                    } else {
                        if (stateElapsed > 3000) { setState(STATE_IDLE); }
                    }
                } else {
                    bodyOffsetY = Math.sin(now / 500) * 2.5;
                }

                ctx.save();
                ctx.translate(w / 2, h / 2 - 4 + bodyOffsetY);
                ctx.scale(1, bodyScaleY);

                // ===== 完成状态：彩虹光晕 =====
                if (currentState === STATE_DONE && stateElapsed < 1400) {
                    var glowAlpha = Math.max(0, 1 - stateElapsed / 1400) * 0.22;
                    var glowR = radius * (1.35 + Math.sin(now / 180) * 0.08);
                    var gradient = ctx.createRadialGradient(0, 0, radius * 0.6, 0, 0, glowR);
                    gradient.addColorStop(0, 'rgba(255,200,80,' + glowAlpha + ')');
                    gradient.addColorStop(0.35, 'rgba(244,114,182,' + (glowAlpha * 0.8) + ')');
                    gradient.addColorStop(0.65, 'rgba(96,165,250,' + (glowAlpha * 0.6) + ')');
                    gradient.addColorStop(1, 'rgba(167,139,250,0)');
                    ctx.beginPath(); ctx.arc(0, 0, glowR, 0, Math.PI * 2);
                    ctx.fillStyle = gradient; ctx.fill();
                }

                // ===== 轨道环 =====
                var ringSpeedMultiplier = (currentState === STATE_THINKING) ? 4 : 1;
                ctx.save();
                ctx.strokeStyle = 'rgba(148,163,184,0.25)';
                ctx.setLineDash([6, 6]);
                ctx.beginPath(); ctx.arc(0, 0, radius * 1.33, now * 0.00008 * ringSpeedMultiplier, Math.PI * 2 + now * 0.00008 * ringSpeedMultiplier); ctx.stroke();
                ctx.beginPath(); ctx.arc(0, 0, radius * 1.63, -now * 0.00006 * ringSpeedMultiplier, Math.PI * 2 - now * 0.00006 * ringSpeedMultiplier); ctx.stroke();
                ctx.restore();

                // ===== 粒子 =====
                var particleSpeedMult = (currentState === STATE_THINKING) ? 4 : 1;
                var particleColorPrimary = (currentState === STATE_THINKING)
                    ? ('rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',0.55)')
                    : ('rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',0.22)');
                particles.forEach(function (p, idx) {
                    var dir = (idx % 2 === 0) ? 1 : -1;
                    var a = p.angle + now * p.speed * dir * particleSpeedMult;
                    var px = Math.cos(a) * p.radius;
                    var py = Math.sin(a) * p.radius * 0.45;
                    ctx.beginPath();
                    if (idx % 2 === 0) {
                        ctx.fillStyle = (currentState === STATE_THINKING) ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.95)';
                    } else {
                        ctx.fillStyle = particleColorPrimary;
                    }
                    var sz = p.size;
                    if (currentState === STATE_THINKING) { sz *= (1 + Math.sin(now / 150 + idx) * 0.35); }
                    ctx.arc(px, py, sz, 0, Math.PI * 2); ctx.fill();
                });

                // ===== 底部阴影 =====
                ctx.beginPath();
                var shadowScaleY = Math.max(0.4, 1 - Math.abs(bodyOffsetY) / 30);
                ctx.ellipse(0, radius * 1.07, radius * 1.01, radius * 0.2 * shadowScaleY, 0, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(15,23,42,' + (0.06 + shadowScaleY * 0.03) + ')'; ctx.fill();

                // ===== 身体 =====
                ctx.beginPath();
                ellipse(ctx, 0, 0, radius, radius * 0.9, 0, 0, Math.PI * 2);
                ctx.fillStyle = primaryColor; ctx.fill();

                // ===== 高光 =====
                ctx.beginPath();
                ctx.arc(-radius * 0.48, -radius * 0.52, radius * 0.16, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255,255,255,0.2)'; ctx.fill();

                // ===== 思考波纹 =====
                if (currentState === STATE_THINKING) {
                    for (var wr = 0; wr < 3; wr++) {
                        var waveT = ((now / 600) + wr / 3) % 1;
                        var waveAlpha = (1 - waveT) * 0.18;
                        var waveR = radius * (1.15 + waveT * 0.5);
                        ctx.beginPath(); ctx.arc(0, 0, waveR, 0, Math.PI * 2);
                        ctx.strokeStyle = 'rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',' + waveAlpha + ')';
                        ctx.lineWidth = 2; ctx.setLineDash([8, 6]); ctx.stroke();
                    }
                    ctx.setLineDash([]);
                }

                // ===== 思考：汗滴 + 问号 + 腮红 =====
                if (currentState === STATE_THINKING) {
                    var sweatPhase = (now % 1400) / 1400;
                    var sweatX = -radius * 0.56;
                    var sweatY = -radius * 0.32 + sweatPhase * radius * 0.45;
                    var sweatAlpha = sweatPhase < 0.85 ? (sweatPhase < 0.15 ? sweatPhase / 0.15 : 1) : Math.max(0, 1 - (sweatPhase - 0.85) / 0.15);
                    if (sweatAlpha > 0.05) {
                        ctx.save();
                        ctx.fillStyle = 'rgba(180,220,255,' + (sweatAlpha * 0.75) + ')';
                        ctx.beginPath();
                        ctx.ellipse(sweatX, sweatY, radius * 0.075, radius * 0.11, -0.3, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.fillStyle = 'rgba(255,255,255,' + (sweatAlpha * 0.6) + ')';
                        ctx.beginPath(); ctx.arc(sweatX - radius * 0.02, sweatY - radius * 0.035, radius * 0.025, 0, Math.PI * 2); ctx.fill();
                        ctx.restore();
                    }
                    var qBobY = Math.sin(now / 350) * 4;
                    var qAlpha = 0.6 + Math.sin(now / 300) * 0.25;
                    ctx.save();
                    ctx.font = 'bold ' + Math.round(radius * 0.36) + 'px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'bottom';
                    ctx.fillStyle = 'rgba(251,191,36,' + qAlpha + ')';
                    ctx.fillText('?', 0, -radius * 0.62 + qBobY);
                    ctx.restore();
                    var blushAlpha = 0.18 + Math.sin(now / 500) * 0.08;
                    ctx.fillStyle = 'rgba(255,130,160,' + blushAlpha + ')';
                    ctx.beginPath(); ctx.arc(-radius * 0.54, radius * 0.08, radius * 0.12, 0, Math.PI * 2); ctx.fill();
                    ctx.beginPath(); ctx.arc(radius * 0.54, radius * 0.08, radius * 0.12, 0, Math.PI * 2); ctx.fill();
                }

                // ===== 眼睛 =====
                var ex = radius * 0.35, ey = -radius * 0.2, es = radius * 0.3;
                var eyeP = getEyeParams(currentState);
                var ps, px, py;
                if (eyeP.type === 'squint') {
                    ctx.strokeStyle = 'white'; ctx.lineWidth = 2.5; ctx.lineCap = 'round';
                    var sq = eyeP.squeeze;
                    ctx.save();
                    ctx.translate(-ex, ey); ctx.rotate(-0.4);
                    ctx.beginPath(); ctx.moveTo(-es * sq, 0); ctx.lineTo(es * sq, 0); ctx.stroke();
                    ctx.restore();
                    ctx.save();
                    ctx.translate(ex, ey); ctx.rotate(0.4);
                    ctx.beginPath(); ctx.moveTo(-es * sq, 0); ctx.lineTo(es * sq, 0); ctx.stroke();
                    ctx.restore();
                    ctx.fillStyle = 'black';
                    ps = es * eyeP.pupilScale;
                    var ppx = Math.cos(lookAngle) * es * 0.25, ppy = Math.sin(lookAngle) * es * 0.25;
                    ctx.beginPath(); ctx.arc(-ex + ppx, ey + ppy, ps, 0, Math.PI * 2); ctx.fill();
                    ctx.beginPath(); ctx.arc(ex + ppx, ey + ppy, ps, 0, Math.PI * 2); ctx.fill();
                } else {
                    var eyeScl = eyeP.scale || 1;
                    ctx.fillStyle = 'white';
                    ctx.beginPath(); ctx.arc(-ex, ey, es * eyeScl, 0, Math.PI * 2); ctx.fill();
                    ctx.beginPath(); ctx.arc(ex, ey, es * eyeScl, 0, Math.PI * 2); ctx.fill();
                    ctx.fillStyle = 'black';
                    ps = es * (eyeP.pupilScale || 0.5);
                    px = Math.cos(lookAngle) * es * 0.4; py = Math.sin(lookAngle) * es * 0.4;
                    ctx.beginPath(); ctx.arc(-ex + px, ey + py, ps, 0, Math.PI * 2); ctx.fill();
                    ctx.beginPath(); ctx.arc(ex + px, ey + py, ps, 0, Math.PI * 2); ctx.fill();
                    if (eyeP.sparkle) {
                        var sparkAng = now / 420;
                        ctx.save();
                        ctx.fillStyle = 'rgba(255,255,255,0.85)';
                        ctx.translate(-ex + px, ey + py);
                        drawStar(es * 0.35 * Math.cos(sparkAng), -es * 0.3 + Math.sin(sparkAng * 2) * 2, 2.5, sparkAng);
                        ctx.translate(ex * 2, 0);
                        drawStar(es * 0.35 * Math.cos(sparkAng + 1), -es * 0.3 + Math.sin(sparkAng * 2 + 1) * 2, 2.5, -sparkAng);
                        ctx.restore();
                    }
                }

                // ===== 嘴巴 =====
                var mp = getMouthParams(currentState, t);
                ctx.strokeStyle = 'rgba(0,0,0,0.4)'; ctx.lineWidth = 3; ctx.lineCap = 'round';
                if (mp.type === 'o') {
                    ctx.fillStyle = 'rgba(0,0,0,0.3)';
                    ctx.beginPath();
                    ctx.ellipse(0, radius * 0.13, radius * mp.openness, radius * mp.openness * 1.1, 0, 0, Math.PI * 2);
                    ctx.fill();
                } else {
                    ctx.beginPath();
                    ctx.arc(0, radius * (0.13 - mp.depth * 0.3), radius * mp.width, 0.2, Math.PI - 0.2);
                    ctx.stroke();
                }

                // ===== 完成腮红 =====
                if (currentState === STATE_DONE) {
                    var doneBlushAlpha = 0.22 + Math.sin(now / 180) * 0.08;
                    ctx.fillStyle = 'rgba(255,120,150,' + doneBlushAlpha + ')';
                    ctx.beginPath(); ctx.arc(-radius * 0.54, radius * 0.08, radius * 0.14, 0, Math.PI * 2); ctx.fill();
                    ctx.beginPath(); ctx.arc(radius * 0.54, radius * 0.08, radius * 0.14, 0, Math.PI * 2); ctx.fill();
                }

                // ===== 小脚 =====
                var legSwing = (currentState === STATE_THINKING) ? Math.sin(now / 100) * 3 : 0;
                ctx.beginPath();
                ctx.moveTo(-18, 70); ctx.lineTo(-8 + legSwing, 88); ctx.lineTo(-2, 70);
                ctx.moveTo(18, 70); ctx.lineTo(8 - legSwing, 88); ctx.lineTo(2, 70);
                ctx.strokeStyle = 'rgba(15,23,42,0.22)'; ctx.lineWidth = 4; ctx.lineCap = 'round'; ctx.stroke();

                // ===== 庆祝粒子 =====
                if (currentState === STATE_DONE) {
                    for (var ci = celebrationParticles.length - 1; ci >= 0; ci--) {
                        var cp = celebrationParticles[ci];
                        cp.x += cp.vx; cp.y += cp.vy;
                        cp.vy += 0.04;
                        cp.rotation += cp.rotSpeed;
                        cp.life -= cp.decay;
                        if (cp.life <= 0) { celebrationParticles.splice(ci, 1); continue; }
                        ctx.globalAlpha = cp.life;
                        ctx.fillStyle = cp.color;
                        drawStar(cp.x, cp.y, cp.size, cp.rotation);
                        ctx.globalAlpha = 1;
                    }
                    for (var hi = hearts.length - 1; hi >= 0; hi--) {
                        var ht = hearts[hi];
                        ht.x += ht.vx; ht.y += ht.vy;
                        ht.vy *= 0.995;
                        ht.life -= ht.decay;
                        if (ht.life <= 0 || ht.y < -radius * 1.5) { hearts.splice(hi, 1); continue; }
                        drawHeart(ht.x, ht.y, ht.size, ht.rotation, ht.life);
                    }
                }

                ctx.restore();
            } catch (drawErr) {
                // 单帧绘制失败不应打断动画循环
            }
        }

        /** 椭圆路径兼容辅助函数（部分浏览器不支持 ellipse） */
        function ellipse(c, x, y, rx, ry, rotation, start, end) {
            if (c.ellipse) { c.ellipse(x, y, rx, ry, rotation, start, end); return; }
            c.save(); c.translate(x, y); c.rotate(rotation); c.scale(1, ry / rx);
            c.arc(0, 0, rx, start, end); c.restore();
        }

        return {
            start: function () { draw(); },
            stop: function () {
                if (rafId) cancelAnimationFrame(rafId);
                $(window).off('.aiMascot' + w);
            },
            setState: setState
        };
    }

    // ============================================================
    //  10. 粘贴图片支持（绑定到 textarea）
    // ============================================================

    /**
     * 为 textarea 启用粘贴图片支持
     * @param {jQuery} $textarea
     * @param {Function} onImage - 回调(file) 处理粘贴的图片文件
     */
    function enablePasteImage($textarea, onImage) {
        $textarea.on('paste.aiPasteImg', function (e) {
            var cb = e.originalEvent.clipboardData || window.clipboardData;
            if (!cb) return;
            var items = cb.items;
            if (!items) return;
            for (var i = 0; i < items.length; i++) {
                if (items[i].type.indexOf('image') !== -1) {
                    e.preventDefault();
                    var f = items[i].getAsFile();
                    if (f && typeof onImage === 'function') { onImage(f); }
                    return;
                }
            }
        });
    }

    // ============================================================
    //  11. 人脸特征点检测与距离监控
    //
    //  基于 MediaPipe Face Mesh（468 个关键点），实时计算：
    //    - 眼睛与嘴巴之间的距离（欧氏距离）
    //    - 特征点是否离开预设区域
    //  距离超阈值时触发通知回调。
    //
    //  用法:
    //    var monitor = AiUtils.createFaceLandmarkMonitor({
    //        videoElement: document.getElementById('myVideo'),
    //        canvasElement: document.getElementById('overlayCanvas'),   // 可选，用于绘制调试
    //        distanceThreshold: 0.35,     // 归一化距离阈值 (0-1)
    //        regionPadding: 0.1,          // 区域边界内缩比例
    //        onThresholdExceed: function(data) { ... },   // 超阈值回调
    //        onRegionExit: function(data) { ... },         // 出区域回调
    //        onFrame: function(data) { ... }                // 每帧数据回调（可选）
    //    });
    //    monitor.start();
    //    monitor.stop();
    //    monitor.getLatestData() → { eyeMouthDist, leftEye, rightEye, mouth, ... }
    // ============================================================

    /**
     * MediaPipe FaceMesh CDN 地址
     */
    var FACEMESH_CDN = 'https://cdn.jsdelivr.net/npm/@mediapipe/face_mesh@0.4/face_mesh.js';
    var CAMERA_UTILS_CDN = 'https://cdn.jsdelivr.net/npm/@mediapipe/camera_utils@0.3/camera_utils.js';

    /**
     * 创建人脸特征点监控器
     * @param {Object} opts 配置项
     * @returns {{start:function, stop:function, getLatestData:function, isRunning:function}}
     */
    function createFaceLandmarkMonitor(opts) {
        opts = opts || {};
        var videoEl = opts.videoElement;
        var canvasEl = opts.canvasElement || null;
        var distanceThreshold = opts.distanceThreshold || 0.35;
        var regionPadding = opts.regionPadding || 0.1;
        var onThresholdExceed = typeof opts.onThresholdExceed === 'function' ? opts.onThresholdExceed : null;
        var onRegionExit = typeof opts.onRegionExit === 'function' ? opts.onRegionExit : null;
        var onFrame = typeof opts.onFrame === 'function' ? opts.onFrame : null;

        var faceMesh = null;
        var camera = null;
        var ctx = null;
        var running = false;
        var latestData = null;
        var lastWarnTime = 0;       // 防抖：阈值警告最小间隔
        var WARN_DEBOUNCE = 1500;   // ms

        // 预设安全区域（首次检测到人脸时初始化）
        var safeRegion = { x: 0, y: 0, w: 1, h: 1, initialized: false };

        /** 关键点索引（MediaPipe Face Mesh 468 点） */
        var IDX = {
            // 左眼关键点
            leftEyeOuter: 33,
            leftEyeInner: 133,
            leftEyeTop: 159,
            leftEyeBottom: 145,
            // 右眼关键点
            rightEyeOuter: 362,
            rightEyeInner: 263,
            rightEyeTop: 386,
            rightEyeBottom: 374,
            // 嘴巴关键点
            mouthUpper: 13,
            mouthLower: 14,
            mouthLeft: 61,
            mouthRight: 291,
            // 面部轮廓
            noseTip: 1,
            forehead: 10,
            chin: 152,
            leftCheek: 234,
            rightCheek: 454
        };

        /** 计算两点间欧氏距离 */
        function dist(p1, p2) {
            if (!p1 || !p2) return 0;
            var dx = p1.x - p2.x, dy = p1.y - p2.y;
            return Math.sqrt(dx * dx + dy * dy);
        }

        /** 取两点中点 */
        function midpoint(p1, p2) {
            if (!p1 || !p2) return { x: 0, y: 0 };
            return { x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 };
        }

        /** 从一组关键点计算眼睛中心 */
        function getEyeCenter(landmarks, outerIdx, innerIdx, topIdx, bottomIdx) {
            if (!landmarks || landmarks.length <= Math.max(outerIdx, innerIdx, topIdx, bottomIdx)) return null;
            var outer = landmarks[outerIdx], inner = landmarks[innerIdx];
            var top = landmarks[topIdx], bottom = landmarks[bottomIdx];
            return midpoint(midpoint(outer, inner), midpoint(top, bottom));
        }

        /** 从一组关键点计算嘴巴中心 */
        function getMouthCenter(landmarks) {
            if (!landmarks || landmarks.length <= 291) return null;
            var upper = landmarks[IDX.mouthUpper], lower = landmarks[IDX.mouthLower];
            var left = landmarks[IDX.mouthLeft], right = landmarks[IDX.mouthRight];
            return midpoint(midpoint(upper, lower), midpoint(left, right));
        }

        /** 初始化安全区域（基于面部边界框） */
        function initSafeRegion(landmarks) {
            if (safeRegion.initialized) return;
            var xs = [], ys = [];
            [IDX.forehead, IDX.chin, IDX.leftCheek, IDX.rightCheek].forEach(function (i) {
                if (landmarks[i]) { xs.push(landmarks[i].x); ys.push(landmarks[i].y); }
            });
            if (xs.length < 4) return;
            var minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs);
            var minY = Math.min.apply(null, ys), maxY = Math.max.apply(null, ys);
            var pw = (maxX - minX) * regionPadding;
            var ph = (maxY - minY) * regionPadding;
            safeRegion = {
                x: minX - pw, y: minY - ph,
                w: (maxX - minX) + pw * 2, h: (maxY - minY) + ph * 2,
                initialized: true
            };
        }

        /** 检查特征点是否在安全区域内 */
        function checkRegionSafety(keypoints) {
            var allInside = true;
            for (var i = 0; i < keypoints.length; i++) {
                var p = keypoints[i];
                if (!p) continue;
                if (p.x < safeRegion.x || p.x > safeRegion.x + safeRegion.w ||
                    p.y < safeRegion.y || p.y > safeRegion.y + safeRegion.h) {
                    allInside = false;
                    break;
                }
            }
            return allInside;
        }

        /** 绘制调试叠加层 */
        function drawDebugOverlay(landmarks, data) {
            if (!canvasEl || !ctx) return;
            var w = canvasEl.width, h = canvasEl.height;
            ctx.clearRect(0, 0, w, h);

            // 绘制安全区域边界
            if (safeRegion.initialized) {
                ctx.strokeStyle = 'rgba(20, 184, 166, 0.5)';
                ctx.lineWidth = 2;
                ctx.setLineDash([6, 4]);
                ctx.strokeRect(safeRegion.x * w, safeRegion.y * h, safeRegion.w * w, safeRegion.h * h);
                ctx.setLineDash([]);
            }

            // 绘制眼睛-嘴巴连线
            if (data.leftEye && data.mouth) {
                ctx.strokeStyle = 'rgba(59, 130, 246, 0.8)';
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(data.leftEye.x * w, data.leftEye.y * h);
                ctx.lineTo(data.mouth.x * w, data.mouth.y * h);
                ctx.stroke();
            }
            if (data.rightEye && data.mouth) {
                ctx.strokeStyle = 'rgba(168, 85, 247, 0.8)';
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(data.rightEye.x * w, data.rightEye.y * h);
                ctx.lineTo(data.mouth.x * w, data.mouth.y * h);
                ctx.stroke();
            }

            // 绘制关键点
            var points = [
                { p: data.leftEye, color: '#3b82f6', label: 'LE' },
                { p: data.rightEye, color: '#a855f7', label: 'RE' },
                { p: data.mouth, color: '#ef4444', label: 'MO' }
            ];
            points.forEach(function (pt) {
                if (!pt.p) return;
                ctx.fillStyle = pt.color;
                ctx.beginPath();
                ctx.arc(pt.p.x * w, pt.p.y * h, 5, 0, Math.PI * 2);
                ctx.fill();
                ctx.fillStyle = '#fff';
                ctx.font = '10px monospace';
                ctx.fillText(pt.label, pt.p.x * w + 7, pt.p.y * h - 7);
            });

            // 显示距离数值
            ctx.fillStyle = 'rgba(15, 23, 42, 0.8)';
            ctx.font = 'bold 13px monospace';
            ctx.fillText('L-Eye ↔ Mouth: ' + (data.leftEyeToMouthDist || 0).toFixed(4), 10, 20);
            ctx.fillText('R-Eye ↔ Mouth: ' + (data.rightEyeToMouthDist || 0).toFixed(4), 10, 38);
            ctx.fillText('Avg Dist: ' + (data.eyeMouthDist || 0).toFixed(4) + ' | Threshold: ' + distanceThreshold.toFixed(2), 10, 56);
            ctx.fillText('In Region: ' + (data.inRegion ? 'YES' : 'NO'), 10, 74);
        }

        /** 单帧处理回调 */
        function onResults(results) {
            if (!results.multiFaceLandmarks || results.multiFaceLandmarks.length === 0) {
                latestData = { detected: false };
                return;
            }

            var landmarks = results.multiFaceLandmarks[0];

            // 计算关键特征点位置
            var leftEye = getEyeCenter(landmarks, IDX.leftEyeOuter, IDX.leftEyeInner, IDX.leftEyeTop, IDX.leftEyeBottom);
            var rightEye = getEyeCenter(landmarks, IDX.rightEyeOuter, IDX.rightEyeInner, IDX.rightEyeTop, IDX.rightEyeBottom);
            var mouth = getMouthCenter(landmarks);

            if (!leftEye || !rightEye || !mouth) {
                latestData = { detected: true, valid: false };
                return;
            }

            // 初始化安全区域
            initSafeRegion(landmarks);

            // 核心指标：眼睛到嘴巴的距离
            var leftDist = dist(leftEye, mouth);
            var rightDist = dist(rightEye, mouth);
            var avgDist = (leftDist + rightDist) / 2;

            // 检查关键特征点是否在安全区域内
            var checkPoints = [leftEye, rightEye, mouth, landmarks[IDX.noseTip]];
            var inRegion = checkRegionSafety(checkPoints);

            // 构建当前帧数据
            var frameData = {
                detected: true,
                valid: true,
                timestamp: Date.now(),
                leftEye: { x: leftEye.x, y: leftEye.y },
                rightEye: { x: rightEye.x, y: rightEye.y },
                mouth: { x: mouth.x, y: mouth.y },
                leftEyeToMouthDist: leftDist,
                rightEyeToMouthDist: rightDist,
                eyeMouthDist: avgDist,
                inRegion: inRegion,
                thresholdExceeded: avgDist > distanceThreshold
            };

            latestData = frameData;

            // 绘制调试层
            drawDebugOverlay(landmarks, frameData);

            // 阈值超限检测（带防抖）
            if (frameData.thresholdExceeded && onThresholdExceed) {
                var now = Date.now();
                if (now - lastWarnTime > WARN_DEBOUNCE) {
                    lastWarnTime = now;
                    onThresholdExceed(frameData);
                }
            }

            // 离开安全区域检测
            if (!inRegion && onRegionExit) {
                onRegionExit(frameData);
            }

            // 每帧通用回调
            if (onFrame) {
                onFrame(frameData);
            }
        }

        /** 动态加载 MediaPipe 脚本并启动 */
        function loadAndStart() {
            if (typeof FaceMesh !== 'undefined' && typeof Camera !== 'undefined') {
                initFaceMesh();
                return;
            }

            // 加载 Camera Utils
            var camScript = document.createElement('script');
            camScript.src = CAMERA_UTILS_CDN;
            camScript.onload = function () {
                // 加载 Face Mesh
                var fmScript = document.createElement('script');
                fmScript.src = FACEMESH_CDN;
                fmScript.onload = function () { initFaceMesh(); };
                document.head.appendChild(fmScript);
            };
            document.head.appendChild(camScript);
        }

        /** 初始化 FaceMesh 和 Camera */
        function initFaceMesh() {
            if (!videoEl) {
                showToast('未指定视频元素，无法启用人脸检测', 'error');
                return;
            }

            faceMesh = new FaceMesh({
                locateFile: function (file) {
                    return 'https://cdn.jsdelivr.net/npm/@mediapipe/face_mesh@0.4/' + file;
                }
            });
            faceMesh.setOptions({
                maxNumFaces: 1,
                refineLandmarks: true,
                minDetectionConfidence: 0.5,
                minTrackingConfidence: 0.5
            });
            faceMesh.setResults(onResults);

            if (canvasEl) {
                ctx = canvasEl.getContext('2d');
                canvasEl.width = videoEl.videoWidth || 640;
                canvasEl.height = videoEl.videoHeight || 480;
            }

            camera = new Camera(videoEl, {
                onFrame: async function () {
                    if (running && faceMesh) {
                        await faceMesh.send({ image: videoEl });
                    }
                },
                width: 1280,
                height: 720
            });
            camera.start().then(function () {
                running = true;
                showToast('人脸特征点检测已启动', 'success');
            }).catch(function (err) {
                showToast('摄像头启动失败: ' + (err.message || '未知错误'), 'error');
            });
        }

        return {
            start: function () {
                loadAndStart();
            },
            stop: function () {
                running = false;
                if (camera) { try { camera.stop(); } catch(e) {} }
                if (faceMesh) { try { faceMesh.close(); } catch(e) {} }
                if (ctx && canvasEl) { ctx.clearRect(0, 0, canvasEl.width, canvasEl.height); }
                latestData = { detected: false, stopped: true };
            },
            getLatestData: function () { return latestData; },
            isRunning: function () { return running; }
        };
    }

    // ============================================================
    //  导出
    // ============================================================

    window.AiUtils = {
        // 纯工具
        escapeHtml: escapeHtml,
        formatMessage: formatMessage,
        nowText: nowText,
        createId: createId,
        hexToRgb: hexToRgb,
        hexToRgba: hexToRgba,
        formatDuration: formatDuration,

        // 存储
        readJSON: readJSON,
        writeJSON: writeJSON,
        readLocalJSON: readLocalJSON,
        writeLocalJSON: writeLocalJSON,

        // 工厂
        createImageHandler: createImageHandler,
        createVoiceRecorder: createVoiceRecorder,
        createMascotCanvas: createMascotCanvas,
        createFaceLandmarkMonitor: createFaceLandmarkMonitor,

        // UI 功能
        openLightbox: openLightbox,
        initGlobalLightbox: initGlobalLightbox,
        bindCopyButton: bindCopyButton,
        showToast: showToast,
        showConfirm: showConfirm,
        enablePasteImage: enablePasteImage,

        // 内部常量（供外部扩展用）
        _iconMap: _iconMap,
        _colorMap: _colorMap
    };

})(window);

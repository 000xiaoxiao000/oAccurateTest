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

        return { init: function ($) {
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
     * @returns {{start:function, stop:function}} 控制句柄
     */
    function createMascotCanvas(opts) {
        opts = opts || {};
        var el = typeof opts.canvas === 'string' ? document.getElementById(opts.canvas) : opts.canvas;
        if (!el) return { start: function () {}, stop: function () {} };

        var ctx = el.getContext('2d');
        var w = el.width, h = el.height;
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

        $(window).on('mousemove.aiMascot' + w, function (e) { mx = e.clientX; my = e.clientY; });

        function draw() {
            ctx.clearRect(0, 0, w, h);
            var rect = el.getBoundingClientRect();
            var lmx = mx - (rect.left + w / 2);
            var lmy = my - (rect.top + h / 2);
            var lookAngle = Math.atan2(lmy, lmx);

            ctx.save();
            ctx.translate(w / 2, h / 2 - 4);
            ctx.translate(0, Math.sin(Date.now() / 500) * 3);

            // 轨道环
            ctx.save();
            ctx.strokeStyle = 'rgba(148,163,184,0.25)';
            ctx.setLineDash([6, 6]);
            ctx.beginPath(); ctx.arc(0, 0, radius * 1.33, 0, Math.PI * 2); ctx.stroke();
            ctx.beginPath(); ctx.arc(0, 0, radius * 1.63, 0, Math.PI * 2); ctx.stroke();
            ctx.restore();

            // 粒子
            particles.forEach(function (p, idx) {
                var a = p.angle + Date.now() * p.speed * (idx % 2 === 0 ? 1 : -1);
                var px = Math.cos(a) * p.radius;
                var py = Math.sin(a) * p.radius * 0.45;
                ctx.beginPath();
                ctx.fillStyle = idx % 2 === 0 ? 'rgba(255,255,255,0.95)' : 'rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',0.22)';
                ctx.arc(px, py, p.size, 0, Math.PI * 2); ctx.fill();
            });

            // 底部阴影
            ctx.beginPath();
            ctx.ellipse(0, radius * 1.07, radius * 1.0, radius * 0.2, 0, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(15,23,42,0.08)'; ctx.fill();

            // 身体
            ctx.beginPath();
            ctx.ellipse(0, 0, radius, radius * 0.9, 0, 0, Math.PI * 2);
            ctx.fillStyle = primaryColor; ctx.fill();

            // 高光
            ctx.beginPath();
            ctx.arc(-radius * 0.48, -radius * 0.52, radius * 0.16, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255,255,255,0.2)'; ctx.fill();

            // 眼睛
            var ex = radius * 0.35, ey = -radius * 0.2, es = radius * 0.3;
            ctx.fillStyle = 'white';
            ctx.beginPath(); ctx.arc(-ex, ey, es, 0, Math.PI * 2); ctx.fill();
            ctx.beginPath(); ctx.arc(ex, ey, es, 0, Math.PI * 2); ctx.fill();

            // 瞳孔（跟随鼠标）
            ctx.fillStyle = 'black';
            var ps = es * 0.5;
            var px = Math.cos(lookAngle) * es * 0.4, py = Math.sin(lookAngle) * es * 0.4;
            ctx.beginPath(); ctx.arc(-ex + px, ey + py, ps, 0, Math.PI * 2); ctx.fill();
            ctx.beginPath(); ctx.arc(ex + px, ey + py, ps, 0, Math.PI * 2); ctx.fill();

            // 嘴巴
            ctx.strokeStyle = 'rgba(0,0,0,0.4)'; ctx.lineWidth = 3;
            ctx.beginPath(); ctx.arc(0, radius * 0.13, radius * 0.25, 0.2, Math.PI - 0.2); ctx.stroke();

            // 小脚
            ctx.beginPath();
            ctx.moveTo(-18, 70); ctx.lineTo(-8, 88); ctx.lineTo(-2, 70);
            ctx.moveTo(18, 70); ctx.lineTo(8, 88); ctx.lineTo(2, 70);
            ctx.strokeStyle = 'rgba(15,23,42,0.22)'; ctx.lineWidth = 4; ctx.lineCap = 'round'; ctx.stroke();

            ctx.restore();
            rafId = window.requestAnimationFrame(draw);
        }

        return {
            start: function () { draw(); },
            stop: function () {
                if (rafId) cancelAnimationFrame(rafId);
                $(window).off('.aiMascot' + w);
            }
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

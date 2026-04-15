<#-- AI 悬浮小窗公共模板 -->
<div id="aiFloatingWidget"
     class="ai-floating-widget"
     data-project-id="${project.id}"
     data-project-name="${project.name}"
     data-ask-url="/p/${project.id}/AIInteractive/ask"
     data-ai-url="/p/${project.id}/AIInteractive"
     data-mascot-primary="${mascotPrimary!('#00b5ad')}"
     data-ai-timeout="${(aiTimeout)!120}">
    <button id="aiFloatingLauncher" class="ai-floating-launcher" type="button" title="打开 AI 助手" data-show-ai-label="false">
        <canvas id="aiFloatingMascotCanvas" width="88" height="88"></canvas>
    </button>

    <div id="aiFloatingPanel" class="ai-floating-panel">
        <#if (aiFloatingEnableResize!true)>
            <div class="ai-floating-resize-handle" title="拖拽放大"></div>
        </#if>
        <div class="ai-floating-panel-header">
            <div>
                <div id="aiFloatingPanelEyebrow" class="ai-floating-panel-eyebrow">AI Interactive</div>
                <div id="aiFloatingPanelTitle" class="ai-floating-panel-title">项目悬浮助手</div>
            </div>
            <div class="ai-floating-panel-tools">
                <#if (aiFloatingEnableAdvancedLayout!true)>
                    <button id="aiFloatingResetLayout" class="ai-floating-icon-btn" type="button" title="恢复默认内部布局">↺</button>
                    <button id="aiFloatingUndoLayout" class="ai-floating-icon-btn" type="button" title="撤销上一步内部布局调整">↶</button>
                    <button id="aiFloatingToggleLayoutLock" class="ai-floating-icon-btn" type="button" title="锁定内部布局，防止误拖动">🔓</button>
                </#if>
                <a class="ai-floating-link" href="/p/${project.id}/AIInteractive" target="_blank">工作台</a>
                <button id="aiFloatingHideMascot" class="ai-floating-icon-btn" type="button" title="隐藏小人">-</button>
                <button id="aiFloatingCollapse" class="ai-floating-icon-btn" type="button" title="收起助手">x</button>
            </div>
        </div>

        <div class="ai-floating-section" id="aiFloatingMessageSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">对话</span>
                <button class="ai-floating-section-toggle" type="button"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingMessageList" class="ai-floating-section-body ai-floating-message-list"></div>
        </div>

        <div id="aiFloatingContextStatus" class="ai-floating-context-status"></div>

        <div class="ai-floating-section" id="aiFloatingQuickLinkSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">快捷入口</span>
                <button class="ai-floating-section-toggle" type="button"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingQuickLinkList" class="ai-floating-section-body ai-floating-quick-links"></div>
        </div>

        <div class="ai-floating-section" id="aiFloatingStarterSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">快捷提问</span>
                <button class="ai-floating-section-toggle" type="button"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingStarterList" class="ai-floating-section-body ai-floating-starters"></div>
        </div>

        <div class="ai-floating-compose">
            <input type="file" id="aiFloatingImageInput" accept="image/*" style="display:none">
            <textarea id="aiFloatingQuestionInput" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看"></textarea>
            <div class="ai-floating-compose-actions">
                <span id="aiFloatingState" class="ai-floating-state">就绪</span>
                <div class="ai-floating-compose-toolbar">
                    <button type="button" id="aiFloatingImageUploadBtn" class="ai-floating-toolbar-btn" title="上传图片">
                        <i class="image icon"></i>
                        <img class="ai-floating-image-preview" alt="preview">
                        <span class="ai-image-remove-btn" title="移除图片"><i class="close icon"></i></span>
                    </button>
                    <button type="button" id="aiFloatingVoiceRecordBtn" class="ai-floating-toolbar-btn" title="语音输入">
                        <i class="microphone icon"></i>
                    </button>
                </div>
                <button id="aiFloatingSendButton" class="ui teal mini button" type="button">发送</button>
            </div>
        </div>
    </div>

    <button id="aiFloatingRestore" class="ai-floating-restore" type="button" title="显示AI助手" aria-label="显示AI助手">${aiFloatingRestoreText!'显示AI助手'}</button>
</div>

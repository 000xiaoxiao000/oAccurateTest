<#-- AI 悬浮小窗公共模板 -->
<div id="aiFloatingWidget"
     class="ai-floating-widget"
     data-project-id="${project.id}"
     data-project-name="${project.name}"
     data-ask-url="/p/${project.id}/AIInteractive/ask"
     data-ai-url="/p/${project.id}/AIInteractive"
     data-mascot-primary="${mascotPrimary!('#00b5ad')}"
     data-ai-timeout="${(aiTimeout)!120}">
    <button id="aiFloatingLauncher" class="ai-floating-launcher" type="button" title="打开 AI 助手" data-tooltip="打开 AI 助手" data-position="left center" data-show-ai-label="false">
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
                    <button id="aiFloatingResetLayout" class="ai-floating-icon-btn" type="button" title="恢复默认内部布局" data-tooltip="恢复默认内部布局" data-position="bottom center">↺</button>
                    <button id="aiFloatingUndoLayout" class="ai-floating-icon-btn" type="button" title="撤销上一步内部布局调整" data-tooltip="撤销上一步内部布局调整" data-position="bottom center">↶</button>
                    <button id="aiFloatingToggleLayoutLock" class="ai-floating-icon-btn" type="button" title="锁定内部布局，防止误拖动" data-tooltip="锁定/解锁内部布局拖动" data-position="bottom center">🔓</button>
                </#if>
                <button id="aiFloatingClearConversation" class="ai-floating-icon-btn ai-floating-danger-btn" type="button" title="清空当前助手对话" data-tooltip="清空当前助手对话" data-position="bottom center"><i class="trash alternate outline icon"></i></button>
                <a class="ai-floating-link" href="/p/${project.id}/AIInteractive" target="_blank" title="打开完整 AI 工作台" data-tooltip="打开完整 AI 工作台" data-position="bottom center">工作台</a>
                <button id="aiFloatingHideMascot" class="ai-floating-icon-btn" type="button" title="隐藏小人" data-tooltip="隐藏小人，只保留恢复入口" data-position="bottom center">-</button>
                <button id="aiFloatingCollapse" class="ai-floating-icon-btn" type="button" title="收起助手" data-tooltip="收起助手面板" data-position="bottom center">x</button>
            </div>
        </div>

        <div class="ai-floating-section" id="aiFloatingMessageSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">对话</span>
                <button class="ai-floating-section-toggle" type="button" title="展开/收起对话区域" data-tooltip="展开/收起对话区域" data-position="left center"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingMessageList" class="ai-floating-section-body ai-floating-message-list"></div>
        </div>

        <div id="aiFloatingContextStatus" class="ai-floating-context-status"></div>

        <div class="ai-floating-section" id="aiFloatingQuickLinkSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">快捷入口</span>
                <button class="ai-floating-section-toggle" type="button" title="展开/收起快捷入口" data-tooltip="展开/收起快捷入口" data-position="left center"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingQuickLinkList" class="ai-floating-section-body ai-floating-quick-links"></div>
        </div>

        <div class="ai-floating-section" id="aiFloatingStarterSection">
            <div class="ai-floating-section-header">
                <span class="ai-floating-section-title">快捷提问</span>
                <button class="ai-floating-section-toggle" type="button" title="展开/收起快捷提问" data-tooltip="展开/收起快捷提问" data-position="left center"><i class="chevron down icon"></i></button>
            </div>
            <div id="aiFloatingStarterList" class="ai-floating-section-body ai-floating-starters"></div>
        </div>

        <div class="ai-floating-compose">
            <input type="file" id="aiFloatingImageInput" accept="image/*" style="display:none">
            <textarea id="aiFloatingQuestionInput" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看"></textarea>
            <div class="ai-floating-compose-actions">
                <span id="aiFloatingState" class="ai-floating-state">就绪</span>
                <div class="ai-floating-compose-toolbar">
                    <button type="button" id="aiFloatingImageUploadBtn" class="ai-floating-toolbar-btn" title="上传图片" data-tooltip="上传图片提问" data-position="top center">
                        <i class="image icon"></i>
                        <img class="ai-floating-image-preview" alt="preview">
                        <span class="ai-image-remove-btn" title="移除图片"><i class="close icon"></i></span>
                    </button>
                    <button type="button" id="aiFloatingVoiceRecordBtn" class="ai-floating-toolbar-btn" title="语音输入" data-tooltip="语音输入" data-position="top center">
                        <i class="microphone icon"></i>
                    </button>
                </div>
                <button id="aiFloatingSendButton" class="ui teal mini button" type="button" title="发送问题" data-tooltip="发送问题 / 生成中可停止" data-position="top center">发送</button>
            </div>
        </div>
    </div>

    <button id="aiFloatingRestore" class="ai-floating-restore" type="button" title="显示 AI 助手" aria-label="显示AI助手" data-tooltip="显示 AI 助手" data-position="left center">${aiFloatingRestoreText!'显示AI助手'}</button>
</div>

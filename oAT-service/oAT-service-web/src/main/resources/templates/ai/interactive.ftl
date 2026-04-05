<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>${projectName?html} - AI Interactive</title>
    <#include "../common.ftl">
    <link href="/css/ai-interactive.css?v=${.now}" rel="stylesheet">
    <script src="/js/ai-interactive.js?v=${.now}"></script>
</head>
<body>
<#assign AIInteractive="active">
<#include "../projectHeader.ftl">

<div id="aiInteractivePage"
     class="ui container ai-interactive-page"
     data-project-id="${projectId?html}"
     data-project-name="${projectName?html}"
     data-assistant-name="${mascotName?html}"
     data-mascot-primary="${mascotPrimary?html}"
     data-ask-url="/p/${projectId?html}/AIInteractive/ask"
     data-ai-timeout="${aiTimeout}">

    <textarea id="aiWelcomeMessage" style="display: none;">${welcomeMessage?html}</textarea>

    <div class="ai-workbench-shell"
         style="--ai-primary:${mascotPrimary}; --ai-accent:${mascotAccent}; --ai-halo:${mascotHalo};">
        <div class="ai-workbench-bg bg-one"></div>
        <div class="ai-workbench-bg bg-two"></div>
        <div class="ai-workbench-grid"></div>

        <!-- Dynamic animation layers -->
        <div class="ai-workbench-particles">
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
            <div class="ai-workbench-particle"></div>
        </div>

        <div class="ai-workbench-hero">
            <div class="ai-hero-copy">
                <div class="ai-eyebrow">AI Interactive Workbench</div>
                <h1>${projectName?html} 的动态工作台</h1>
                <p class="ai-hero-description">${projectSummary?html}</p>

                <div class="ai-hero-tags">
                    <span class="ai-hero-tag">角色：${mascotRole?html}</span>
                    <span class="ai-hero-tag">状态：${mascotMood?html}</span>
                    <span class="ai-hero-tag">在线应用：${onlineAppCount}</span>
                </div>

                <div class="ai-hero-actions">
                    <button class="ui teal button starter-question" data-question="帮我总结一下当前项目概况">启动概览</button>
                    <button class="ui basic button starter-question" data-question="如果线上有异常，排查顺序是什么">开始排查</button>
                    <a class="ui basic button" href="/p/${projectId?html}/monitor">打开监控台</a>
                </div>
            </div>

            <div class="ai-stage-panel">
                <div class="ai-stage-ring ring-one"></div>
                <div class="ai-stage-ring ring-two"></div>
                <div class="ai-stage-platform"></div>
                <canvas id="aiMascotCanvas" class="ai-mascot-canvas" width="180" height="180"></canvas>
                <div class="ai-stage-badge badge-one">Project Pulse</div>
                <div class="ai-stage-badge badge-two">Signal Online</div>
                <div class="ai-stage-badge badge-three">${mascotName?html}</div>
            </div>

            <div class="ai-side-console">
                <div class="ai-console-card ai-signal-card">
                    <div class="ai-console-label">工作台状态灯</div>
                    <div id="aiSignalLights" class="ai-signal-lights">
                        <div class="ai-signal-light active" data-state="online">
                            <span class="dot"></span>
                            <span>在线</span>
                        </div>
                        <div class="ai-signal-light" data-state="thinking">
                            <span class="dot"></span>
                            <span>分析中</span>
                        </div>
                        <div class="ai-signal-light" data-state="reply">
                            <span class="dot"></span>
                            <span>响应完成</span>
                        </div>
                    </div>
                    <div class="ai-request-stream">
                        <span class="stream-line"></span>
                        <span class="stream-line"></span>
                        <span class="stream-line"></span>
                        <span class="stream-line"></span>
                    </div>
                </div>

                <div class="ai-console-card">
                    <div class="ai-console-label">助手身份</div>
                    <div class="ai-console-value">${mascotName?html}</div>
                    <div class="ai-console-desc">${mascotRole?html} · ${mascotMood?html}</div>
                </div>

                <div class="ai-console-card">
                    <div class="ai-console-label">工作台提示</div>
                    <div class="ai-console-desc">${mascotHint?html}</div>
                </div>

                <div class="ai-console-card">
                    <div class="ai-console-label">代表应用</div>
                    <div class="ai-console-stack">
                        <#if appNames?size gt 0>
                            <#list appNames as appName>
                                <#if appName_index lt 3>
                                    <span class="ai-console-pill">${appName?html}</span>
                                </#if>
                            </#list>
                        <#else>
                            <span class="ai-console-pill muted">暂未接入应用</span>
                        </#if>
                    </div>
                </div>
            </div>
        </div>

        <div class="ai-workbench-dock">
            <div class="ai-dock-title">工作台快捷动作</div>
            <div class="ai-dock-actions">
                <#list starterQuestions as question>
                    <button class="ai-dock-chip starter-question" data-question="${question?html}">
                        ${question?html}
                    </button>
                </#list>
            </div>
        </div>

        <div class="ui stackable grid ai-workbench-main">
            <!-- 左侧：会话列表 - 占比增加 -->
            <div class="five wide column">
                <div class="ai-panel ai-session-panel glass">
                    <div class="ai-panel-header compact">
                        <div>
                            <div class="ai-eyebrow">Session Memory</div>
                            <h3>会话列表</h3>
                        </div>
                        <button id="aiNewSessionButton" class="ui mini teal button" type="button">新会话</button>
                    </div>

                    <div class="ai-session-toolbar">
                        <div class="ai-session-search">
                            <div class="ui icon input fluid">
                                <input id="aiSessionSearchInput" type="text" placeholder="搜索会话标题或内容">
                                <i class="search icon"></i>
                            </div>
                        </div>
                        <div class="ai-session-sort">
                            <select id="aiSessionSortSelect" class="ui fluid dropdown">
                                <option value="recent">最近更新</option>
                                <option value="oldest">最早更新</option>
                                <option value="name">标题排序</option>
                            </select>
                        </div>
                    </div>

                    <div id="aiSessionList" class="ai-session-list"></div>
                </div>
            </div>

            <!-- 中间：实时交互流 - 占比最大 -->
            <div class="seven wide column">
                <div class="ai-panel ai-chat-panel glass">
                    <div class="ai-panel-header">
                        <div>
                            <div class="ai-eyebrow">Animated Console</div>
                            <h3>实时交互流</h3>
                        </div>
                        <div class="ai-panel-desc">像工作台一样连续接收建议、入口和下一步动作</div>
                    </div>

                    <div id="aiMessageList" class="ai-message-list"></div>

                    <div class="ai-timeline-panel">
                        <div class="ai-sub-title">会话时间线</div>
                        <div id="aiTimelineList" class="ai-timeline-list"></div>
                    </div>

                    <div class="ai-starter-row">
                        <#list starterQuestions as question>
                            <button class="ui button basic tiny teal starter-question"
                                    data-question="${question?html}">
                                ${question?html}
                            </button>
                        </#list>
                    </div>

                    <div class="ai-compose-box">
                        <!-- file input 必须在 button 外部，否则浏览器会吞掉 click 事件 -->
                        <input type="file" id="aiImageInput" accept="image/*" style="display:none">
                        <textarea id="aiQuestionInput" rows="3"
                                  placeholder="例如：帮我总结一下当前项目概况，或者告诉我线上异常该怎么排查"></textarea>
                        <div class="ai-compose-actions">
                            <span id="aiRequestState" class="ai-request-state">就绪</span>
                            <div class="ai-compose-toolbar">
                                <button type="button" id="aiImageUploadBtn" class="ai-toolbar-btn" title="上传图片">
                                    <i class="image icon"></i>
                                    <img class="ai-image-preview" alt="preview">
                                    <span class="ai-image-remove-btn" title="移除图片"><i class="close icon"></i></span>
                                </button>
                                <button type="button" id="aiVoiceRecordBtn" class="ai-toolbar-btn" title="语音输入">
                                    <i class="microphone icon"></i>
                                </button>
                            </div>
                            <button id="aiSendButton" class="ui teal button">发送</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 右侧：动态侧边栏 - 占比减少 -->
            <div class="four wide column">
                <div class="ai-panel ai-capability-panel glass">
                    <div class="ai-panel-header compact">
                        <div>
                            <div class="ai-eyebrow">Workbench Signals</div>
                            <h3>动态侧边栏</h3>
                        </div>
                    </div>

                    <div class="ai-mini-stats workbench">
                        <div class="ai-mini-stat">
                            <span class="label">接入应用</span>
                            <span class="value">${appCount}</span>
                        </div>
                        <div class="ai-mini-stat">
                            <span class="label">在线应用</span>
                            <span class="value">${onlineAppCount}</span>
                        </div>
                    </div>

                    <div class="ai-sub-title">能力卡片</div>
                    <div class="ai-ability-list">
                        <#list abilityCards as card>
                            <div class="ai-ability-card">
                                <div class="ai-ability-title">${card.title?html}</div>
                                <div class="ai-ability-value">${card.value?html}</div>
                                <div class="ai-ability-desc">${card.description?html}</div>
                            </div>
                        </#list>
                    </div>

                    <div class="ai-sub-title">应用矩阵</div>
                    <div class="ai-tag-list">
                        <#if appNames?size gt 0>
                            <#list appNames as appName>
                                <span class="ai-tag">${appName?html}</span>
                            </#list>
                        <#else>
                            <span class="ai-empty-tag">暂未接入应用</span>
                        </#if>
                    </div>

                    <div class="ai-sub-title">推荐入口</div>
                    <div id="aiQuickLinkList" class="ai-quick-link-list">
                        <#list quickLinks as link>
                            <a class="ai-quick-link-card" href="${link.url?html}">
                                <div class="ai-quick-link-title">${link.title?html}</div>
                                <div class="ai-quick-link-desc">${link.description?html}</div>
                            </a>
                        </#list>
                    </div>

                    <div class="ai-sub-title">推荐提问</div>
                    <div id="aiFollowUpList" class="ai-follow-up-list">
                        <#list starterQuestions as question>
                            <button class="ui button basic fluid small starter-question"
                                    data-question="${question?html}">
                                ${question?html}
                            </button>
                        </#list>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>

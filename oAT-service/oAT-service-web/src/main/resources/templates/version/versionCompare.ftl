<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>版本中心-版本比对</title>
    <#include "../common.ftl">
    <script src="/js/upload.js?version=1"></script>
    <script src="/js/spark-md5.min.js"></script>
<style>
    .git-compare-panel .git-entry-shell {
        background: linear-gradient(180deg, #f9fbff 0%, #f4f7fb 100%);
        border: 1px solid #d9e1ec;
        border-radius: 12px;
        padding: 16px 16px 12px;
        box-shadow: inset 0 1px 0 rgba(255,255,255,0.7);
    }

    .git-compare-panel .git-entry-topbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 14px;
        flex-wrap: wrap;
    }

    .git-compare-panel .git-entry-title {
        font-size: 14px;
        font-weight: 700;
        color: #243447;
        letter-spacing: 0.02em;
    }

    .git-compare-panel .git-entry-subtitle {
        font-size: 12px;
        color: #6b7b8c;
        margin-top: 4px;
    }

    .git-compare-panel .git-entry-actions {
        display: flex;
        align-items: center;
        gap: 8px;
        flex-wrap: wrap;
    }

    .git-compare-panel .git-entry-grid {
        display: grid;
        grid-template-columns: 1.05fr 1.3fr auto 1.3fr;
        gap: 14px;
        align-items: stretch;
    }

    .git-compare-panel .git-entry-card {
        background: #ffffff;
        border: 1px solid #dfe6ee;
        border-radius: 12px;
        padding: 14px;
        min-height: 154px;
        display: flex;
        flex-direction: column;
        gap: 10px;
        transition: box-shadow .15s ease, border-color .15s ease, transform .15s ease;
    }

    .git-compare-panel .git-entry-card:hover {
        border-color: #c8d5e6;
        box-shadow: 0 8px 20px rgba(36,52,71,0.08);
        transform: translateY(-1px);
    }

    .git-compare-panel .git-entry-card.compare-target-old {
        border-top: 3px solid #f2711c;
    }

    .git-compare-panel .git-entry-card.compare-target-new {
        border-top: 3px solid #2185d0;
    }

    .git-compare-panel .git-entry-card.branch-card {
        border-top: 3px solid #767676;
    }

    .git-compare-panel .git-compare-direction {
        display: flex;
        align-items: center;
        justify-content: center;
        min-width: 78px;
    }

    .git-compare-panel .git-compare-direction-inner {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 12px 8px;
        min-height: 154px;
        color: #4d6175;
    }

    .git-compare-panel .git-compare-direction-arrow {
        width: 42px;
        height: 42px;
        border-radius: 999px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, #fff7f2 0%, #eef7ff 100%);
        border: 1px solid #d7e1ec;
        color: #3c5975;
        box-shadow: 0 6px 16px rgba(36,52,71,0.08);
    }

    .git-compare-panel .git-compare-direction-title {
        font-size: 12px;
        font-weight: 700;
        color: #33475b;
        text-align: center;
    }

    .git-compare-panel .git-compare-direction-text {
        font-size: 11px;
        color: #7b8a99;
        line-height: 1.5;
        text-align: center;
        max-width: 76px;
    }

    .git-compare-panel .git-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
    }

    .git-compare-panel .git-card-label {
        font-size: 13px;
        font-weight: 700;
        color: #2d3e50;
    }

    .git-compare-panel .git-card-hint {
        font-size: 12px;
        color: #7b8a99;
        line-height: 1.5;
        min-height: 36px;
    }

    .git-compare-panel .git-manual-input {
        width: 100%;
    }

    .git-compare-panel .git-manual-input input {
        border-radius: 10px !important;
    }

    .git-compare-panel .git-inline-actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        flex-wrap: wrap;
    }

    .git-compare-panel .git-selected-preview {
        min-height: 34px;
        padding: 8px 10px;
        border-radius: 10px;
        background: #f7f9fc;
        border: 1px dashed #d7dee8;
        color: #526273;
        font-size: 12px;
        line-height: 1.5;
        word-break: break-all;
    }

    .git-compare-panel .git-selected-preview.is-empty {
        color: #9aa7b4;
    }

    .git-picker-modal.ui.modal {
        border-radius: 16px;
        overflow: hidden;
        position: fixed !important;
        top: 50% !important;
        left: 50% !important;
        transform: translate(-50%, -50%) !important;
        margin: 0 !important;
        z-index: 1002 !important;
        max-width: min(980px, calc(100vw - 32px));
        width: min(980px, calc(100vw - 32px));
    }

    .ui.dimmer.modals.page.transition.visible.active {
        display: flex !important;
        align-items: center;
        justify-content: center;
        padding: 16px;
        z-index: 1001 !important;
    }

    .git-picker-modal .header {
        background: linear-gradient(135deg, #243447 0%, #2f4258 100%);
        color: #fff !important;
        border-bottom: none !important;
    }

    .git-picker-modal .content {
        padding: 20px 22px 16px !important;
        background: #f7f9fc;
    }

    .git-picker-modal .actions {
        background: #eef3f8 !important;
        border-top: 1px solid #dce5ef !important;
        padding: 14px 18px !important;
    }

    .git-picker-modal .git-picker-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 12px;
        flex-wrap: wrap;
    }

    .git-picker-modal .git-picker-search {
        flex: 1 1 520px;
        min-width: 320px;
    }

    .git-picker-modal .git-picker-toggle {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 7px 10px;
        border-radius: 999px;
        background: #eef4fb;
        border: 1px solid #d8e2ef;
        color: #38506a;
        font-size: 12px;
        cursor: pointer;
        user-select: none;
    }

    .git-picker-modal .git-picker-toggle input {
        margin: 0;
    }

    .git-picker-modal .git-picker-list {
        max-height: 420px;
        overflow-y: auto;
        border: 1px solid #dce5ef;
        border-radius: 12px;
        background: #fff;
        padding: 10px;
    }

    .git-picker-modal .git-picker-item {
        border: 1px solid transparent;
        border-radius: 12px;
        padding: 12px 12px 10px;
        cursor: pointer;
        transition: all .15s ease;
        background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
    }

    .git-picker-modal .git-picker-item + .git-picker-item {
        margin-top: 8px;
    }

    .git-picker-modal .git-picker-item:hover {
        background: #f5f9ff;
        border-color: #cfe0f2;
    }

    .git-picker-modal .git-picker-item.disabled {
        opacity: 0.55;
        cursor: not-allowed;
        background: #f7f8fa;
        border-color: #e4e8ee;
        box-shadow: none;
    }

    .git-picker-modal .git-picker-item.disabled:hover {
        background: #f7f8fa;
        border-color: #e4e8ee;
    }

    .git-picker-modal .git-picker-warning {
        margin-top: 8px;
        font-size: 11px;
        color: #c26d1a;
    }

    .git-picker-modal .git-picker-main {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 8px;
    }

    .git-picker-modal .git-picker-main-left {
        min-width: 0;
        flex: 1;
    }

    .git-picker-modal .git-picker-value {
        font-size: 13px;
        font-weight: 700;
        color: #203040;
        word-break: break-all;
    }

    .git-picker-modal .git-picker-subvalue {
        font-size: 11px;
        color: #8090a0;
        margin-top: 3px;
    }

    .git-picker-modal .git-picker-tag {
        white-space: nowrap;
        font-size: 11px;
        color: #526273;
        background: #f4f7fb;
        border: 1px solid #dbe4ee;
        border-radius: 999px;
        padding: 3px 8px;
    }

    .git-picker-modal .git-picker-meta-row {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        margin-bottom: 8px;
    }

    .git-picker-modal .git-picker-chip {
        font-size: 11px;
        color: #415365;
        background: #f6f9fc;
        border: 1px solid #dce5ef;
        border-radius: 999px;
        padding: 2px 8px;
    }

    .git-picker-modal .git-picker-meta {
        font-size: 12px;
        color: #738395;
        line-height: 1.5;
    }

    .git-picker-modal .git-picker-empty {
        padding: 26px 12px;
        text-align: center;
        color: #91a0af;
        font-size: 13px;
    }

    @media (max-width: 1200px) {
        .git-compare-panel .git-entry-grid {
            grid-template-columns: 1fr;
        }

        .git-compare-panel .git-compare-direction-inner {
            min-height: auto;
            padding: 2px 0;
        }

        .git-compare-panel .git-compare-direction-text {
            max-width: none;
        }
    }
</style>
</head>
<body>
<!--头部菜单 引入-->
<#assign versionItemActive="active">
<#include "../projectHeader.ftl">
<!--面包屑导航-->
<div class="ui breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
    <span class="divider">/</span>
    <a class=" section" href="/p/${project.id}/${app.id}/version/list">${app.name}</a>
    <span class="divider">/</span>
    <div class="active section">版本比对</div>
</div>

<!--内容主体-->
<div class="ui grid attached container" style="margin-top: 14px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign appId=app.id/>
        <#assign appName=app.name/>
        <#assign versionCompareActive="active"/>
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <div class="ui attached positive message">
            <div class="header">
                开始比对版本
            </div>
            <p>基于 Git 或 制品包（JAR 包或 WAR 包）比对其内部文件差异，并根据差异分析出对系统的功能影响范围</p>
        </div>

        <div class="ui attached segment git-compare-panel" style="min-height: 200px">
            <div class="ui top attached tabular menu">
                <a class="item active" data-tab="git">基于 Git 差异</a>
                <a class="item" data-tab="package">制品包比对</a>
            </div>
            <div class="ui bottom attached segment">
                <div class="ui tab active" data-tab="git">
                    <form class="ui form" action="/p/${project.id}/${app.id}/version/git/compare" method="get" id="gitCompareForm">
                        <div class="git-entry-shell">
                            <div class="git-entry-topbar">
                                <div>
                                    <div class="git-entry-title">Git 差异比对参数</div>
                                    <div class="git-entry-subtitle">支持直接录入，也支持从已存在的版本记录中快速带入分支与 commit。</div>
                                </div>
                                <div class="git-entry-actions">
                                    <button class="ui small basic button" type="button" id="gitComparePickBranchButton">
                                        <i class="list ul icon"></i>从版本列表选择分支
                                    </button>
                                </div>
                            </div>

                            <div class="git-entry-grid">
                                <div class="git-entry-card branch-card">
                                    <div class="git-card-header">
                                        <div class="git-card-label">分支 (branch)</div>
                                        <span class="ui mini basic label">必填</span>
                                    </div>
                                    <div class="git-card-hint">先确定比对所在分支，可手工输入，也可从已有版本记录中选择。</div>
                                    <div class="ui input git-manual-input">
                                        <input type="text" name="branch" placeholder="例如: master / release/1.2.x" id="gitCompareBranchInput" list="gitCompareBranchOptions">
                                    </div>
                                    <div class="git-inline-actions">
                                        <button class="ui mini basic button" type="button" id="gitComparePickBranchInlineButton">从版本列表选择</button>
                                    </div>
                                    <div class="git-selected-preview is-empty" id="gitCompareBranchPreview">尚未选择分支，可直接手工输入。</div>
                                </div>

                                <div class="git-entry-card compare-target-old">
                                    <div class="git-card-header">
                                        <div class="git-card-label">旧 Commit / ref</div>
                                        <span class="ui mini orange basic label">基线</span>
                                    </div>
                                    <div class="git-card-hint">填写旧版本的 commit、分支或 tag，作为比对起点。</div>
                                    <div class="ui input git-manual-input">
                                        <input type="text" name="oldCommit" placeholder="手工输入旧版本 commit id / 分支 / tag" id="gitCompareOldCommitInput" list="gitCompareOldCommitOptions">
                                    </div>
                                    <div class="git-inline-actions">
                                        <button class="ui mini basic orange button" type="button" id="gitComparePickOldButton">从版本列表选择</button>
                                    </div>
                                    <div class="git-selected-preview is-empty" id="gitCompareOldCommitPreview">尚未选择旧版本，可直接手工输入。</div>
                                </div>

                                <div class="git-compare-direction">
                                    <div class="git-compare-direction-inner">
                                        <div class="git-compare-direction-arrow">
                                            <i class="long arrow alternate right icon"></i>
                                        </div>
                                        <div class="git-compare-direction-title">比对方向</div>
                                        <div class="git-compare-direction-text">从旧版本基线流向新版本目标</div>
                                    </div>
                                </div>

                                <div class="git-entry-card compare-target-new">
                                    <div class="git-card-header">
                                        <div class="git-card-label">新 Commit / ref</div>
                                        <span class="ui mini blue basic label">目标</span>
                                    </div>
                                    <div class="git-card-hint">填写新版本的 commit、分支或 tag，作为本次差异的目标版本。</div>
                                    <div class="ui input git-manual-input">
                                        <input type="text" name="newCommit" placeholder="手工输入新版本 commit id / 分支 / tag" id="gitCompareNewCommitInput" list="gitCompareNewCommitOptions">
                                    </div>
                                    <div class="git-inline-actions">
                                        <button class="ui mini basic blue button" type="button" id="gitComparePickNewButton">从版本列表选择</button>
                                    </div>
                                    <div class="git-selected-preview is-empty" id="gitCompareNewCommitPreview">尚未选择新版本，可直接手工输入。</div>
                                </div>
                            </div>
                        </div>
                        <datalist id="gitCompareBranchOptions"></datalist>
                        <datalist id="gitCompareOldCommitOptions"></datalist>
                        <datalist id="gitCompareNewCommitOptions"></datalist>
                        <div class="ui mini info message" id="gitCompareCommitTip" style="margin-top: 12px;">
                            分支、旧 Commit / ref、新 Commit / ref 都支持手工输入，也支持从当前版本列表中已有的 branch / commitId 中选择。
                        </div>
                        <div class="field" style="margin-top: 14px;">
                            <label>比较范围（包名）：</label>
                            <input type="text" name="packageName" placeholder="默认是包的所有范围（例如 com.example）">
                        </div>
                        <button class="ui button primary" type="submit">开始 Git 比对</button>
                    </form>
                </div>

                <div class="ui tab" data-tab="package">
                    <form class="ui form" action="compare/start" method="post">
                        <div class="two fields">
                            <div class=" field upload file">
                                <label>源版本(新)：</label>
                                <div class="ui fluid search selection dropdown uploaded-files" id="sourceFileDropdown">
                                    <input type="hidden" name="sourceFile">
                                    <i class="dropdown icon"></i>
                                    <div class="default text">上传或选择已上传包</div>
                                    <div class="menu">
                                        <#list items as item>
                                            <div class="item" data-value="${item.programFile!}" data-name="${item.programName!}">
                                                <i class="file alternate outline icon"></i>
                                                <span class="text">${item.programName!}</span>
                                                <i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;" onclick="doDeleteFile('${item.programFile!}', event, this)"></i>
                                            </div>
                                        </#list>
                                    </div>
                                </div>
                                <div style="margin-top: 8px;">
                                    <input type="file" accept=".war,.jar" style="display: none;" id="sourceFileInput">
                                    <button class="ui mini basic button" type="button" onclick="$('#sourceFileInput').click()"><i class="upload icon"></i>上传新包</button>
                                </div>
                                <div class="ui bottom attached progress" style="margin-top: 5px; height: 4px;">
                                    <div class="bar"></div>
                                </div>
                                <div class="file-info" style="margin-top: 5px; display: none;">
                                    <span class="file-name" style="font-size: 0.9em; color: #666;"></span>
                                    <a href="javascript:void(0)" class="delete-file" style="margin-left: 10px; color: #db2828; font-size: 0.85em;"><i class="trash alternate icon"></i>删除物理文件</a>
                                </div>
                            </div>
                            <i class="ui icon large grey retweet" style="position: relative;top: 30px"></i>
                            <div class="field upload file">
                                <label>目标版本(旧)：</label>
                                <div class="ui fluid search selection dropdown uploaded-files" id="targetFileDropdown">
                                    <input type="hidden" name="targetFile">
                                    <i class="dropdown icon"></i>
                                    <div class="default text">上传或选择已上传包</div>
                                    <div class="menu">
                                        <#list items as item>
                                            <div class="item" data-value="${item.programFile!}" data-name="${item.programName!}">
                                                <i class="file alternate outline icon"></i>
                                                <span class="text">${item.programName!}</span>
                                                <i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;" onclick="doDeleteFile('${item.programFile!}', event, this)"></i>
                                            </div>
                                        </#list>
                                    </div>
                                </div>
                                <div style="margin-top: 8px;">
                                    <input type="file" accept=".war,.jar" style="display: none;" id="targetFileInput">
                                    <button class="ui mini basic button" type="button" onclick="$('#targetFileInput').click()"><i class="upload icon"></i>上传新包</button>
                                </div>
                                <div class="ui bottom attached progress" style="margin-top: 5px; height: 4px;">
                                    <div class="bar"></div>
                                </div>
                                <div class="file-info" style="margin-top: 5px; display: none;">
                                    <span class="file-name" style="font-size: 0.9em; color: #666;"></span>
                                    <a href="javascript:void(0)" class="delete-file" style="margin-left: 10px; color: #db2828; font-size: 0.85em;"><i class="trash alternate icon"></i>删除物理文件</a>
                                </div>
                            </div>
                        </div>
                        <div class="field">
                            <label>比较范围（应用包名）：</label>
                            <input type="text" name="packageName" placeholder="默认是包的所有范围">
                        </div>
                        <button class="ui button primary" type="submit">开始比对</button>
                    </form>
                </div>
            </div>
        </div>


        <h4 class="ui header top attached block">
            <i class="grey icon history"></i>
            比对记录
        </h4>
        <div class="ui attached segment">
            <table class="ui fixed selectable table celled">
                <thead>
                <tr>
                    <th class="five wide">报告名称</th>
                    <th class="four wide center aligned">创建时间</th>
                    <th class="four wide center aligned">比对版本 (新 / 旧)</th>
                    <th class="three wide center aligned">操作</th>
                </tr>
                </thead>
                <tbody>
                <#list reports as report >
                <tr>
                    <td class="ellipsis-tooltip" title="${report.name!}"><b>${report.name!}</b>
                        <#if report.addClassCount?? || report.updateClassCount?? || report.deleteClassCount?? || report.addMethodCount?? || report.updateMethodCount?? || report.deleteMethodCount?? || report.impactCaseCount??>
                            <div style="margin-top:6px;font-size:0.85em;color:#666;">
                                <#if report.addClassCount??>
                                    <span class="ui mini green label">新增类 ${report.addClassCount}</span>
                                </#if>
                                <#if report.updateClassCount??>
                                    <span class="ui mini orange label">更新类 ${report.updateClassCount}</span>
                                </#if>
                                <#if report.deleteClassCount??>
                                    <span class="ui mini red label">删除类 ${report.deleteClassCount}</span>
                                </#if>
                                <#if report.addMethodCount??>
                                    <span class="ui mini">新增方法 ${report.addMethodCount}</span>
                                </#if>
                                <#if report.updateMethodCount??>
                                    <span class="ui mini">更新方法 ${report.updateMethodCount}</span>
                                </#if>
                                <#if report.deleteMethodCount??>
                                    <span class="ui mini red">删除方法 ${report.deleteMethodCount}</span>
                                </#if>
                                <#if report.impactCaseCount??>
                                    <span class="ui mini label">影响用例 ${report.impactCaseCount}</span>
                                </#if>
                            </div>
                        </#if>
                    </td>
                    <td class="center aligned">
                        ${report.createTime?datetime}
                    </td>
                    <td class="center aligned">
                        <div class="ui mini label">${report.sourceVersion!'-'}</div>
                        <i class="right arrow icon"></i>
                        <div class="ui mini label">${report.targetVersion!'-'}</div>
                    </td>
                    <td class="center aligned">
                        <div style="display: flex; justify-content: center; gap: 5px;">
                            <a href="/p/${project.id}/version/report/${report.id}" class="ui mini basic blue button">查看</a>
                            <button class="ui mini basic red button" onclick="doDeleteReport('${report.id}', this)">删除</button>
                        </div>
                    </td>
                </tr>
                </#list>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div id="deleteVersionDialog" class="ui small modal">
    <div class="header">删除报告</div>
    <div class="ui negative message">
        <div class="header">
            确定删除该报告吗？
        </div>
        <p> 删除之后将无法恢复</p>
    </div>

    <div class="actions">
        <a id="deleteReportButton" href="#" class="ui negative button">删除</a>
        <div class="ui cancel button">不</div>
    </div>
</div>

<div id="deleteFileDialog" class="ui small modal">
    <div class="header">删除本地文件</div>
    <div class="ui negative message">
        <div class="header">
            确定要删除本地服务器上的这个文件吗？
        </div>
        <p>删除之后将无法恢复，且下拉列表中该选项也将消失。</p>
    </div>

    <div class="actions">
        <button id="confirmDeleteFileButton" class="ui negative button">确定删除</button>
        <div class="ui cancel button">取消</div>
    </div>
</div>

<script>
    // 初始化下拉菜单并设置持久化
    $(function() {
        var appId = "${app.id}";
        var storageKeySource = "oAT_compare_source_" + appId;
        var storageKeyTarget = "oAT_compare_target_" + appId;
        var storageKeyPkg = "oAT_compare_pkg_" + appId;
        var storageKeyAllFiles = "oAT_uploaded_files_" + appId;
        var gitCommitStorageKey = "oAT_compare_git_commits_" + appId;
        var gitBranchStorageKey = "oAT_compare_git_branch_" + appId;
        var gitOldCommitStorageKey = "oAT_compare_git_old_commit_" + appId;
        var gitNewCommitStorageKey = "oAT_compare_git_new_commit_" + appId;
        var gitCommitList = [];
        var gitVersionOptions = [
            <#list versionItems as item>
            {
                versionNumber: '${(item.versionNumber!"")?js_string}',
                branch: '${(item.repoBranch!"")?js_string}',
                commitId: '${(item.repoCommitId!"")?js_string}',
                programName: '${(item.programName!"")?js_string}'
            }<#if item_has_next>,</#if>
            </#list>
        ];

        function escapeHtml(text) {
            return $('<div/>').text(text || '').html();
        }

        function renderVersionBasedGitOptions() {
            var branchMap = {};
            var commitMap = {};
            gitVersionOptions.forEach(function(item) {
                if (item.branch) {
                    branchMap[item.branch] = true;
                }
                if (item.commitId) {
                    commitMap[item.commitId] = item;
                }
            });

            var branchHtml = Object.keys(branchMap).sort().map(function(branch) {
                return '<option value="' + escapeHtml(branch) + '"></option>';
            }).join('');
            $('#gitCompareBranchOptions').html(branchHtml);

            var commitHtml = Object.keys(commitMap).map(function(commitId) {
                var item = commitMap[commitId] || {};
                var label = commitId;
                if (item.versionNumber) {
                    label += ' | 版本 ' + item.versionNumber;
                }
                if (item.branch) {
                    label += ' | 分支 ' + item.branch;
                }
                return '<option value="' + escapeHtml(commitId) + '">' + escapeHtml(label) + '</option>';
            }).join('');
            $('#gitCompareOldCommitOptions').html(commitHtml);
            $('#gitCompareNewCommitOptions').html(commitHtml);
        }

        function buildVersionPickerItems(type) {
            var candidates = gitVersionOptions.filter(function(item) {
                return item && (item.branch || item.commitId || item.versionNumber);
            });
            if (!candidates.length) {
                return '<div class="git-picker-empty">版本列表中暂无可选项</div>';
            }
            return candidates.map(function(item) {
                var value = type === 'branch' ? item.branch : item.commitId;
                var shortCommit = item.commitId ? item.commitId.substring(0, 8) : '';
                var chips = [];
                var metaText = item.programName || '来自版本记录';
                var missingInfo = [];
                var selectable = type === 'branch' ? !!item.branch : !!item.commitId;

                if (item.versionNumber) {
                    chips.push('<span class="git-picker-chip">版本 ' + escapeHtml(item.versionNumber) + '</span>');
                }
                if (item.branch) {
                    chips.push('<span class="git-picker-chip">分支 ' + escapeHtml(item.branch) + '</span>');
                } else {
                    missingInfo.push('缺少 branch');
                }
                if (item.commitId) {
                    if (type === 'commit' && shortCommit) {
                        chips.push('<span class="git-picker-chip">短 Commit ' + escapeHtml(shortCommit) + '</span>');
                    }
                } else {
                    missingInfo.push('缺少 commitId');
                }

                return '<div class="git-picker-item' + (selectable ? '' : ' disabled') + '" data-value="' + escapeHtml(value || '') + '" data-selectable="' + (selectable ? 'true' : 'false') + '">'
                    + '<div class="git-picker-main">'
                    + '<div class="git-picker-main-left">'
                    + '<div class="git-picker-value">' + escapeHtml(value || ('版本 ' + (item.versionNumber || '-') + ' 缺少可用 Git 信息')) + '</div>'
                    + (type === 'commit' && item.versionNumber ? '<div class="git-picker-subvalue">版本 ' + escapeHtml(item.versionNumber) + ' 的代码记录</div>' : '')
                    + '</div>'
                    + '<div class="git-picker-tag">' + (type === 'branch' ? 'Branch' : 'Commit') + '</div>'
                    + '</div>'
                    + (chips.length ? '<div class="git-picker-meta-row">' + chips.join('') + '</div>' : '')
                    + '<div class="git-picker-meta">' + escapeHtml(metaText) + '</div>'
                    + (missingInfo.length ? '<div class="git-picker-warning">' + escapeHtml(missingInfo.join('，') + '，该记录不可选') + '</div>' : '')
                    + '</div>';
            }).join('');
        }

        function updateGitSelectionPreview($input, $preview, type) {
            var value = $.trim($input.val() || '');
            var text = '';
            if (!value) {
                text = type === 'branch' ? '尚未选择分支，可直接手工输入。' : '尚未选择版本，可直接手工输入。';
                $preview.addClass('is-empty').text(text);
                return;
            }

            var matched = null;
            gitVersionOptions.some(function(item) {
                if (type === 'branch' && item.branch === value) {
                    matched = item;
                    return true;
                }
                if (type === 'commit' && item.commitId === value) {
                    matched = item;
                    return true;
                }
                return false;
            });

            if (matched) {
                var lines = [value];
                if (matched.versionNumber) {
                    lines.push('版本：' + matched.versionNumber);
                }
                if (matched.branch && type === 'commit') {
                    lines.push('分支：' + matched.branch);
                }
                $preview.removeClass('is-empty').html(lines.map(function(line) {
                    return '<div>' + escapeHtml(line) + '</div>';
                }).join(''));
            } else {
                $preview.removeClass('is-empty').text(value + '（手工录入）');
            }
        }

        function refreshGitSelectionPreviews() {
            updateGitSelectionPreview($('#gitCompareBranchInput'), $('#gitCompareBranchPreview'), 'branch');
            updateGitSelectionPreview($('#gitCompareOldCommitInput'), $('#gitCompareOldCommitPreview'), 'commit');
            updateGitSelectionPreview($('#gitCompareNewCommitInput'), $('#gitCompareNewCommitPreview'), 'commit');
        }

        function openVersionPicker(type, $input) {
            var modalTitle = type === 'branch' ? '从版本列表选择分支' : '从版本列表选择 Commit';
            var placeholder = type === 'branch' ? '搜索分支名' : '搜索 commitId / 版本号 / 分支';
            $('body').append('<div class="ui tiny modal git-picker-modal" id="gitCompareVersionModal">'
                + '<div class="header">' + modalTitle + '</div>'
                + '<div class="content">'
                + '<div class="git-picker-toolbar">'
                + '<div class="ui icon input fluid git-picker-search">'
                + '<input type="text" id="gitCompareVersionSearch" placeholder="' + placeholder + '">'
                + '<i class="search icon"></i>'
                + '</div>'
                + '<label class="git-picker-toggle">'
                + '<input type="checkbox" id="gitCompareOnlySelectableToggle" checked>'
                + '<span>仅显示可用记录</span>'
                + '</label>'
                + '</div>'
                + '<div class="git-picker-list" id="gitCompareVersionPickerList">'
                + buildVersionPickerItems(type)
                + '</div>'
                + '</div>'
                + '<div class="actions">'
                + '<div class="ui cancel button">取消</div>'
                + '<div class="ui primary approve button disabled" id="gitCompareVersionConfirmButton">使用这个值</div>'
                + '</div>'
                + '</div>');

            var $modal = $('#gitCompareVersionModal');
            var currentValue = $.trim($input.val() || '');

            function applyFilter(keyword) {
                var lower = $.trim(keyword || '').toLowerCase();
                var onlySelectable = $('#gitCompareOnlySelectableToggle').prop('checked');
                var visibleCount = 0;
                $modal.find('.git-picker-item').each(function() {
                    var $item = $(this);
                    var itemText = $item.text().toLowerCase();
                    var selectable = $item.data('selectable') === true;
                    var matchesKeyword = !lower || itemText.indexOf(lower) >= 0;
                    var visible = matchesKeyword && (!onlySelectable || selectable);
                    $item.toggle(visible);
                    if (visible) visibleCount++;
                });
                var $empty = $modal.find('.git-picker-empty-state');
                if (!visibleCount && !$modal.find('.git-picker-empty').length) {
                    if (!$empty.length) {
                        $modal.find('#gitCompareVersionPickerList').append('<div class="git-picker-empty git-picker-empty-state">未找到匹配项</div>');
                    }
                } else {
                    $empty.remove();
                }
            }

            function setActiveItem($item) {
                $modal.find('.git-picker-item').removeClass('active');
                if ($item && $item.length && $item.data('selectable') === true) {
                    $item.addClass('active');
                    $('#gitCompareVersionConfirmButton').removeClass('disabled').data('value', $item.data('value'));
                } else {
                    $('#gitCompareVersionConfirmButton').addClass('disabled').removeData('value');
                }
            }

            $modal.modal({
                detachable: true,
                autofocus: false,
                observeChanges: true,
                allowMultiple: false,
                closable: true,
                onVisible: function() {
                    var $items = $modal.find('.git-picker-item');
                    if (currentValue) {
                        $items.each(function() {
                            var $item = $(this);
                            if ($item.data('value') === currentValue) {
                                setActiveItem($item);
                                return false;
                            }
                        });
                    }
                    applyFilter('');
                    $('#gitCompareVersionSearch').focus();
                },
                onApprove: function() {
                    var selectedValue = $('#gitCompareVersionConfirmButton').data('value');
                    if (!selectedValue) {
                        return false;
                    }
                    $input.val(selectedValue).trigger('input').trigger('change');
                },
                onHidden: function() {
                    $modal.remove();
                }
            }).modal('show');

            $modal.on('click', '.git-picker-item', function() {
                if ($(this).data('selectable') !== true) {
                    return;
                }
                setActiveItem($(this));
            });

            $modal.on('dblclick', '.git-picker-item', function() {
                if ($(this).data('selectable') !== true) {
                    return;
                }
                setActiveItem($(this));
                $modal.modal('approve');
            });

            $modal.on('input', '#gitCompareVersionSearch', function() {
                applyFilter($(this).val());
            });

            $modal.on('change', '#gitCompareOnlySelectableToggle', function() {
                setActiveItem(null);
                applyFilter($('#gitCompareVersionSearch').val());
            });
        }
        function persistGitInputs() {
            localStorage.setItem(gitBranchStorageKey, $('#gitCompareBranchInput').val() || '');
            localStorage.setItem(gitOldCommitStorageKey, $('#gitCompareOldCommitInput').val() || '');
            localStorage.setItem(gitNewCommitStorageKey, $('#gitCompareNewCommitInput').val() || '');
            refreshGitSelectionPreviews();
        }

        function restoreGitInputs() {
            var savedBranch = localStorage.getItem(gitBranchStorageKey);
            var savedOldCommit = localStorage.getItem(gitOldCommitStorageKey);
            var savedNewCommit = localStorage.getItem(gitNewCommitStorageKey);

            if (savedBranch) {
                $('#gitCompareBranchInput').val(savedBranch);
            }
            if (savedOldCommit) {
                $('#gitCompareOldCommitInput').val(savedOldCommit);
            }
            if (savedNewCommit) {
                $('#gitCompareNewCommitInput').val(savedNewCommit);
            }
            $('#gitCompareCommitTip').removeClass('negative warning').addClass('info').text('分支与 commit 支持直接录入；如需复用已有版本记录，可点击下方按钮快速带入。');
            refreshGitSelectionPreviews();
        }

        $('#gitCompareBranchInput, #gitCompareOldCommitInput, #gitCompareNewCommitInput').on('input change blur', function() {
            persistGitInputs();
        });

        $('#gitComparePickBranchButton, #gitComparePickBranchInlineButton').on('click', function() {
            openVersionPicker('branch', $('#gitCompareBranchInput'));
        });

        $('#gitComparePickOldButton').on('click', function() {
            openVersionPicker('commit', $('#gitCompareOldCommitInput'));
        });

        $('#gitComparePickNewButton').on('click', function() {
            openVersionPicker('commit', $('#gitCompareNewCommitInput'));
        });

        $('#gitCompareForm').on('submit', function() {
            persistGitInputs();
        });

        renderVersionBasedGitOptions();
        restoreGitInputs();

        // 绑定删除事件的辅助函数
        function bindDeleteItemEvents($context) {
            $context.find('.delete-item').off('click').on('click', function(e) {
                var filePath = $(this).closest('.item').data('value');
                doDeleteFile(filePath, e, this);
            });
        }

        $('.uploaded-files').dropdown({
            onChange: function(value, text, $choice) {
                var id = $(this).attr('id');
                if (value) {
                    if (id === 'sourceFileDropdown') {
                        localStorage.setItem(storageKeySource, value);
                        localStorage.setItem(storageKeySource + "_text", $choice.data('name') || text);
                    } else if (id === 'targetFileDropdown') {
                        localStorage.setItem(storageKeyTarget, value);
                        localStorage.setItem(storageKeyTarget + "_text", $choice.data('name') || text);
                    }
                }
            }
        });

        // 恢复所有手动上传的文件到下拉列表
        var allUploadedFiles = JSON.parse(localStorage.getItem(storageKeyAllFiles) || "[]");
        if (allUploadedFiles.length > 0) {
            var extraItemsHtml = '';
            allUploadedFiles.forEach(function(f) {
                // 仅当菜单中不存在时添加（避免与后端返回的重复）
                if ($('.uploaded-files .menu .item[data-value="' + f.path + '"]').length === 0) {
                    extraItemsHtml += '<div class="item" data-value="' + f.path + '" data-name="' + f.name + '">' +
                        '<i class="file alternate outline icon"></i>' +
                        '<span class="text">' + f.name + '</span>' +
                        '<i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;"></i>' +
                        '</div>';
                }
            });
            if (extraItemsHtml) {
                $('.uploaded-files .menu').prepend(extraItemsHtml);
                $('.uploaded-files').dropdown('refresh');
                bindDeleteItemEvents($('.uploaded-files .menu'));
            }
        } else {
            // 对现有的项也要绑定一次删除事件
            bindDeleteItemEvents($('.uploaded-files .menu'));
        }

        // 恢复持久化数据
        var savedSource = localStorage.getItem(storageKeySource);
        var savedSourceText = localStorage.getItem(storageKeySource + "_text");
        if (savedSource) {
            $('#sourceFileDropdown').dropdown('set selected', savedSource);
            if (savedSourceText) $('#sourceFileDropdown').dropdown('set text', savedSourceText);
        }

        var savedTarget = localStorage.getItem(storageKeyTarget);
        var savedTargetText = localStorage.getItem(storageKeyTarget + "_text");
        if (savedTarget) {
            $('#targetFileDropdown').dropdown('set selected', savedTarget);
            if (savedTargetText) $('#targetFileDropdown').dropdown('set text', savedTargetText);
        }

        var savedPkg = localStorage.getItem(storageKeyPkg);
        if (savedPkg) {
            $('input[name="packageName"]').val(savedPkg);
        }

        $('input[name="packageName"]').on('input', function() {
            localStorage.setItem(storageKeyPkg, $(this).val());
        });
    });

    // 处理删除下拉项中的文件
    function doDeleteFile(filePath, event, elem) {
        if (event) event.stopPropagation();
        if (!filePath) return;

        // 存储待删除路径到 Modal 确认按钮
        $("#confirmDeleteFileButton").data('filePath', filePath);
        // 显示 Modal 代替原生 confirm
        $("#deleteFileDialog").modal('show');
    }

    $(function() {
        // 绑定删除文件确认按钮点击事件
        $("#confirmDeleteFileButton").on('click', function() {
            var filePath = $(this).data('filePath');
            if (!filePath) return;

            $.post('/p/${project.id}/${appId}/version/file/delete', {filePath: filePath}, function(res) {
                if (res.success) {
                    showToast('本地文件已删除', 'success');
                    // 隐藏 Modal
                    $("#deleteFileDialog").modal('hide');

                    // 从左右下拉菜单中移除
                    $('.uploaded-files .menu .item[data-value="' + filePath + '"]').remove();

                    // 同步清理 localStorage 中的上传记录
                    var appId = "${app.id}";
                    var storageKeyAllFiles = "oAT_uploaded_files_" + appId;
                    var allUploadedFiles = JSON.parse(localStorage.getItem(storageKeyAllFiles) || "[]");
                    allUploadedFiles = allUploadedFiles.filter(function(f) { return f.path !== filePath; });
                    localStorage.setItem(storageKeyAllFiles, JSON.stringify(allUploadedFiles));

                    // 如果当前选中了该文件，重置下拉框
                    $('.uploaded-files').each(function() {
                        if ($(this).dropdown('get value') === filePath) {
                            $(this).dropdown('clear');
                            var id = $(this).attr('id');
                            var appId = "${app.id}";
                            if (id === 'sourceFileDropdown') {
                                localStorage.removeItem("oAT_compare_source_" + appId);
                                localStorage.removeItem("oAT_compare_source_" + appId + "_text");
                            } else {
                                localStorage.removeItem("oAT_compare_target_" + appId);
                                localStorage.removeItem("oAT_compare_target_" + appId + "_text");
                            }
                        }
                    });
                } else {
                    showToast('删除失败: ' + res.message, 'error');
                }
            });
        });
    });

    $(".upload.file input:file").change(function () {
        var $field = $(this).closest('.field');
        var dropdown = $field.find(".uploaded-files");
        var valueItem = $field.find("input:hidden");
        var progressItem = $field.find("div.progress");
        var fileInfo = $field.find(".file-info");
        var fileNameSpan = fileInfo.find(".file-name");

        var file = this.files[0];
        if (!file) return;

        uploadFile(file, "/resource/upload"
                , function (ev2) {
                    var results = eval("(" + this.responseText + ")");
                    if (results.success) {
                        var filePath = results.data;
                        progressItem.progress('complete');

                        // 记录到 localStorage 以便刷新后恢复
                        var appId = "${app.id}";
                        var storageKeyAllFiles = "oAT_uploaded_files_" + appId;
                        var allUploadedFiles = JSON.parse(localStorage.getItem(storageKeyAllFiles) || "[]");
                        // 查重
                        if (!allUploadedFiles.some(function(f) { return f.path === filePath; })) {
                            allUploadedFiles.push({path: filePath, name: file.name});
                            localStorage.setItem(storageKeyAllFiles, JSON.stringify(allUploadedFiles));
                        }

                        // 动态添加到下拉菜单
                        var newItemHtml = '<div class="item" data-value="' + filePath + '" data-name="' + file.name + '">' +
                            '<i class="file alternate outline icon"></i>' +
                            '<span class="text">' + file.name + '</span>' +
                            '<i class="trash alternate icon delete-item" style="float: right; color: #db2828; margin-top: 3px;"></i>' +
                            '</div>';

                        // 如果已存在相同路径的项，先移除
                        $('.uploaded-files .menu .item[data-value="' + filePath + '"]').remove();

                        // 两个下拉框都添加，确保同步
                        $('.uploaded-files .menu').prepend(newItemHtml);

                        // 重新绑定删除事件（因为 prepend 的是字符串）
                        $('.uploaded-files .menu .item[data-value="' + filePath + '"] .delete-item').off('click').on('click', function(e) {
                            doDeleteFile(filePath, e, this);
                        });

                        // 选中新上传的文件
                        $('.uploaded-files').dropdown('refresh');
                        dropdown.dropdown('set selected', filePath);
                        dropdown.dropdown('set text', file.name);

                        showToast('文件上传成功', 'success');
                    } else {
                        showToast('上传失败: ' + results.message, 'error');
                        progressItem.progress('reset');
                    }
                }
                , function (evt) {
                    // 进度条
                    progressItem.progress('set progress', evt.loaded * 100 / evt.total);
                });

    });

    // 修改现有的删除逻辑，兼容下拉框
    $(".delete-file").off('click').click(function() {
        var $field = $(this).closest('.field');
        var dropdown = $field.find(".uploaded-files");
        var filePath = dropdown.dropdown('get value');
        doDeleteFile(filePath);
    });

    $('.ui.click.dropdown').dropdown({
        on: 'click'
    });
    // tab init
    $('.menu .item').tab();
    $('.ui.filter.dropdown').dropdown({
        on: 'click'
    });

    function showDetail(id) {
        <!--显示节点详情-->
        $("#" + id).toggle();

    }

    function doDeleteReport(id, elem) {
        // 如果传入 elem，则记录该行用于局部刷新
        var row = elem ? $(elem).closest('tr') : null;
        // 存储待删除 id 与对应行到按钮 data 属性（避免直接使用 href 导致跳转）
        $("#deleteReportButton").data('reportId', id).data('reportRow', row).removeAttr('href');
        // 显示删除对话框
        $("#deleteVersionDialog").modal('show');
    }

    // 使用 AJAX POST 删除并优先进行局部刷新
    $(function() {
        $("#deleteReportButton").on('click', function(e) {
            // 阻止默认链接行为
            e.preventDefault();
            var id = $(this).data('reportId');
            if (!id) return;
            var row = $(this).data('reportRow');
            $.post('/p/${project.id}/${appId}/version/report/delete', {reportId: id}, function(res) {
                // Support both `success` (new) and legacy `result` (old)
                var ok = res && (res.success === true || res.result === true);
                if (ok) {
                    // 显示成功 toast
                    showToast(res.message || '删除成功', 'success');
                    // 优先进行局部移除行操作
                    if (row && row.length) {
                        row.remove();
                        // 隐藏 modal
                        $("#deleteVersionDialog").modal('hide');
                        // 如果表格已空，替换为占位提示
                        if ($("table.ui.fixed.selectable.table.celled tbody tr").length === 0) {
                            var placeholder = '<div class="ui placeholder segment">'
                                + '<div class="ui icon header">'
                                + '<i class="law icon"></i>'
                                + '暂无代码比对记录</div>'
                                + '<a href="/p/${project.id}/${appId}/version/compare" class="ui primary button">前往发起比对</a>'
                                + '</div>';
                            $("table.ui.fixed.selectable.table.celled").parent().html(placeholder);
                        }
                    } else {
                        // 如果无法定位到行（例如从其它页面触发），则跳转回列表页以刷新
                        setTimeout(function() {
                            window.location.href = '/p/${project.id}/${appId}/version/report/list?tab=compare';
                        }, 700);
                    }
                } else {
                    showToast('删除失败: ' + (res ? (res.message || res.errorMessage || '未知错误') : '未知错误'), 'error');
                }
            }).fail(function() {
                showToast('删除请求发送失败', 'error');
            });
        });
    });
</script>
</body>
</html>

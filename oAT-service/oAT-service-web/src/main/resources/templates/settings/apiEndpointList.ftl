<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>接口扫描与覆盖</title>
    <#include "../common.ftl">
    <style>
        .project-settings-page {
            margin-top: 18px;
            margin-bottom: 42px;
        }

        .project-settings-breadcrumb {
            margin: 6px auto 18px !important;
            color: #6b7785;
        }

        .project-settings-layout {
            display: grid;
            grid-template-columns: 280px minmax(0, 1fr);
            gap: 20px;
            align-items: start;
        }

        .project-settings-side,
        .project-settings-main {
            background: #fff;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
        }

        .project-settings-side {
            padding: 18px;
        }

        .project-settings-main {
            overflow: hidden;
        }

        .project-settings-hero {
            padding: 26px 28px;
            background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 55%, #f9fbfd 100%);
            border-bottom: 1px solid #e6eef7;
        }

        .project-settings-hero-label {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.08);
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.04em;
            margin-bottom: 14px;
        }

        .project-settings-hero-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 28px;
            font-weight: 700;
        }

        .project-settings-hero-desc {
            margin: 0;
            color: #617080;
            line-height: 1.8;
            max-width: 900px;
        }

        .project-settings-meta {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 14px;
            margin-top: 20px;
        }

        .project-settings-meta-card {
            background: rgba(255, 255, 255, 0.82);
            border: 1px solid #e4edf7;
            border-radius: 14px;
            padding: 14px 16px;
        }

        .project-settings-meta-label {
            color: #8a97a6;
            font-size: 12px;
            margin-bottom: 6px;
        }

        .project-settings-meta-value {
            color: #1f2937;
            font-size: 18px;
            font-weight: 700;
            line-height: 1.4;
            word-break: break-word;
        }

        .project-settings-content {
            padding: 28px;
        }

        .project-settings-section {
            border: 1px solid #e7edf5;
            border-radius: 16px;
            background: #fff;
            padding: 22px;
        }

        .project-settings-section + .project-settings-section {
            margin-top: 18px;
        }

        .project-settings-section-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-section-desc {
            margin: 0 0 20px;
            color: #6b7785;
            line-height: 1.75;
        }

        .endpoint-toolbar { display: flex; gap: 12px; flex-wrap: wrap; align-items: end; margin-bottom: 14px; }
        .endpoint-toolbar .field { min-width: 180px; }
        .endpoint-card { border-radius: 12px; padding: 14px; margin-bottom: 12px; border: 1px solid #e5e7eb; }
        .endpoint-covered { background: #eff6ff; border-color: #93c5fd; }
        .endpoint-uncovered { background: #f3f4f6; border-color: #d1d5db; }
        .endpoint-url { font-weight: 700; word-break: break-all; }
        .endpoint-meta { color: #6b7280; margin-top: 6px; word-break: break-all; }
        .endpoint-badge { margin-right: 8px !important; margin-bottom: 6px !important; }
        .endpoint-summary { display: flex; gap: 16px; flex-wrap: wrap; }
        .endpoint-summary .statistic { min-width: 120px; }
        .endpoint-view-switch { display: inline-flex; gap: 8px; align-items: center; margin-left: auto; }
        .endpoint-section-title { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
        .endpoint-group-card { border-radius: 12px; padding: 14px; margin-bottom: 12px; border: 1px solid #d1d5db; background: #f9fafb; }
        .endpoint-group-card.endpoint-group-uncovered { background: #fff7f7; border-color: #fca5a5; box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.08); }
        .endpoint-group-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
        .endpoint-group-children { margin-top: 12px; }
        .endpoint-uncovered-focus { margin-bottom: 12px; border: 1px solid #fecaca; background: #fff1f2; }
        .endpoint-group-toggle { cursor: pointer; color: #2563eb; font-weight: 600; }
        .endpoint-empty { padding: 36px 12px !important; }
        .endpoint-sublist { margin-top: 6px; color: #6b7280; }
        .endpoint-sublist ul { margin: 6px 0 0 18px; }
        .endpoint-toggle { cursor: pointer; color: #2563eb; margin-left: 8px; }
        .endpoint-quick-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
        .endpoint-filter-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
        .endpoint-copy-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
        .endpoint-pagination { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 16px; }
        .endpoint-pagination-info { color: #6b7280; }

        @media only screen and (max-width: 960px) {
            .project-settings-layout {
                grid-template-columns: 1fr;
            }
        }

        @media only screen and (max-width: 767px) {
            .project-settings-hero,
            .project-settings-side,
            .project-settings-content {
                padding: 22px 20px !important;
            }
        }
    </style>
</head>
<body>
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/version/apps">版本中心</a>
        <span class="divider">/</span>
        <a class="section" href="/p/${project.id}/${app.id}/version/list">${app.name}</a>
        <span class="divider">/</span>
        <div class="active section">接口扫描与覆盖</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign appId=app.id/>
            <#assign appName=app.name/>
            <#assign apiEndpointActive="active"/>
            <#include "../version/LeftNavigationMenu.ftl">
        </div>
        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="sitemap icon"></i>
                    接口扫描与覆盖
                </div>
                <h1 class="project-settings-hero-title">接口扫描与覆盖</h1>
                <p class="project-settings-hero-desc">
                    支持优先使用系统中已通过 Git 拉取并缓存的源码 .zip，也可手动上传源码 .zip 或制品包 .jar / .war。系统会扫描 HTTP、HTTP Client、Feign、RPC
                    接口。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">应用名称</div>
                        <div class="project-settings-meta-value">${app.name}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">项目编号</div>
                        <div class="project-settings-meta-value">${project.id}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">当前权限</div>
                        <div class="project-settings-meta-value"><#if loginNameRole == "visitor">访客<#else>${loginNameRole}</#if></div>
                    </div>
                </div>
            </div>

            <div class="project-settings-content">
                <div class="project-settings-section">
                    <h2 class="project-settings-section-title">扫描输入</h2>
                    <p class="project-settings-section-desc">蓝色表示已覆盖，灰色表示未覆盖。建议优先使用系统已缓存的源码包，以便重复扫描和结果复用。</p>
                    <div class="ui info message">
                        支持优先使用系统中已通过 Git 拉取并缓存的源码 <b>.zip</b>，也可手动上传源码 <b>.zip</b> 或制品包 <b>.jar / .war</b>。系统会扫描
                        HTTP、HTTP Client、Feign、RPC 接口；蓝色表示已覆盖，灰色表示未覆盖。
                    </div>
                    <form class="ui form" id="uploadForm" enctype="multipart/form-data">
                        <div class="field">
                            <label>优先选择已拉取的源码包</label>
                            <select id="cachedZipSelect" class="ui dropdown">
                                <option value="">系统中暂无可用源码包，请先从 Git 拉取或手动上传</option>
                            </select>
                            <div style="margin-top:8px;color:#6b7280;">如果系统中已存在 Git 拉取后的源码 zip，将优先使用这里的文件进行扫描。</div>
                        </div>
                        <div class="field">
                            <label>手动上传源码包或制品包（兜底）</label>
                            <input type="file" name="file" id="artifactFile" accept=".zip,.jar,.war">
                        </div>
                        <button id="apiEndpointUploadButton" type="button" class="ui primary button" onclick="uploadArtifact()"><i class="upload icon"></i> 开始扫描</button>
                        <button type="button" class="ui button" onclick="loadCachedZips();loadEndpoints()"><i class="refresh icon"></i> 刷新列表</button>
                    </form>
                </div>

                <div class="project-settings-section">
                    <div class="endpoint-summary">
                        <div class="ui blue statistic">
                            <div class="value" id="coveredCount">0</div>
                            <div class="label">已覆盖</div>
                        </div>
                        <div class="ui grey statistic">
                            <div class="value" id="uncoveredCount">0</div>
                            <div class="label">未覆盖</div>
                        </div>
                        <div class="ui statistic">
                            <div class="value" id="totalCount">0</div>
                            <div class="label">总接口数</div>
                        </div>
                        <div class="ui statistic">
                            <div class="value" id="filteredCount">0</div>
                            <div class="label">筛选结果</div>
                        </div>
                    </div>
                </div>

                <div class="project-settings-section">
                    <div class="endpoint-section-title">
                        <div class="ui form endpoint-toolbar">
                            <div class="field">
                                <label>关键字</label>
                                <input type="text" id="keyword" placeholder="搜索 URL / 类名 / 方法名 / 来源" oninput="applyFilters()">
                            </div>
                            <div class="field">
                                <label>接口类型</label>
                                <select id="endpointTypeFilter" class="ui dropdown" onchange="applyFilters()">
                                    <option value="">全部类型</option>
                                </select>
                            </div>
                            <div class="field">
                                <label>覆盖状态</label>
                                <select id="coverageFilter" class="ui dropdown" onchange="applyFilters()">
                                    <option value="">全部</option>
                                    <option value="covered">已覆盖</option>
                                    <option value="uncovered">未覆盖</option>
                                </select>
                            </div>
                            <div class="field">
                                <label>命中次数</label>
                                <select id="hitCountFilter" class="ui dropdown" onchange="applyFilters()">
                                    <option value="">全部命中</option>
                                    <option value="0">仅 0 次</option>
                                    <option value="1-5">1 - 5 次</option>
                                    <option value="6-20">6 - 20 次</option>
                                    <option value="21+">21 次以上</option>
                                </select>
                            </div>
                        </div>
                        <div class="endpoint-view-switch">
                            <span style="color:#6b7280;">视图</span>
                            <div class="ui tiny buttons">
                                <button type="button" class="ui button active" id="detailViewBtn" onclick="switchViewMode('detail')">按明细</button>
                                <button type="button" class="ui button" id="groupViewBtn" onclick="switchViewMode('group')">按 URL 合并</button>
                            </div>
                        </div>
                    </div>
                    <div class="endpoint-quick-actions">
                        <button type="button" class="ui tiny red basic button" id="uncoveredQuickBtn" onclick="toggleUncoveredOnly()">只看未覆盖</button>
                        <button type="button" class="ui tiny button" onclick="resetFilters()">重置筛选</button>
                        <button type="button" class="ui tiny primary basic button" onclick="exportCurrentViewCsv()">导出当前结果 CSV</button>
                    </div>
                    <div class="endpoint-filter-chips" id="activeFilterChips"></div>
                </div>

                <div class="project-settings-section">
                    <div id="endpointList" class="ui divided items">
                        <div class="ui placeholder segment endpoint-empty">
                            <div class="ui icon header"><i class="search icon"></i> 暂无接口数据，请先上传扫描文件。</div>
                        </div>
                    </div>
                    <div id="endpointPagination" class="endpoint-pagination" style="display:none;"></div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    var allEndpoints = [];
    var currentViewMode = 'detail';
    var collapsedGroupKeys = {};
    var endpointStorageKey = 'api-endpoints-view-state-${project.id}-${app.id}';
    var lastFilteredEndpoints = [];
    var currentPage = 1;
    var pageSize = 10;

    function uploadArtifact() {
        var form = document.getElementById('uploadForm');
        var $form = $(form);
        if (oatIsFormSubmitting($form)) {
            return;
        }
        var data = new FormData(form);
        var cachePath = $('#cachedZipSelect').val() || '';
        var fileInput = document.getElementById('artifactFile');
        var selectedFile = fileInput && fileInput.files && fileInput.files.length ? fileInput.files[0] : null;

        if (cachePath) {
            data.delete('file');
            data.append('cachePath', cachePath);
        } else if (!selectedFile) {
            showToast('请先选择系统已拉取的源码包，或手动上传 zip/jar/war 文件', 'error');
            return;
        }

        oatSetFormSubmitting($form, true, {submitButton: '#apiEndpointUploadButton', message: '正在上传并扫描接口...'});
        $.ajax({
            url: '/p/${project.id}/app/${app.id}/api-endpoints/upload',
            type: 'POST',
            data: data,
            processData: false,
            contentType: false,
            success: function (res) {
                oatSetFormSubmitting($form, false, {submitButton: '#apiEndpointUploadButton'});
                if (res.result || res.success) {
                    showToast(res.message || '扫描成功', 'success');
                    form.reset();
                    $('#cachedZipSelect').dropdown('clear');
                    loadCachedZips();
                    loadEndpoints();
                } else {
                    showToast(res.message || '扫描失败', 'error');
                }
            },
            error: function (xhr) {
                oatSetFormSubmitting($form, false, {submitButton: '#apiEndpointUploadButton'});
                showToast((xhr.responseJSON && xhr.responseJSON.message) || '上传失败', 'error');
            }
        });
    }

    function loadCachedZips() {
        $.get('/p/${project.id}/app/${app.id}/api-endpoints/pulled-zips', function (list) {
            var items = list || [];
            var options = ['<option value="">' + (items.length ? '请选择系统已拉取的源码包' : '系统中暂无可用源码包，请先从 Git 拉取或手动上传') + '</option>'];
            items.forEach(function (item) {
                var text = item.fileName + '（' + formatFileSize(item.size) + '，' + formatDateTime(item.lastModified) + '）';
                options.push('<option value="' + escapeHtml(item.cachePath) + '">' + escapeHtml(text) + '</option>');
            });
            $('#cachedZipSelect').html(options.join(''));
            $('#cachedZipSelect').dropdown();
        });
    }

    function loadEndpoints() {
        $.get('/p/${project.id}/app/${app.id}/api-endpoints/list', function (list) {
            allEndpoints = list || [];
            refreshFilterOptions(allEndpoints);
            restoreViewState();
            $('.ui.dropdown').dropdown('refresh');
            $('#detailViewBtn').toggleClass('active', currentViewMode === 'detail');
            $('#groupViewBtn').toggleClass('active', currentViewMode === 'group');
            syncQuickButtons();
            applyFilters();
        });
    }

    function refreshFilterOptions(list) {
        fillSelect('endpointTypeFilter', uniqueValues(list.map(function (item) { return item.endpointType; })), '全部类型');
        fillSelect('httpMethodFilter', uniqueValues(list.map(function (item) { return item.httpMethod; })), '全部方法');
        $('.ui.dropdown').dropdown();
    }

    function fillSelect(selectId, values, defaultLabel) {
        var current = $('#' + selectId).val() || '';
        var options = ['<option value="">' + defaultLabel + '</option>'];
        values.forEach(function (value) {
            options.push('<option value="' + escapeHtml(value) + '">' + escapeHtml(value) + '</option>');
        });
        $('#' + selectId).html(options.join(''));
        $('#' + selectId).val(current);
    }

    function uniqueValues(values) {
        var map = {};
        values.forEach(function (value) {
            if (value) {
                map[value] = true;
            }
        });
        return Object.keys(map).sort();
    }

    function formatFileSize(size) {
        if (size === null || size === undefined) {
            return '-';
        }
        if (size < 1024) {
            return size + ' B';
        }
        if (size < 1024 * 1024) {
            return (size / 1024).toFixed(1).replace(/\.0$/, '') + ' KB';
        }
        if (size < 1024 * 1024 * 1024) {
            return (size / (1024 * 1024)).toFixed(1).replace(/\.0$/, '') + ' MB';
        }
        return (size / (1024 * 1024 * 1024)).toFixed(1).replace(/\.0$/, '') + ' GB';
    }

    function formatDateTime(timestamp) {
        if (!timestamp) {
            return '-';
        }
        var date = new Date(timestamp);
        if (isNaN(date.getTime())) {
            return '-';
        }
        var pad = function (value) { return value < 10 ? '0' + value : '' + value; };
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    }

    function applyFilters() {
        currentPage = 1;
        applyFiltersForCurrentPage();
    }

    function applyFiltersForCurrentPage() {
        var keyword = ($('#keyword').val() || '').toLowerCase().trim();
        var endpointType = $('#endpointTypeFilter').val() || '';
        var coverage = $('#coverageFilter').val() || '';
        var httpMethod = $('#httpMethodFilter').val() || '';
        var hitCountRange = $('#hitCountFilter').val() || '';

        var filtered = allEndpoints.filter(function (item) {
            if (endpointType && item.endpointType !== endpointType) {
                return false;
            }
            if (httpMethod && item.httpMethod !== httpMethod) {
                return false;
            }
            if (coverage === 'covered' && !item.covered) {
                return false;
            }
            if (coverage === 'uncovered' && item.covered) {
                return false;
            }
            if (!matchesHitCountRange(item.hitCount || 0, hitCountRange)) {
                return false;
            }
            if (!keyword) {
                return true;
            }
            var content = [item.url, item.className, item.methodName, item.methodDesc, item.sourceName, item.sourceType, item.endpointType, item.httpMethod]
                .join(' ')
                .toLowerCase();
            return content.indexOf(keyword) >= 0;
        });

        var sorted = sortEndpoints(filtered);
        lastFilteredEndpoints = sorted;
        persistViewState();
        syncQuickButtons();
        renderActiveFilterChips();
        renderSummary(allEndpoints, sorted);
        renderEndpoints(sorted);
    }

    function matchesHitCountRange(hitCount, range) {
        if (!range) {
            return true;
        }
        if (range === '0') {
            return hitCount === 0;
        }
        if (range === '1-5') {
            return hitCount >= 1 && hitCount <= 5;
        }
        if (range === '6-20') {
            return hitCount >= 6 && hitCount <= 20;
        }
        if (range === '21+') {
            return hitCount >= 21;
        }
        return true;
    }

    function sortEndpoints(list) {
        return list.slice().sort(function (a, b) {
            if (!!a.covered !== !!b.covered) {
                return a.covered ? 1 : -1;
            }
            var hitDiff = (b.hitCount || 0) - (a.hitCount || 0);
            if (hitDiff !== 0) {
                return hitDiff;
            }
            var urlA = (a.url || '').toLowerCase();
            var urlB = (b.url || '').toLowerCase();
            if (urlA !== urlB) {
                return urlA.localeCompare(urlB);
            }
            var methodA = (a.httpMethod || '').toLowerCase();
            var methodB = (b.httpMethod || '').toLowerCase();
            return methodA.localeCompare(methodB);
        });
    }

    function switchViewMode(mode) {
        currentViewMode = mode === 'group' ? 'group' : 'detail';
        $('#detailViewBtn').toggleClass('active', currentViewMode === 'detail');
        $('#groupViewBtn').toggleClass('active', currentViewMode === 'group');
        persistViewState();
        applyFilters();
    }

    function toggleUncoveredOnly() {
        var nextValue = ($('#coverageFilter').val() || '') === 'uncovered' ? '' : 'uncovered';
        $('#coverageFilter').val(nextValue);
        $('.ui.dropdown').dropdown('refresh');
        applyFilters();
    }

    function resetFilters() {
        $('#keyword').val('');
        $('#endpointTypeFilter').val('');
        $('#coverageFilter').val('');
        $('#httpMethodFilter').val('');
        $('#hitCountFilter').val('');
        $('.ui.dropdown').dropdown('refresh');
        applyFilters();
    }

    function syncQuickButtons() {
        var uncoveredOnly = ($('#coverageFilter').val() || '') === 'uncovered';
        $('#uncoveredQuickBtn').toggleClass('red', uncoveredOnly).toggleClass('basic', !uncoveredOnly);
    }

    function renderActiveFilterChips() {
        var chips = [];
        var keyword = $('#keyword').val() || '';
        var endpointType = $('#endpointTypeFilter').val() || '';
        var coverage = $('#coverageFilter').val() || '';
        var hitCountRange = $('#hitCountFilter').val() || '';
        var viewLabel = currentViewMode === 'group' ? '按 URL 合并' : '按明细';
        if (keyword) {
            chips.push(renderFilterChip('关键字', keyword, "$('#keyword').val('');applyFilters();"));
        }
        if (endpointType) {
            chips.push(renderFilterChip('接口类型', endpointType, "$('#endpointTypeFilter').val('');$('.ui.dropdown').dropdown('refresh');applyFilters();"));
        }
        if (coverage) {
            chips.push(renderFilterChip('覆盖状态', coverage === 'uncovered' ? '未覆盖' : '已覆盖', "$('#coverageFilter').val('');$('.ui.dropdown').dropdown('refresh');applyFilters();"));
        }
        if (hitCountRange) {
            chips.push(renderFilterChip('命中次数', hitCountRange, "$('#hitCountFilter').val('');$('.ui.dropdown').dropdown('refresh');applyFilters();"));
        }
        chips.push('<span class="ui tiny basic label">视图：' + escapeHtml(viewLabel) + '</span>');
        $('#activeFilterChips').html(chips.join(''));
    }

    function renderFilterChip(label, value, onRemoveJs) {
        return '<span class="ui tiny label">' + escapeHtml(label) + '：' + escapeHtml(value) +
            '<i class="delete icon" style="margin-left:6px;cursor:pointer;" onclick="' + onRemoveJs + '"></i></span>';
    }

    function getViewState() {
        return {
            keyword: $('#keyword').val() || '',
            endpointType: $('#endpointTypeFilter').val() || '',
            coverage: $('#coverageFilter').val() || '',
            httpMethod: $('#httpMethodFilter').val() || '',
            hitCountRange: $('#hitCountFilter').val() || '',
            currentViewMode: currentViewMode,
            collapsedGroupKeys: collapsedGroupKeys
        };
    }

    function persistViewState() {
        try {
            localStorage.setItem(endpointStorageKey, JSON.stringify(getViewState()));
        } catch (e) {
        }
    }

    function restoreViewState() {
        try {
            var raw = localStorage.getItem(endpointStorageKey);
            if (!raw) {
                return;
            }
            var state = JSON.parse(raw);
            $('#keyword').val(state.keyword || '');
            $('#endpointTypeFilter').val(state.endpointType || '');
            $('#coverageFilter').val(state.coverage || '');
            $('#httpMethodFilter').val(state.httpMethod || '');
            $('#hitCountFilter').val(state.hitCountRange || '');
            currentViewMode = state.currentViewMode === 'group' ? 'group' : 'detail';
            collapsedGroupKeys = state.collapsedGroupKeys || {};
        } catch (e) {
        }
    }

    function renderSummary(allList, filteredList) {
        var covered = allList.filter(function (e) { return e.covered; }).length;
        var uncovered = allList.length - covered;
        $('#coveredCount').text(covered);
        $('#uncoveredCount').text(uncovered);
        $('#totalCount').text(allList.length);
        $('#filteredCount').text(filteredList.length);
    }

    function renderEndpoints(list) {
        if (!allEndpoints.length) {
            $('#endpointPagination').hide().empty();
            $('#endpointList').html('<div class="ui placeholder segment endpoint-empty"><div class="ui icon header"><i class="search icon"></i> 暂无接口数据，请先上传扫描文件。</div></div>');
            return;
        }
        if (!list.length) {
            $('#endpointPagination').hide().empty();
            $('#endpointList').html('<div class="ui placeholder segment endpoint-empty"><div class="ui icon header"><i class="filter icon"></i> 当前筛选条件下没有匹配的接口。</div></div>');
            return;
        }
        var focusBanner = renderUncoveredFocus(list);
        if (currentViewMode === 'group') {
            renderGroupedEndpoints(list, focusBanner);
            return;
        }
        var pageData = getPageData(list);
        var html = focusBanner + pageData.items.map(function (item) {
            return renderEndpointCard(item);
        }).join('');
        $('#endpointList').html(html);
        renderPagination(list.length, pageData.totalPages);
    }

    function renderUncoveredFocus(list) {
        var uncovered = list.filter(function (item) { return !item.covered; });
        if (!uncovered.length) {
            return '';
        }
        return '<div class="ui message endpoint-uncovered-focus">' +
            '<div class="header">未覆盖接口优先关注</div>' +
            '<p>当前筛选结果中共有 <b>' + escapeHtml(String(uncovered.length)) + '</b> 条未覆盖接口，列表已优先置顶显示。</p>' +
        '</div>';
    }

    function exportCurrentViewCsv() {
        if (!lastFilteredEndpoints.length) {
            showToast('当前没有可导出的结果', 'error');
            return;
        }
        var rows = [
            ['endpointType', 'httpMethod', 'url', 'covered', 'hitCount', 'mergedSourceCount', 'className', 'methodName', 'methodDesc', 'sourceType', 'sourceName']
        ];
        lastFilteredEndpoints.forEach(function (item) {
            rows.push([
                item.endpointType || '',
                item.httpMethod || '',
                item.url || '',
                item.covered ? 'covered' : 'uncovered',
                String(item.hitCount || 0),
                String(item.mergedSourceCount || 0),
                (item.classNameList || [item.className || '']).join(' | '),
                (item.methodNameList || [item.methodName || '']).join(' | '),
                (item.methodDescList || [item.methodDesc || '']).join(' | '),
                (item.sourceTypeList || [item.sourceType || '']).join(' | '),
                (item.sourceNameList || [item.sourceName || '']).join(' | ')
            ]);
        });
        downloadCsv('api-endpoints-' + Date.now() + '.csv', rows);
    }

    function downloadCsv(filename, rows) {
        var csv = rows.map(function (row) {
            return row.map(function (value) {
                var text = String(value == null ? '' : value).replace(/"/g, '""');
                return '"' + text + '"';
            }).join(',');
        }).join('\n');
        var blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
        var link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(link.href);
    }

    function renderGroupedEndpoints(list, focusBanner) {
        var groups = groupEndpointsByInterface(list);
        var pageData = getPageData(groups);
        var html = focusBanner + pageData.items.map(function (group) {
            var firstItem = group.items[0];
            var uncoveredCount = group.items.filter(function (item) { return !item.covered; }).length;
            var coveredCount = group.items.length - uncoveredCount;
            var color = uncoveredCount > 0 ? 'red' : 'blue';
            var collapsed = collapsedGroupKeys[group.key] !== false;
            var childrenHtml = collapsed ? '' : group.items.map(function (item) {
                return renderEndpointCard(item, true);
            }).join('');
            return '<div class="endpoint-group-card ' + (uncoveredCount > 0 ? 'endpoint-group-uncovered' : '') + '">' +
                '<div class="endpoint-group-header">' +
                    '<div>' +
                        '<div class="endpoint-url">' + escapeHtml(group.url || '-') + '</div>' +
                        '<div class="endpoint-meta">接口键：' + escapeHtml(buildEndpointKey(firstItem)) + '</div>' +
                    '</div>' +
                    '<div>' +
                        '<span class="ui ' + color + ' label endpoint-badge">共 ' + escapeHtml(String(group.items.length)) + ' 条</span>' +
                        '<span class="ui basic label endpoint-badge">未覆盖 ' + escapeHtml(String(uncoveredCount)) + '</span>' +
                        '<span class="ui basic label endpoint-badge">已覆盖 ' + escapeHtml(String(coveredCount)) + '</span>' +
                        '<span class="endpoint-group-toggle" onclick="toggleGroup(\'' + escapeJs(group.key) + '\')">' + (collapsed ? '展开明细' : '收起明细') + '</span>' +
                    '</div>' +
                '</div>' +
                '<div class="endpoint-group-children" style="display:' + (collapsed ? 'none' : 'block') + ';">' + childrenHtml + '</div>' +
            '</div>';
        }).join('');
        $('#endpointList').html(html);
        renderPagination(groups.length, pageData.totalPages);
    }

    function toggleGroup(groupKey) {
        collapsedGroupKeys[groupKey] = collapsedGroupKeys[groupKey] === false ? true : false;
        applyFiltersForCurrentPage();
    }

    function getPageData(list) {
        var totalPages = Math.max(1, Math.ceil(list.length / pageSize));
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }
        var start = (currentPage - 1) * pageSize;
        return {
            items: list.slice(start, start + pageSize),
            totalPages: totalPages
        };
    }

    function renderPagination(total, totalPages) {
        if (total <= pageSize) {
            $('#endpointPagination').hide().empty();
            return;
        }
        var start = (currentPage - 1) * pageSize + 1;
        var end = Math.min(currentPage * pageSize, total);
        var buttons = [];
        buttons.push('<button type="button" class="ui tiny button ' + (currentPage === 1 ? 'disabled' : '') + '" onclick="goToPage(' + (currentPage - 1) + ')">上一页</button>');
        for (var page = 1; page <= totalPages; page++) {
            if (page === 1 || page === totalPages || Math.abs(page - currentPage) <= 2) {
                buttons.push('<button type="button" class="ui tiny button ' + (page === currentPage ? 'primary' : '') + '" onclick="goToPage(' + page + ')">' + page + '</button>');
            } else if (page === currentPage - 3 || page === currentPage + 3) {
                buttons.push('<button type="button" class="ui tiny disabled button">...</button>');
            }
        }
        buttons.push('<button type="button" class="ui tiny button ' + (currentPage === totalPages ? 'disabled' : '') + '" onclick="goToPage(' + (currentPage + 1) + ')">下一页</button>');
        $('#endpointPagination').html(
            '<div class="endpoint-pagination-info">第 ' + currentPage + ' / ' + totalPages + ' 页，显示 ' + start + '-' + end + '，共 ' + total + ' 条</div>' +
            '<div class="ui tiny buttons">' + buttons.join('') + '</div>'
        ).show();
    }

    function goToPage(page) {
        currentPage = page;
        renderEndpoints(lastFilteredEndpoints);
        $('html, body').animate({ scrollTop: $('#endpointList').offset().top - 120 }, 150);
    }

    function renderEndpointCard(item, compact) {
        var cardClass = item.covered ? 'endpoint-covered' : 'endpoint-uncovered';
        var color = item.covered ? 'blue' : 'grey';
        var extraStyle = compact ? 'margin-bottom:8px;' : '';
        return '<div class="endpoint-card ' + cardClass + '" style="' + extraStyle + '">' +
            '<div class="endpoint-url" style="color:' + (item.covered ? '#2563eb' : '#6b7280') + '">' + escapeHtml(item.url || '-') + '</div>' +
            '<div class="endpoint-meta">接口键：' + escapeHtml(buildEndpointKey(item)) + '</div>' +
            '<div>' +
                '<span class="ui ' + color + ' label endpoint-badge">' + escapeHtml(item.endpointType || '-') + '</span>' +
                '<span class="ui basic label endpoint-badge">' + escapeHtml(item.httpMethod || '-') + '</span>' +
                '<span class="ui ' + color + ' basic label endpoint-badge">' + (item.covered ? '已覆盖' : '未覆盖') + '</span>' +
                '<span class="ui basic label endpoint-badge">命中 ' + escapeHtml(String(item.hitCount || 0)) + '</span>' +
                '<span class="ui basic label endpoint-badge">来源 ' + escapeHtml(String(item.mergedSourceCount || 0)) + '</span>' +
            '</div>' +
            '<div class="endpoint-copy-actions">' +
                '<button type="button" class="ui mini basic button" onclick="copyText(' + quoteJs(buildEndpointKey(item)) + ', \'接口键已复制\')">复制接口键</button>' +
                '<button type="button" class="ui mini basic button" onclick="copyText(' + quoteJs(item.url || '') + ', \'URL 已复制\')">复制 URL</button>' +
                '<button type="button" class="ui mini basic button" onclick="copyText(' + quoteJs(item.methodDesc || '') + ', \'方法签名已复制\')">复制方法签名</button>' +
            '</div>' +
            renderGroupBlock('类', item.classNameList, item.className) +
            renderGroupBlock('方法', item.methodNameList, item.methodName) +
            renderGroupBlock('签名', item.methodDescList, item.methodDesc) +
            renderGroupBlock('来源类型', item.sourceTypeList, item.sourceType) +
            renderGroupBlock('来源文件', item.sourceNameList, item.sourceName) +
        '</div>';
    }

    function groupEndpointsByInterface(list) {
        var groups = {};
        list.forEach(function (item) {
            var key = buildEndpointKey(item);
            if (!groups[key]) {
                groups[key] = {
                    key: key,
                    url: item.url,
                    items: []
                };
            }
            groups[key].items.push(item);
        });
        return Object.keys(groups).map(function (key) { return groups[key]; }).sort(function (a, b) {
            var aUncovered = a.items.filter(function (item) { return !item.covered; }).length;
            var bUncovered = b.items.filter(function (item) { return !item.covered; }).length;
            if (aUncovered !== bUncovered) {
                return bUncovered - aUncovered;
            }
            return (a.url || '').localeCompare(b.url || '');
        });
    }
    function buildEndpointKey(item) {
        if (Array.isArray(item.endpointKeyParts) && item.endpointKeyParts.length) {
            return item.endpointKeyParts.join(' | ');
        }
        return [item.endpointType || '', item.httpMethod || '', item.url || ''].join(' | ');
    }

    function renderMultiline(text) {
        return escapeHtml(text).replace(/\n/g, '<br>');
    }

    function renderGroupBlock(label, list, fallback) {
        var values = Array.isArray(list) && list.length ? list : [fallback || '-'];
        var first = values[0] || '-';
        if (values.length <= 1) {
            return '<div class="endpoint-meta">' + escapeHtml(label) + '：' + renderMultiline(first) + '</div>';
        }
        var items = values.map(function (value) {
            return '<li>' + renderMultiline(value) + '</li>';
        }).join('');
        return '<div class="endpoint-meta">' + escapeHtml(label) + '：' + renderMultiline(first) +
            '<span class="endpoint-toggle" onclick="toggleSublist(this)">展开 ' + escapeHtml(String(values.length)) + ' 项</span>' +
            '<div class="endpoint-sublist" style="display:none;"><ul>' + items + '</ul></div></div>';
    }

    function toggleSublist(el) {
        var box = $(el).siblings('.endpoint-sublist');
        var visible = box.is(':visible');
        box.toggle(!visible);
        $(el).text((visible ? '展开 ' : '收起 ') + $(el).text().replace(/^(展开|收起)\s*/, ''));
    }

    function escapeHtml(text) {
        return $('<div/>').text(text == null ? '' : text).html();
    }

    function escapeJs(text) {
        return String(text == null ? '' : text)
            .replace(/\\/g, '\\\\')
            .replace(/'/g, "\\'");
    }

    function quoteJs(text) {
        return '\'' + escapeJs(text) + '\'';
    }

    function copyText(text, successMessage) {
        var value = text == null ? '' : String(text);
        if (!value) {
            showToast('没有可复制的内容', 'error');
            return;
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(value).then(function () {
                showToast(successMessage || '已复制', 'success');
            }).catch(function () {
                fallbackCopyText(value, successMessage);
            });
            return;
        }
        fallbackCopyText(value, successMessage);
    }

    function fallbackCopyText(value, successMessage) {
        var input = document.createElement('textarea');
        input.value = value;
        document.body.appendChild(input);
        input.select();
        document.execCommand('copy');
        document.body.removeChild(input);
        showToast(successMessage || '已复制', 'success');
    }

    $(function () {
        $('.ui.dropdown').dropdown();
        loadCachedZips();
        loadEndpoints();
    });
</script>
</body>
</html>

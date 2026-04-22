var selectTraceId = null;
var currentMonitorTitle = '';
var currentAutoSaveRequestToken = 0;
var currentProjectId = null;
var autoSavedTraceCache = {
    my: {},
    system: {}
};

/*
 * 打开监控详情
 * */
function openMonitorDetail(projectid, traceId) {
    currentProjectId = projectid;
    // 初始化界面
    $("#emptyTip").hide();
    $("#monitorDetail").show();
    $("#svg-canvas").children().remove();
    // 初始化画布大小
    $("#svg-canvas").attr('width', $("#monitorDetail").width());
    // 清除节点详情信息
    $("#nodeDetail").children("div.entry").remove();
    // 装载监控数据
    var monitorData = $.ajax({
        url: "/p/" + projectid + "/monitor/getTraceGraph?traceId=" + traceId, async: false
    }).responseJSON;

    // 设置标题
    currentMonitorTitle = monitorData.title || '';
    $("#monitorDetailTitle").text(currentMonitorTitle);

    // 构建流程图
    buildFlow(projectid, traceId, monitorData);

    // 打开默认节点详情
    openNodeDetails(projectid, traceId, monitorData.showDefaultNode.id);
    selectTraceId = traceId;
    $(".button.save.snapshot").removeClass("disabled");
    updateMonitorActionAvailability();
}

function buildFlow(projectId, traceId, data) {
    var g = buildTopo("svg-canvas", data, {
        nodeClick: function (id, index, array) {
            var node = g.node(id);
            openNodeDetails(projectId, traceId, id);
        }
    });
}

/**
 * 打开监控详情中的某个节点
 * @param nodeId
 * @param nodeType
 */
function openNodeDetails(projectid, traceId, nodeId) {
    $("#nodeDetail").children("div.entry").remove();
    var nodeDiv = $('<div></div>');
    nodeDiv.attr("nodeId", nodeId);
    nodeDiv.attr("class", "entry");
    var htmlobj = $.ajax({url: "/p/" + projectid + "/monitor/" + traceId + "/" + nodeId + ".html", async: false});
    nodeDiv.html(htmlobj.responseText);
    $("#nodeDetail").append(nodeDiv);

}

var lastIndex = 0
var lastUpdateTime = 0;

//刷新监控列表
function refreshMonitorList(projectid) {
    $("#monitorListBody").children().remove();
    //var newItems = $("#itemFilter").ajaxSubmit({ async: false}).responseJSON;
    // var form = new FormData(document.getElementById("itemFilter"));
    // var dataForm = $("#itemFilter").serialize();
    var newItems = $.ajax({
        url: "/p/" + projectid + "/monitor/getNodeByTime", data: $("#itemFilter").serialize(), async: false
    }).responseJSON;

    if (newItems === undefined || newItems.length == 0) {
        resetMonitorSelectionState();
        $("#emptyTip").show();
        $("#monitorDetail").hide();
    } else {
        appendItems(projectid, newItems);
    }
    lastUpdateTime = new Date().getTime();
}

// 拉取新的监控数据
function pullNewItem(projectid) {
    // 如果最后更新时间超过两分钟，拉取则换成更新
    if (lastIndex == 0 || (new Date().getTime() - lastUpdateTime) > (2 * 60 * 1000)) {
        refreshMonitorList(projectid);
        return;
    }

    var newItems = $.ajax({
        url: "/p/" + projectid + "/monitor/getNodeByIndex?lastIndex=" + lastIndex + "&maxSize=200", async: false
    }).responseJSON;

    if (newItems !== undefined && newItems.length > 0) {
        appendItems(projectid, newItems);
    } else {
        // 如果返回为空，且不是第一次拉取，说明可能数据已过期或被清理，尝试全量刷新
        refreshMonitorList(projectid);
    }
    lastUpdateTime = new Date().getTime();
}

function appendItems(projectid, newItems) {
    currentProjectId = projectid;
    if (newItems === undefined || newItems.length == 0) {
        return;
    }
    newItems.some(function (value, index, array) {
        var cacheDate = new Date(value.cacheTime);
        var timeText = cacheDate.toLocaleDateString() + " " + cacheDate.toLocaleTimeString();
        var itemText = ""
            + "<tr data-trace-id='" + value.traceId + "' onclick=\"doSelect(this); openMonitorDetail('" + projectid + "','" + value.traceId + "')\">"
            + "  <td class='monitor-name-cell' title='" + value.title + "'>"
            + "    <div class='monitor-primary'>"
            + "      <i class='feed icon'></i>"
            + "      <span class='monitor-primary-text'>" + value.title + "</span>"
            + "    </div>"
            + "    <div class='ui mini grey text' style='margin-top: 4px; white-space: nowrap;'>" + timeText + "</div>"
            + "  </td>"
            + "</tr>";
        $("#monitorListBody").prepend(itemText);
        tryAutoSaveSnapshots(projectid, value.traceId, value.title || '');
    });
    lastIndex = newItems[newItems.length - 1].index;
}

function clearItem() {
    $("#monitorListBody").children().remove();
}

// 添加样式选中效果
function doSelect(t) {
    $("#monitorListBody tr.focus").toggleClass("focus");//取消原先选中行
    $(t).toggleClass("focus");//设定当前行为选中行
}

function updateMonitorActionAvailability() {
    var hasSelection = !!selectTraceId;
    var selectionHint = hasSelection ? '已选中当前链路，可手动保存；自动保存会在新监控数据进入列表时触发' : '自动保存会在新监控数据进入列表时触发；手动保存请先选择一条记录';

    $('#saveSnapshotDropdown').toggleClass('disabled', !hasSelection);
    $('#saveSnapshotDropdownButton').toggleClass('disabled', !hasSelection);
    $('#monitorActionHint').text(selectionHint);
}

function resetMonitorSelectionState() {
    selectTraceId = null;
    currentMonitorTitle = '';
    updateMonitorActionAvailability();
}

function buildEncodedFormData($form, extraFields) {
    var formArray = $form.serializeArray();
    $.each(extraFields || {}, function (name, value) {
        formArray.push({
            name: name,
            value: value == null ? '' : value
        });
    });
    return $.param(formArray, true);
}

function getMonitorAutoSaveStorageKey(projectId, type) {
    return 'monitorAutoSave:' + projectId + ':' + type;
}

function isMonitorAutoSaveEnabled(projectId, type) {
    return localStorage.getItem(getMonitorAutoSaveStorageKey(projectId, type)) === 'true';
}

function setMonitorAutoSaveEnabled(projectId, type, enabled) {
    localStorage.setItem(getMonitorAutoSaveStorageKey(projectId, type), enabled ? 'true' : 'false');
}

function syncMonitorAutoSaveSwitches(projectId) {
    $('#autoSaveMySnapshotToggle').prop('checked', isMonitorAutoSaveEnabled(projectId, 'my'));
    $('#autoSaveSystemSnapshotToggle').prop('checked', isMonitorAutoSaveEnabled(projectId, 'system'));
    updateMonitorActionAvailability();
}

function buildAutoSnapshotName(prefix, title) {
    var normalizedTitle = $.trim(title || '未命名链路');
    if (normalizedTitle.length > 24) {
        normalizedTitle = normalizedTitle.substring(0, 24);
    }
    var now = new Date();
    var pad = function (value) {
        return value < 10 ? '0' + value : '' + value;
    };
    var timestamp = now.getFullYear()
        + pad(now.getMonth() + 1)
        + pad(now.getDate())
        + pad(now.getHours())
        + pad(now.getMinutes())
        + pad(now.getSeconds());
    return prefix + '-' + normalizedTitle + '-' + timestamp;
}

function extractRequestErrorMessage(xhr, defaultMessage) {
    var errorMessage = defaultMessage || '网络请求失败';
    if (xhr && xhr.responseJSON) {
        errorMessage = xhr.responseJSON.errorMessage || xhr.responseJSON.message || errorMessage;
    }
    return errorMessage;
}

function doAutoSaveMySnapshot(projectid, traceId, title) {
    return $.ajax({
        url: '/p/' + projectid + '/snapshot/save',
        type: 'POST',
        dataType: 'json',
        data: {
            traceId: traceId,
            autoSave: true,
            name: buildAutoSnapshotName('自动快照', title),
            describe: '实时监控自动保存',
            labels: ['自动保存', '实时监控']
        }
    });
}

function doAutoSaveSystemSnapshot(projectid, traceId, title) {
    return $.ajax({
        url: '/p/' + projectid + '/monitor/autoSaveSystemSnapshot',
        type: 'POST',
        dataType: 'json',
        data: {
            traceId: traceId,
            title: buildAutoSnapshotName('自动系统快照', title)
        }
    });
}

function tryAutoSaveSnapshots(projectid, traceId, title) {
    if (!traceId) {
        return;
    }

    var myEnabled = isMonitorAutoSaveEnabled(projectid, 'my') && !autoSavedTraceCache.my[traceId];
    var systemEnabled = isMonitorAutoSaveEnabled(projectid, 'system') && !autoSavedTraceCache.system[traceId];
    if (!myEnabled && !systemEnabled) {
        return;
    }

    var token = ++currentAutoSaveRequestToken;
    var tasks = [];
    var summary = {
        success: [],
        skipped: [],
        failed: []
    };

    function pushSummaryResult(bucket, message) {
        if (message) {
            summary[bucket].push(message);
        }
    }

    function finalizeSummary() {
        if (token !== currentAutoSaveRequestToken) {
            return;
        }
        var messages = [];
        if (summary.success.length > 0) {
            messages.push(summary.success.join('；'));
        }
        if (summary.skipped.length > 0) {
            messages.push(summary.skipped.join('；'));
        }
        if (summary.failed.length > 0) {
            messages.push(summary.failed.join('；'));
        }
        if (messages.length === 0) {
            return;
        }
        var toastType = summary.failed.length > 0 ? (summary.success.length > 0 || summary.skipped.length > 0 ? 'warning' : 'error') : 'success';
        showToast(messages.join('；'), toastType);
    }

    if (myEnabled) {
        tasks.push(doAutoSaveMySnapshot(projectid, traceId, title).done(function (resultInform) {
            if (resultInform && resultInform.result) {
                var message = resultInform.message || '已自动保存我的快照';
                if (message.indexOf('已自动保存过') >= 0) {
                    autoSavedTraceCache.my[traceId] = true;
                    pushSummaryResult('skipped', message);
                    return;
                }
                autoSavedTraceCache.my[traceId] = true;
                pushSummaryResult('success', message);
                return;
            }
            pushSummaryResult('failed', (resultInform && (resultInform.errorMessage || resultInform.message)) || '自动保存我的快照失败');
        }).fail(function (xhr) {
            pushSummaryResult('failed', '自动保存我的快照失败: ' + extractRequestErrorMessage(xhr, '网络请求失败'));
        }));
    }

    if (systemEnabled) {
        tasks.push(doAutoSaveSystemSnapshot(projectid, traceId, title).done(function (resultInform) {
            if (resultInform && resultInform.result) {
                var message = resultInform.message || '已自动保存系统快照';
                if (message.indexOf('已自动保存过') >= 0) {
                    autoSavedTraceCache.system[traceId] = true;
                    pushSummaryResult('skipped', message);
                    return;
                }
                autoSavedTraceCache.system[traceId] = true;
                pushSummaryResult('success', message);
                return;
            }
            pushSummaryResult('failed', (resultInform && (resultInform.errorMessage || resultInform.message)) || '自动保存系统快照失败');
        }).fail(function (xhr) {
            pushSummaryResult('failed', '自动保存系统快照失败: ' + extractRequestErrorMessage(xhr, '网络请求失败'));
        }));
    }

    if (tasks.length > 0) {
        $.when.apply($, tasks).always(function () {
            finalizeSummary();
        });
    }

    return tasks;
}

function doSaveSnapshot(projectid, onSuccess) {
    if (!selectTraceId) {
        showToast('请选择一条监控记录后再保存快照', 'error');
        return $.Deferred().reject().promise();
    }

    var $form = $("#newSnapshotForm");
    return $.ajax({
        url: "/p/" + projectid + "/snapshot/save",
        type: "POST",
        dataType: "json",
        data: buildEncodedFormData($form, {traceId: selectTraceId})
    }).done(function (resultInform) {
        if (resultInform && resultInform.result) {
            showToast(resultInform.message || '快照保存成功', 'success');
            if (typeof onSuccess === 'function') {
                onSuccess(resultInform);
            }
            return;
        }

        showToast((resultInform && (resultInform.errorMessage || resultInform.message)) || '快照保存失败', 'error');
    }).fail(function (xhr) {
        var errorMessage = '网络请求失败';
        if (xhr && xhr.responseJSON) {
            errorMessage = xhr.responseJSON.errorMessage || xhr.responseJSON.message || errorMessage;
        }
        showToast('快照保存失败: ' + errorMessage, 'error');
    });
}

function openCreateSnapshot() {
    $("#snapshotDialog").modal('show');
}


// 打开创建系统快照事件窗口
function openCreateSystemSnapshot() {
    $("#systemSnapshotDialog").load('monitor/openSystemSnapshot?traceId=' + selectTraceId);
    $("#systemSnapshotDialog").modal('show');
}


/*function doSaveSystemSnapshot(projectid) {
    var resultInform = $.ajax({
        url: "/p/" + projectid + "/monitor/doSaveSystemSnapshot",
        data: $("#systemSnapshotForm").serialize(),
        async: false
    }).responseJSON;
    if (resultInform.result) {
        $("#systemSnapshotDialog").modal('hide');
        alert(resultInform.message);
    } else {
        alert(resultInform.errorMessage);
    }
}*/




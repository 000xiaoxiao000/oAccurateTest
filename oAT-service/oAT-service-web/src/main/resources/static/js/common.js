/**
 * 构建拓扑流程图 依赖 d3.v4.min.js 与dagre-d3.0.6.1.min.js
 * @param svgId
 * @param data
 * @param operation
 * @returns {*}
 */
function buildTopo(svgId, data, operation) {
    // 创建 dagreD3 对象
    var g = new dagreD3.graphlib.Graph()
        .setGraph({
            rankdir: "LR"
        })
        .setDefaultEdgeLabel(function () {
            return {};
        });
    // 添加节点
    data.nodes.some(function (value, index, array) {
        var labelVal = "<div class='labelContent'>" +
                            "<i class='fas fa-" + value.icon + " fa-2x labelIcon'></i>" +
                            " <span class='labelTitle'>" +value.title + "</span>" +
                            "<br> " +
                            "<span class='subTitle'> " + value.subTitle + "</span>" +
                        " </div>";
        g.setNode(value.id,  {label: labelVal, labelType: "html", class:value.state ,rx: 5, ry: 5});
    });
    // 添加关系
    data.edges.some(function (value, index, array) {
        g.setEdge(value.from, value.to, value)
    });

    g.nodes().forEach(function (v) {
        var node = g.node(v);
        // 设置矩形的圆角
        node.rx = node.ry = 5;
    });
    // 获取svg 画布
    var svg = d3.select("#" + svgId);
    var svgGroup = svg.append("g");


    //创建渲染器
    var render = new dagreD3.render();
    inner=d3.select("svg g");
    //执行渲染
    render(inner, g);

    // 设置缩放支持
    var zoom = d3.zoom().on("zoom", function() {
        inner.attr("transform", d3.event.transform);
    });
    svg.call(zoom);

    // 将渲染后的图表居中显示
    var xCenterOffset = (svg.attr("width") - g.graph().width) / 2;
    svgGroup.attr("transform", "translate(" + xCenterOffset + ", 20)");
    svg.attr("height", g.graph().height + 40);

    //  设置缩放支持
    var initialScale = 0.75;
    svg.call(zoom.transform, d3.zoomIdentity.translate((svg.attr("width") - g.graph().width * initialScale) / 2, 20).scale(initialScale));


    // 触发点击事件
    svg.selectAll("g.node")
        .on('click', function (id, index, array) {
            // 选中样式变化
            for (i = 0; i < array.length; i++) {
                d3.select(array[i].children[0]).style("stroke", null).style("stroke-width", null);
            }
            d3.select(this.children[0]).style("stroke", "dodgerblue").style("stroke-width", "2.5px");

            if (operation != null) {
                operation.nodeClick(id, index, array)
            }
        });
    return g;
}

/**
 * 显示悬浮提示 (Toast)
 * @param message 提示内容
 * @param type 类型: 'success' (成功，绿色), 'error' (失败，红色), 'info' (默认，无色/蓝色)
 * @param customDuration 自定义展示时长，单位毫秒
 */
function showToast(message, type, customDuration) {
    var container = $('#toast-container');
    if (container.length === 0) {
        $('body').append('<div id="toast-container"></div>');
        container = $('#toast-container');
    }

    var messageClass = 'info';
    var headerText = '提示';
    var iconClass = 'info circle';
    var duration = 3000; // Default 3 seconds

    if (type === 'success') {
        messageClass = 'positive'; // Semantic UI positive = green
        headerText = '成功';
        iconClass = 'check circle';
    } else if (type === 'error' || type === 'failure') {
        messageClass = 'negative'; // Semantic UI negative = red
        headerText = '失败';
        iconClass = 'exclamation circle';
        duration = 10000; // Failure toasts stay for 10 seconds
    } else if (type === 'warning') {
        messageClass = 'warning';
        headerText = '提醒';
        iconClass = 'exclamation triangle';
        duration = 8000;
    }
    if (customDuration && customDuration > 0) {
        duration = customDuration;
    }

    var toastHtml =
        '<div class="ui ' + messageClass + ' message floating">' +
            '<i class="close icon"></i>' +
            '<div class="header">' +
                '<i class="' + iconClass + ' icon"></i> ' + headerText +
            '</div>' +
            '<p>' + message + '</p>' +
        '</div>';

    var $toast = $(toastHtml).hide().appendTo(container);

    // Animate in
    if (typeof $toast.transition === 'function') {
         $toast.transition('fade left');
    } else {
         $toast.fadeIn();
    }

    // Auto dismiss logic
    var timer = setTimeout(function() {
        dismissToast($toast);
    }, duration);

    // Close button
    $toast.find('.close').on('click', function() {
        clearTimeout(timer);
        dismissToast($toast);
    });
}

// Unified notify entry: prefer toast, fallback to native alert when toast stack is unavailable.
function notifyToast(message, type, customDuration) {
    var text = (message === undefined || message === null) ? '' : String(message);
    if (typeof showToast === 'function') {
        showToast(text, type || 'info', customDuration);
        return;
    }
    if (typeof window !== 'undefined' && typeof window.alert === 'function') {
        window.alert(text);
    }
}

function dismissToast($toast) {
    if (typeof $toast.transition === 'function') {
        $toast.transition({
            animation: 'fade right',
            onComplete: function() {
                $toast.remove();
            }
        });
    } else {
        $toast.fadeOut(function() {
            $toast.remove();
        });
    }
}

$(function() {
    // Check for message in URL parameters
    function getQueryParam(name) {
        var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
        var r = window.location.search.substr(1).match(reg);
        if (r != null) return decodeURIComponent(r[2]);
        return null;
    }

    // Check for message in sessionStorage (for page reloads)
    var sessionMessage = sessionStorage.getItem('toastMessage');
    var sessionMessageType = sessionStorage.getItem('toastMessageType');

    if (sessionMessage) {
        showToast(sessionMessage, sessionMessageType || 'info');
        // Clear immediately so it doesn't show again on next refresh
        sessionStorage.removeItem('toastMessage');
        sessionStorage.removeItem('toastMessageType');
    }

    var message = getQueryParam('message');
    if (message) {
        // Simple heuristic: if message contains "失败" or "Error", use error type, otherwise success
        var type = 'success';
        if (message.indexOf('失败') !== -1 || message.indexOf('Error') !== -1 || message.indexOf('fail') !== -1) {
            type = 'error';
        }
        showToast(message, type);
    }
});

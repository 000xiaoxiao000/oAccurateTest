var concentricLayoutOptions = {
    name: 'concentric',
    // minNodeSpacing:50,//  节点间最小间距
    fit: false,  // 是否刚好填满画
    nodeDimensionsIncludeLabels: true, // 布局时是否考虑标签大小
    concentric: function (ele) {
        return ele.data('weight');
    },
    levelWidth: function (nodes) {
        return 10;
    },
    padding: 10 // 视图与布画的内间距
};

const coseLayoutOptions = {
    name: 'cose',
    //运行布局时是否动画
    // true:在布局运行时连续动画
    // false:只显示最终结果
    // 'end':用最终结果进行动画，从初始位置到结束位置
    animate: false,
    // 布局刷新间隔时间，以毫秒为单位。默认值为20，值域为[0, Infinity]。增加该值会减少刷新次数，减小该值会增加刷新次数。
    refresh: 0.01,
    // 指定是否将布局视图适合容器。默认值为true
    fit: true,
    // 布局视图边缘与容器的距离。默认值为30，值域为[0, Infinity]。增加该值会增加节点与边缘之间的距离，减小该值会减小节点与边缘之间的距离
    padding: 30,
    // 在计算布局算法的节点包围框时排除标签
    nodeDimensionsIncludeLabels: false,
    // 指定是否使用随机布局。默认值为true
    randomize: false,
    // 组件之间的距离。默认值为40，值域为[0, Infinity]。增加该值会增加组件之间的距离，减小该值会减小组件之间的距离。
    componentSpacing: 100,
    // 节点的最小重叠量。默认值为20，值域为[0, Infinity]。增加该值会减少节点之间的重叠，减小该值会增加节点之间的重叠
    nodeRepulsion: 80000000,
    // 节点排斥(重叠)乘数
    nodeOverlap: 20,
    // 期望的边长度。默认值为100，值域为[0, Infinity]。增加该值会增加节点之间的距离，减小该值会减小节点之间的距离
    idealEdgeLength: 500,
    // 边的弹性。默认值为100，值域为[0, Infinity]。增加该值会减少边的弯曲程度，减小该值会增加边的弯曲程度
    edgeElasticity: 40,
    // 嵌套因子。默认值为1.2，值域为[0, Infinity]。增加该值会增加嵌套组件的大小，减小该值会减小嵌套组件的大小
    nestingFactor: 5,
    // 重力大小。默认值为80，值域为[0, Infinity]。增加该值会增加节点的向心力，减小该值会减小节点的向心力
    gravity: 80,
    // 最大迭代次数。默认值为1000，值域为[0, Infinity]。增加该值会增加布局的迭代次数，减小该值会减少布局的迭代次数
    numIter: 1000,
    // 初始温度。默认值为1000，值域为[0, Infinity]。增加该值会增加节点的随机移动范围，减小该值会减小节点的随机移动范围
    initialTemp: 200,
    // 降温系数。默认值为0.99，值域为[0, 1]。降温系数。增加该值会加速降温过程，减小该值会减缓降温过程
    coolingFactor: 0.95,
    // 最小温度。默认值为1，值域为[0, Infinity]。增加该值会减少随机移动的幅度，减小该值会增加随机移动的幅度
    minTemp: 1.0
};

var appRelationLayoutOptions = Object.assign({}, coseLayoutOptions, {
    padding: 90,
    componentSpacing: 60,
    nodeRepulsion: 1200000,
    idealEdgeLength: 180,
    edgeElasticity: 120,
    gravity: 120,
    numIter: 800,
    initialTemp: 120
});

var appSnapshotLayoutOptions = {
    name: 'grid',
    fit: true,
    padding: 135,
    avoidOverlap: true,
    avoidOverlapPadding: 92,
    nodeDimensionsIncludeLabels: true,
    animate: false,
    condense: false,
    spacingFactor: 1.55
};

var codeLayerLayoutOptions = Object.assign({}, coseLayoutOptions, {
    padding: 140,
    componentSpacing: 80,
    nodeRepulsion: 900000,
    idealEdgeLength: 95,
    edgeElasticity: 120,
    gravity: 90,
    numIter: 700,
    initialTemp: 80
});

function getMapLayoutOptions(data) {
    var elements = Array.isArray(data) ? data : ((data && data.elements) || []);
    var hasCodeLayer = elements.some(function (element) {
        if (!element || !element.classes) {
            return false;
        }
        return Array.isArray(element.classes)
            ? element.classes.indexOf('code_class') >= 0
            : String(element.classes).indexOf('code_class') >= 0;
    });
    var hasAppRelation = elements.some(function (element) {
        if (!element || element.group !== 'edges' || !element.classes) {
            return false;
        }
        return Array.isArray(element.classes)
            ? element.classes.indexOf('app-relation') >= 0
            : String(element.classes).indexOf('app-relation') >= 0;
    });
    if (hasAppRelation) {
        return appRelationLayoutOptions;
    }
    if (hasCodeLayer) {
        return codeLayerLayoutOptions;
    }
    return appSnapshotLayoutOptions;
}

var breadthfirstLayoutOptions = {
    name: 'breadthfirst',
    fit: true, // 是否将观察窗口与图相匹配
    directed: true, // 树是否向下(如果为false，边可以指向任何方向)
    padding: 20, // padding on fit
    circle: false, // 如果为真，把深度画成同心圆，如果为假，把深度画成上下
    grid: true, // 是否创建一个均匀的网格来放置DAG(圆圈:仅为false)
    spacingFactor: 2.75, // 正间距因子，更大=>节点之间的空间更大(N.B. n/a如果导致重叠)
    boundingBox: undefined, // 约束布局边界;{x1, y1, x2, y2}或{x1, y1, w, h}
    avoidOverlap: true, // 防止节点重叠，如果没有足够的空间，可能溢出boundingBox
    nodeDimensionsIncludeLabels: false, // 在计算布局算法的节点包围框时排除标签
    roots: undefined, // the roots of the trees
    maximal: false, // 是否将节点向下移动其自然BFS深度以避免向上边缘(仅限DAGS)
    depthSort: undefined, // 一种排序函数，使节点按等深度排序。例如，函数(a, b){返回a.data('weight') - b.data('weight')}
    animate: false, // 是否转换节点位置
    animationDuration: 500, // 如果启用，动画持续时间以毫秒为单位
    animationEasing: undefined, // 如果启用动画简化
    animateFilter: function (node, i) {
        return true;
    }, // 决定是否对节点进行动画处理的函数。所有节点默认启用动画。非动画节点在布局开始时立即被定位
    transform: function (node, position) {
        return position;
    } // 变换一个给定的节点位置。用于改变离散布局中的流动方向
};

function buildMap(data) {
    try {
        var layoutOptions = getMapLayoutOptions(data);
        var cy = window.cy = cytoscape({
            container: document.getElementById('map_body'), // 容器id
            minZoom: 0.2,// 缩放最小比例
            maxZoom: 6, // 缩放最大比例
            wheelSensitivity: 0.1, boxSelectionEnabled: true,// 是否允许框选 按住ctrl或shift 拖动鼠标框选
            elements: data,
            style: fetch('/css/map.cycss?v=3').then(function (value) {
                return value.text();
            }),
            layout: layoutOptions
        });

        var doubleClickDelayMs = 350;
        var previousTapStamp;
        cy.on('tap', function (event) {
            var currentTapStamp = event.timeStamp;
            var msFromLastTap = currentTapStamp - previousTapStamp;

            if (msFromLastTap < doubleClickDelayMs) {
                event.target.trigger('doubleTap', event);
            }
            previousTapStamp = currentTapStamp;

            // 只在点击空白画布时隐藏底部信息框
            if (event.target === cy) {
                document.getElementById('bottom_nodeInfo').style.display = 'none';
            }
        });

        // 选中节点后
        cy.on('select', 'node.snapshot', function (event) {
            var ele = event.target;
            //  是否显示关联节点
            if (cy.settings.subSelectUnionNode()) {
                doSubSelectUnionNode(cy.nodes('node.snapshot:selected'));
            }
        });
        // 取消选中 只删除当前取消节点
        cy.on('unselect', 'node.snapshot', function (event) {
            if (cy.settings.subSelectUnionNode()) {
                doSubSelectUnionNode(cy.nodes('node.snapshot:selected'));
            } else {
                cy.batch(function () {
                    cy.nodes(".subSelected").removeClass('subSelected');
                });
            }
        });

        // 双击节点 后
        cy.on('doubleTap', 'node', function (event) {
            var ele = event.target;
            let arr = ele.outgoers().targets();
            for (let i = 0; i < arr.length; i++) {
                ele.edgesTo(arr[i]).style('content', i + 1);
            }
        });

        // 节点，点击节点，根据选中的尾节点，突出显示最长路径中的节点
        cy.on('tap', 'node', function (event) {
            var target = event.target;

            // 从目标节点运行dijkstra算法
            var dijkstra = cy.elements().dijkstra(target, function (edge) {
                return edge.data('weight');
            });

            // 从目标节点获取到每个节点的最短路径
            var pathsTo = {};
            cy.nodes().forEach(function (node) {
                var path = dijkstra.pathTo(node);
                pathsTo[node.id()] = path;
            });

            // 通过遍历所有路径获得最长路径
            var longestPath = null;
            cy.nodes().forEach(function (node) {
                var path = pathsTo[node.id()];
                if (path != null && (longestPath == null || path.distance > longestPath.distance)) {
                    longestPath = path;
                }
            });

            // 突出显示最长路径中的节点
            cy.elements().removeClass('highlight');
            if (longestPath != null) {
                longestPath.forEach(function (node, index) {
                    setTimeout(function () {
                        node.addClass('highlight');
                        node.animate({
                            duration: 1000
                        }, index * 1000);
                    });
                });
            }
        });

        // 节点，鼠标按下，节点与边增加高亮
        cy.on('mousedown', 'node', function (event) {
            var eles = event.target;
            cy.elements().difference(eles.outgoers());
            eles.addClass('highlight').outgoers().addClass('highlight');
        });
        // 节点，鼠标抬起，取消高亮，节点与边全部选中
        cy.on('mouseup', 'node', function (event) {
            var eles = event.target;
            eles.removeClass('highlight').outgoers().removeClass('highlight');

            eles.select();
            eles.outgoers().select();
        });

        // 节点，鼠标右键单击，清除所有节点node的边和高亮
        cy.on('cxttapstart', 'node', function (event) {
            var eles = event.target;
            let arr = eles.outgoers().targets();
            for (let i = 0; i < arr.length; i++) {
                eles.edgesTo(arr[i]).style('label', '');
            }
            cy.$('.highlight').removeClass('highlight').outgoers().removeClass('highlight');
        });
        // 边,鼠标右键单击，清除edge上的数字
        cy.on('cxttapstart', 'edge', function (event) {
            var eles = event.target;
            eles.style('label', '');
        });

        // 自定义设置选项
        cy.settings = {
            subSelectUnionNode: function () {
                return false;
            }
        };
        cy.layoutOptions = layoutOptions;
        cy.applyComfortableFit = applyComfortableFit;
        cy.refreshSnapshotReferenceEdges = refreshSnapshotReferenceEdges;
        cy.loadElement = loadElement;
        cy.doFind = doFind;
        cy.doRefresh = doRefresh;
        return cy;
    } catch (e) {
        console.error('An error occurred during Cytoscape initialization:', e);
    }
}

function refreshSnapshotReferenceEdges() {
    if (!window.cy) {
        return;
    }
    cy.batch(function () {
        cy.edges('.snapshot-reference').remove();
        var edgeIds = {};
        cy.nodes('.snapshot').forEach(function (snapshotNode) {
            var references = snapshotNode.data('references') || [];
            references.forEach(function (referenceId) {
                var targetNode = findReferenceNode(referenceId);
                if (targetNode.empty()) {
                    return;
                }
                var edgeId = 'snapshot-reference-' + snapshotNode.id() + '-' + targetNode.id();
                if (edgeIds[edgeId]) {
                    return;
                }
                edgeIds[edgeId] = true;
                cy.add({
                    group: 'edges',
                    classes: ['snapshot-reference'],
                    data: {
                        id: edgeId,
                        source: snapshotNode.id(),
                        target: targetNode.id(),
                        name: '关联'
                    }
                });
            });
        });
    });
}

function findReferenceNode(referenceId) {
    var directNode = cy.$id(referenceId);
    if (directNode.nonempty()) {
        return directNode;
    }
    var normalized = normalizeReferenceId(referenceId);
    return cy.nodes().filter(function (node) {
        if (node.hasClass('snapshot')) {
            return false;
        }
        return normalizeReferenceId(node.id()) === normalized
            || normalizeReferenceId(node.data('name')) === normalized;
    }).first();
}

function normalizeReferenceId(referenceId) {
    if (!referenceId) {
        return '';
    }
    var value = String(referenceId).trim().replace(/\\/g, '/');
    while (value.charAt(0) === '/') {
        value = value.substring(1);
    }
    return value.replace(/\//g, '.').toLowerCase();
}

function applyComfortableFit(elements) {
    if (!window.cy) {
        return;
    }
    var target = elements && elements.nonempty && elements.nonempty() ? elements : cy.elements();
    if (target.empty()) {
        return;
    }
    cy.fit(target, 120);
    if (cy.zoom() > 1.55) {
        cy.zoom(1.55);
        cy.center(target);
    }
    if (cy.zoom() < 0.45) {
        cy.zoom(0.45);
        cy.center(target);
    }
}

function loadElement(url, select, fullLayout, title) {
    fetch(url)   // 加载数据
        .then(function (res) {  //封装json
            if (!res.ok) {
                return res.text().then(function (text) {
                    throw new Error(text || ('请求失败：' + res.status));
                });
            }
            return res.json();
    }).then(function (data) { // 添加节点
        if (!Array.isArray(data)) {
            return cy.collection();
        }
        return cy.add(data);
    }).then(function (eles) { // 布局
        refreshSnapshotReferenceEdges();
        if (fullLayout === false) {
            cy.layout(cy.layoutOptions || coseLayoutOptions).run();
        } else {
            doRefresh();
        }
        if (eles.length > 0) {
            applyComfortableFit(eles.union(cy.nodes(':selected')));
        }
        return eles;
    }).then(function (eles) { // 选中
        if (select && eles.length > 0) {
            cy.nodes(':selected').deselect();
            eles.select();
        }
    }).then(function (eles) {
        if (typeof (title) != "undefined") {
            if (title.length > 0) {
                cy.filter("node[name='" + title + "']").select();
            }
        }
    }).catch(error => {
        console.error("fetch()异常：", error);
        if (typeof showToast === 'function') {
            showToast(error.message || '图谱加载失败', 'error');
        }
    });
}

// 查找元素
function doFind(key) {
    cy.batch(function () {
        cy.nodes('.find').removeClass('find');
        if (key != "") {
            // 正则匹配
            var pattern = key.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, ".*");
            var regexp = new RegExp("^" + pattern + ".*$", "i");

            var findNodes = cy.filter(function (element, i) {
                if (!element.isNode()) {
                    return false;
                }
                var name = (element.data('name') || element.data('label') || '').trim();
                return regexp.test(name);
            });
            findNodes.addClass("find");
            $("#find_element label").html(findNodes.length);
        } else {
            $("#find_element label").html("0");
        }
        $("#map_body").focus();
    });
}

function showHot() {
    cy.batch(function () {
        cy.filter(function (element, i) {
            return element.isNode() && !element.hasClass("snapshot");
        }).addClass('hot');
    });
}

function closeHot() {
    cy.batch(function () {
        cy.nodes('.hot').removeClass('hot');
    });
}


// 子选中关联节点
function doSubSelectUnionNode(ele) {
    cy.batch(function () {
        refreshSnapshotReferenceEdges();
        cy.edges('.snapshot-reference').removeClass('subSelected');
        cy.nodes(".subSelected").removeClass("subSelected");
        var matchedCount = 0;
        ele.forEach(function (node) {
            var references = node.data('references') || [];
            references.forEach(function (r) {
                var referenceNode = findReferenceNode(r);
                if (referenceNode.nonempty()) {
                    referenceNode.addClass("subSelected");
                    matchedCount += referenceNode.length;
                    node.edgesTo(referenceNode).filter('.snapshot-reference').addClass('subSelected');
                }
            });
        });
        if (matchedCount === 0 && ele.length > 0 && typeof showToast === 'function') {
            showToast('当前画布没有可高亮的关联节点，请先开启代码或表结构图层', 'info');
        }
    });
}

function doRefresh() {
    var layout = cy.layout(cy.layoutOptions || coseLayoutOptions);
    layout.one('layoutstop', function () {
        applyComfortableFit(cy.elements());
    });
    layout.run();
}

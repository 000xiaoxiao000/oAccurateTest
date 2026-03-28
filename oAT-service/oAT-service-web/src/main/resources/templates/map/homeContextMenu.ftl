<div class="vertical menu">
    <#-- 画布上文菜单-->
    <div class="item" onclick="showDoFind();" targetElement=".context">
        <i class="icon find"></i>查找
        <span class="description">
            Ctrl+f
        </span>
    </div>
    <div class="item" onclick="cy.doRefresh();" targetElement=".context">
        <i class="icon refresh"></i>
        刷新
    </div>
    <div class="item" onclick="clearAllImage();" targetElement=".context">
        <i class="icon eraser"></i>
        清空
    </div>

    <#-- APP节点上文菜单-->
    <div class="item" onclick="gotoAppDetailVisual();" targetElement="node.app">详情视图</div>
    <div class="item" onclick="loadSnapshotLayer();" targetElement="node.app">展开快照</div>
    <#-- 快照节点上文菜单-->
    <div class="header" targetElement="node.snapshot" style="font-size: 0.9em">
        <i class="folder open icon"></i>展开
    </div>
    <div class="item" onclick="loadSnapshotTableLayer();" targetElement="node.snapshot">表结构</div>
    <div class="item" onclick="loadSnapshotRemoteLayer();" targetElement="node.snapshot">远程服务</div>
    <div class="item" onclick="loadStackCodeLayer();" targetElement="node.snapshot">源码关联图谱</div>
    <div class="item" onclick="delLayer();" targetElement="node.snapshot">清除图谱</div>

    <div class="divider" targetElement="node.snapshot"></div>
    <div class="item" onclick="openSnapshotDetailPage();" targetElement="node.snapshot">
        详情页
        <span class="description">
         <i class="icon grey linkify"></i>
        </span>
    </div>
    <div class="item" onclick="showNodeTips();" targetElement="node.snapshot">
        概要
        <span class="description">F2</span>
    </div>

    <div class="item" onclick="loadTableSnapshotLayer();" targetElement="node.table">展开关联快照</div>
    <div class="item" onclick="loadDubboSnapshotLayer();" targetElement="node.remote.dubbo">展开关联快照</div>

    <div class="divider" targetElement="node"></div>
    <#if loginRole != "visitor">
        <div class="item" onclick="doRemoveNodeSelected();" targetElement="node">
            删除该节点
            <span class="description">Delete</span>
        </div>
        <div class="item" onclick="doRemoveEdgeSelected();" targetElement="edge">
            删除该关系线
            <span class="description">Delete</span>
        </div>
    </#if>
</div>
<script>
    // 打开应用详情视图
    function gotoAppDetailVisual() {
        var app = $("#content_menu")[0].data;
        window.location.href = "/p/${project.id}/map/app?appId=" + app.id;
    }

    // 展开快照节点
    function loadSnapshotLayer() {
        var app = $("#content_menu")[0].data;
        cy.loadElement("/p/${project.id}/map/layer/appSnapshot?appId=" + app.id);
    }

    // 展开快照表结构 节点
    function loadSnapshotTableLayer() {
        delLayer();

        var snapshot = $("#content_menu")[0].data;
        cy.loadElement("/p/${project.id}/map/layer/snapshotTable?snapshotId=" + snapshot.id, true, false);


    }

    // 展开快照远程服务 节点
    function loadSnapshotRemoteLayer() {
        delLayer();

        var snapshot = $("#content_menu")[0].data;
        cy.loadElement("/p/${project.id}/map/layer/snapshotRemote?snapshotId=" + snapshot.id, true, false);
    }

    function loadTableSnapshotLayer() {
        var table = $("#content_menu")[0].data;
        cy.loadElement("/p/${project.id}/map/layer/tableSnapshot?DataBase=" + table.database + "&table=" + table.name);
    }

    function loadDubboSnapshotLayer() {
        var dubbo = $("#content_menu")[0].data;
        cy.loadElement("/p/${project.id}/map/layer/dubboSnapshot?interfaceName=" + dubbo.interfaceName + "&methodName=" + dubbo.methodName);
    }

    function loadStackCodeLayer() {
        delLayer();

        var snapshot = $("#content_menu")[0].data;
        cy.loadElement("/p/${project.id}/map/layer/stack/code?snapshotId=" + snapshot.id, true, false);
    }

    // 清除图谱
    function delLayer() {
        cy.remove('node[weight < 40]')
    }

    // 进入快照详情页
    function openSnapshotDetailPage() {
        var snapshot = $("#content_menu")[0].data;
        var url = "/p/${project.id}/" + snapshot.appId + "/snapshot/detail/" + snapshot.id
        window.open(url, "_blank");
    }

    // 删除当前节点
    function deleteCurrentNode() {
        var nodeData = $("#content_menu")[0].data;
        cy.$id(nodeData.id).remove();
    }

    // 清空整个图表
    function clearAllImage() {
        cy.remove("*");
    }

    function doRemoveNodeSelected() {
        cy.nodes(":selected").remove();
    }

    function doRemoveEdgeSelected() {
        cy.edges(":selected").remove();
    }

</script>
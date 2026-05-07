<#--地图 应用视角工具栏-->
<div class="ui text menu" style="margin: 0px">
    <a class="blue item popup" href="/p/${project.id}/map/home" data-content="返回项目图谱"
       data-variation="mini inverted"
       data-position="bottom center">
        <i class="arrow left icon"></i>
    </a>
    <div class="item" style="font-size: 1.2em">|</div>
    <a class="blue item popup layer toggle ${codeActive!}" value="code" data-content="源码类调用图层"
       data-variation="mini inverted"
       data-position="bottom center">
        <i class="code icon"></i>
    </a>
<#--<a class="blue item popup layer toggle ${methodActive!}" value="method" data-content="方法列表" data-variation="mini inverted"
   data-position="bottom center">
    <i class="th list icon"></i>
</a>-->
    <a class="blue item popup layer toggle ${tableActive!}" value="table" data-content="表结构图层"
       data-variation="mini inverted"
       data-position="bottom center">
        <i class="table icon"></i>
    </a>
    <div class="item" style="font-size: 1.2em">|</div>
    <a class="blue item popup hot toggle" data-content="关联热度"
       data-position="bottom center">
        <i class="dot circle outline icon"></i>
    </a>
    <a class="blue item popup union toggle" data-content="高亮所选快照关联的代码/表"
       data-position="bottom center">
        <i class="share alternate icon"></i>
    </a>
</div>
<script>
    $("#right_toolbar .popup").popup();
    $("#right_toolbar a.item.toggle").click(function (e) {
        $(e.delegateTarget).toggleClass("active");
    })
</script>
<script>
    // 跳转图层切换
    $("#right_toolbar .item.layer.toggle").click(function (event) {
        var href = "/p/${project.id}/map/app?appId=${app.id}";
        var layers = ""
        $("#right_toolbar .item.layer.toggle.active").each(function (index, item) {
            layers += "," + $(item).attr('value');
        });
        href += "&layers=" + layers;
        window.location.href = href;
    });
    //热点图层切换
    $("#right_toolbar .item.hot.toggle").click(function (event) {
        if ($(event.currentTarget).hasClass('active')) {
            showHot();
        } else {
            closeHot();
        }
    });

    // 是否显示快照关联项
    function isShowUnion() {
        return $("#right_toolbar .item.union.toggle.active").length > 0;
    }

    //  切换 快照关联选项
    $("#right_toolbar .item.union.toggle").click(function (e) {
        if ($(e.currentTarget).hasClass('active')) {
            doSubSelectUnionNode(cy.nodes(":selected"));
        } else {
            cy.batch(function () {
                cy.nodes(".subSelected").removeClass('subSelected');
                cy.edges(".snapshot-reference").removeClass('subSelected');
            });
        }
    });

    if ($("#right_toolbar .item.layer.toggle[value='code'].active").length > 0) {
        $("#right_toolbar .item.union.toggle").addClass("active");
    }
</script>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-表结构搜索</title>
     <#include "../common.ftl">
    <script src="/js/cytoscape.min.js"></script>
</head>
<body>

<#assign searchItemActive="active"/>
<#include "../projectHeader.ftl">
<style>
    em {
        color: red;
    }

    #cy {
        position: fixed;
        top: 70px;
        left: 0px;
        right: 0px;
        bottom: 0px;
        z-index: 1;

    }
</style>
<div class="ui container" style="z-index: 2">
    <br>
    <form id="searchForm" class="ui form">
        <div class="ui error message" style="z-index: 2;max-width: 600px;padding-top: 7px;padding-bottom: 7px;">
        </div>
        <div class="ui action input" style="min-width: 400px;z-index: 2" <#--data-tooltip="输入：数据库名 表名" data-position="bottom left"-->>
            <input id="keyword" type="text" name="keyword" placeholder="格式：数据库名 表名">
            <div class="ui dropdown  basic button">
                <div class="text">表结构图</div>
                <i class="dropdown icon"></i>
                <div class="menu">
                    <a class="item" href="search">用例</a>
                    <a class="item" href="searchTable">表结构图</a>
                </div>
            </div>
            <#--初始化下拉框-->
            <script>
                $("#searchForm .dropdown.button").dropdown({
                    on: 'hover'
                });
            </script>
            <div class="ui blue button" onclick="doSearch();">搜索</div>
        </div>

    </form>
</div>
<#--表结构关系图画布-->
<div id="cy">

</div>

<script>
    // 初始化画布
    var cy = cytoscape({
        container: document.getElementById('cy'), // container to render in
        style: fetch('/css/cy-style.json').then(function (response) {
            return response.json();
        }),
        layout: {
            name: 'breadthfirst',
            fit: true, // whether to fit the viewport to the graph
            directed: true, // 树节点是否向下
            padding: 100, // padding on fit
            circle: false,// put depths in concentric circles if true, put depths top down if false
            grid: true,
            spacingFactor: 1,
        },
        minZoom: 0.5,// 缩放最小比例
        maxZoom: 3, // 缩放最大比例
        boxSelectionEnabled: true
    });

    $('#searchForm').submit(function () {
        doSearch();
        return false;
    })

    function doSearch() {
        // 如果字符为空， 刷新当前页

        if (!$('.ui.form').form('validate form')) {
            return false;
        }
        database = $('#keyword').val().split(" ")[0];
        table = $('#keyword').val().split(" ")[1];

        // 开始处理
        cy.startBatch();
        var nodeJson = $.ajax({
            url: "/p/${project.id}/doSearchTable/",
            data: "DataBase=" + database + "&table=" + table,
            async: false
        }).responseJSON;

        // 基于选择器删除所有
        cy.remove("");
        nodeJson.nodes.forEach(function (node) {
            cy.add({
                group: 'nodes', data: node, classes: [node.type]
            });
        });
        nodeJson.edges.forEach(function (edge) {
            style = "";
            if (edge.action.indexOf("delete") != -1) {
                style = 'red';
            } else if (edge.action.indexOf("update") != -1) {
                style = 'orange';
            } else if (edge.action.indexOf("insert") != -1) {
                style = 'green';
            }
            cy.add({
                group: 'edges', data: edge, classes: [edge.type, style]
            });
        });

        cy.layout({
            name: 'breadthfirst',
            fit: true, // whether to fit the viewport to the graph
            directed: true, // 树节点是否向下
            padding: 100, // padding on fit
            circle: false,// put depths in concentric circles if true, put depths top down if false
            grid: true,
            spacingFactor: 1,
        }).run();
        // 结束处理
        cy.endBatch();
    }

    $('.ui.form').form({
        inline: false,
        onFailure: function (formErrors, fields) {
            if (formErrors && formErrors.length > 0) {
                showToast(formErrors[0], 'error');
            }
            return false;
        },
        fields: {
            color: {
                identifier: 'keyword',
                rules: [{
                    type: 'maxLength[30]',
                    prompt: '搜索关键字不能超过30个字符'
                },{
                        type: 'regExp',
                        value: '^\\S+ \\S+$',
                        prompt: '输入格式为:数据库名 表名'
                }]
            }
        }
    });

</script>
</body>
</html>

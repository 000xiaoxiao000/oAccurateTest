<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>用例中心-用例搜索</title>
     <#include "../common.ftl">
</head>
<body>

<#assign searchItemActive="active"/>
<#include "../projectHeader.ftl">
<style>
    em {
        color: red;
    }
</style>
<div class="ui container">
    <br>
    <form id="searchForm" class="ui form">
        <div class="ui action input" style="min-width: 400px">
            <input id="keyword" type="text" name="keyword" placeholder="搜索...">
            <div class="ui dropdown  basic button">
                <div class="text">用例</div>
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
    <div id="itemResult" class="ui">

    </div>
</div>
<script>
    $('#searchForm').submit(function () {
        doSearch();
        return false;
    })

    function doSearch() {
        // 如果字符为空， 刷新当前页
        if ($.trim($('#keyword').val()).length == 0) {
            window.location.reload();
            return false;
        }
        var items = $.ajax({
            url: "/p/${project.id}/doSearch",
            data: $("#searchForm").serialize(),
            async: false
        }).responseText;
        $("#itemResult").html(items);
    }


</script>
</body>
</html>

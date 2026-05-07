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
    <div class="ui container search-page">
    <br>
    <form id="searchForm" class="ui form">
        <div class="ui action input search-action-input">
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
            <button id="searchButton" type="submit" class="ui blue button">搜索</button>
        </div>
    </form>
    <div id="itemResult" class="ui search-result-panel">
        <div class="ui placeholder segment search-empty-state">
            <div class="ui icon header">
                <i class="search icon"></i>
                输入关键词搜索系统快照、SQL 或远程调用
            </div>
        </div>
    </div>
</div>
<style>
    .search-page .search-action-input {
        min-width: 400px;
        max-width: 720px;
        width: 60%;
    }

    .search-result-panel {
        margin-top: 24px;
    }

    .search-empty-state {
        min-height: 220px;
    }

    @media only screen and (max-width: 767px) {
        .search-page .search-action-input {
            min-width: 0;
            width: 100%;
        }
    }
</style>
<script>
    $('#searchForm').submit(function () {
        doSearch();
        return false;
    })

    function doSearch() {
        // 如果字符为空， 刷新当前页
        if ($.trim($('#keyword').val()).length == 0) {
            $('#itemResult').html('<div class="ui info message">请输入搜索关键字</div>');
            return false;
        }
        $('#searchButton').addClass('loading disabled');
        $.ajax({
            url: "/p/${project.id}/doSearch",
            data: $("#searchForm").serialize()
        }).done(function (items) {
            $("#itemResult").html(items);
        }).fail(function (xhr) {
            var message = xhr.responseText || '搜索失败，请稍后重试';
            $("#itemResult").html('<div class="ui negative message">' + message + '</div>');
        }).always(function () {
            $('#searchButton').removeClass('loading disabled');
        });
    }


</script>
</body>
</html>

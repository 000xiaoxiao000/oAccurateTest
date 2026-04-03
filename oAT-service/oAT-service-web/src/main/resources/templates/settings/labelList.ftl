<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>项目设置-标签管理</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<!--内容主体-->
<div class="ui grid attached  container" style="margin-top: 5px">

    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign labelItemActive="active"/>
        <#assign loginRole=loginNameRole />
        <#include "LeftNavigationMenu.ftl">
    </div>

    <!-- 中间内容 -->
    <div id="center-content" class="ui twelve wide column">
        <h4 class="ui top attached block header">标签管理</h4>
        <div class="ui attached segment">
            <h4 class="ui horizontal divider header">
                用例标签
            </h4>
            <div class="labels">
                <#list usecaseLables as lab>
                    <div class="ui ${lab.color} label" style="margin-top: 5px">${lab.name}
                        <a href="/p/${project.id}/label/delete?type=usecase&name=${lab.name}"
                           data-tooltip="删除标签" data-position="top left" data-inverted="">
                            <i class="ui icon delete"></i>
                        </a>
                    </div>
                </#list>
            </div>

            <div style="margin-top: 17px">
                <form action="/p/${project.id}/label/add">
                    <input type="hidden" name="type" value="usecase">
                    <div class="ui small right labeled input">
                        <input type="text" name="name" placeholder="标签名称">
                        <div class="ui dropdown label">
                            <input type="hidden" name="color">
                            <div class="text">颜色选择</div>
                            <div class=" menu">
                                <div class="item" data-value="red">
                                    <div class="ui red label"></div>
                                </div>
                                <div class="item" data-value="orange">
                                    <div class="ui orange label"></div>
                                </div>
                                <div class="item" data-value="yellow">
                                    <div class="ui yellow label"></div>
                                </div>
                                <div class="item" data-value="olive">
                                    <div class="ui olive label"></div>
                                </div>
                                <div class="item" data-value="green">
                                    <div class="ui green label"></div>
                                </div>
                                <div class="item" data-value="teal">
                                    <div class="ui teal label"></div>
                                </div>
                                <div class="item" data-value="blue">
                                    <div class="ui blue label"></div>
                                </div>
                                <div class="item" data-value="violet">
                                    <div class="ui violet label"></div>
                                </div>

                                <div class="item" data-value="purple">
                                    <div class="ui purple label"></div>
                                </div>
                                <div class="item" data-value="pink">
                                    <div class="ui pink label"></div>
                                </div>
                                <div class="item" data-value="brown">
                                    <div class="ui brown label"></div>
                                </div>
                                <div class="item" data-value="grey">
                                    <div class="ui grey label"></div>
                                </div>
                                <div class="item" data-value="black">
                                    <div class="ui black label"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <#if loginNameRole != "visitor">
                        <button class="ui small positive button " type="submit" style="margin-left: 10px">添加</button>
                    </#if>
                </form>
            </div>

            <h4 class="ui horizontal divider header">
                快照标签
            </h4>
            <div class="labels">
                <#list snapshotLables as lab>
                    <div class="ui ${lab.color} label" style="margin-top: 5px">${lab.name}
                        <a href="/p/${project.id}/label/delete?type=snapshot&name=${lab.name}"
                           data-tooltip="删除标签" data-position="top left" data-inverted="">
                            <i class="ui icon delete"></i>
                        </a>
                    </div>
                </#list>
            </div>

            <div style="margin-top: 17px">
                <form action="/p/${project.id}/label/add">
                    <input type="hidden" name="type" value="snapshot">
                    <div class="ui small right labeled input">
                        <input type="text" name="name" placeholder="标签名称">
                        <div class="ui dropdown label">
                            <input type="hidden" name="color">
                            <div class="text">颜色选择</div>
                            <div class=" menu">
                                <div class="item" data-value="red">
                                    <div class="ui red label"></div>
                                </div>
                                <div class="item" data-value="orange">
                                    <div class="ui orange label"></div>
                                </div>
                                <div class="item" data-value="yellow">
                                    <div class="ui yellow label"></div>
                                </div>
                                <div class="item" data-value="olive">
                                    <div class="ui olive label"></div>
                                </div>
                                <div class="item" data-value="green">
                                    <div class="ui green label"></div>
                                </div>
                                <div class="item" data-value="teal">
                                    <div class="ui teal label"></div>
                                </div>
                                <div class="item" data-value="blue">
                                    <div class="ui blue label"></div>
                                </div>
                                <div class="item" data-value="violet">
                                    <div class="ui violet label"></div>
                                </div>

                                <div class="item" data-value="purple">
                                    <div class="ui purple label"></div>
                                </div>
                                <div class="item" data-value="pink">
                                    <div class="ui pink label"></div>
                                </div>
                                <div class="item" data-value="brown">
                                    <div class="ui brown label"></div>
                                </div>
                                <div class="item" data-value="grey">
                                    <div class="ui grey label"></div>
                                </div>
                                <div class="item" data-value="black">
                                    <div class="ui black label"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <#if loginNameRole != "visitor">
                        <button class="ui small positive button" type="submit" style="margin-left: 10px">添加</button>
                    </#if>
                </form>
            </div>

        </div>
    </div>
</div>

<script>
    $('#center-content .ui.dropdown').dropdown({
        on: 'hover'
    });
</script>
</body>
</html>

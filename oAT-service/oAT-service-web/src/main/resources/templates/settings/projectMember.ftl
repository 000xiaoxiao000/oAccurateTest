<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-成员管理</title>
    <#include "../common.ftl">
</head>
<body>

<!--头部菜单 引入-->
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui small breadcrumb" style="margin: 5px">
    <a class="section" href="/p/${project.id}/home">${project.name}</a>
    <span class="divider">/</span>
    <a class="section" href="/p/${project.id}/usecase/list">用例中心</a>
    <span class="divider">/</span>
    <div class="active section">设置</div>
</div>

<!--内容主体-->
<div id="center-content" class="ui grid attached  container" style="margin-top: 5px">
    <!-- 左边导航菜单 -->
    <div class="ui four wide column">
        <#assign settingsMemberActive="active"/>
        <#assign memberItemActive="active"/>
        <#assign loginRole=loginNameRole />
        <#include "LeftNavigationMenu.ftl">
    </div>
    <!-- 中间内容 -->
    <div class="ui twelve wide column">

        <h4 class="ui top attached block header">项目成员列表</h4>
        <div class="ui attached segment" style="padding: 0px">
            <table class="ui table" style="border: none;">
                <tbody>
                <#list members as member>
                    <tr>
                        <td class="center aligned">
                            ${member.memberName}
                        </td>
                        <td class="center aligned">
                            <#if member.role=="owner">
                                创建人
                            <#elseif loginNameRole == "visitor">
                                访客
                            <#else>
                                <div class="ui selection dropdown role">
                                    <input type="hidden" name="role" value="${member.role}">
                                    <i class="dropdown icon"></i>
                                    <div class="text">管理员</div>
                                    <div class="menu">
                                        <div class="item" data-value="admin" onclick="updateRole('${member.id}', 'admin')">管理员</div>
                                        <div class="item" data-value="normal" onclick="updateRole('${member.id}', 'normal')">普通成员</div>
                                        <div class="item" data-value="visitor" onclick="updateRole('${member.id}', 'visitor')">访客</div>
                                    </div>
                                </div>
                            </#if>
                        </td>
                        <#if loginNameRole == "visitor">
                            <td class="right aligned"></td>
                        <#else>
                            <td class="right aligned">
                                <button class="ui negative button" onclick="removeMember('${member.id}')">移除</button>
                            </td>
                        </#if>
                    </tr>
                </#list>
                </tbody>

                <tfoot>
                <tr>
                    <#if loginNameRole != "visitor">
                        <th colspan="3">
                            <div id="addMemberArea">
                                <div class="ui search multiple  selection  dropdown">
                                    <input type="hidden" name="ids" id="userIds">
                                    <i class="dropdown icon"></i>
                                    <div class="default text">请选择用户</div>
                                    <div class="menu">
                                        <#list users as user>
                                            <div class="item" data-value="${user.id}">${user.name}</div>
                                        </#list>
                                    </div>
                                </div>
                                <div class="ui positive button" onclick="addMembers()"> 添加成员</div>
                            </div>
                        </th>
                    </#if>
                </tr>
                </tfoot>
            </table>
        </div>
    </div>
</div>

<script>
    function updateRole(memberId, role) {
        $.post('updateRole', {projectMemberId: memberId, role: role}, function(res) {
            if (res.success || res.result) {
                showToast(res.message || '修改成功', 'success');
                setTimeout(function() { location.reload(); }, 1000);
            } else {
                showToast(res.message || '修改失败', 'error');
            }
        });
    }

    function removeMember(memberId) {
        $.post('delete', {projectMemberId: memberId}, function(res) {
            if (res.success || res.result) {
                showToast(res.message || '移除成功', 'success');
                setTimeout(function() { location.reload(); }, 1000);
            } else {
                showToast(res.message || '移除失败', 'error');
            }
        });
    }

    function addMembers() {
        var ids = $('#userIds').val();
        if (!ids) {
            showToast('请选择要添加的用户', 'warning');
            return;
        }
        $.post('add', {ids: ids}, function(res) {
            if (res.success || res.result) {
                showToast(res.message || '添加成功', 'success');
                setTimeout(function() { location.reload(); }, 1000);
            } else {
                showToast(res.message || '添加失败', 'error');
            }
        });
    }

    $('#center-content .ui.dropdown').dropdown({
        on: 'hover'
    });
    /* $('#center-content .ui.filter.dropdown').dropdown({
         on: 'click'
     });*/
    $('#center-content .ui.role.dropdown').dropdown({
        on: 'click'
    });
</script>
</body>
</html>

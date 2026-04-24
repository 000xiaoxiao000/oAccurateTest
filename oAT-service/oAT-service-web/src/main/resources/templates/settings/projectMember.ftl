<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-成员管理</title>
    <#include "../common.ftl">
    <style>
        .project-settings-page {
            margin-top: 18px;
            margin-bottom: 42px;
        }

        .project-settings-breadcrumb {
            margin: 6px auto 18px !important;
            color: #6b7785;
        }

        .project-settings-layout {
            display: grid;
            grid-template-columns: 280px minmax(0, 1fr);
            gap: 20px;
            align-items: start;
        }

        .project-settings-side,
        .project-settings-main {
            background: #fff;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
        }

        .project-settings-side {
            padding: 18px;
        }

        .project-settings-main {
            overflow: hidden;
        }

        .project-settings-hero {
            padding: 26px 28px;
            background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 55%, #f9fbfd 100%);
            border-bottom: 1px solid #e6eef7;
        }

        .project-settings-hero-label {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.08);
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.04em;
            margin-bottom: 14px;
        }

        .project-settings-hero-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 28px;
            font-weight: 700;
        }

        .project-settings-hero-desc {
            margin: 0;
            color: #617080;
            line-height: 1.8;
            max-width: 780px;
        }

        .project-settings-meta {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 14px;
            margin-top: 20px;
        }

        .project-settings-meta-card {
            background: rgba(255, 255, 255, 0.82);
            border: 1px solid #e4edf7;
            border-radius: 14px;
            padding: 14px 16px;
        }

        .project-settings-meta-label {
            color: #8a97a6;
            font-size: 12px;
            margin-bottom: 6px;
        }

        .project-settings-meta-value {
            color: #1f2937;
            font-size: 18px;
            font-weight: 700;
            line-height: 1.4;
            word-break: break-word;
        }

        .project-settings-content {
            padding: 28px;
        }

        .project-settings-section-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-section-desc {
            margin: 0 0 24px;
            color: #6b7785;
            line-height: 1.75;
        }

        .project-member-table-wrap {
            border: 1px solid #e7edf5;
            border-radius: 16px;
            overflow: hidden;
            background: #fff;
        }

        .project-member-table-wrap .ui.table {
            margin: 0;
            border: none;
        }

        .project-member-table-wrap .ui.table tbody td,
        .project-member-table-wrap .ui.table tfoot th {
            padding-top: 16px;
            padding-bottom: 16px;
            vertical-align: middle;
        }

        .project-member-name {
            font-weight: 600;
            color: #1f2937;
        }

        .project-member-role-badge {
            display: inline-flex;
            align-items: center;
            padding: 6px 10px;
            border-radius: 999px;
            font-size: 12px;
            font-weight: 700;
            background: #eef2ff;
            color: #4338ca;
        }

        .project-member-footer {
            display: flex;
            gap: 12px;
            align-items: center;
            flex-wrap: wrap;
        }

        .project-member-footer .ui.dropdown {
            min-width: 280px;
        }

        .project-member-footer .ui.button {
            border-radius: 10px;
        }

        @media only screen and (max-width: 960px) {
            .project-settings-layout {
                grid-template-columns: 1fr;
            }
        }

        @media only screen and (max-width: 767px) {
            .project-settings-hero,
            .project-settings-side,
            .project-settings-content {
                padding: 22px 20px !important;
            }

            .project-member-footer {
                flex-direction: column;
                align-items: stretch;
            }

            .project-member-footer .ui.dropdown,
            .project-member-footer .ui.button {
                width: 100%;
            }
        }
    </style>
</head>
<body>

<!--头部菜单 引入-->
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <div class="active section">成员管理</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsMemberActive="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="users icon"></i>
                    团队协作管理
                </div>
                <h1 class="project-settings-hero-title">项目成员列表</h1>
                <p class="project-settings-hero-desc">
                    查看当前项目成员及其角色权限，按需添加成员、调整协作角色，保持项目权限边界清晰可控。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">成员数量</div>
                        <div class="project-settings-meta-value">${members?size}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">可添加用户数</div>
                        <div class="project-settings-meta-value">${users?size}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">当前权限</div>
                        <div class="project-settings-meta-value"><#if loginNameRole == "visitor">访客<#else>${loginNameRole}</#if></div>
                    </div>
                </div>
            </div>

            <div class="project-settings-content">
                <h2 class="project-settings-section-title">成员与权限</h2>
                <p class="project-settings-section-desc">创建人始终保留最高权限。非访客角色可调整成员权限，并将新成员加入当前项目。</p>

                <div class="project-member-table-wrap">
                    <table class="ui table">
                        <tbody>
                        <#list members as member>
                            <tr>
                                <td class="center aligned">
                                    <span class="project-member-name">${member.memberName}</span>
                                </td>
                                <td class="center aligned">
                                    <#if member.role=="owner">
                                        <span class="project-member-role-badge">创建人</span>
                                    <#elseif loginNameRole == "visitor">
                                        <span class="project-member-role-badge">访客</span>
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
                                    <div id="addMemberArea" class="project-member-footer">
                                        <div class="ui search multiple selection dropdown">
                                            <input type="hidden" name="ids" id="userIds">
                                            <i class="dropdown icon"></i>
                                            <div class="default text">请选择用户</div>
                                            <div class="menu">
                                                <#list users as user>
                                                    <div class="item" data-value="${user.id}">${user.name}</div>
                                                </#list>
                                            </div>
                                        </div>
                                        <div class="ui positive button" onclick="addMembers()">添加成员</div>
                                    </div>
                                </th>
                            </#if>
                        </tr>
                        </tfoot>
                    </table>
                </div>
            </div>
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

    $('.project-settings-page .ui.dropdown').dropdown({
        on: 'hover'
    });
    $('#center-content .ui.role.dropdown, .project-settings-page .ui.role.dropdown').dropdown({
        on: 'click'
    });
</script>
</body>
</html>

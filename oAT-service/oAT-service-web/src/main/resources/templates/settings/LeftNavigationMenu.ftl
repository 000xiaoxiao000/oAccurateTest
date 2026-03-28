<div class="ui vertical menu">
    <h4 class="ui item header">项目设置</h4>
    <a class="item ${editItemActive!}" href="/p/${project.id}/edit">
        基本信息
    </a>
    <a class="item ${labelItemActive!}" href="/p/${project.id}/label">
        标签管理
    </a>
    <a class="item ${appListItemActive!}" href="/p/${project.id}/app/list">
        应用列表
    </a>
    <a class="item ${onlineItemActive!}" href="/p/${project.id}/app/online">
        在线应用
    </a>
    <a class="item teal ${manageAppCodeActive!}" href="/p/${project.id}/manageAppCode">
        应用与代码管理
    </a>
    <a class="item teal ${memberItemActive!}" href="/p/${project.id}/member/list">
        成员管理
    </a>
    <#if loginRole != "visitor">
        <a class="red item ${deleteItemActive!}" href="/p/${project.id}/delete">
            删除项目
        </a>
    </#if>
</div>

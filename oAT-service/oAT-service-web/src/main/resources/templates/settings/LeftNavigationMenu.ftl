<style>
    .settings-nav.ui.vertical.menu {
        border-radius: 16px;
        overflow: hidden;
        box-shadow: none;
        border: none;
        width: 100%;
    }

    .settings-nav .section-title.item {
        background: linear-gradient(135deg, #f8fafc 0%, #eef6f6 100%);
        color: #0f172a;
        font-weight: 700;
        letter-spacing: 0.02em;
        padding-top: 14px;
        padding-bottom: 14px;
    }

    .settings-nav .top-level.item {
        font-weight: 600;
        border-radius: 10px;
        margin: 4px 10px;
        width: calc(100% - 20px);
        color: #334155;
    }

    .settings-nav .top-level.item:hover {
        background: #f8fbfd;
    }

    .settings-nav .subnav-group.item {
        padding-top: 12px;
        padding-bottom: 12px;
        background: #fcfefe;
        margin: 6px 10px 10px;
        border-radius: 14px;
        width: calc(100% - 20px);
    }

    .settings-nav .subnav-label {
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: #6b7280;
        margin-bottom: 10px;
    }

    .settings-nav .subnav-menu {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .settings-nav .subnav-item {
        margin-left: 0;
        padding: 10px 12px !important;
        border-left: 3px solid transparent;
        border-radius: 10px;
        color: #475569;
    }

    .settings-nav .subnav-item:hover {
        background: #f8fbfd;
    }

    .settings-nav .subnav-item.active,
    .settings-nav .top-level.item.active {
        background: rgba(0, 181, 173, 0.1);
        border-left-color: #00b5ad;
        color: #007a74;
        font-weight: 700;
    }

    .settings-nav .subnav-item.red.active {
        background: rgba(219, 40, 40, 0.08);
        border-left-color: #db2828;
        color: #b42318;
    }
</style>

<div class="ui vertical menu settings-nav">
    <div class="section-title item">设置导航</div>
    <a class="top-level item ${usecaseMenuActive!}" href="/p/${project.id}/usecase/list">
        用例中心
    </a>
    <#if defaultApp??>
        <a class="top-level item ${snapshotMenuActive!}" href="/p/${project.id}/${defaultApp.id}/snapshot/list">
            系统快照
        </a>
    </#if>
    <div class="subnav-group item">
        <div class="subnav-label">设置</div>
        <div class="subnav-menu">
            <a class="item subnav-item ${settingsBasicActive!}" href="/p/${project.id}/edit">基本信息</a>
            <a class="item subnav-item ${settingsLabelActive!}" href="/p/${project.id}/label">标签管理</a>
            <a class="item subnav-item ${settingsMemberActive!}" href="/p/${project.id}/member/list">成员管理</a>
            <a class="item subnav-item ${settingsAppActive!}" href="/p/${project.id}/app/list">应用列表</a>
            <a class="item subnav-item ${settingsManageCodeActive!}" href="/p/${project.id}/manageAppCode">应用与代码管理</a>
            <a class="item subnav-item ${settingsOnlineActive!}" href="/p/${project.id}/app/online">在线应用</a>
            <#if loginRole != "visitor">
                <a class="red item subnav-item ${settingsDeleteActive!}" href="/p/${project.id}/delete">删除项目</a>
            </#if>
        </div>
    </div>
</div>

<style>
    .snapshot-nav.ui.vertical.menu {
        border-radius: 12px;
        overflow: hidden;
        box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
    }

    .snapshot-nav .section-title.item {
        background: linear-gradient(135deg, #f8fafc 0%, #eef6f6 100%);
        color: #0f172a;
        font-weight: 700;
        letter-spacing: 0.02em;
    }

    .snapshot-nav .top-level.item {
        font-weight: 600;
    }

    .snapshot-nav .subnav-group.item {
        padding-top: 12px;
        padding-bottom: 12px;
        background: #fcfefe;
    }

    .snapshot-nav .subnav-label {
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: #6b7280;
        margin-bottom: 10px;
    }

    .snapshot-nav .subnav-menu {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .snapshot-nav .subnav-item {
        margin-left: 10px;
        padding: 9px 12px !important;
        border-left: 3px solid transparent;
        border-radius: 8px;
        color: #475569;
    }

    .snapshot-nav .subnav-item.active {
        background: rgba(0, 181, 173, 0.1);
        border-left-color: #00b5ad;
        color: #007a74;
        font-weight: 700;
    }
</style>

<div class="ui vertical menu snapshot-nav">
    <div class="section-title item">快照导航</div>
    <a class="top-level item ${usecaseMenuActive!}" href="/p/${project.id}/usecase/list">
        用例中心
    </a>
    <div class="subnav-group item">
        <div class="subnav-label">${snapshotGroupName}</div>
        <div class="subnav-menu">
            <a class="item subnav-item ${systemSnapshotListActive!}" href="${snapshotListHref}">我的快照</a>
            <#if snapshotDetailHref??>
                <a class="item subnav-item ${snapshotDetailActive!}" href="${snapshotDetailHref}">详情视图</a>
            </#if>
        </div>
    </div>
    <a class="top-level item ${settingsMenuActive!}" href="${settingsHref}">
        设置
    </a>
</div>

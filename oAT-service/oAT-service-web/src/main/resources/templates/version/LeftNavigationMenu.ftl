<style>
    .version-nav.ui.vertical.menu {
        border-radius: 12px;
        overflow: hidden;
        box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
    }

    .version-nav .section-title.item {
        background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
        color: #0f172a;
        font-weight: 700;
        letter-spacing: 0.02em;
    }

    .version-nav .top-level.item {
        font-weight: 600;
    }

    .version-nav .subnav-group.item {
        padding-top: 12px;
        padding-bottom: 12px;
        background: #fcfcff;
    }

    .version-nav .subnav-label {
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: #6b7280;
        margin-bottom: 10px;
    }

    .version-nav .subnav-menu {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .version-nav .subnav-item {
        margin-left: 10px;
        padding: 9px 12px !important;
        border-left: 3px solid transparent;
        border-radius: 8px;
        color: #475569;
    }

    .version-nav .subnav-item.active {
        background: rgba(33, 133, 208, 0.1);
        border-left-color: #2185d0;
        color: #1678c2;
        font-weight: 700;
    }
</style>

<div class="ui vertical menu version-nav">
    <div class="section-title item">版本导航</div>
    <div class="subnav-group item">
        <div class="subnav-label">${appName}</div>
        <div class="subnav-menu">
            <a class="item subnav-item ${versionListActive!}" href="/p/${project.id}/${appId}/version/list">版本列表</a>
            <a class="item subnav-item ${reportListActive!}" href="/p/${project.id}/${appId}/version/report/list">报告列表</a>
            <a class="item subnav-item ${versionCompareActive!}" href="/p/${project.id}/${appId}/version/compare">版本比对</a>
            <a class="item subnav-item ${apiEndpointActive!}" href="/p/${project.id}/app/${appId}/api-endpoints">接口扫描与覆盖</a>
        </div>
    </div>
</div>

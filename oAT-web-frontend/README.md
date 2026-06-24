# oAT-web-frontend

中文 | [English](#english)

oAT-web-frontend 是 oAccurateTest 平台的前端界面，基于 Vue 3 + TypeScript 构建，提供覆盖率分析、系统快照、链路图谱、版本管理、用例中心、AI 对话等功能的 Web 操作界面。

---

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue 3 | 3.5.x | 前端框架 |
| TypeScript | 5.9.x | 类型系统 |
| Vite | 7.x | 构建工具与开发服务器 |
| Vue Router | 4.x | 前端路由 |
| Pinia | 3.x | 全局状态管理 |

---

## 目录结构

```
oAT-web-frontend/src/
├── api/
│   ├── bootstrap.ts      # API 初始化（baseURL、全局拦截器等）
│   ├── http.ts           # 封装的 HTTP 请求方法
│   └── types.ts          # API 请求/响应类型定义
├── components/
│   ├── map/
│   │   └── RelationBoard.vue         # 代码调用关系图谱
│   ├── snapshot/
│   │   ├── CoverageCodeView.vue      # 覆盖率源码着色视图
│   │   ├── CoverageReportView.vue    # 覆盖率报告展示
│   │   ├── GraphNodeDetailCard.vue   # 图谱节点详情卡片
│   │   └── GraphView.vue             # 链路/调用图谱视图
│   ├── usecase/
│   │   └── UsecasePicker.vue         # 用例选择器
│   ├── AiFloatingAssistant.vue       # AI 浮动对话助手
│   ├── AppBreadcrumbs.vue            # 全局面包屑导航
│   ├── AppDialogHost.vue             # 全局弹窗宿主
│   ├── AppPagination.vue             # 分页组件
│   ├── AppToastHost.vue              # 全局 Toast 通知
│   ├── GlobalProbeAlertToasts.vue    # 探针告警通知
│   └── MascotCanvas.vue             # 吉祥物画布
├── composables/
│   ├── useDialog.ts      # 弹窗管理 Composable
│   └── useToast.ts       # Toast 通知 Composable
├── layouts/
│   └── AppShell.vue      # 全局布局（侧边栏、顶栏、内容区）
├── pages/                # 页面组件（路由一一对应）
├── router/
│   └── index.ts          # 路由配置
├── stores/
│   ├── auth.ts           # 用户认证状态
│   └── project.ts        # 当前项目状态
└── utils/
    ├── markdown.ts       # Markdown 渲染工具
    └── snapshot.ts       # 快照相关工具函数
```

---

## 页面说明

### 项目与应用

| 页面 | 功能 |
|---|---|
| `ProjectsPage` | 项目列表 |
| `ProjectHomePage` | 项目首页（概览） |
| `ProjectAppsPage` | 项目下的应用列表 |
| `ProjectMembersPage` | 项目成员管理 |
| `ProjectLabelsPage` | 标签管理 |
| `AppSettingsPage` | 应用配置（Git 仓库、appKey 等） |
| `RepositoryConfigPage` | Git 仓库详细配置 |
| `OnlineAppsPage` | 在线应用（Agent 心跳监控） |
| `MonitorPage` | 实时链路监控 |
| `ProbeAlertsPage` | 探针告警事件 |

### 快照

| 页面 | 功能 |
|---|---|
| `SystemSnapshotListPage` | 系统快照列表 |
| `SystemSnapshotDetailPage` | 系统快照详情 |
| `SystemSnapshotGraphPage` | 系统快照链路图谱 |
| `SystemSnapshotReportPage` | 系统快照覆盖率报告 |
| `SystemSnapshotCodePage` | 系统快照源码着色 |
| `MySnapshotListPage` | 个人快照列表 |
| `MySnapshotDetailPage` | 个人快照详情 |
| `MySnapshotGraphPage` | 个人快照链路图谱 |
| `MySnapshotReportPage` | 个人快照覆盖率报告 |
| `MySnapshotCodePage` | 个人快照源码着色 |
| `MySnapshotAggregateCodePage` | 聚合快照源码视图 |
| `MySnapshotCodeReportPage` | 个人快照代码报告 |
| `ShareSnapshotPage` | 快照分享页 |

### 覆盖率

| 页面 | 功能 |
|---|---|
| `CoverageHubPage` | 覆盖率中心（报告列表） |
| `CoverageOverviewPage` | 覆盖率报告概览 |
| `CoverageDetailsPage` | 覆盖率详情（类/方法明细） |
| `CoverageCodePage` | 覆盖率源码着色 |
| `VersionReportDetailPage` | 版本覆盖率报告详情 |

覆盖率中心可展示 Java Agent 快照覆盖率、前端 Istanbul 覆盖率以及 Go / Python / C/C++ 通用覆盖率报告；非 Java 覆盖率由后端 `/coverage/frontend` 与 `/coverage/universal/{CPP|GO|PYTHON}` 接口生成。

### 版本管理

| 页面 | 功能 |
|---|---|
| `VersionListPage` | 版本列表 |
| `VersionCreatePage` | 创建版本 |
| `VersionAppsPage` | 版本关联应用 |
| `VersionComparePage` | 版本差异对比 |

### 用例中心

| 页面 | 功能 |
|---|---|
| `UsecaseListPage` | 用例列表 |
| `UsecaseDetailPage` | 用例详情 |
| `UsecaseEditorPage` | 用例编辑 |
| `ShareUsecasePage` | 用例分享页 |

### 其他

| 页面 | 功能 |
|---|---|
| `SearchCenterPage` | 全局搜索（链路、快照、用例） |
| `ApiEndpointsPage` | API 接口列表与覆盖分析 |
| `MapHomePage` / `MapAppPage` / `MapCodePage` | 代码调用关系图谱 |
| `ProjectAiPage` | AI 智能分析对话页 |
| `AccountSettingsPage` | 账号设置 |
| `LoginPage` | 登录 / 注册页（`/login` 与 `/register` 共用） |

---

## 开发环境

### 安装依赖

```bash
cd oAT-web-frontend
npm install
```

### 启动开发服务器

```bash
npm run dev
```

默认运行在 `http://localhost:5173`，请求通过 Vite 代理转发到后端（`http://localhost:8899`）。

> 代理配置在 `vite.config.ts` 中，按需修改后端地址。

### 类型检查

```bash
npm run typecheck
```

---

## 构建生产包

```bash
npm run build
```

产出物在 `dist/` 目录，将其部署到 Web 服务器或通过 `oAT-service-web` 的静态资源服务托管。

### 本地预览构建结果

```bash
npm run preview
```

---

## 与后端集成

前端通过 `src/api/http.ts` 中封装的 HTTP 客户端与 `oAT-service-web` 通信，所有接口均以 `/api/` 为前缀。

后端地址在两处配置：

- **开发环境**：`vite.config.ts` 中的 `server.proxy`
- **生产环境**：由部署时的反向代理（Nginx 等）或 `oAT-service-web` 的静态托管配置决定

如果前端请求需经过 `oAT-relay`，将开发代理或生产反向代理的后端目标改为 relay 地址，例如 `http://127.0.0.1:18089`。

---

## 注意事项

- Node.js 版本建议 18+，低版本可能与 Vite 7.x 不兼容。
- 生产部署时前端静态文件可以直接由 `oAT-service-web`（Spring Boot）托管，将 `dist/` 内容放入 war 包的 `static/` 目录即可，无需单独的 Web 服务器。
- `stores/auth.ts` 管理登录状态，Token 存储在 `localStorage`，刷新后自动恢复会话。

---

## English

[中文](#oat-web-frontend) | English

`oAT-web-frontend` is the Web UI for oAccurateTest. It is built with Vue 3 and TypeScript and provides pages for coverage analysis, system snapshots, trace graphs, version management, use cases, AI chat, and related workflows.

## Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Vue 3 | 3.5.x | Frontend framework |
| TypeScript | 5.9.x | Type system |
| Vite | 7.x | Build tool and dev server |
| Vue Router | 4.x | Routing |
| Pinia | 3.x | Global state |

## Directory Structure

```text
oAT-web-frontend/src/
├── api/          # HTTP client, bootstrap, request/response types
├── components/   # Shared UI, graph, snapshot, use case, AI assistant components
├── composables/  # Dialog and toast composables
├── layouts/      # App shell
├── pages/        # Route-level pages
├── router/       # Router configuration
├── stores/       # Auth and project state
└── utils/        # Markdown and snapshot helpers
```

## Main Pages

- Project and app management: projects, project home, apps, members, labels, app settings, repository config, online apps, monitor, and probe alerts.
- Snapshots: system snapshots, personal snapshots, graph views, coverage reports, code views, aggregate code views, and share pages.
- Coverage: coverage hub, overview, details, source highlighting, and version report details.
- Version management: version list, creation, app association, and version comparison.
- Use case center: use case list, details, editor, and share page.
- Other pages: global search, API endpoints, code relation graphs, project AI chat, account settings, login, and registration.

The coverage center supports Java Agent snapshot coverage, frontend Istanbul coverage, and Go / Python / C/C++ universal coverage reports. Non-Java reports are generated by backend `/coverage/frontend` and `/coverage/universal/{CPP|GO|PYTHON}` APIs.

## Development

### Install Dependencies

```bash
cd oAT-web-frontend
npm install
```

### Start Dev Server

```bash
npm run dev
```

The default URL is `http://localhost:5173`. API requests are forwarded by the Vite proxy to `http://localhost:8899`.

If frontend API requests need to go through `oAT-relay`, point the Vite proxy or production reverse proxy to the relay address, for example `http://127.0.0.1:18089`.

### Type Check

```bash
npm run typecheck
```

## Production Build

```bash
npm run build
```

The output is written to `dist/`. Deploy it to a Web server or serve it through `oAT-service-web` static resources.

Preview the production build locally:

```bash
npm run preview
```

## Backend Integration

The frontend talks to `oAT-service-web` through the HTTP client in `src/api/http.ts`. All API paths use the `/api/` prefix.

Backend addresses are configured in two places:

- Development: `server.proxy` in `vite.config.ts`
- Production: reverse proxy configuration or `oAT-service-web` static hosting

## Notes

- Node.js 18+ is recommended. Older versions may be incompatible with Vite 7.x.
- In production, static files can be served directly by `oAT-service-web`; put `dist/` contents under the WAR `static/` directory when using that deployment mode.
- `stores/auth.ts` manages login state. Tokens are stored in `localStorage` and restored after refresh.

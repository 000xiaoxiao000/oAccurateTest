# oAT-web-frontend

`oAT-web-frontend` 是 oAccurateTest 的 Web 操作界面，基于 Vue 3、TypeScript、Vite、Pinia 和 Vue Router 构建。它面向精准测试日常工作流，提供项目应用管理、探针监控、快照、覆盖率、版本、用例、代码图谱、搜索、质量门禁和 AI 智能分析页面。

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue | 3.5.x | UI 框架 |
| TypeScript | 5.9.x | 类型系统 |
| Vite | 7.x | 开发服务器和构建 |
| Vue Router | 4.x | 路由 |
| Pinia | 3.x | 状态管理 |

## 目录结构

```text
oAT-web-frontend/
├── src/
│   ├── api/          # HTTP 客户端、后端 API 封装、接口类型
│   ├── components/   # 全局组件、图谱、快照、用例、AI 浮动助手
│   ├── composables/  # Dialog、Toast 等组合式函数
│   ├── entities/     # 领域模型，当前包含 coverage model
│   ├── features/     # 业务特性组件与配置，AI prompt、应用语言配置等
│   ├── layouts/      # AppShell 全局布局
│   ├── pages/        # 路由页面
│   ├── router/       # 路由与登录守卫
│   ├── shared/       # 共享组件、composable 和分层说明
│   ├── stores/       # auth、project 等 Pinia store
│   └── utils/        # Markdown、快照等工具
├── vite.config.ts
├── package.json
└── dist/
```

## 页面能力

| 分类 | 页面 |
|---|---|
| 账号 | 登录、注册、账号设置。 |
| 项目 | 项目列表、项目首页、成员、标签。 |
| 应用 | 应用列表、应用设置、语言配置、仓库配置、API 端点、探针告警、在线应用。 |
| 监控 | 实时链路、链路详情、保存系统快照、保存个人快照。 |
| 版本 | 应用版本列表、创建版本、当前版本、Git 拉取、版本对比、版本报告详情。 |
| 覆盖率 | 覆盖率中心、报告概览、类/方法明细、源码着色、footprint、趋势、导出。 |
| 多语言覆盖率 | Java、前端 JS/TS、Go、Python、C/C++ 应用配置与覆盖率报告。 |
| 质量 | 质量门禁 CI、测试缺口、测试影响分析入口。 |
| 快照 | 系统快照、个人快照、快照报告、代码报告、链路图谱、分享页。 |
| 用例 | 用例列表、详情、编辑、目录、导入导出、分享、快照/覆盖率关联。 |
| 图谱与搜索 | 项目图谱、应用图谱、代码图谱、全局搜索、表关系搜索。 |
| AI | 项目 AI 工作台、全局浮动助手、页面上下文问答、图片/附件输入、反馈与学习状态。 |

## 路由概览

公开路由：

```text
/login
/register
/share/snapshot/:snapshotId
/share/usecase/:usecaseId
```

登录后主路由：

```text
/projects
/account
/p/:projectId/home
/p/:projectId/ai
/p/:projectId/apps
/p/:projectId/apps/online
/p/:projectId/apps/:appId/settings
/p/:projectId/apps/:appId/repository
/p/:projectId/apps/:appId/api-endpoints
/p/:projectId/apps/:appId/probe-alerts
/p/:projectId/monitor
/p/:projectId/version/apps
/p/:projectId/apps/:appId/versions
/p/:projectId/apps/:appId/compare
/p/:projectId/version/reports/:reportId
/p/:projectId/coverage
/p/:projectId/quality-gate-ci
/p/:projectId/apps/:appId/coverage
/p/:projectId/apps/:appId/coverage/details
/p/:projectId/apps/:appId/coverage/code
/p/:projectId/map/home
/p/:projectId/map/app/:appId
/p/:projectId/map/code
/p/:projectId/search
/p/:projectId/apps/:appId/snapshots
/p/:projectId/my-snapshots
/p/:projectId/usecases
/p/:projectId/members
/p/:projectId/labels
```

路由守卫位于 `src/router/index.ts`，通过 `authStore.ensureLoaded()` 校验登录态；后端返回 `401` 时会跳转到 `/login?redirect=...`。

## API 与状态

HTTP 客户端位于 `src/api/http.ts`：

- 默认使用相对路径请求后端。
- 可通过 `VITE_OAT_BACKEND_BASE_URL` 指定完整后端地址。
- 请求默认携带 `credentials: 'include'`。
- 统一处理 `ApiResponse<T>`、`401` 登录跳转和后端错误消息。

主要 API 文件：

| 文件 | 说明 |
|---|---|
| `src/api/bootstrap.ts` | 账号、项目、应用、监控、图谱、搜索、用例、AI 等主接口。 |
| `src/api/coverage.ts` | 兼容覆盖率接口、报告生成、趋势、footprint 和源码查询。 |
| `src/api/version.ts` | 版本中心、Git 拉取、Commit、对比任务和版本报告。 |
| `src/api/snapshot.ts` | 系统快照、个人快照、图谱、代码报告和用例绑定。 |
| `src/api/types.ts` | 主业务接口类型。 |
| `src/api/types-coverage-runtime.ts` | 覆盖率运行时相关类型。 |

Pinia store：

| Store | 说明 |
|---|---|
| `stores/auth.ts` | 当前用户、登录、退出、登录态恢复。 |
| `stores/project.ts` | 项目、应用、成员、标签、快照、用例、AI、探针、仓库等页面缓存和操作。 |

## 应用语言配置

应用设置页支持多语言采集配置，配置定义在 `src/features/app/languageProfiles.ts`：

| 语言 | 采集类型 | 说明 |
|---|---|---|
| `JAVA` | `RESIDENT` | Java Agent 常驻心跳，支持实时链路、Sandbox 和探针告警。 |
| `FRONTEND` | `BATCH` | 前端 Istanbul 覆盖率批量上报，支持 SourceMap 和静默阈值。 |
| `GO` | `BATCH` | Go coverage / LCOV 文件或 SDK 上报。 |
| `PYTHON` | `BATCH` | coverage.py JSON / LCOV 文件或 SDK 上报。 |
| `CPP` | `BATCH` | LCOV / gcov 文件或 SDK 上报。 |

## AI 工作台

AI 相关代码：

```text
src/components/AiFloatingAssistant.vue
src/pages/ProjectAiPage.vue
src/features/ai/toolPrompts.ts
src/features/ai/types.ts
```

能力包括：

- 项目级 AI 工作台。
- 全局浮动助手。
- 页面上下文问答。
- 图片和附件输入。
- 会话状态保存和清理。
- AI 反馈、统计和学习建议。
- 内置快捷 Prompt：项目概览、应用状态、覆盖率概览、测试推荐、覆盖率工作流、最近调用链、异常根因、代码关系、Bug 检测。

后端接口前缀：

```text
/api/projects/{projectId}/ai/*
/api/ai/feedback/*
```

## 安装依赖

```bash
cd oAT-web-frontend
npm install
```

建议使用 Node.js 18+。

## 开发运行

```bash
npm run dev
```

默认开发地址：

```text
http://localhost:5176
```

开发代理默认转发到：

```text
http://localhost:8899
```

通过环境变量切换后端：

```bash
OAT_BACKEND_TARGET=http://127.0.0.1:18089 npm run dev
```

如果不使用 Vite 代理，也可以让前端直接请求完整后端地址：

```bash
VITE_OAT_BACKEND_BASE_URL=http://127.0.0.1:8899 npm run dev
```

## 类型检查与构建

```bash
npm run typecheck
npm run build
```

`npm run build` 会先执行 `vue-tsc --noEmit`，类型错误会导致构建失败。

构建产物：

```text
dist/
```

本地预览：

```bash
npm run preview
```

预览服务默认端口同样是：

```text
http://localhost:5176
```

## Vite 代理说明

`vite.config.ts` 中的代理覆盖：

- `/api`
- `/share/api`
- 兼容旧后端路径的 `/p/.../coverage/*`、`/p/.../monitor/*`、`/p/.../map/*`、`/p/.../version/*`
- `/css`、`/js`、`/images`、`/user`、`/r`

浏览器页面导航由 Vue Router 接管；API、表单提交、下载和静态资源请求按规则代理到后端。`/assets/*` 在开发环境会优先尝试从 `dist/assets` 回退读取，便于本地调试构建产物。

## 后端集成

后端服务为 `oAT-service-web`，默认地址：

```text
http://127.0.0.1:8899
```

如通过 `oAT-relay` 中继，开发时可设置：

```bash
OAT_BACKEND_TARGET=http://127.0.0.1:18089 npm run dev
```

生产部署时可以选择：

| 方式 | 说明 |
|---|---|
| Nginx/网关 | 部署 `dist/` 静态文件，并把 `/api`、`/share/api` 等路径反向代理到后端。 |
| 后端托管 | 将 `dist/` 内容放入后端静态资源发布流程。 |
| 独立静态站点 | 使用 `VITE_OAT_BACKEND_BASE_URL` 指向完整后端地址，并处理跨域与 Cookie 策略。 |

## 注意事项

- 开发端口是 `5176`，不是 Vite 默认 `5173`。
- 登录态依赖后端 Cookie，会随请求自动携带。
- `VITE_OAT_BACKEND_BASE_URL` 会绕过相对路径代理，请确认后端 CORS 和 Cookie 策略。
- 构建输出设置为稳定文件名，部署时注意浏览器缓存策略；HTML 建议 `no-store`。
- 前端覆盖率上送需要业务前端项目完成 Istanbul 插桩，采集脚本见 `../oAT-traffic-capture/sdk/oat-coverage-reporter.ts`。

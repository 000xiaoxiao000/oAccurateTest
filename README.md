# oAccurateTest (oAT)

oAccurateTest 是一个面向 Java 应用的智能测试分析平台。通过 JavaAgent 无侵入地采集运行时链路数据，结合静态代码分析与 AI 能力，为测试和研发团队提供覆盖率分析、链路追踪、版本对比和智能诊断。

---

## 整体架构

```
oAccurateTest/
├── oAT-agent/            # 探针模块（Java 8）
│   ├── oAT-client-model/ # Agent 与服务端共用的数据模型
│   ├── oAT-agent-core/   # 字节码增强、链路采集、数据上报
│   └── oAT-agnet-shaded/ # 依赖 shaded 打包
├── oAT-service/          # 服务端模块（Java 17）
│   ├── oAT-ai/           # AI 分析模块（LangChain4j）
│   └── oAT-service-web/  # Web 平台主服务（Spring Boot 3）
├── oAT-web-frontend/     # 前端界面（Vue 3 + TypeScript）
└── README.md
```

### 数据流

```
目标 Java 应用
  └─ oAT-agent（JavaAgent）
       │ HTTP 上报链路 / 快照数据
       ▼
  oAT-service-web（Spring Boot）
       ├─ Elasticsearch  ← 快照、链路、覆盖率、静态源码
       ├─ MySQL          ← 项目、应用、版本、用例等结构化数据
       ├─ Redis          ← 会话、缓存
       ├─ Git 仓库       ← Commit、Diff、源码下载
       └─ oAT-ai        ← AI 工具编排与 LLM 集成
            │
            ▼
  oAT-web-frontend（Vue 3）
```

---

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 探针 | Java、ASM 字节码增强 | Java 8 |
| 服务端框架 | Spring Boot | 3.3.6 |
| 服务端语言 | Java | 17 |
| AI 框架 | LangChain4j | 1.12.2 |
| 存储 - 文档 | Elasticsearch | 7.x / 8.x |
| 存储 - 关系型 | MySQL | 5.7+ / 8.x |
| 存储 - 缓存 | Redis + Redisson | — |
| 代码分析 | JGit、ASM、JavaParser | — |
| 报表导出 | EasyExcel | 3.1.1 |
| 前端框架 | Vue 3 + TypeScript | Vue 3.5 |
| 前端构建 | Vite | 7.x |
| 前端状态 | Pinia | 3.x |

---

## 核心功能

**运行时采集**
Agent 通过字节码增强拦截 HTTP、SQL、Redis、Dubbo、SOFA-RPC、Feign、RocketMQ、Kafka、RabbitMQ 等协议，将链路节点和代码执行栈实时上报至平台。

**系统快照**
每次测试场景执行后，平台将该次请求的完整调用链路沉淀为系统快照，支持场景目录管理、版本归档、图谱可视化。

**覆盖率分析**
基于快照和静态源码结构生成全量或增量覆盖率报告，支持类 / 方法 / 行 / 分支四个维度，提供源码着色视图和 Excel 导出。

**版本管理**
接入 Git 仓库，管理应用的分支、Commit 与版本号，支持跨版本 Diff 和代码变更影响分析。

**用例中心**
管理测试用例目录与详情，支持与快照关联、导入导出，关联缺陷/PRD 链接。

**API 端点分析**
自动识别应用暴露的 HTTP 接口，结合链路数据分析接口覆盖情况。

**探针监控**
实时监控在线 Agent 实例状态，支持探针下线告警和 Webhook 通知。

**AI 智能分析**
基于 LangChain4j 的对话式 AI 助手，内置覆盖率分析、缺陷检测、性能分析、调用链比较、测试推荐等专用工具，支持 OpenAI / Ollama / DeepSeek 等多种模型。

---

## 模块说明

每个模块的详细构建、配置和使用说明见各自的 README：

- `oAT-agent/` → [oAT-agent README](oAT-agent/README.md)
- `oAT-service/oAT-ai/` → [oAT-ai README](oAT-service/oAT-ai/README.md)
- `oAT-service/oAT-service-web/` → [oAT-service-web README](oAT-service/oAT-service-web/README.md)
- `oAT-web-frontend/` → [oAT-web-frontend README](oAT-web-frontend/README.md)

---

## 构建顺序

模块间存在依赖，必须按以下顺序构建：

```bash
# 1. 构建 Agent（含 oAT-client-model，安装到本地 Maven 仓库）
cd oAT-agent
mvn clean install

# 2. 构建 AI 模块
cd ../oAT-service/oAT-ai
mvn clean install

# 3. 构建 Web 服务
cd ../oAT-service-web
mvn clean package

# 4. 构建前端（可选，生产部署时需要）
cd ../../oAT-web-frontend
npm install
npm run build
```

---

## 启动顺序

1. 启动 Elasticsearch
2. 启动 MySQL
3. 启动 Redis
4. 启动 `oAT-service-web`
5. 启动挂载了 Agent 的目标应用

详细配置和启动参数见各模块 README。

---

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK（Agent） | 8+ |
| JDK（Service） | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（前端开发/构建） |
| Elasticsearch | 7.x / 8.x |
| MySQL | 5.7+ / 8.x |
| Redis | 5.x+ |

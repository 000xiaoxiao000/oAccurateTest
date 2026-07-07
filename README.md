# oAccurateTest (oAT)

oAccurateTest 是面向 Java 应用的精准测试与智能分析平台。平台通过 JavaAgent 采集运行时链路和代码执行栈，结合静态源码分析、版本 Diff、覆盖率报告与 AI 工具编排，帮助测试和研发团队定位变更影响、评估覆盖质量、追踪调用链路并沉淀测试资产。

> 使用声明：本项目仅供个人学习、技术研究与交流使用，不得用于商业用途或未经授权的生产环境部署。使用者需自行遵守相关法律法规和第三方组件许可协议。

## 交流与反馈

| 添加作者微信 | 微信交流入口 | AI + 精准测试实战交流群 |
|---|---|---|
| 扫码添加好友，备注 `oAT` / `精准测试`。 | 交流项目使用、部署问题与二次开发思路。 | 讨论 AI 测试分析、覆盖率治理和工程落地。 |
| <img src="docs/assets/wechat-friend.png" alt="添加作者微信二维码" width="220"> | <img src="docs/assets/wechat-contact.jpg" alt="微信交流二维码" width="220"> | <img src="docs/assets/ai-testing-group.png" alt="AI + 精准测试实战交流群二维码" width="220"> |

## 模块总览

```text
oAccurateTest/
├── oAT-agent/            # JavaAgent 探针，采集链路、方法调用、覆盖率 codeNodes
│   ├── oAT-client-model/ # Agent 与服务端共用模型
│   ├── oAT-agent-core/   # 字节码增强、采集、上报
│   └── oAT-agnet-shaded/ # shaded 依赖包
├── oAT-service/
│   ├── oAT-ai/           # LangChain4j AI 分析模块
│   └── oAT-service-web/  # 平台后端主服务
├── oAT-relay/            # HTTP 转发中继
├── oAT-web-frontend/     # Web 前端
├── oAT-traffic-capture/  # Electron 桌面流量采集器和覆盖率中继
└── docs/                 # 文档与图片资源
```

## 数据流

```text
目标 Java 应用
  └─ oAT-agent
       │ 上报链路、快照、代码执行栈
       ▼
  oAT-service-web
       ├─ Elasticsearch：链路、快照、静态源码、报告索引
       ├─ MySQL：项目、应用、版本、用例、成员等结构化数据
       ├─ Redis：会话、缓存、AI 语义缓存
       ├─ MinIO：覆盖率 codeNodes 对象存储
       ├─ Git 仓库：源码、Commit、Diff
       └─ oAT-ai：AI 工具编排与 LLM 调用
       ▼
  oAT-web-frontend

可选入口：
  oAT-relay：在隔离网络中转发 Agent / SDK / 前端请求
  oAT-traffic-capture：采集桌面流量，并中继前端、多语言覆盖率
```

## 核心能力

- 运行时链路采集：支持 HTTP、JDBC、Redis、Feign、Dubbo、SOFA-RPC、RocketMQ、Kafka、RabbitMQ 等常见调用链路。
- 系统快照：将一次测试场景的完整调用链沉淀为快照，支持目录管理、版本归档和图谱查看。
- 覆盖率分析：支持全量和增量报告，提供类、方法、行、分支维度统计、源码着色和 Excel 导出。
- 多语言覆盖率：支持前端 Istanbul、Go、Python、C/C++ 覆盖率上送与报告生成。
- 版本中心：管理应用版本、分支、Commit、Git Diff 和变更影响范围。
- 用例中心：管理用例目录、用例详情、快照关联、缺陷/PRD 链接和导入导出。
- API 端点分析：识别 HTTP 接口并结合链路数据分析接口覆盖情况。
- 探针监控：查看在线 Agent、心跳状态和离线告警。
- AI 智能分析：基于 LangChain4j 和工具调用实现覆盖率分析、缺陷检测、性能分析、链路对比和测试推荐。

## 技术栈

| 层 | 技术 |
|---|---|
| Agent | Java、ASM、JavaAgent，默认 Java 7 字节码，提供 JDK6/7/8 profile |
| 后端 | Java 17、Spring Boot 3.3.6、Spring Data Elasticsearch、Redis、JDBC |
| AI | LangChain4j 1.12.2，支持 OpenAI / DeepSeek / Ollama / 兼容 OpenAI 协议接口 |
| 存储 | Elasticsearch 7.x/8.x、MySQL 5.7+/8.x、Redis、MinIO |
| 前端 | Vue 3、TypeScript、Vite 7、Pinia、Vue Router |
| 桌面端 | Electron 30、Vue 3、Vite 5、SQLite、http-mitm-proxy |

## 环境要求

| 组件 | 建议版本 | 用途 |
|---|---|---|
| JDK | Agent 构建建议 8+；服务端运行 17+ | Java 模块构建与运行 |
| Maven | 3.8+ | Java 模块构建 |
| Node.js | 18+ | 前端和桌面端构建 |
| MySQL | 5.7+ / 8.x | 结构化数据 |
| Elasticsearch | 7.x / 8.x | 快照、链路、报告索引 |
| Redis | 5.x+ | 缓存、会话、AI 语义缓存 |
| MinIO | RELEASE.2023+ | 覆盖率 codeNodes 对象存储 |

## 构建顺序

模块间存在本地 Maven 依赖，建议按以下顺序构建：

```bash
# 1. 构建 Agent，同时安装 oAT-client-model
cd oAT-agent
mvn clean install

# 2. 构建 AI 模块
cd ../oAT-service/oAT-ai
mvn clean install

# 3. 构建后端主服务
cd ../oAT-service-web
mvn clean package

# 4. 构建 HTTP 中继，可选
cd ../../oAT-relay
mvn clean package

# 5. 构建 Web 前端，可选
cd ../oAT-web-frontend
npm install
npm run build

# 6. 构建桌面流量采集器，可选
cd ../oAT-traffic-capture
npm install
npm run build
```

## 启动顺序

1. 启动 MySQL、Elasticsearch、Redis。
2. 如果启用覆盖率对象存储，启动 MinIO。
3. 初始化 MySQL 表结构，脚本位于 `oAT-service/oAT-service-web/src/main/resources/db/mysql/`。
4. 启动 `oAT-service-web`。
5. 按需启动 `oAT-relay`。
6. 启动 `oAT-web-frontend` 或部署前端静态资源。
7. 在目标 Java 应用中挂载 `oAT-agent`。
8. 按需启动 `oAT-traffic-capture` 采集流量或中继覆盖率。

## 模块文档

- [oAT-agent](oAT-agent/README.md)
- [oAT-service](oAT-service/README.md)
- [oAT-service-web](oAT-service/oAT-service-web/README.md)
- [oAT-ai](oAT-service/oAT-ai/README.md)
- [oAT-relay](oAT-relay/README.md)
- [oAT-web-frontend](oAT-web-frontend/README.md)
- [oAT-traffic-capture](oAT-traffic-capture/README.md)
- [多语言覆盖率上送 SDK](oAT-traffic-capture/sdk/coverage/README.md)

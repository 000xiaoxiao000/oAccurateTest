# oAT-service-web 覆盖率报告架构说明（管理视角 + 研发视角）

本文基于 `oAT-service-web` 现有实现，围绕覆盖率报告子域输出双视角架构图。

- 管理视角：业务、产品、应用、功能、信息
- 研发视角：系统、数据、技术、UML 类图、UML 时序图

关键代码参考：
- `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/CoverageControl.java`
- `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/CoverageService.java`
- `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/CoverageServiceImpl.java`
- `oAT-service/oAT-service-web/src/main/java/com/oAT/web/esDao/entity/CoverageReportIndex.java`
- `oAT-service/oAT-service-web/src/main/java/com/oAT/web/esDao/entity/ClassCoverageIndex.java`
- `oAT-web-frontend` 覆盖率概览前端路由
- `oAT-web-frontend` 覆盖率详情前端路由
- `oAT-web-frontend` 覆盖率源码视图前端路由

---

## 一、管理视角

### 1) 业务架构图 - 覆盖率治理业务全景图

```mermaid
flowchart LR
  A[项目成员/测试/研发] --> B[版本中心选择版本和Commit]
  B --> C[覆盖率概览]
  C --> D{报告类型}
  D -->|全量| E[生成全量覆盖率]
  D -->|增量| F[生成增量覆盖率]
  E --> G[基于系统快照聚合执行链路]
  F --> H[基于Git Diff筛选变更类]
  G --> I[产出报告与类明细]
  H --> I
  I --> J[覆盖率详情 列表和树]
  J --> K[源码着色查看]
  I --> L[导出Excel]
  I --> M[趋势与前后对比]
```

图目的：统一覆盖率报告在版本治理中的业务定位。

---

### 2) 产品架构图 - 覆盖率产品能力地图

```mermaid
flowchart TD
  V[版本列表前端页] --> O[覆盖率概览前端页]
  O -->|查看详情| D[覆盖率详情前端页]
  D -->|查看代码| C[源码视图前端页]
  O -->|生成全量/增量| J[任务进度与日志]
  O -->|导出| X[类级Excel/方法级Excel]
  O -->|趋势| T[覆盖率趋势图]
  O -->|对比| P[较上一报告对比]
```

图目的：描述用户在页面中的主路径与关键动作。

---

### 3) 应用架构图 - 应用覆盖率生命周期图

```mermaid
flowchart LR
  A[项目与权限域] --> B[版本中心域]
  B --> C[覆盖率域]
  D[系统快照域] --> C
  E[链路追踪域] --> C
  F[源码与资源域] --> C
  C --> G[报表导出与审计日志]
```

图目的：说明覆盖率域与其他业务域的边界和协作关系。

---

### 4) 功能架构图 - 覆盖率功能分解图

```mermaid
mindmap
  root((覆盖率报告))
    报告生成
      全量生成
      增量生成
      任务状态查询
      过期检测与重生成
    报告查看
      概览指标
      趋势图
      对比分析
      报告删除
    详细分析
      列表视图
      树形懒加载
      多条件筛选
    源码洞察
      方法覆盖列表
      行级着色
      方法锚点跳转
    报表输出
      类级Excel
      方法级Excel
```

图目的：将覆盖率模块拆解为可管理、可迭代的能力树。

---

### 5) 信息架构图 - 覆盖率信息流与指标口径图

```mermaid
flowchart TD
  R[CoverageReportIndex 报告头]
  C[ClassCoverageIndex 类覆盖明细]
  M[MethodCoverageDetail 方法覆盖明细]
  S[SystemSnapshot 系统快照]
  T[TraceNodeIndex 链路节点]
  V[VersionCenter/VersionItem 代码版本]
  A[App 应用]

  O1[覆盖率概览前端页]
  O2[覆盖率详情前端页]
  O3[源码视图前端页]

  S --> R
  T --> C
  R --> C
  C --> M
  V --> O1
  A --> O1
  R --> O1
  C --> O2
  M --> O3
```

图目的：明确各页面展示信息来自哪些对象、指标如何串联。

---

## 二、研发视角

### 6) 系统架构图 - 覆盖率系统组件与边界图

```mermaid
flowchart LR
  U[Browser + Web 前端] --> CC[CoverageControl]
  CC --> CS[CoverageServiceImpl]
  CS --> CR[(coverage_report)]
  CS --> CCR[(class_coverage)]
  CS --> SSR[(system_snapshot)]
  CS --> TR[(trace_node)]
  CS --> SIR[(static_source_info)]
  CS --> VCR[(version_center)]
  CS --> GS[GitService]
  CS --> RS[ResourceService]
  GS --> GIT[(Git Repo)]
  RS --> FS[(codeData 本地缓存)]
```

图目的：展示控制层、服务层、存储层和外部系统的调用边界。

---

### 7) 数据架构图 - 覆盖率数据模型关系图

```mermaid
erDiagram
  COVERAGE_REPORT ||--o{ CLASS_COVERAGE : contains

  COVERAGE_REPORT {
    string id
    string appId
    string versionNumber
    string repoBranch
    string repoCommitId
    int reportType
    string snapshotFingerprint
    string snapshotIds
    long totalLines
    long coveredLines
  }

  CLASS_COVERAGE {
    string id
    string reportId
    string appId
    string className
    int totalMethods
    int coveredMethods
    int totalBranches
    int coveredBranches
    int totalLines
    int coveredLines
  }

  SYSTEM_SNAPSHOT {
    string id
    string projectId
    string appId
    string version
    string traceId
    string updateTime
  }

  TRACE_NODE {
    string id
    string traceId
    string appId
    string type
  }

  STATIC_SOURCE_INFO {
    string id
    string appId
    object classInfo
  }
```

图目的：突出报告头与类明细的主从关系，以及快照/链路/静态源码的数据来源。

---

### 8) 技术架构图 - 覆盖率技术栈与执行链路图

```mermaid
flowchart TB
  subgraph 展示层
    WEB[Web 前端 + REST API]
  end

  subgraph 应用层
    SPR[Spring Boot MVC]
    CTRL[CoverageControl]
    SVC[CoverageServiceImpl]
    JOB[Job + 固定线程池]
  end

  subgraph 数据层
    ES[Spring Data Elasticsearch]
    REDIS[Spring Data Redis]
    FILE[Zip/Jar/目录源码]
  end

  subgraph 集成层
    JGIT[JGit/GitService]
    EXCEL[EasyExcel]
  end

  FM --> SPR --> CTRL --> SVC
  SVC --> ES
  SVC --> REDIS
  SVC --> FILE
  SVC --> JGIT
  SVC --> EXCEL
  SVC --> JOB
```

图目的：说明覆盖率域的主要技术构件与依赖链路。

---

### 9) UML 类图 - Coverage 核心类关系图

```mermaid
classDiagram
  class CoverageControl {
    +overview(...)
    +details(...)
    +generate(...)
    +generateIncremental(...)
    +getJob(jobId)
    +viewCode(...)
    +delete(...)
    +getTreeNodes(...)
  }

  class CoverageService {
    <<interface>>
    +startGenerateJob(...)
    +startGenerateIncrementalJob(...)
    +getClassCoveragePage(...)
    +getTreeNodes(...)
    +getTrendData(...)
    +getColoredSource(...)
    +deleteReport(...)
  }

  class CoverageServiceImpl {
    -generateReportInternal(...)
    -buildSnapshotCoverageContext(...)
    -mergeSnapshotTraceCoverage(...)
    -applyColoring(...)
  }

  class CoverageReportIndex
  class ClassCoverageIndex
  class MethodCoverageDetail
  class CoverageReportRepository
  class ClassCoverageRepository
  class SystemSnapshotRepository
  class TraceNodeRepository
  class StaticInfoRepository

  CoverageControl --> CoverageService
  CoverageService <|.. CoverageServiceImpl
  CoverageServiceImpl --> CoverageReportRepository
  CoverageServiceImpl --> ClassCoverageRepository
  CoverageServiceImpl --> SystemSnapshotRepository
  CoverageServiceImpl --> TraceNodeRepository
  CoverageServiceImpl --> StaticInfoRepository
  CoverageReportIndex "1" --> "*" ClassCoverageIndex
  ClassCoverageIndex "1" --> "*" MethodCoverageDetail
```

图目的：给研发快速建立“入口-契约-实现-存储”的类关系认知。

---

### 10) UML 时序图 - 覆盖率报告生成时序图

```mermaid
sequenceDiagram
  participant User
  participant UI as 覆盖率前端页
  participant Ctrl as CoverageControl
  participant Svc as CoverageServiceImpl
  participant App as AppService
  participant Git as GitService
  participant ES as Elasticsearch Repositories

  User->>UI: 点击生成全量/增量
  UI->>Ctrl: POST /coverage/generate or /generate-incremental
  Ctrl->>Svc: startGenerateJob(...)
  Svc->>Svc: 创建Job并异步执行

  loop 生成主流程
    Svc->>App: getApp(appId)
    Svc->>ES: 查询 static_source_info
    alt 增量模式
      Svc->>Git: getDiffDetail(baseCommit, commit)
    end
    Svc->>ES: 查询 system_snapshot 构建快照上下文
    Svc->>ES: 查询 trace_node 聚合覆盖率
    Svc->>ES: 保存 class_coverage
    Svc->>ES: 保存 coverage_report
  end

  User->>Ctrl: GET /coverage/job/{jobId}
  Ctrl-->>User: 返回进度/日志/reportId
```

图目的：帮助研发对齐异步任务、快照累计、落库过程的时序细节。

---

## 三、跨视角映射（管理层能力 -> 研发落地点）

| 管理视角关注点 | 研发落地点 |
|---|---|
| 生成全量/增量覆盖率 | `CoverageControl.generate*` + `CoverageServiceImpl.startJob/generateReportInternal` |
| 指标概览与趋势 | `CoverageReportIndex` + `CoverageServiceImpl.getTrendData` |
| 详情筛选与树视图 | `CoverageServiceImpl.getClassCoveragePage/getTreeNodes` |
| 源码着色洞察 | `CoverageServiceImpl.getColoredSource/applyColoring` |
| 数据时效与重生成 | `CoverageServiceImpl.hasNewerData/buildSnapshotCoverageContext` |

---

## 四、使用建议

- 管理评审：优先看“管理视角”5 张图。
- 技术评审：按“系统 -> 数据 -> 技术 -> UML”顺序讨论。
- 版本演进：每次覆盖率逻辑变更后，至少同步更新时序图与数据架构图。

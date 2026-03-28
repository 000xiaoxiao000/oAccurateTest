# P1 专利证据矩阵（Java Only）

用于统一核验 P1 五件专利的最小证据链，避免证据冲突与遗漏。

## 字段说明

- 专利编号：P1-01 ~ P1-05
- 证据文件：仅允许 `src/main/java` 下文件
- 关键符号：建议至少方法级
- Commit：建议填写最终取证提交号
- 行号区间：建议填写稳定区间，便于法务引用
- 责任人：研发提供人
- 备注：如与其他专利共享证据，请注明“共享”

| 专利编号 | 证据文件 | 关键符号 | Commit | Line range | 责任人 | 备注 |
|---|---|---|---|---|---|---|
| P1-01 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/CoverageServiceImpl.java` | `generateReportInternal(...)` | 待取证 | 172-359 | 待指定 | 主证据 |
| P1-01 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/CoverageServiceImpl.java` | `buildSnapshotCoverageContext(...)` | 待取证 | 1010-1070 | 待指定 | 主证据 |
| P1-01 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/CoverageServiceImpl.java` | `reuseMatchedCoverageData(...)` | 待取证 | 1138-1166 | 待指定 | 主证据 |
| P1-02 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/CoverageControl.java` | `details(...)` | 待取证 | 116-185 | 待指定 | 主证据 |
| P1-02 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/CoverageControl.java` | `viewCode(...)` | 待取证 | 272-319 | 待指定 | 主证据 |
| P1-02 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/CoverageServiceImpl.java` | `getColoredSource(...)` | 待取证 | 1232-1389 | 待指定 | 与 P1-01 共享部分文件 |
| P1-03 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/GitServiceImpl.java` | `checkGitPull(...)` | 待取证 | 113-154 | 待指定 | 主证据 |
| P1-03 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/GitServiceImpl.java` | `getDiff(...)` | 待取证 | 636-722 | 待指定 | 主证据 |
| P1-03 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/VersionServiceImpl.java` | `startCompareFromGit(...)` | 待取证 | 295-513 | 待指定 | 主证据 |
| P1-04 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/TraceGraphParse.java` | `parse()` | 待取证 | 40-158 | 待指定 | 主证据 |
| P1-04 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/TraceGraphParse.java` | `buildEdges(...)` | 待取证 | 164-186 | 待指定 | 主证据 |
| P1-04 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/MonitorControl.java` | `getTraceGraph(...)` | 待取证 | 100-104 | 待指定 | 辅证据 |
| P1-05 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/SnapshotSearchServiceImpl.java` | `doSearch(...)` | 待取证 | 60-108 | 待指定 | 主证据 |
| P1-05 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/service/impl/SnapshotSearchServiceImpl.java` | `searchByTable(...)` | 待取证 | 112-134 | 待指定 | 主证据 |
| P1-05 | `oAT-service/oAT-service-web/src/main/java/com/oAT/web/control/SearchControl.java` | `doSearch(...)` | 待取证 | 40-46 | 待指定 | 辅证据 |

## 快速校验清单

- [x] 每个 P1 至少 2 个关键符号
- [ ] 每条证据已补 Commit 与行号
- [x] 共享文件已在备注标识
- [ ] 责任人已确认可解释实现细节


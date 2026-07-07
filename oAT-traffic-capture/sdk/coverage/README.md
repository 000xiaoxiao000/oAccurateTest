# oAT 多语言覆盖率上送 SDK

本目录提供 Go、Python、C/C++ 覆盖率产物上送 helper，用于把非 Java 覆盖率数据提交到 oAccurateTest，再由覆盖率中心生成报告。

## 上送入口

本目录下的 Go、Python、C/C++ helper 会在 `endpoint` 后自动追加统一上送路径：

```text
POST /api/v2/ingest/coverage
```

因此 `endpoint` 只需要填写服务基础地址。推荐直接指向 `oAT-service-web`：

```text
http://localhost:8899
```

测试环境无法直连平台时，可以把 `endpoint` 指向 `oAT-relay`，relay 会按原路径转发：

```text
http://localhost:18089
```

桌面采集器中继使用另一个本地接收地址，适合前端 Istanbul 或采集器统一转发场景：

```text
http://localhost:8889/oat/coverage/report
```

上送完成后，在 oAT 覆盖率中心使用相同版本号、分支和 Commit 生成报告。

## 必填元数据

| 字段 | 说明 |
|---|---|
| `endpoint` | `oAT-service-web`、`oAT-relay` 或桌面采集器中继地址 |
| `projectId` | oAT 项目 ID |
| `appId` | oAT 应用 ID |
| `versionNumber` | 版本号 |
| `commitId` | Git Commit |
| `branch` | 分支名，推荐填写 |
| `buildId` | 构建号，推荐填写 |
| `testStage` | 测试阶段，例如 `unit`、`integration`、`e2e` |
| `caseName` | 用例或场景名称，推荐填写 |

## Go

生成 cover profile：

```bash
go test ./... -coverprofile=coverage.out
```

上送：

```go
err := oatcover.PostProfile(ctx, oatcover.ReportOptions{
    Endpoint: "http://localhost:8899",
    ProjectID: "project-id",
    AppID: "app-id",
    ProfilePath: "coverage.out",
    VersionNumber: "v1.0.0",
    CommitID: "git-sha",
    Branch: "main",
    CaseName: "case-name",
    BuildID: "build-20260707",
    TestStage: "unit",
})
```

后端解析标准 `go test -coverprofile` 文本格式。

## Python

生成 coverage.py JSON：

```bash
coverage run -m pytest
coverage json -o coverage.json
```

上送：

```bash
python python/oat_python_coverage_reporter.py \
  --endpoint http://localhost:8899 \
  --project-id project-id \
  --app-id app-id \
  --coverage-json coverage.json \
  --version-number v1.0.0 \
  --commit-id git-sha \
  --branch main \
  --build-id build-20260707 \
  --test-stage unit
```

## C/C++

生成 gcov JSON：

```bash
gcov --json-format path/to/file.gcda
```

上送一个或多个 JSON / JSON.GZ：

```bash
python native/oat_native_gcov_reporter.py \
  --endpoint http://localhost:8899 \
  --project-id project-id \
  --app-id app-id \
  --coverage-json 'build/**/*.gcov.json.gz' \
  --version-number v1.0.0 \
  --commit-id git-sha \
  --branch main \
  --build-id build-20260707 \
  --test-stage unit
```

## 与覆盖率中心配合

1. 在 oAT 中确认项目和应用已创建。
2. 确认应用关联了正确 Git 仓库，且 `commitId` 能在仓库中找到。
3. 上传覆盖率产物。
4. 在覆盖率中心选择相同版本、分支和 Commit 生成报告。
5. 如果通过桌面采集器中继，先确认 `oat-coverage-relay` 插件已启用。

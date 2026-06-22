# oAT 多语言覆盖率上送 SDK

这些 helper 用于把非 Java 覆盖率产物上送到 oAT：

```text
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
```

上送完成后，在 oAT 覆盖率中心使用相同版本号和 Commit 生成报告。

默认服务端端口是 `8899`。如果通过 `oAT-traffic-capture` 中继上送，也可以先 POST 到 `http://localhost:8889/oat/coverage/report`，由采集器转发到服务端。

## Go

先生成 Go cover profile，再上送：

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
})
```

后端解析器期望标准 `go test -coverprofile` 文本格式。

## Python

先执行 `coverage.py json`，再上送生成的 JSON：

```bash
coverage json -o coverage.json
python python/oat_python_coverage_reporter.py \
  --endpoint http://localhost:8899 \
  --project-id project-id \
  --app-id app-id \
  --coverage-json coverage.json \
  --version-number v1.0.0 \
  --commit-id git-sha
```

## C/C++

先生成 gcov JSON，再上送一个或多个 JSON 文件：

```bash
gcov --json-format path/to/file.gcda
python native/oat_native_gcov_reporter.py \
  --endpoint http://localhost:8899 \
  --project-id project-id \
  --app-id app-id \
  --coverage-json 'build/**/*.gcov.json.gz' \
  --version-number v1.0.0 \
  --commit-id git-sha
```

脚本同时支持纯 JSON 和 gzip 压缩后的 JSON。

# oAT multi-language coverage reporters

These helpers post native coverage output to:

```text
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
```

Then generate the report in oAT Coverage Center for the same version and commit.

## Go

Generate a Go cover profile, then post it:

```go
err := oatcover.PostProfile(ctx, oatcover.ReportOptions{
    Endpoint: "http://localhost:8080",
    ProjectID: "project-id",
    AppID: "app-id",
    ProfilePath: "coverage.out",
    VersionNumber: "v1.0.0",
    CommitID: "git-sha",
    Branch: "main",
    CaseName: "case-name",
})
```

The backend parser expects standard `go test -coverprofile` text format.

## Python

Use `coverage.py json` first, then post the generated JSON:

```bash
coverage json -o coverage.json
python python/oat_python_coverage_reporter.py \
  --endpoint http://localhost:8080 \
  --project-id project-id \
  --app-id app-id \
  --coverage-json coverage.json \
  --version-number v1.0.0 \
  --commit-id git-sha
```

## C/C++

Generate gcov JSON output, then post one or more JSON files:

```bash
gcov --json-format path/to/file.gcda
python native/oat_native_gcov_reporter.py \
  --endpoint http://localhost:8080 \
  --project-id project-id \
  --app-id app-id \
  --coverage-json 'build/**/*.gcov.json.gz' \
  --version-number v1.0.0 \
  --commit-id git-sha
```

The script accepts plain JSON and gzip-compressed JSON.

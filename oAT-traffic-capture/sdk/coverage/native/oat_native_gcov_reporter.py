#!/usr/bin/env python3
import argparse
import glob
import gzip
import json
import time
import urllib.error
import urllib.request


def read_coverage_json(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        return json.load(handle)


def post_json(url, payload):
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"upload failed: status={error.code} body={body}") from error


def main():
    parser = argparse.ArgumentParser(description="Post gcov JSON output to oAT.")
    parser.add_argument("--endpoint", required=True, help="oAT service base URL, for example http://localhost:8080")
    parser.add_argument("--project-id", required=True)
    parser.add_argument("--app-id", required=True)
    parser.add_argument("--coverage-json", required=True, action="append", help="gcov JSON path or glob. May be repeated.")
    parser.add_argument("--version-number")
    parser.add_argument("--commit-id")
    parser.add_argument("--branch")
    parser.add_argument("--case-name")
    parser.add_argument("--build-id")
    parser.add_argument("--test-stage", default="unknown")
    args = parser.parse_args()

    paths = []
    for pattern in args.coverage_json:
        matches = glob.glob(pattern, recursive=True)
        paths.extend(matches or [pattern])

    if not paths:
        raise SystemExit("no coverage JSON files matched")

    url = args.endpoint.rstrip("/") + "/api/v2/ingest/coverage"
    for path in paths:
        payload = {
            "projectId": args.project_id,
            "appId": args.app_id,
            "language": "CPP",
            "versionNumber": args.version_number,
            "commitId": args.commit_id,
            "branch": args.branch,
            "caseName": args.case_name,
            "buildId": args.build_id,
            "testStage": args.test_stage,
            "timestamp": int(time.time() * 1000),
            "payload": read_coverage_json(path),
        }
        print(f"{path}: {post_json(url, payload)}")


if __name__ == "__main__":
    main()

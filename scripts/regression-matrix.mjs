#!/usr/bin/env node

const CASES = [
  c('LK-01', 'P0', 'monitor-snapshot', '实时监控自动保存快照', ['J', 'F', 'G', 'Py', 'C'], 'manual', '会创建我的快照和系统快照'),
  c('LK-02', 'P0', 'monitor-snapshot', '实时监控手动保存系统快照', ['J'], 'manual', '会创建系统快照'),
  c('LK-03', 'P1', 'monitor-snapshot', '实时监控手动保存我的快照', ['J'], 'manual', '会创建我的快照'),
  c('LK-04', 'P1', 'monitor-map', '实时监控查看链路地图', ['J'], 'safe', 'OAT_TRACE_ID', ({ projectId, traceId }) => raw(`/api/projects/${projectId}/map/code?traceId=${q(traceId)}`)),
  c('LK-05', 'P1', 'snapshot', '我的快照另存系统快照', ['J', 'F', 'G', 'Py', 'C'], 'manual', '会创建系统快照'),
  c('LK-06', 'P0', 'snapshot-report', '系统快照报告读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID,OAT_SNAPSHOT_ID', ({ projectId, appId, snapshotId }) => json(`/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/report`)),
  c('LK-07', 'P1', 'snapshot-code', '系统快照源码着色读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID,OAT_SNAPSHOT_ID,OAT_UNIT_KEY', ({ projectId, appId, snapshotId, unitKey }) => json(`/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/report/code?className=${q(unitKey)}`)),
  c('LK-08', 'P1', 'snapshot-graph', '系统快照图谱读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID,OAT_SNAPSHOT_ID', ({ projectId, appId, snapshotId }) => json(`/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`)),
  c('LK-09', 'P0', 'snapshot-usecase', '系统快照绑定用例', ['J', 'F', 'G', 'Py', 'C'], 'manual', '会修改用例关联'),
  c('LK-10', 'P1', 'snapshot-usecase', '系统快照批量绑定用例', ['J', 'F', 'G', 'Py', 'C'], 'manual', '会修改用例关联'),
  c('LK-11', 'P1', 'my-snapshot', '我的快照报告与源码读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_MY_SNAPSHOT_ID', ({ projectId, mySnapshotId }) => json(`/api/projects/${projectId}/snapshots/my/${mySnapshotId}/report`)),
  c('LK-12', 'P1', 'usecase', '用例详情反查快照', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_USECASE_ID', ({ projectId, usecaseId }) => json(`/api/projects/${projectId}/usecases/${usecaseId}`)),
  c('LK-13', 'P2', 'usecase', '用例缺陷/PRD 链接', ['NA'], 'manual', '需要浏览器验证外链模板'),
  c('LK-14', 'P0', 'coverage', '覆盖率概览读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID,OAT_VERSION_NUMBER', ({ projectId, appId, versionNumber, reportId, commitId }) => json(`/api/projects/${projectId}/coverage/overview?appId=${q(appId)}&versionNumber=${q(versionNumber)}${opt('reportId', reportId)}${opt('commitId', commitId)}`)),
  c('LK-15', 'P0', 'coverage', '增量覆盖率生成', ['J', 'F', 'G', 'Py', 'C'], 'manual', '会创建覆盖率任务'),
  c('LK-16', 'P1', 'coverage', '覆盖率详情读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID,OAT_VERSION_NUMBER,OAT_REPORT_ID', ({ projectId, appId, versionNumber, reportId }) => json(`/api/projects/${projectId}/coverage/details?appId=${q(appId)}&versionNumber=${q(versionNumber)}&reportId=${q(reportId)}`)),
  c('LK-17', 'P1', 'coverage', '覆盖率趋势读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID,OAT_VERSION_NUMBER', ({ projectId, appId, versionNumber }) => json(`/api/projects/${projectId}/coverage/trend-data?appId=${q(appId)}&versionNumber=${q(versionNumber)}`)),
  c('LK-18', 'P0', 'version', '版本比对报告读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_VERSION_REPORT_ID', ({ projectId, versionReportId }) => json(`/api/projects/${projectId}/version/reports/${versionReportId}`)),
  c('LK-19', 'P1', 'search', '全局搜索', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_SEARCH_KEYWORD', ({ projectId, searchKeyword }) => json(`/api/projects/${projectId}/search/keyword?keyword=${q(searchKeyword)}`)),
  c('LK-20', 'P1', 'map', '表节点反查快照', ['J'], 'safe', 'OAT_DATABASE,OAT_TABLE', ({ projectId, database, table }) => json(`/api/projects/${projectId}/search/tables?database=${q(database)}&table=${q(table)}`)),
  c('LK-21', 'P2', 'map', 'Dubbo 节点反查快照', ['J'], 'safe', 'OAT_DUBBO_INTERFACE,OAT_DUBBO_METHOD', ({ projectId, dubboInterface, dubboMethod }) => raw(`/api/projects/${projectId}/map/layers/dubbo/snapshots?interfaceName=${q(dubboInterface)}&methodName=${q(dubboMethod)}`)),
  c('LK-22', 'P0', 'ops', 'Sandbox 命令下发', ['J'], 'manual', '会下发探针命令'),
  c('LK-23', 'P1', 'ops', '探针告警读取', ['J', 'BATCH'], 'safe', 'OAT_APP_ID', ({ projectId, appId }) => json(`/api/projects/${projectId}/apps/${appId}/probe-alerts`)),
  c('LK-24', 'P1', 'ops', '采集源在线状态读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', null, ({ projectId }) => json(`/api/projects/${projectId}/collector-sources`)),
  c('LK-25', 'P1', 'app', '应用仓库配置读取', ['J', 'common'], 'safe', 'OAT_APP_ID', ({ projectId, appId }) => json(`/api/projects/${projectId}/apps/${appId}/repository`)),
  c('LK-26', 'P1', 'ai', 'AI 页面上下文', ['J', 'F', 'G', 'Py', 'C'], 'safe', null, ({ projectId }) => json(`/api/projects/${projectId}/ai/context`)),
  c('LK-27', 'P1', 'ai', 'AI 覆盖率工具跨语言读取', ['J', 'F', 'G', 'Py', 'C'], 'manual', '需要 LLM/工具链环境'),
  c('LK-28', 'P2', 'ai', 'AI 会话状态切换', ['NA'], 'manual', '会修改当前用户会话'),
  c('LK-29', 'P0', 'app-settings', '新增应用语言配置', ['J', 'F', 'G', 'Py', 'C'], 'manual', '会创建应用'),
  c('LK-30', 'P0', 'app-settings', '应用设置语言配置读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', 'OAT_APP_ID', ({ projectId, appId }) => json(`/api/projects/${projectId}/apps/${appId}/settings`)),
  c('LK-31', 'P1', 'app-settings', '存量 Java 应用设置读取', ['J'], 'safe', 'OAT_APP_ID', ({ projectId, appId }) => json(`/api/projects/${projectId}/apps/${appId}/settings`)),
  c('LK-32', 'P1', 'share', '公开分享快照读取', ['J', 'F', 'G', 'Py', 'C'], 'safe', '!PROJECT,OAT_SHARE_SNAPSHOT_ID', ({ shareSnapshotId }) => json(`/share/api/snapshots/${shareSnapshotId}`)),
  c('LK-33', 'P2', 'share', '公开分享用例读取', ['NA'], 'safe', '!PROJECT,OAT_SHARE_USECASE_ID', ({ shareUsecaseId }) => json(`/share/api/usecases/${shareUsecaseId}`)),
];

const CORE_IDS = new Set([
  'LK-01', 'LK-02', 'LK-06', 'LK-09', 'LK-14', 'LK-15', 'LK-18', 'LK-22', 'LK-24', 'LK-29', 'LK-30',
]);

const args = new Set(process.argv.slice(2));
const selectedIds = valueArg('--ids')?.split(',').map((id) => id.trim()).filter(Boolean);
const selectedPriority = valueArg('--priority');
const selectedDomain = valueArg('--domain');
const includeManual = args.has('--include-manual');
const includeMutating = args.has('--include-mutating');
const dryRun = args.has('--dry-run');
const jsonOutput = args.has('--json');

if (args.has('--help')) {
  printHelp();
  process.exit(0);
}

const env = readEnv();
let cases = CASES.filter((item) => !selectedIds || selectedIds.includes(item.id))
  .filter((item) => !selectedPriority || item.priority === selectedPriority)
  .filter((item) => !selectedDomain || item.domain === selectedDomain)
  .filter((item) => !args.has('--core') || CORE_IDS.has(item.id));

if (args.has('--list')) {
  printList(cases);
  process.exit(0);
}

if (!env.baseUrl && !dryRun) {
  console.error('Missing OAT_BASE_URL. Use --dry-run to validate the matrix without calling APIs.');
  process.exit(2);
}

const results = [];
for (const item of cases) {
  const result = await runCase(item, env);
  results.push(result);
  if (!jsonOutput) {
    console.log(`${mark(result.status)} ${item.id} ${item.priority} ${item.title} - ${result.message}`);
  }
}

if (jsonOutput) {
  console.log(JSON.stringify({ total: results.length, results }, null, 2));
}

const failedResults = results.filter((item) => item.status === 'failed');
if (failedResults.length) {
  process.exit(1);
}

async function runCase(item, env) {
  if (item.mode === 'manual' && !includeManual) {
    return skipped('manual: ' + item.note);
  }
  if (item.mode === 'manual' && !includeMutating) {
    return skipped('mutating/manual case requires --include-mutating');
  }
  if (!item.request) {
    return skipped('no automated API probe is defined');
  }
  const missing = missingEnv(item.required, env);
  if (missing.length) {
    return skipped(`missing ${missing.join(', ')}`);
  }
  if (dryRun) {
    const req = item.request(env);
    return passed(`ready ${req.method} ${req.path}`);
  }
  try {
    const req = item.request(env);
    const response = await fetch(env.baseUrl.replace(/\/$/, '') + req.path, {
      method: req.method,
      headers: {
        Accept: 'application/json',
        ...(env.cookie ? { Cookie: env.cookie } : {}),
        ...(req.headers || {}),
      },
      body: req.body,
    });
    const text = await response.text();
    if (!response.ok) {
      return failed(`HTTP ${response.status}: ${text.slice(0, 180)}`);
    }
    if (req.expectJson) {
      const payload = JSON.parse(text);
      if (payload && Object.prototype.hasOwnProperty.call(payload, 'result') && payload.result !== true) {
        return failed(payload.errorMessage || payload.message || 'ResultNotified result=false');
      }
    }
    return passed(`HTTP ${response.status}`);
  } catch (error) {
    return failed(error instanceof Error ? error.message : String(error));
  }
}

function c(id, priority, domain, title, languages, mode, required, request) {
  const manualNote = mode === 'manual' && typeof required === 'string' ? required : '';
  return { id, priority, domain, title, languages, mode, required, request, note: manualNote };
}

function json(path) {
  return { method: 'GET', path, expectJson: true };
}

function raw(path) {
  return { method: 'GET', path, expectJson: false };
}

function readEnv() {
  return {
    baseUrl: process.env.OAT_BASE_URL,
    cookie: process.env.OAT_COOKIE,
    projectId: process.env.OAT_PROJECT_ID,
    appId: process.env.OAT_APP_ID,
    snapshotId: process.env.OAT_SNAPSHOT_ID,
    mySnapshotId: process.env.OAT_MY_SNAPSHOT_ID,
    usecaseId: process.env.OAT_USECASE_ID,
    reportId: process.env.OAT_REPORT_ID,
    versionReportId: process.env.OAT_VERSION_REPORT_ID,
    versionNumber: process.env.OAT_VERSION_NUMBER,
    commitId: process.env.OAT_COMMIT_ID,
    unitKey: process.env.OAT_UNIT_KEY,
    traceId: process.env.OAT_TRACE_ID,
    searchKeyword: process.env.OAT_SEARCH_KEYWORD || 'test',
    database: process.env.OAT_DATABASE,
    table: process.env.OAT_TABLE,
    dubboInterface: process.env.OAT_DUBBO_INTERFACE,
    dubboMethod: process.env.OAT_DUBBO_METHOD,
    shareSnapshotId: process.env.OAT_SHARE_SNAPSHOT_ID,
    shareUsecaseId: process.env.OAT_SHARE_USECASE_ID,
  };
}

function missingEnv(required, env) {
  const raw = typeof required === 'string' && required.trim()
    ? required.split(',').map((item) => item.trim()).filter(Boolean)
    : [];
  const needsProject = !raw.includes('!PROJECT');
  const names = needsProject ? ['OAT_PROJECT_ID'] : [];
  names.push(...raw.filter((item) => item !== '!PROJECT'));
  return [...new Set(names)].filter((name) => !envValue(name, env));
}

function envValue(name, env) {
  const key = name.replace(/^OAT_/, '').toLowerCase().replace(/_([a-z])/g, (_, ch) => ch.toUpperCase());
  return env[key];
}

function opt(key, value) {
  return value ? `&${key}=${q(value)}` : '';
}

function q(value) {
  return encodeURIComponent(value ?? '');
}

function valueArg(name) {
  const prefix = `${name}=`;
  const arg = process.argv.slice(2).find((item) => item.startsWith(prefix));
  return arg ? arg.slice(prefix.length) : undefined;
}

function passed(message) {
  return { status: 'passed', message };
}

function skipped(message) {
  return { status: 'skipped', message };
}

function failed(message) {
  return { status: 'failed', message };
}

function mark(status) {
  if (status === 'passed') return 'PASS';
  if (status === 'skipped') return 'SKIP';
  return 'FAIL';
}

function printList(items) {
  for (const item of items) {
    console.log(`${item.id}\t${item.priority}\t${item.domain}\t${item.mode}\t${item.languages.join('/')}\t${item.title}`);
  }
}

function printHelp() {
  console.log(`Usage: node scripts/regression-matrix.mjs [options]

Options:
  --list                 Print the LK-01..LK-33 matrix.
  --dry-run              Validate required variables and print planned probes.
  --core                 Run only core release-gate cases.
  --ids=LK-06,LK-14      Run selected cases.
  --priority=P0          Filter by priority.
  --domain=coverage      Filter by domain.
  --include-manual       Include manual cases as skipped checks.
  --include-mutating     Allow manual/mutating cases. Add probes before using this in CI.
  --json                 Print JSON results.

Required for API probes:
  OAT_BASE_URL, OAT_COOKIE, OAT_PROJECT_ID, plus the variables shown in skipped messages.
`);
}

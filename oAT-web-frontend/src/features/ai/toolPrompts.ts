export interface AiToolPrompt {
  id: string
  group: string
  name: string
  description: string
  prompt: string
  icon: 'project' | 'app' | 'coverage' | 'testcase' | 'trace' | 'defect' | 'code' | 'bug' | 'workflow'
}

export const AI_TOOL_PROMPTS: AiToolPrompt[] = [
  {
    id: 'project-overview',
    group: '项目与应用',
    name: '项目概览',
    description: '查看项目基础信息、应用状态和覆盖率概况',
    prompt: '帮我查看当前项目概览，包含应用状态、覆盖率概况和需要关注的风险。',
    icon: 'project',
  },
  {
    id: 'app-status',
    group: '项目与应用',
    name: '应用状态',
    description: '查询在线应用、应用详情和运行状态',
    prompt: '当前项目有哪些应用在线？请基于实时应用状态数据回答。',
    icon: 'app',
  },
  {
    id: 'coverage-overview',
    group: '覆盖率与测试',
    name: '覆盖率概览',
    description: '汇总项目或应用覆盖率，识别覆盖盲区',
    prompt: '帮我分析当前项目的覆盖率概览，并指出覆盖率最低、最需要补测的部分。',
    icon: 'coverage',
  },
  {
    id: 'testcase-recommend',
    group: '覆盖率与测试',
    name: '测试用例推荐',
    description: '按覆盖盲区推荐补充测试场景',
    prompt: '请根据当前覆盖率数据推荐需要补充的测试用例，按风险优先级排序。',
    icon: 'testcase',
  },
  {
    id: 'coverage-workflow',
    group: '覆盖率与测试',
    name: '生成覆盖率报告',
    description: '检查 Git 配置、生成报告、查询任务进度',
    prompt: '我要生成覆盖率报告。请先检查可用应用和必要配置，再告诉我下一步需要选择什么。',
    icon: 'workflow',
  },
  {
    id: 'recent-traces',
    group: '链路与缺陷',
    name: '最近调用链',
    description: '查看最近 Trace、链路详情和调用路径',
    prompt: '帮我查看最近调用链记录，优先列出有异常或值得关注的链路。',
    icon: 'trace',
  },
  {
    id: 'root-cause',
    group: '链路与缺陷',
    name: '异常根因定位',
    description: '结合异常和调用链定位可能根因',
    prompt: '帮我定位当前项目最近异常的根因，请基于异常统计和真实调用链数据回答。',
    icon: 'defect',
  },
  {
    id: 'code-relation',
    group: '代码分析',
    name: '代码关系搜索',
    description: '搜索类、方法的调用和引用关系',
    prompt: '帮我查找相关代码的调用关系。请基于真实代码关系数据回答，不要猜测调用链。',
    icon: 'code',
  },
  {
    id: 'bug-detect',
    group: '代码分析',
    name: 'Bug 检测',
    description: '基于真实源码分析类或方法风险',
    prompt: '帮我检测当前关注代码可能存在的 Bug 或风险，请基于真实源码逐项说明。',
    icon: 'bug',
  },
]

export const AI_TOOL_PROMPT_GROUPS = Array.from(new Set(AI_TOOL_PROMPTS.map((tool) => tool.group)))

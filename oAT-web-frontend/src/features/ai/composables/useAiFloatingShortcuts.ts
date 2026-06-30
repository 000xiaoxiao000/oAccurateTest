import type { ComputedRef } from 'vue'

import type { AIQuickLink } from '@/api/types'
import type { AiFloatingLiveSignals } from '@/features/ai/composables/useAiFloatingPageSignals'

export function useAiFloatingShortcuts(projectId: ComputedRef<string>, liveSignals: ComputedRef<AiFloatingLiveSignals> | { value: AiFloatingLiveSignals }) {
  function normalizeLinks(links: AIQuickLink[], path: string, includeRouteLinks = true) {
    const routeLinks = includeRouteLinks ? routeQuickLinks(path) : []
    const merged = [...routeLinks, ...links]
    const seen = new Set<string>()
    return merged
      .map((link) => ({ ...link, priority: quickLinkPriority(link) }))
      .sort((a, b) => b.priority - a.priority)
      .filter((link) => {
        if (!link.url || seen.has(link.url)) return false
        seen.add(link.url)
        return true
      }).slice(0, 6)
  }

  function quickLinkPriority(link: AIQuickLink) {
    const text = `${link.title || ''} ${link.description || ''} ${link.url || ''}`
    if (text.includes('选中')) return 100
    if (text.includes('悬停')) return 92
    if (text.includes('筛选')) return 80
    if (text.includes('/monitor') || text.includes('监控')) return 76
    if (text.includes('/search') || text.includes('搜索')) return 72
    if (text.includes('快照')) return 66
    if (text.includes('覆盖率')) return 60
    if (text.includes('AI')) return 50
    return 40
  }

  function buildAdaptiveQuickLinks(links: AIQuickLink[]) {
    if (!projectId.value) {
      return [
        { title: '项目列表', description: '选择一个项目后使用完整 AI 助手能力', url: '/projects' },
      ]
    }
    const base = `/p/${projectId.value}`
    const next = [...links]
    if (liveSignals.value.tableSelection) {
      next.unshift({ title: '围绕选中数据进入监控台', description: `结合当前选中行查看实时请求：${liveSignals.value.tableSelection}`, url: `${base}/monitor` })
      next.unshift({ title: '围绕选中数据继续搜索', description: `带着当前选中行继续定位：${liveSignals.value.tableSelection}`, url: `${base}/search` })
    } else if (liveSignals.value.tableHover) {
      next.unshift({ title: '围绕悬停数据继续搜索', description: `以当前悬停行为线索继续排查：${liveSignals.value.tableHover}`, url: `${base}/search` })
      next.unshift({ title: '围绕悬停数据查看快照', description: '把当前悬停数据映射到历史快照入口', url: `${base}/my-snapshots` })
    }
    if (liveSignals.value.filters.length) {
      next.push({ title: '保留当前筛选去项目地图', description: `带着筛选条件继续缩小范围：${liveSignals.value.filters.join('、')}`, url: `${base}/map/home` })
    }
    return next
  }

  function buildAdaptiveStarters(starters: string[]) {
    const next = [...starters]
    if (liveSignals.value.tableSelection) {
      next.unshift(`帮我分析这条数据：${liveSignals.value.tableSelection}`)
      next.unshift('这条选中数据我该重点看什么')
    } else if (liveSignals.value.tableHover) {
      next.unshift('帮我看看这条悬停数据代表什么')
      next.unshift('这条悬停数据下一步应该怎么排查')
    }
    if (liveSignals.value.filters.length) next.push('结合当前筛选条件，建议我下一步操作')
    return Array.from(new Set(next.filter(Boolean))).slice(0, 8)
  }

  function normalizeSpaUrl(url?: string) {
    if (!url) return ''
    try {
      const parsed = new URL(url, window.location.origin)
      if (parsed.origin === window.location.origin) {
        return `${parsed.pathname}${parsed.search}${parsed.hash}`
      }
      return url
    } catch {
      return url.startsWith('/') ? url : `/${url}`
    }
  }

  function routeQuickLinks(path: string): AIQuickLink[] {
    const base = `/p/${projectId.value}`
    if (path.includes('/monitor')) {
      return [
        { title: '实时监控', description: '查看最新调用链路', url: `${base}/monitor` },
        { title: '我的快照', description: '查看已保存快照', url: `${base}/my-snapshots` },
      ]
    }
    if (path.includes('/coverage')) {
      return [
        { title: '覆盖率中心', description: '返回覆盖率总览', url: `${base}/coverage` },
        { title: '版本中心', description: '查看版本和报告', url: `${base}/version/apps` },
      ]
    }
    return [
      { title: '项目首页', description: '返回项目上下文', url: `${base}/home` },
      { title: 'AI 工作台', description: '打开完整对话页面', url: `${base}/ai` },
    ]
  }

  function routeStarters(path: string) {
    if (path.includes('/monitor')) return ['当前监控页应该先看哪些请求', '如果线上有异常，排查顺序是什么']
    if (path.includes('/api-endpoints')) return ['哪些接口还没有覆盖用例', '如何提升当前接口覆盖率']
    if (path.includes('/coverage')) return ['这个页面的覆盖率风险在哪里', '帮我按风险排序低覆盖方法']
    if (path.includes('/usecases')) return ['帮我改进这个用例', '这个用例应该关联哪些快照']
    return ['当前页面我应该先看什么', '帮我总结项目当前测试风险']
  }

  return {
    buildAdaptiveQuickLinks,
    buildAdaptiveStarters,
    normalizeLinks,
    normalizeSpaUrl,
    routeStarters,
  }
}

import { ref, type Ref } from 'vue'

export type AiFloatingLiveSignals = {
  filters: string[]
  tableHover: string
  tableSelection: string
}

export type AiFloatingSignalId = 'filter' | 'hover' | 'selection'

export function useAiFloatingPageSignals(rootRef: Ref<HTMLElement | null>) {
  const liveSignals = ref<AiFloatingLiveSignals>({ filters: [], tableHover: '', tableSelection: '' })
  let hoveredRowEl: HTMLElement | null = null
  let selectedRowEl: HTMLElement | null = null

  function collectLiveFilterState() {
    const filters: string[] = []
    const selector = 'input[type="text"], input[type="search"], input:not([type]), select, textarea'
    for (const element of Array.from(document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(selector))) {
      if (rootRef.value?.contains(element)) continue
      const value = String(element.value || '').trim()
      if (!value || value.length > 24) continue
      const placeholder = element.getAttribute('placeholder') || element.getAttribute('name') || element.getAttribute('aria-label') || ''
      const item = placeholder.trim() ? `${placeholder.trim()}=${value}` : value
      if (!filters.includes(item)) filters.push(item)
      if (filters.length >= 4) break
    }
    liveSignals.value = { ...liveSignals.value, filters }
  }

  function summarizeRow(row: HTMLElement) {
    const cells: string[] = []
    for (const cell of Array.from(row.querySelectorAll('td'))) {
      const text = (cell.textContent || '').replace(/\s+/g, ' ').trim()
      if (text && !cells.includes(text)) cells.push(text)
      if (cells.length >= 4) break
    }
    return cells.join(' | ')
  }

  function findTableRow(target: EventTarget | null) {
    const element = target instanceof HTMLElement ? target : null
    const row = element?.closest('table tbody tr, .ui.table tbody tr') as HTMLElement | null
    if (!row || rootRef.value?.contains(row)) return null
    return row
  }

  function onDocumentInput(event: Event) {
    if (rootRef.value?.contains(event.target as Node)) return
    collectLiveFilterState()
  }

  function onDocumentMouseOver(event: MouseEvent) {
    const row = findTableRow(event.target)
    if (!row || row === hoveredRowEl) return
    if (hoveredRowEl && hoveredRowEl !== selectedRowEl) hoveredRowEl.classList.remove('ai-floating-row-hover')
    hoveredRowEl = row
    if (hoveredRowEl !== selectedRowEl) hoveredRowEl.classList.add('ai-floating-row-hover')
    liveSignals.value = { ...liveSignals.value, tableHover: summarizeRow(row) }
  }

  function onDocumentMouseOut(event: MouseEvent) {
    const row = findTableRow(event.target)
    if (!row) return
    const related = event.relatedTarget instanceof Node ? event.relatedTarget : null
    if (related && row.contains(related)) return
    if (row !== selectedRowEl) row.classList.remove('ai-floating-row-hover')
    if (hoveredRowEl === row) {
      hoveredRowEl = null
      liveSignals.value = { ...liveSignals.value, tableHover: '' }
    }
  }

  function onDocumentClick(event: MouseEvent) {
    const row = findTableRow(event.target)
    if (!row) return
    selectedRowEl?.classList.remove('ai-floating-row-selected', 'ai-floating-row-hover')
    selectedRowEl = row
    selectedRowEl.classList.add('ai-floating-row-selected')
    liveSignals.value = { ...liveSignals.value, tableSelection: summarizeRow(row) }
  }

  function clearSignal(id: AiFloatingSignalId) {
    if (id === 'filter') {
      liveSignals.value = { ...liveSignals.value, filters: [] }
      return
    }
    if (id === 'hover') {
      hoveredRowEl?.classList.remove('ai-floating-row-hover')
      hoveredRowEl = null
      liveSignals.value = { ...liveSignals.value, tableHover: '' }
      return
    }
    selectedRowEl?.classList.remove('ai-floating-row-selected', 'ai-floating-row-hover')
    selectedRowEl = null
    liveSignals.value = { ...liveSignals.value, tableSelection: '' }
  }

  function clearLiveRowState() {
    hoveredRowEl?.classList.remove('ai-floating-row-hover')
    selectedRowEl?.classList.remove('ai-floating-row-selected', 'ai-floating-row-hover')
    hoveredRowEl = null
    selectedRowEl = null
    liveSignals.value = { ...liveSignals.value, tableHover: '', tableSelection: '' }
  }

  function bindLivePageSignals() {
    document.addEventListener('input', onDocumentInput, true)
    document.addEventListener('change', onDocumentInput, true)
    document.addEventListener('mouseover', onDocumentMouseOver, true)
    document.addEventListener('mouseout', onDocumentMouseOut, true)
    document.addEventListener('click', onDocumentClick, true)
    collectLiveFilterState()
  }

  function unbindLivePageSignals() {
    document.removeEventListener('input', onDocumentInput, true)
    document.removeEventListener('change', onDocumentInput, true)
    document.removeEventListener('mouseover', onDocumentMouseOver, true)
    document.removeEventListener('mouseout', onDocumentMouseOut, true)
    document.removeEventListener('click', onDocumentClick, true)
  }

  function buildPageContext(currentQuestion: string, routeFullPath: string) {
    const parts = [`route=${routeFullPath}`]
    const pageTitle = document.title.trim()
    if (pageTitle) parts.push(`页面标题:${pageTitle}`)
    const headers = collectTexts('th, .ui.table thead th', 6)
    if (headers.length) parts.push(`表格字段:${headers.join('、')}`)
    const filterLabels = collectTexts('input[placeholder], textarea[placeholder], select, .search input[placeholder]', 6)
    if (filterLabels.length) parts.push(`筛选线索:${filterLabels.join('、')}`)
    const actionTexts = collectTexts('button, a, .menu .item', 8)
    if (actionTexts.length) parts.push(`可操作项:${actionTexts.join('、')}`)
    if (liveSignals.value.filters.length) parts.push(`当前筛选:${liveSignals.value.filters.join('、')}`)
    if (liveSignals.value.tableSelection) parts.push(`当前选中行:${liveSignals.value.tableSelection}`)
    if (liveSignals.value.tableHover) parts.push(`当前悬停行:${liveSignals.value.tableHover}`)
    const coverageSourceContext = collectCoverageSourceContext(currentQuestion)
    if (coverageSourceContext) parts.push(coverageSourceContext)
    if (currentQuestion) parts.push(`当前问题:${currentQuestion}`)
    return parts.join('；')
  }

  function collectTexts(selector: string, limit: number) {
    const values: string[] = []
    for (const element of Array.from(document.querySelectorAll(selector))) {
      if (rootRef.value?.contains(element)) continue
      const text = ((element.getAttribute('placeholder') || element.textContent || '') as string).replace(/\s+/g, ' ').trim()
      if (text && !values.includes(text)) values.push(text.slice(0, 48))
      if (values.length >= limit) break
    }
    return values
  }

  function collectCoverageSourceContext(currentQuestion: string) {
    const source = document.querySelector<HTMLElement>('.source-container')
    if (!source || rootRef.value?.contains(source)) return ''

    const sourceText = normalizeContextText(source.innerText || source.textContent || '')
    if (!sourceText) return ''

    const methodNames = collectSourceMethodNames()
    const targetMethod = findTargetMethodName(currentQuestion, methodNames, sourceText)
    const snippet = buildSourceSnippet(sourceText, targetMethod)
    const className = collectCoverageClassName()
    const selectedMethodSummary = collectSelectedMethodSummary(targetMethod)
    const parts = ['页面类型:覆盖率源码页']
    if (className) parts.push(`当前类:${className}`)
    if (targetMethod) parts.push(`目标方法:${targetMethod}`)
    if (selectedMethodSummary) parts.push(`方法覆盖信息:${selectedMethodSummary}`)
    if (isMethodBugQuestion(currentQuestion) && snippet) parts.push('用户意图:分析当前/目标方法可能存在的 Bug，请直接基于下方真实源码片段回答')
    if (snippet) parts.push(`当前源码片段:\n${snippet}`)
    return parts.join('；')
  }

  function normalizeContextText(text: string) {
    return text
      .replace(/\u00a0/g, ' ')
      .replace(/[ \t]+\n/g, '\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
  }

  function collectSourceMethodNames() {
    const names: string[] = []
    for (const button of Array.from(document.querySelectorAll<HTMLElement>('.method-jump'))) {
      if (rootRef.value?.contains(button)) continue
      const raw = normalizeContextText(button.textContent || '')
      const name = raw.split('(')[0]?.trim()
      if (name && !names.includes(name)) names.push(name)
    }
    for (const row of Array.from(document.querySelectorAll<HTMLElement>('[data-oat-coverage-method-row="true"], .method-table tbody tr'))) {
      if (rootRef.value?.contains(row)) continue
      const name = extractMethodNameFromRow(row)
      if (name && !names.includes(name)) names.push(name)
    }
    return names
  }

  function findTargetMethodName(question: string, methodNames: string[], sourceText: string) {
    const selectedMethod = selectedRowEl ? extractMethodNameFromRow(selectedRowEl) : ''
    if (selectedMethod) return selectedMethod

    const visibleRows = Array.from(document.querySelectorAll<HTMLElement>('[data-oat-coverage-method-row="true"], .method-table tbody tr'))
      .filter((row) => !rootRef.value?.contains(row) && row.offsetParent !== null)
    if (visibleRows.length === 1) {
      const visibleMethod = extractMethodNameFromRow(visibleRows[0])
      if (visibleMethod) return visibleMethod
    }

    const contextText = [
      question,
      liveSignals.value.filters.join(' '),
      liveSignals.value.tableSelection,
      liveSignals.value.tableHover,
    ].join(' ').toLowerCase()
    return methodNames.find((name) => contextText.includes(name.toLowerCase()))
      || methodNames.find((name) => contextText.includes(name.split('(')[0]?.trim().toLowerCase() || name.toLowerCase()))
      || inferMethodNameFromQuestion(question, sourceText)
      || ''
  }

  function inferMethodNameFromQuestion(question: string, sourceText: string) {
    const tokens = Array.from(question.matchAll(/[A-Za-z_$][\w$]*\s*(?=\()/g)).map((match) => match[0].trim())
    tokens.push(...Array.from(question.matchAll(/[A-Za-z_$][\w$]{2,}/g)).map((match) => match[0].trim()))
    const ignored = new Set(['bug', 'method', 'null', 'true', 'false', 'return', 'public', 'private', 'protected', 'static', 'void', 'int', 'long', 'string', 'integer', 'number', 'boolean'])
    for (const token of Array.from(new Set(tokens))) {
      if (ignored.has(token.toLowerCase())) continue
      if (new RegExp(`\\b${escapeRegExp(token)}\\s*\\(`).test(sourceText)) return token
    }
    return ''
  }

  function buildSourceSnippet(sourceText: string, methodName: string) {
    const lines = sourceText.split('\n').map((line) => line.trimEnd()).filter((line) => line.trim())
    if (!lines.length) return ''
    const methodLineIndex = methodName
      ? lines.findIndex((line) => new RegExp(`\\b${escapeRegExp(methodName)}\\s*\\(`).test(line))
      : -1
    const start = methodLineIndex >= 0 ? Math.max(0, methodLineIndex - 4) : 0
    const end = methodLineIndex >= 0 ? Math.min(lines.length, methodLineIndex + 42) : Math.min(lines.length, 52)
    return lines.slice(start, end).join('\n').slice(0, 6000)
  }

  function collectCoverageClassName() {
    const title = document.querySelector<HTMLElement>('.coverage-code-view h1')?.textContent
      || document.querySelector<HTMLElement>('h1')?.textContent
      || ''
    return normalizeContextText(title)
  }

  function collectSelectedMethodSummary(targetMethod: string) {
    const rows = Array.from(document.querySelectorAll<HTMLElement>('[data-oat-coverage-method-row="true"], .method-table tbody tr'))
    const targetRow = rows.find((row) => targetMethod && rowMatchesMethod(row, targetMethod))
      || (selectedRowEl && selectedRowEl.closest('.method-table') ? selectedRowEl : null)
    if (!targetRow) return ''
    return (targetRow.dataset.methodSummary || summarizeRow(targetRow)).slice(0, 500)
  }

  function extractMethodNameFromRow(row: HTMLElement) {
    const fromData = row.dataset.methodName || row.dataset.methodDisplayName
    if (fromData) return fromData.split('(')[0]?.trim() || fromData.trim()
    const raw = normalizeContextText(row.querySelector<HTMLElement>('.method-jump')?.textContent || row.textContent || '')
    return raw.split('(')[0]?.trim() || ''
  }

  function rowMatchesMethod(row: HTMLElement, methodName: string) {
    const normalized = methodName.toLowerCase()
    return [row.dataset.methodName, row.dataset.methodDisplayName, row.dataset.methodDesc, row.textContent]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalized))
  }

  function isMethodBugQuestion(question: string) {
    const text = question.toLowerCase()
    return (text.includes('bug') || text.includes('缺陷') || text.includes('风险') || text.includes('可能存在') || text.includes('潜在问题'))
      && (text.includes('方法') || text.includes('method') || text.includes('函数') || text.includes('此') || text.includes('这个'))
  }

  function escapeRegExp(value: string) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  }

  return {
    liveSignals,
    bindLivePageSignals,
    buildPageContext,
    clearLiveRowState,
    clearSignal,
    collectLiveFilterState,
    unbindLivePageSignals,
  }
}

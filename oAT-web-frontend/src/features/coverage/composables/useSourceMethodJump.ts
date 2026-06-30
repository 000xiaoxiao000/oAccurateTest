import { nextTick } from 'vue'

export function useSourceMethodJump(sourceRef: () => HTMLElement | null) {
  async function jumpToMethod(methodName: string) {
    const lookupName = methodName.split('(')[0]?.trim() || methodName.trim()
    if (!lookupName) return
    await nextTick()
    const source = sourceRef()
    if (!source) return
    clearSourceJumpHighlight(source)
    const escaped = lookupName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const signaturePattern = new RegExp(`\\b${escaped}\\s*\\(`)
    const target = findSourceLineTarget(source, signaturePattern)
    if (!target) {
      source.scrollIntoView({ behavior: 'smooth', block: 'start' })
      return
    }
    target.dataset.sourceJumpHighlight = 'true'
    scrollSourceTargetIntoView(source, target)
  }

  return {
    jumpToMethod,
  }
}

function clearSourceJumpHighlight(source: HTMLElement) {
  source.querySelectorAll<HTMLElement>('[data-source-jump-highlight="true"]').forEach((node) => {
    delete node.dataset.sourceJumpHighlight
  })
}

function findSourceLineTarget(source: HTMLElement, signaturePattern: RegExp) {
  const lineElements = Array.from(source.querySelectorAll<HTMLElement>('tr, pre > div, .line, [class~="line"], li'))
  const targetLine = lineElements.find((element) => signaturePattern.test(element.textContent || ''))
  if (targetLine) return targetLine

  const text = source.innerText || source.textContent || ''
  const match = signaturePattern.exec(text)
  if (!match) return null
  const lineNumber = text.slice(0, match.index).split('\n').length
  return lineElements[lineNumber - 1] || findClosestTextElement(source, signaturePattern)
}

function findClosestTextElement(source: HTMLElement, signaturePattern: RegExp) {
  return Array.from(source.querySelectorAll<HTMLElement>('tr, li, div, span, code'))
    .filter((element) => signaturePattern.test(element.textContent || ''))
    .sort((a, b) => (a.textContent || '').length - (b.textContent || '').length)[0] || null
}

function scrollSourceTargetIntoView(source: HTMLElement, target: HTMLElement) {
  const sourceRect = source.getBoundingClientRect()
  const targetRect = target.getBoundingClientRect()
  const nextTop = source.scrollTop + targetRect.top - sourceRect.top - source.clientHeight / 2 + targetRect.height / 2
  source.scrollTo({ top: Math.max(0, nextTop), behavior: 'smooth' })
  source.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

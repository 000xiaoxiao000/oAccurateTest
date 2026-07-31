<template>
  <Teleport to="body">
    <div
      v-if="visible && text"
      ref="tooltipRef"
      class="app-tooltip-host"
      :style="{ left: `${position.left}px`, top: `${position.top}px` }"
      role="tooltip"
    >
      {{ text }}
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

const visible = ref(false)
const text = ref('')
const tooltipRef = ref<HTMLElement | null>(null)
const position = reactive({ left: 0, top: 0 })

let activeElement: HTMLElement | null = null
let activeTitle = ''
let activeMode: 'hover' | 'focus' | null = null

function findTitleElement(target: EventTarget | null) {
  if (!(target instanceof Element)) return null
  const element = target.closest<HTMLElement>('[title]')
  if (!element || element.dataset.oatTooltipDisabled === 'true') return null
  const title = element.getAttribute('title')?.trim()
  return title ? element : null
}

function showTooltip(element: HTMLElement, title: string, mode: 'hover' | 'focus', event?: MouseEvent | FocusEvent) {
  if (activeElement && activeElement !== element) {
    restoreActiveTitle()
  }
  activeElement = element
  activeTitle = title
  activeMode = mode
  element.setAttribute('data-oat-native-title', title)
  element.removeAttribute('title')
  text.value = title
  visible.value = true
  updatePosition(event, element)
}

function hideTooltip() {
  restoreActiveTitle()
  visible.value = false
  text.value = ''
  activeMode = null
}

function restoreActiveTitle() {
  if (!activeElement) return
  const storedTitle = activeElement.getAttribute('data-oat-native-title') || activeTitle
  if (storedTitle) {
    activeElement.setAttribute('title', storedTitle)
  }
  activeElement.removeAttribute('data-oat-native-title')
  activeElement = null
  activeTitle = ''
}

function updatePosition(event?: MouseEvent | FocusEvent, element = activeElement) {
  if (!element) return
  const rect = element.getBoundingClientRect()
  const nextLeft = event instanceof MouseEvent ? event.clientX + 14 : rect.left + rect.width / 2
  const nextTop = event instanceof MouseEvent ? event.clientY + 18 : rect.bottom + 10
  position.left = nextLeft
  position.top = nextTop

  nextTick(() => {
    const tooltip = tooltipRef.value
    if (!tooltip) return
    const margin = 12
    const tooltipRect = tooltip.getBoundingClientRect()
    position.left = Math.min(Math.max(margin, position.left), window.innerWidth - tooltipRect.width - margin)
    position.top = Math.min(Math.max(margin, position.top), window.innerHeight - tooltipRect.height - margin)
  })
}

function handleMouseOver(event: MouseEvent) {
  const element = findTitleElement(event.target)
  if (!element) return
  showTooltip(element, element.getAttribute('title') || '', 'hover', event)
}

function handleMouseMove(event: MouseEvent) {
  if (visible.value && activeMode === 'hover') {
    updatePosition(event)
  }
}

function handleMouseOut(event: MouseEvent) {
  if (!activeElement || activeMode !== 'hover') return
  if (event.relatedTarget instanceof Node && activeElement.contains(event.relatedTarget)) return
  hideTooltip()
}

function handleFocusIn(event: FocusEvent) {
  const element = findTitleElement(event.target)
  if (!element) return
  showTooltip(element, element.getAttribute('title') || '', 'focus', event)
}

function handleFocusOut() {
  if (activeMode === 'focus') {
    hideTooltip()
  }
}

function handleWindowChange() {
  if (visible.value) {
    hideTooltip()
  }
}

onMounted(() => {
  document.addEventListener('mouseover', handleMouseOver, true)
  document.addEventListener('mousemove', handleMouseMove, true)
  document.addEventListener('mouseout', handleMouseOut, true)
  document.addEventListener('focusin', handleFocusIn, true)
  document.addEventListener('focusout', handleFocusOut, true)
  window.addEventListener('scroll', handleWindowChange, true)
  window.addEventListener('resize', handleWindowChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('mouseover', handleMouseOver, true)
  document.removeEventListener('mousemove', handleMouseMove, true)
  document.removeEventListener('mouseout', handleMouseOut, true)
  document.removeEventListener('focusin', handleFocusIn, true)
  document.removeEventListener('focusout', handleFocusOut, true)
  window.removeEventListener('scroll', handleWindowChange, true)
  window.removeEventListener('resize', handleWindowChange)
  restoreActiveTitle()
})
</script>

<style scoped>
.app-tooltip-host {
  position: fixed;
  z-index: 10000;
  max-width: min(360px, calc(100vw - 24px));
  padding: 9px 11px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.16), 0 2px 8px rgba(15, 23, 42, 0.08);
  color: #172033;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.55;
  pointer-events: none;
  white-space: pre-wrap;
}
</style>

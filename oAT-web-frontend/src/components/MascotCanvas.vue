<template>
  <canvas ref="canvasRef" class="mascot-canvas" :width="size" :height="size" aria-hidden="true"></canvas>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  size?: number
  color?: string
  seed?: string
  mood?: 'happy' | 'error' | 'thinking'
  interactive?: boolean
  float?: boolean
}>(), {
  size: 88,
  color: '#0f766e',
  seed: 'oat-mascot',
  mood: 'happy',
  interactive: true,
  float: true,
})

const canvasRef = ref<HTMLCanvasElement | null>(null)
let animationId = 0
let mouseX = 0
let mouseY = 0
let pupilX = 0
let pupilY = 0
let bodyAngle = 0

function hash(value: string) {
  let h = 2166136261
  for (let i = 0; i < value.length; i++) {
    h ^= value.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

function random(seed: number) {
  let state = seed || 1
  return () => {
    state = (state + 0x6D2B79F5) | 0
    let t = Math.imul(state ^ (state >>> 15), 1 | state)
    t ^= t + Math.imul(t ^ (t >>> 7), 61 | t)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function hexToRgb(hex: string) {
  const normalized = hex.replace('#', '')
  const full = normalized.length === 3
    ? normalized.split('').map((item) => item + item).join('')
    : normalized
  return {
    r: parseInt(full.slice(0, 2), 16) || 15,
    g: parseInt(full.slice(2, 4), 16) || 118,
    b: parseInt(full.slice(4, 6), 16) || 110,
  }
}

function draw() {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return

  const dpr = window.devicePixelRatio || 1
  const cssSize = props.size
  if (canvas.width !== cssSize * dpr) {
    canvas.width = cssSize * dpr
    canvas.height = cssSize * dpr
    canvas.style.width = `${cssSize}px`
    canvas.style.height = `${cssSize}px`
  }

  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, cssSize, cssSize)

  const seeded = random(hash(props.seed))
  const accessoryKind = Math.floor(seeded() * 4)
  const radius = cssSize * 0.34
  const centerX = cssSize / 2
  const centerY = cssSize / 2 + (props.float ? Math.sin(Date.now() / 720) * 3 : 0)
  const rgb = hexToRgb(props.color)
  const rect = canvas.getBoundingClientRect()
  const worldCenterX = props.interactive ? rect.left + centerX : centerX
  const worldCenterY = props.interactive ? rect.top + centerY : centerY
  const dx = props.interactive ? mouseX - worldCenterX : 24
  const dy = props.interactive ? mouseY - worldCenterY : 10
  const localDistance = Math.hypot(dx, dy) || 1
  const eyeSizeForTarget = radius * 0.25
  const pupilMaxDist = eyeSizeForTarget * 0.48
  const targetX = (dx / localDistance) * pupilMaxDist
  const targetY = (dy / localDistance) * pupilMaxDist
  pupilX += (targetX - pupilX) * 0.18
  pupilY += (targetY - pupilY) * 0.18
  bodyAngle += (0 - bodyAngle) * 0.08

  ctx.save()
  ctx.translate(centerX, centerY)
  ctx.rotate(Math.sin(Date.now() / 1100 + seeded()) * 0.012)

  const halo = ctx.createRadialGradient(0, 0, radius * 0.15, 0, 0, radius * 1.55)
  halo.addColorStop(0, `rgba(${rgb.r},${rgb.g},${rgb.b},0.18)`)
  halo.addColorStop(1, `rgba(${rgb.r},${rgb.g},${rgb.b},0)`)
  ctx.fillStyle = halo
  ctx.beginPath()
  ctx.arc(0, 0, radius * 1.55, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = props.color
  ctx.beginPath()
  ctx.ellipse(0, 0, radius, radius * 0.90, 0, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = `rgba(${rgb.r},${rgb.g},${rgb.b},0.30)`
  ctx.beginPath()
  ctx.arc(-radius * 0.2, -radius * 0.18, radius * 0.82, 0, Math.PI * 2)
  ctx.fill()

  drawAccessory(ctx, accessoryKind, radius)

  const eyeOffsetX = radius * 0.34
  const eyeOffsetY = -radius * 0.12
  const eyeSize = radius * 0.25
  ctx.fillStyle = '#fff'
  ctx.beginPath()
  ctx.arc(-eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2)
  ctx.arc(eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = '#0f172a'
  const pupilSize = props.mood === 'error' ? eyeSize * 0.24 : eyeSize * 0.42
  ctx.beginPath()
  ctx.arc(-eyeOffsetX + pupilX, eyeOffsetY + pupilY, pupilSize, 0, Math.PI * 2)
  ctx.arc(eyeOffsetX + pupilX, eyeOffsetY + pupilY, pupilSize, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = 'rgba(255, 255, 255, 0.82)'
  ctx.beginPath()
  ctx.arc(-eyeOffsetX + pupilX - pupilSize * 0.25, eyeOffsetY + pupilY - pupilSize * 0.25, pupilSize * 0.18, 0, Math.PI * 2)
  ctx.arc(eyeOffsetX + pupilX - pupilSize * 0.25, eyeOffsetY + pupilY - pupilSize * 0.25, pupilSize * 0.18, 0, Math.PI * 2)
  ctx.fill()

  ctx.strokeStyle = 'rgba(15, 23, 42, 0.55)'
  ctx.lineWidth = 2.4
  ctx.lineCap = 'round'
  ctx.beginPath()
  if (props.mood === 'error') {
    ctx.moveTo(-radius * 0.22, radius * 0.30)
    ctx.quadraticCurveTo(0, radius * 0.15, radius * 0.22, radius * 0.30)
  } else if (props.mood === 'thinking') {
    ctx.moveTo(-radius * 0.22, radius * 0.28)
    ctx.lineTo(radius * 0.22, radius * 0.28)
  } else {
    ctx.arc(0, radius * 0.18, radius * 0.25, 0.15, Math.PI - 0.15)
  }
  ctx.stroke()
  ctx.restore()

  animationId = window.requestAnimationFrame(draw)
}

function drawAccessory(ctx: CanvasRenderingContext2D, kind: number, radius: number) {
  ctx.save()
  ctx.fillStyle = '#64748b'
  ctx.strokeStyle = '#475569'
  ctx.lineWidth = Math.max(2, radius * 0.07)
  if (kind === 1) {
    ctx.translate(0, -radius * 0.92)
    ctx.rotate(-0.08)
    ctx.fillRect(-radius * 0.36, -radius * 0.16, radius * 0.72, radius * 0.22)
    ctx.beginPath()
    ctx.moveTo(-radius * 0.48, radius * 0.08)
    ctx.lineTo(radius * 0.48, radius * 0.08)
    ctx.stroke()
  } else if (kind === 2) {
    ctx.translate(0, radius * 0.88)
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(-radius * 0.34, -radius * 0.18)
    ctx.lineTo(-radius * 0.34, radius * 0.18)
    ctx.closePath()
    ctx.moveTo(0, 0)
    ctx.lineTo(radius * 0.34, -radius * 0.18)
    ctx.lineTo(radius * 0.34, radius * 0.18)
    ctx.closePath()
    ctx.fill()
  } else if (kind === 3) {
    const eyeOffsetX = radius * 0.34
    const eyeOffsetY = -radius * 0.12
    ctx.strokeRect(-eyeOffsetX - radius * 0.20, eyeOffsetY - radius * 0.14, radius * 0.40, radius * 0.28)
    ctx.strokeRect(eyeOffsetX - radius * 0.20, eyeOffsetY - radius * 0.14, radius * 0.40, radius * 0.28)
    ctx.beginPath()
    ctx.moveTo(-eyeOffsetX + radius * 0.20, eyeOffsetY)
    ctx.lineTo(eyeOffsetX - radius * 0.20, eyeOffsetY)
    ctx.stroke()
  }
  ctx.restore()
}

function handleMouse(event: MouseEvent) {
  if (props.interactive) {
    mouseX = event.clientX
    mouseY = event.clientY
    return
  }
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  mouseX = event.clientX - rect.left
  mouseY = event.clientY - rect.top
}

onMounted(() => {
  const rect = canvasRef.value?.getBoundingClientRect()
  mouseX = props.interactive && rect ? rect.left + props.size / 2 + 28 : props.size / 2 + 16
  mouseY = props.interactive && rect ? rect.top + props.size / 2 + 8 : props.size / 2 + 8
  const eventTarget = props.interactive ? window : canvasRef.value
  eventTarget?.addEventListener('mousemove', handleMouse as EventListener)
  draw()
})

onBeforeUnmount(() => {
  if (animationId) window.cancelAnimationFrame(animationId)
  const eventTarget = props.interactive ? window : canvasRef.value
  eventTarget?.removeEventListener('mousemove', handleMouse as EventListener)
})

watch(() => [props.color, props.seed, props.mood, props.size], () => {
  if (!animationId) draw()
})
</script>

<style scoped>
.mascot-canvas {
  display: block;
}
</style>

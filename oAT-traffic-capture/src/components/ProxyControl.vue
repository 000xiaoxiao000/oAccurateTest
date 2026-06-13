<script setup lang="ts">
import { onMounted, ref } from 'vue'

const props = defineProps<{ port: number }>()

const isElectronWindow = ref(false)
const systemProxyEnabled = ref(false)
const loading = ref(false)
const certLoading = ref(false)
const certInfo = ref<{ exists: boolean; certPath?: string; expiresAt?: string }>({ exists: false })
const fallbackError = '主进程未返回错误信息，请查看终端日志'
const missingElectronApiError = '当前窗口未连接 Electron 主进程。请关闭旧窗口，使用 npm run dev 启动的桌面窗口，不要在浏览器里打开 localhost 页面。'

onMounted(async () => {
  isElectronWindow.value = Boolean(window.electronAPI)
  if (!window.electronAPI) return

  const status = await window.electronAPI.getProxyStatus()
  systemProxyEnabled.value = status?.enabled ?? false
  await refreshCertInfo()
})

async function refreshCertInfo() {
  if (!window.electronAPI) return
  certInfo.value = await window.electronAPI.getCertInfo()
}

async function toggleSystemProxy() {
  const api = window.electronAPI
  if (!api) {
    alert(missingElectronApiError)
    return
  }

  loading.value = true
  try {
    if (systemProxyEnabled.value) {
      const result = await api.disableSystemProxy()
      if (result?.success) {
        systemProxyEnabled.value = false
      } else {
        alert(`关闭系统代理失败：${result?.error ?? fallbackError}`)
      }
      return
    }

    const result = await api.enableSystemProxy(props.port)
    if (result?.success) {
      systemProxyEnabled.value = true
    } else {
      alert(`开启系统代理失败（可能需要管理员权限）：${result?.error ?? fallbackError}`)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    alert(`系统代理操作失败：${message || fallbackError}`)
  } finally {
    loading.value = false
  }
}

async function generateCert() {
  const api = window.electronAPI
  if (!api) {
    alert(missingElectronApiError)
    return
  }

  certLoading.value = true
  try {
    const result = await api.generateCert()
    if (!result?.success) {
      alert(`生成证书失败：${result?.error ?? fallbackError}`)
      return
    }
    await refreshCertInfo()
    alert(`证书已生成：${result.certPath}`)
  } finally {
    certLoading.value = false
  }
}

async function installCert() {
  const api = window.electronAPI
  if (!api) {
    alert(missingElectronApiError)
    return
  }

  certLoading.value = true
  try {
    if (!certInfo.value.exists) {
      const generated = await api.generateCert()
      if (!generated?.success) {
        alert(`生成证书失败：${generated?.error ?? fallbackError}`)
        return
      }
      await refreshCertInfo()
    }
    const result = await api.installCert()
    if (result?.success) {
      alert('证书已安装到系统信任列表。请重启浏览器或被测客户端。')
    } else {
      alert(`安装证书失败：${result?.error ?? fallbackError}`)
    }
  } finally {
    certLoading.value = false
  }
}

async function uninstallCert() {
  const api = window.electronAPI
  if (!api) {
    alert(missingElectronApiError)
    return
  }
  if (!confirm('确定从系统钥匙串中卸载 oAT 根证书？卸载后 HTTPS/WSS 捕获将失效。')) return

  certLoading.value = true
  try {
    const result = await api.uninstallCert()
    if (result?.success) {
      alert('证书信任已卸载。必要时请重启浏览器或被测客户端。')
    } else {
      alert(`卸载证书失败：${result?.error ?? fallbackError}`)
    }
  } finally {
    certLoading.value = false
  }
}

async function openCertFolder() {
  const api = window.electronAPI
  if (!api) {
    alert(missingElectronApiError)
    return
  }
  if (!certInfo.value.exists) {
    await generateCert()
  }
  await api.openCertFolder()
}
</script>

<template>
  <div class="proxy-control">
    <span class="label">系统代理</span>
    <button
      class="toggle-btn"
      :class="{ active: systemProxyEnabled, loading, unavailable: !isElectronWindow }"
      :disabled="loading || !isElectronWindow"
      @click="toggleSystemProxy"
    >
      <span class="dot"></span>
      {{
        !isElectronWindow
          ? '浏览器预览'
          : loading
            ? '处理中...'
            : systemProxyEnabled
              ? `已启用 (127.0.0.1:${props.port})`
              : '未启用'
      }}
    </button>
    <span class="hint">
      {{ isElectronWindow ? '启用后系统 HTTP/HTTPS 流量会经过本地代理' : '系统代理只能在 Electron 桌面窗口中启用' }}
    </span>
    <div class="cert-actions">
      <span class="label">HTTPS 证书</span>
      <button class="mini-btn" :disabled="certLoading || !isElectronWindow" @click="generateCert">
        {{ certInfo.exists ? '重新生成' : '生成证书' }}
      </button>
      <button class="mini-btn" :disabled="certLoading || !isElectronWindow" @click="installCert">安装信任</button>
      <button class="mini-btn" :disabled="certLoading || !isElectronWindow" @click="uninstallCert">卸载信任</button>
      <button class="mini-btn" :disabled="certLoading || !isElectronWindow" @click="openCertFolder">打开目录</button>
      <span class="hint">
        {{ certInfo.exists ? `已生成，有效期至 ${certInfo.expiresAt}` : '未生成' }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.proxy-control {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.label {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.92);
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
  color: white;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.toggle-btn.active {
  background: #f6ffed;
  border-color: #52c41a;
  color: #237804;
}

.toggle-btn.loading {
  opacity: 0.65;
  cursor: not-allowed;
}

.toggle-btn.unavailable {
  opacity: 0.7;
  cursor: not-allowed;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.65);
}

.toggle-btn.active .dot {
  background: #52c41a;
}

.hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}

.cert-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
}

.mini-btn {
  height: 26px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.12);
  color: white;
  font-size: 12px;
  cursor: pointer;
}

.mini-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
}

.mini-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>

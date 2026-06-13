import fs from 'fs'
import path from 'path'
import { pathToFileURL } from 'url'
import { app } from 'electron'
import type { PluginInfo, PluginManifest, TrafficRecord } from '../types.js'

type PluginModule = {
  onRecordCaptured?: (record: TrafficRecord) => TrafficRecord | null | Promise<TrafficRecord | null>
  beforeSave?: (record: TrafficRecord) => TrafficRecord | null | Promise<TrafficRecord | null>
}

type LoadedPlugin = {
  info: PluginInfo
  module?: PluginModule
}

const loadedPlugins = new Map<string, LoadedPlugin>()

function pluginsRoot(): string {
  return path.join(app.getPath('userData'), 'plugins')
}

function readManifest(pluginDir: string): PluginManifest | null {
  const manifestPath = path.join(pluginDir, 'plugin.json')
  if (!fs.existsSync(manifestPath)) return null
  return JSON.parse(fs.readFileSync(manifestPath, 'utf-8')) as PluginManifest
}

export async function loadPlugins(): Promise<PluginInfo[]> {
  loadedPlugins.clear()
  const root = pluginsRoot()
  fs.mkdirSync(root, { recursive: true })
  const entries = fs.readdirSync(root, { withFileTypes: true }).filter(entry => entry.isDirectory())

  for (const entry of entries) {
    const pluginDir = path.join(root, entry.name)
    try {
      const manifest = readManifest(pluginDir)
      if (!manifest?.id || !manifest.name || !manifest.version || !manifest.main) continue
      const info: PluginInfo = {
        ...manifest,
        enabled: manifest.enabled !== false,
        path: pluginDir
      }
      if (info.enabled) {
        const modulePath = path.join(pluginDir, manifest.main)
        const moduleUrl = `${pathToFileURL(modulePath).href}?t=${Date.now()}`
        const module = await import(moduleUrl) as PluginModule
        loadedPlugins.set(info.id, { info, module })
      } else {
        loadedPlugins.set(info.id, { info })
      }
    } catch (error: any) {
      const fallback: PluginInfo = {
        id: entry.name,
        name: entry.name,
        version: 'unknown',
        main: '',
        enabled: false,
        path: pluginDir,
        error: error?.message ?? String(error)
      }
      loadedPlugins.set(fallback.id, { info: fallback })
    }
  }

  return listPlugins()
}

export function listPlugins(): PluginInfo[] {
  return [...loadedPlugins.values()].map(plugin => plugin.info)
}

export function getPluginsPath(): string {
  const root = pluginsRoot()
  fs.mkdirSync(root, { recursive: true })
  return root
}

export async function runRecordCapturedHooks(record: TrafficRecord): Promise<TrafficRecord | null> {
  let next: TrafficRecord | null = record
  for (const plugin of loadedPlugins.values()) {
    if (!next || !plugin.info.enabled || !plugin.module?.onRecordCaptured) continue
    try {
      next = await plugin.module.onRecordCaptured(next)
    } catch (error: any) {
      plugin.info.error = error?.message ?? String(error)
    }
  }
  return next
}

export async function runBeforeSaveHooks(record: TrafficRecord): Promise<TrafficRecord | null> {
  let next: TrafficRecord | null = record
  for (const plugin of loadedPlugins.values()) {
    if (!next || !plugin.info.enabled || !plugin.module?.beforeSave) continue
    try {
      next = await plugin.module.beforeSave(next)
    } catch (error: any) {
      plugin.info.error = error?.message ?? String(error)
    }
  }
  return next
}

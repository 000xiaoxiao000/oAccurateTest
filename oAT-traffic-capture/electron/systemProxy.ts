import { exec } from 'child_process'
import { promisify } from 'util'

const execAsync = promisify(exec)

export interface ProxyConfig {
  port: number
  bypass?: string[]
}

function shellQuote(value: string): string {
  return `"${value.replace(/(["\\$`])/g, '\\$1')}"`
}

function appleScriptQuote(value: string): string {
  return `"${value.replace(/(["\\])/g, '\\$1')}"`
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return String(error)
}

async function execWithAdmin(command: string): Promise<void> {
  try {
    await execAsync(`osascript -e ${shellQuote(`do shell script ${appleScriptQuote(command)} with administrator privileges`)}`)
  } catch (error: any) {
    const message = errorMessage(error)
    if (message.includes('User canceled') || message.includes('-128')) {
      throw new Error('用户取消了管理员授权')
    }
    throw new Error(message)
  }
}

async function runNetworkSetupBatch(commands: string[]): Promise<void> {
  const script = [
    'set -u',
    'errors=""',
    ...commands.map((command, index) => `(${command}) || errors="$errors\\n[${index + 1}] ${command}"`),
    'if [ -n "$errors" ]; then',
    '  echo "$errors" >&2',
    '  exit 1',
    'fi'
  ].join('\n')

  await execWithAdmin(script)
}

export async function getNetworkServices(): Promise<string[]> {
  try {
    const { stdout } = await execAsync('networksetup -listallnetworkservices')
    return stdout
      .split('\n')
      .slice(1)
      .filter(line => line.trim() && !line.startsWith('*'))
      .map(line => line.trim())
  } catch (error) {
    console.error('Failed to list network services:', error)
    return ['Wi-Fi', 'Ethernet']
  }
}

export async function enableSystemProxy(config: ProxyConfig): Promise<void> {
  const services = await getNetworkServices()
  const { port, bypass = ['localhost', '127.0.0.1', '*.local'] } = config
  if (services.length === 0) {
    throw new Error('未找到可配置的网络服务')
  }

  const bypassDomains = bypass.map(shellQuote).join(' ')
  const commands = services.flatMap((service) => {
    const quotedService = shellQuote(service)
    const serviceCommands = [
      `networksetup -setwebproxy ${quotedService} 127.0.0.1 ${port}`,
      `networksetup -setsecurewebproxy ${quotedService} 127.0.0.1 ${port}`,
      `networksetup -setwebproxystate ${quotedService} on`,
      `networksetup -setsecurewebproxystate ${quotedService} on`
    ]

    if (bypassDomains) {
      serviceCommands.push(`networksetup -setproxybypassdomains ${quotedService} ${bypassDomains}`)
    }

    return serviceCommands
  })

  await runNetworkSetupBatch(commands)
}

export async function disableSystemProxy(): Promise<void> {
  const services = await getNetworkServices()
  if (services.length === 0) {
    throw new Error('未找到可配置的网络服务')
  }

  const commands = services.flatMap((service) => {
    const quotedService = shellQuote(service)
    return [
      `networksetup -setwebproxystate ${quotedService} off`,
      `networksetup -setsecurewebproxystate ${quotedService} off`
    ]
  })

  await runNetworkSetupBatch(commands)
}

export async function getSystemProxyStatus(): Promise<{ enabled: boolean; port?: number }> {
  try {
    const services = await getNetworkServices()
    if (services.length === 0) return { enabled: false }

    const { stdout } = await execAsync(`networksetup -getwebproxy ${shellQuote(services[0])}`)
    const lines = stdout.split('\n')
    const enabled = lines.some(line => line.includes('Enabled: Yes'))
    
    if (enabled) {
      const portLine = lines.find(line => line.includes('Port:'))
      const port = portLine ? parseInt(portLine.split(':')[1].trim()) : undefined
      return { enabled: true, port }
    }
    
    return { enabled: false }
  } catch (error) {
    return { enabled: false }
  }
}

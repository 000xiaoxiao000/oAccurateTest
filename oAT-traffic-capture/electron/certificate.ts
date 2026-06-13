import forge from 'node-forge'
import fs from 'fs'
import path from 'path'
import { app, shell } from 'electron'
import { exec, execFile } from 'child_process'
import { promisify } from 'util'

const execAsync = promisify(exec)
const execFileAsync = promisify(execFile)

export interface CertInfo {
  exists: boolean
  certPath?: string
  expiresAt?: string
}

function getCertDir(): string {
  return path.join(app.getPath('userData'), 'certs')
}

export function generateRootCert(): { certPath: string; keyPath: string } {
  const certDir = getCertDir()
  if (!fs.existsSync(certDir)) fs.mkdirSync(certDir, { recursive: true })

  const keys = forge.pki.rsa.generateKeyPair(2048)
  const cert = forge.pki.createCertificate()
  cert.publicKey = keys.publicKey
  cert.serialNumber = Date.now().toString(16)
  cert.validity.notBefore = new Date()
  cert.validity.notAfter = new Date()
  cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 10)

  const attrs = [
    { name: 'commonName', value: 'oAT Traffic Capture Root CA' },
    { name: 'organizationName', value: 'oAT Traffic Capture' }
  ]
  cert.setSubject(attrs)
  cert.setIssuer(attrs)
  cert.setExtensions([
    { name: 'basicConstraints', cA: true },
    { name: 'keyUsage', keyCertSign: true, digitalSignature: true, cRLSign: true }
  ])
  cert.sign(keys.privateKey, forge.md.sha256.create())

  const certPath = path.join(certDir, 'ca.pem')
  const keyPath = path.join(certDir, 'ca.key')
  fs.writeFileSync(certPath, forge.pki.certificateToPem(cert))
  fs.writeFileSync(keyPath, forge.pki.privateKeyToPem(keys.privateKey))

  return { certPath, keyPath }
}

export function getCertInfo(): CertInfo {
  const certPath = path.join(getCertDir(), 'ca.pem')
  if (!fs.existsSync(certPath)) return { exists: false }

  const cert = forge.pki.certificateFromPem(fs.readFileSync(certPath, 'utf-8'))
  return {
    exists: true,
    certPath,
    expiresAt: cert.validity.notAfter.toLocaleDateString('zh-CN')
  }
}

export async function installCertMacOS(certPath: string): Promise<{ success: boolean; error?: string }> {
  const loginKeychain = path.join(app.getPath('home'), 'Library/Keychains/login.keychain-db')
  try {
    await execFileAsync('security', ['add-trusted-cert', '-r', 'trustRoot', '-k', loginKeychain, certPath])
    return { success: true }
  } catch (error: any) {
    try {
      const script = [
        `set certPath to POSIX path of ${JSON.stringify(certPath)}`,
        'do shell script "security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain " & quoted form of certPath with administrator privileges'
      ].join('\n')
      await execFileAsync('osascript', ['-e', script])
      return { success: true }
    } catch (adminError: any) {
      return {
        success: false,
        error: [
          '自动安装失败。',
          '请点击“打开目录”，手动将 ca.pem 导入“钥匙串访问”，并设置为“始终信任”。',
          adminError?.message || error?.message
        ].filter(Boolean).join('\n')
      }
    }
  }
}

export async function uninstallCertMacOS(): Promise<{ success: boolean; error?: string }> {
  const loginKeychain = path.join(app.getPath('home'), 'Library/Keychains/login.keychain-db')
  const commonName = 'oAT Traffic Capture Root CA'
  const errors: string[] = []

  function isNotFound(message: string): boolean {
    return /could not be found|unable to delete certificate matching|SecCertificateSearchCopyNext/i.test(message)
  }

  for (const keychain of [loginKeychain, '/Library/Keychains/System.keychain']) {
    try {
      await execFileAsync('security', ['delete-certificate', '-c', commonName, keychain])
    } catch (error: any) {
      const message = error?.stderr || error?.message || String(error)
      if (!isNotFound(message)) {
        errors.push(message)
      }
    }
  }

  if (errors.length === 0) {
    return { success: true }
  }

  try {
    const script = [
      `set certName to ${JSON.stringify(commonName)}`,
      'do shell script "security delete-certificate -c " & quoted form of certName & " /Library/Keychains/System.keychain" with administrator privileges'
    ].join('\n')
    await execFileAsync('osascript', ['-e', script])
    return { success: true }
  } catch (error: any) {
    const message = error?.stderr || error?.message || String(error)
    if (isNotFound(message)) {
      return { success: true }
    }
    return {
      success: false,
      error: [
        '自动卸载失败。',
        '请打开“钥匙串访问”，搜索 oAT Traffic Capture Root CA，手动删除该证书。',
        error?.message || errors.join('\n')
      ].filter(Boolean).join('\n')
    }
  }
}

export function openCertFolder(certPath: string): void {
  shell.showItemInFolder(certPath)
}

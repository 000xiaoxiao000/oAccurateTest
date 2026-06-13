import forge from 'node-forge'
import fs from 'fs'
import path from 'path'
import { app, shell } from 'electron'
import { exec } from 'child_process'
import { promisify } from 'util'

const execAsync = promisify(exec)

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
  try {
    await execAsync(`security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain "${certPath}"`)
    return { success: true }
  } catch (error: any) {
    return { success: false, error: error.message }
  }
}

export function openCertFolder(certPath: string): void {
  shell.showItemInFolder(certPath)
}

import Database from 'better-sqlite3'
import path from 'path'
import { app } from 'electron'
import type { CaptureSession, TrafficRecord } from './types.js'

let db: Database.Database | null = null

function getDb(): Database.Database {
  if (!db) {
    throw new Error('Database has not been initialized')
  }
  return db
}

export function initDatabase(): void {
  const dbPath = path.join(app.getPath('userData'), 'traffic.db')
  db = new Database(dbPath)
  db.exec(`
    CREATE TABLE IF NOT EXISTS sessions (
      id TEXT PRIMARY KEY,
      case_name TEXT NOT NULL,
      start_time INTEGER NOT NULL,
      end_time INTEGER
    );

    CREATE TABLE IF NOT EXISTS records (
      id TEXT PRIMARY KEY,
      session_id TEXT,
      method TEXT,
      url TEXT,
      protocol TEXT,
      status_code TEXT,
      duration INTEGER,
      timestamp INTEGER,
      request_headers TEXT,
      request_body TEXT,
      response_headers TEXT,
      response_body TEXT,
      error TEXT,
      FOREIGN KEY(session_id) REFERENCES sessions(id)
    );

    CREATE INDEX IF NOT EXISTS idx_records_url ON records(url);
    CREATE INDEX IF NOT EXISTS idx_records_timestamp ON records(timestamp);
    CREATE INDEX IF NOT EXISTS idx_records_session ON records(session_id);
  `)
}

export function saveSession(session: Omit<CaptureSession, 'records'>): void {
  getDb()
    .prepare('INSERT OR REPLACE INTO sessions (id, case_name, start_time, end_time) VALUES (?, ?, ?, ?)')
    .run(session.id, session.caseName, session.startTime, session.endTime ?? null)
}

export function updateSessionEndTime(sessionId: string, endTime: number): void {
  getDb().prepare('UPDATE sessions SET end_time = ? WHERE id = ?').run(endTime, sessionId)
}

export function saveRecord(record: TrafficRecord, sessionId?: string): void {
  getDb()
    .prepare(`
      INSERT OR REPLACE INTO records
      (id, session_id, method, url, protocol, status_code, duration, timestamp,
       request_headers, request_body, response_headers, response_body, error)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `)
    .run(
      record.id,
      sessionId || null,
      record.method,
      record.url,
      record.protocol,
      String(record.statusCode),
      record.duration,
      record.timestamp,
      JSON.stringify(record.requestHeaders ?? {}),
      record.requestBody ?? '',
      JSON.stringify(record.responseHeaders ?? {}),
      record.responseBody ?? '',
      record.error ?? null
    )
}

export function listSessions(): Array<{
  id: string
  case_name: string
  start_time: number
  end_time: number | null
  record_count: number
}> {
  return getDb()
    .prepare(`
      SELECT s.id, s.case_name, s.start_time, s.end_time, COUNT(r.id) AS record_count
      FROM sessions s
      LEFT JOIN records r ON r.session_id = s.id
      GROUP BY s.id
      ORDER BY s.start_time DESC
      LIMIT 100
    `)
    .all() as Array<{
      id: string
      case_name: string
      start_time: number
      end_time: number | null
      record_count: number
    }>
}

export function loadSessionRecords(sessionId: string): TrafficRecord[] {
  const rows = getDb()
    .prepare('SELECT * FROM records WHERE session_id = ? ORDER BY timestamp ASC')
    .all(sessionId) as Array<Record<string, any>>

  return rows.map((row) => ({
    id: row.id,
    caseName: '',
    method: row.method,
    url: row.url,
    protocol: row.protocol,
    statusCode: row.status_code,
    duration: row.duration,
    timestamp: row.timestamp,
    requestHeaders: JSON.parse(row.request_headers || '{}'),
    requestBody: row.request_body,
    responseHeaders: JSON.parse(row.response_headers || '{}'),
    responseBody: row.response_body,
    error: row.error ?? undefined
  }))
}

export function deleteSession(sessionId: string): void {
  const database = getDb()
  database.prepare('DELETE FROM records WHERE session_id = ?').run(sessionId)
  database.prepare('DELETE FROM sessions WHERE id = ?').run(sessionId)
}

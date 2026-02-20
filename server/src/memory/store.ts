import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { SiteMemory, MemoryEntry } from '../types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export class MemoryStore {
  private dataDir: string;

  constructor(dataDir?: string) {
    this.dataDir = dataDir ?? path.resolve(__dirname, '../../data');
    if (!fs.existsSync(this.dataDir)) {
      fs.mkdirSync(this.dataDir, { recursive: true });
    }
  }

  // ------------------------------------------------------------------
  // Read
  // ------------------------------------------------------------------

  getMemory(domain: string): SiteMemory | null {
    const filePath = this.filePath(domain);
    if (!fs.existsSync(filePath)) return null;
    try {
      const raw = fs.readFileSync(filePath, 'utf-8');
      return JSON.parse(raw) as SiteMemory;
    } catch (err) {
      console.warn(`[MemoryStore] Failed to read ${domain}:`, (err as Error).message);
      return null;
    }
  }

  getAllMemories(): Record<string, SiteMemory> {
    const result: Record<string, SiteMemory> = {};
    try {
      const files = fs.readdirSync(this.dataDir).filter((f) => f.endsWith('.json'));
      for (const file of files) {
        const domain = file.replace(/\.json$/, '');
        const mem = this.getMemory(domain);
        if (mem) result[domain] = mem;
      }
    } catch { /* ignore */ }
    return result;
  }

  // ------------------------------------------------------------------
  // Write
  // ------------------------------------------------------------------

  saveEntry(domain: string, key: string, content: string): void {
    let memory = this.getMemory(domain);
    const now = new Date().toISOString();

    if (!memory) {
      memory = {
        domain,
        entries: [],
        lastVisited: now,
      };
    }

    memory.lastVisited = now;

    const existing = memory.entries.find((e) => e.key === key);
    if (existing) {
      existing.content = content;
      existing.updatedAt = now;
      existing.usedCount += 1;
    } else {
      const entry: MemoryEntry = {
        key,
        content,
        createdAt: now,
        updatedAt: now,
        usedCount: 1,
      };
      memory.entries.push(entry);
    }

    this.write(domain, memory);
  }

  // ------------------------------------------------------------------
  // Formatted output for Claude context
  // ------------------------------------------------------------------

  getFormattedMemory(domain: string): string {
    const mem = this.getMemory(domain);
    if (!mem || mem.entries.length === 0) return '';

    const lines = [`Site: ${domain}`];
    for (const entry of mem.entries) {
      lines.push(`- ${entry.key}: ${entry.content}`);
    }
    return lines.join('\n');
  }

  // ------------------------------------------------------------------
  // Private
  // ------------------------------------------------------------------

  private filePath(domain: string): string {
    // Sanitise domain to be a valid filename
    const safe = domain.replace(/[^a-zA-Z0-9._-]/g, '_');
    return path.join(this.dataDir, `${safe}.json`);
  }

  private write(domain: string, memory: SiteMemory): void {
    const filePath = this.filePath(domain);
    try {
      fs.writeFileSync(filePath, JSON.stringify(memory, null, 2), 'utf-8');
    } catch (err) {
      console.error(`[MemoryStore] Failed to write ${domain}:`, (err as Error).message);
    }
  }
}

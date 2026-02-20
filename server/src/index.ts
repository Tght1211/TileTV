import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { WebSocketServer } from 'ws';
import { config } from './config.js';
import { BrowserManager } from './browser/manager.js';
import { MemoryStore } from './memory/store.js';
import { createConnectionHandler } from './ws/handler.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// ------------------------------------------------------------------
// Utility: get local IP
// ------------------------------------------------------------------

function getLocalIP(): string {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    const entries = interfaces[name];
    if (!entries) continue;
    for (const entry of entries) {
      if (entry.family === 'IPv4' && !entry.internal) {
        return entry.address;
      }
    }
  }
  return '127.0.0.1';
}

// ------------------------------------------------------------------
// Main
// ------------------------------------------------------------------

async function main(): Promise<void> {
  console.log('='.repeat(50));
  console.log('  TileTV AI Navigation Server v2.0');
  console.log('='.repeat(50));

  // Validate config
  if (!config.anthropicApiKey) {
    console.warn(
      '[WARN] ANTHROPIC_API_KEY is not set. AI features will not work.',
    );
    console.warn('       Set it via environment variable or .env file.');
  }

  // ---- Express ----
  const app = express();
  const server = http.createServer(app);

  // Serve H5 static files
  const h5Dir = path.resolve(__dirname, '../h5');
  app.use('/h5', express.static(h5Dir));

  // JSON body parser for API routes
  app.use(express.json());

  // ---- Memory Store ----
  const memoryStore = new MemoryStore();

  // API routes
  app.get('/api/status', (_req, res) => {
    res.json({
      status: 'running',
      version: '2.0.0',
      viewport: {
        width: config.viewportWidth,
        height: config.viewportHeight,
      },
      model: config.claudeModel,
      uptime: process.uptime(),
    });
  });

  app.get('/api/memory/:domain', (req, res) => {
    const domain = req.params.domain;
    const memory = memoryStore.getMemory(domain);
    if (memory) {
      res.json(memory);
    } else {
      res.status(404).json({ error: 'No memory for this domain' });
    }
  });

  app.get('/api/memory', (_req, res) => {
    res.json(memoryStore.getAllMemories());
  });

  // ---- Browser Manager ----
  const browserManager = new BrowserManager();
  await browserManager.init();

  // ---- WebSocket Server ----
  const wss = new WebSocketServer({ server, path: '/ws' });
  const handleConnection = createConnectionHandler(browserManager, memoryStore);

  wss.on('connection', (ws) => {
    handleConnection(ws);
  });

  // ---- Start ----
  const localIP = getLocalIP();

  server.listen(config.port, () => {
    console.log('');
    console.log(`  Server:     http://localhost:${config.port}`);
    console.log(`  LAN:        http://${localIP}:${config.port}`);
    console.log(`  WebSocket:  ws://${localIP}:${config.port}/ws`);
    console.log(`  H5 Remote:  http://${localIP}:${config.port}/h5`);
    console.log(`  API Status: http://localhost:${config.port}/api/status`);
    console.log('');
    console.log(`  Browser:    ${config.viewportWidth}x${config.viewportHeight} headless=${config.browserHeadless}`);
    console.log(`  AI Model:   ${config.claudeModel}`);
    console.log('');
    console.log('  Ready for connections.');
    console.log('='.repeat(50));
  });

  // ---- Graceful shutdown ----
  const shutdown = async (signal: string) => {
    console.log(`\n[${signal}] Shutting down...`);
    wss.close();
    await browserManager.close();
    server.close();
    process.exit(0);
  };

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});

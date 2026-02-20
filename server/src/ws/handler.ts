import type { WebSocket } from 'ws';
import { config } from '../config.js';
import type { BrowserManager } from '../browser/manager.js';
import type { MemoryStore } from '../memory/store.js';
import { NavigationAgent } from '../agent/navigator.js';
import type { ClientMessage, ServerMessage } from '../types.js';

export function createConnectionHandler(
  browserManager: BrowserManager,
  memoryStore: MemoryStore,
) {
  return function handleConnection(ws: WebSocket): void {
    const agent = new NavigationAgent(browserManager, memoryStore);
    console.log('[WS] Client connected');

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    function send(msg: ServerMessage): void {
      if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(msg));
      }
    }

    function sendFrame(buffer: Buffer): void {
      send({
        type: 'frame',
        data: buffer.toString('base64'),
        width: config.viewportWidth,
        height: config.viewportHeight,
      });
    }

    function sendStatus(text: string, level: string): void {
      send({
        type: 'status',
        text,
        level: level as 'info' | 'thinking' | 'done' | 'error',
      });
    }

    // ------------------------------------------------------------------
    // Message handler
    // ------------------------------------------------------------------

    ws.on('message', async (raw) => {
      let msg: ClientMessage;
      try {
        msg = JSON.parse(raw.toString()) as ClientMessage;
      } catch {
        console.warn('[WS] Invalid message received');
        return;
      }

      try {
        switch (msg.type) {
          case 'open': {
            sendStatus(`正在打开 ${msg.name}...`, 'info');
            await browserManager.navigate(msg.url);
            agent.clearNavMap();

            // Send initial screenshot
            const frame = await browserManager.screenshot();
            sendFrame(frame);

            // Auto-analyze page
            await agent.analyzePage(sendStatus);

            // Send post-analysis screenshot (with highlight)
            sendFrame(await browserManager.screenshot());
            break;
          }

          case 'dpad': {
            const result = await agent.handleDpad(msg.direction, sendStatus);
            sendFrame(result.screenshot);
            if (result.focusRect) {
              send({ type: 'focus', rect: result.focusRect });
            }
            break;
          }

          case 'back': {
            const backResult = await agent.handleBack();
            sendFrame(backResult.screenshot);
            if (!backResult.wentBack) {
              send({ type: 'toast', text: '已是最后一页' });
            }
            break;
          }

          case 'voice': {
            const voiceScreenshot = await agent.handleVoiceCommand(
              msg.text,
              sendStatus,
            );
            sendFrame(voiceScreenshot);
            break;
          }

          case 'cursor': {
            const cursorScreenshot = await agent.handleCursor(
              msg.action,
              msg.x,
              msg.y,
            );
            sendFrame(cursorScreenshot);
            break;
          }

          case 'home': {
            agent.clearNavMap();
            send({ type: 'toast', text: '已返回首页' });
            break;
          }

          case 'ping': {
            const info = await browserManager.getCurrentInfo();
            send({ type: 'pong', url: info.url, title: info.title });
            break;
          }

          default: {
            console.warn(`[WS] Unknown message type: ${(msg as { type: string }).type}`);
          }
        }
      } catch (err) {
        console.error(`[WS] Error handling message "${msg.type}":`, (err as Error).message);
        sendStatus(`处理出错: ${(err as Error).message}`, 'error');
      }
    });

    ws.on('close', () => {
      console.log('[WS] Client disconnected');
      browserManager.stopScreenshotStream();
    });

    ws.on('error', (err) => {
      console.error('[WS] WebSocket error:', err.message);
    });

    // Welcome
    send({ type: 'status', text: 'TileTV Server 已连接', level: 'done' });
  };
}

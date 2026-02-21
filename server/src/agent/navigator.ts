import Anthropic from '@anthropic-ai/sdk';
import type { MessageParam, ToolResultBlockParam, ImageBlockParam, TextBlockParam, Tool } from '@anthropic-ai/sdk/resources/messages.js';
import type { Page } from 'playwright';
import { config } from '../config.js';
import type { BrowserManager } from '../browser/manager.js';
import type { MemoryStore } from '../memory/store.js';
import type { FocusRect } from '../types.js';

const MAX_ITERATIONS = 15;

// ================================================================
// Tool definitions — standard Claude tool-use format
// ================================================================

const browserTools: Tool[] = [
  {
    name: 'click',
    description:
      'Click at specific x,y coordinates on the page screenshot. Look at the screenshot carefully to determine the right position. Coordinates are in pixels, origin is top-left.',
    input_schema: {
      type: 'object' as const,
      properties: {
        x: { type: 'number', description: 'X coordinate (pixels from left)' },
        y: { type: 'number', description: 'Y coordinate (pixels from top)' },
      },
      required: ['x', 'y'],
    },
  },
  {
    name: 'type_text',
    description:
      'Type text using the keyboard. The text will be typed into whatever element currently has focus. Use click first to focus an input field, then type_text to enter text.',
    input_schema: {
      type: 'object' as const,
      properties: {
        text: { type: 'string', description: 'Text to type' },
      },
      required: ['text'],
    },
  },
  {
    name: 'press_key',
    description:
      'Press a keyboard key. Common keys: Enter, Tab, Escape, Backspace, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Space. For combos use + like Control+a, Control+c.',
    input_schema: {
      type: 'object' as const,
      properties: {
        key: { type: 'string', description: 'Key name (e.g. Enter, Tab, Escape, Control+a)' },
      },
      required: ['key'],
    },
  },
  {
    name: 'scroll',
    description:
      'Scroll the page. Use direction "up" or "down". Default scrolls 400 pixels.',
    input_schema: {
      type: 'object' as const,
      properties: {
        direction: {
          type: 'string',
          enum: ['up', 'down'],
          description: 'Scroll direction',
        },
        amount: { type: 'number', description: 'Pixels to scroll (default 400)' },
      },
      required: ['direction'],
    },
  },
  {
    name: 'navigate',
    description:
      'Navigate the browser to a URL. Use this to go to a specific website. Example: navigate({url: "https://www.bilibili.com"})',
    input_schema: {
      type: 'object' as const,
      properties: {
        url: { type: 'string', description: 'Full URL to navigate to' },
      },
      required: ['url'],
    },
  },
  {
    name: 'go_back',
    description: 'Go back to the previous page in browser history.',
    input_schema: {
      type: 'object' as const,
      properties: {},
    },
  },
  {
    name: 'wait',
    description: 'Wait for a specified number of seconds for the page to load or update.',
    input_schema: {
      type: 'object' as const,
      properties: {
        seconds: { type: 'number', description: 'Seconds to wait (1-10, default 2)' },
      },
    },
  },
  {
    name: 'screenshot',
    description:
      'Take a fresh screenshot of the current page to see what happened after an action. Use this when you need to observe the current state.',
    input_schema: {
      type: 'object' as const,
      properties: {},
    },
  },
  {
    name: 'save_memory',
    description:
      'Save a navigation insight about this website for future visits (e.g. where the search box is, how to navigate).',
    input_schema: {
      type: 'object' as const,
      properties: {
        key: { type: 'string', description: 'Memory key, e.g. "search_flow"' },
        content: { type: 'string', description: 'What to remember about this site' },
      },
      required: ['key', 'content'],
    },
  },
];

// ================================================================
// Helpers
// ================================================================

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Strip image blocks from older messages to prevent the request payload
 * from growing unboundedly. Keeps images only in the most recent N user
 * messages (tool_result rounds) that contain images.
 */
function compactMessages(messages: MessageParam[], keepRecentImages: number = 2): MessageParam[] {
  // Find indices of user messages that contain image blocks
  const imageUserIndices: number[] = [];
  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i];
    if (msg.role !== 'user') continue;
    const content = Array.isArray(msg.content) ? msg.content : [];
    const hasImage = content.some(
      (block: any) => block.type === 'image' || (block.type === 'tool_result' && Array.isArray(block.content) && block.content.some((c: any) => c.type === 'image')),
    );
    if (hasImage) imageUserIndices.push(i);
  }

  // If within budget, nothing to strip
  if (imageUserIndices.length <= keepRecentImages) return messages;

  // Indices to strip (all image-bearing user messages except the last N)
  const toStrip = new Set(imageUserIndices.slice(0, -keepRecentImages));

  return messages.map((msg, idx) => {
    if (!toStrip.has(idx)) return msg;
    // Strip images from this user message
    const content = Array.isArray(msg.content) ? msg.content : [];
    const stripped = content.map((block: any) => {
      if (block.type === 'image') {
        return { type: 'text', text: '[screenshot removed to save space]' } as TextBlockParam;
      }
      if (block.type === 'tool_result' && Array.isArray(block.content)) {
        return {
          ...block,
          content: block.content.map((c: any) =>
            c.type === 'image'
              ? { type: 'text', text: '[screenshot removed]' } as TextBlockParam
              : c,
          ),
        };
      }
      return block;
    });
    return { ...msg, content: stripped };
  });
}

function describeAction(
  toolName: string,
  input: Record<string, unknown>,
): string {
  switch (toolName) {
    case 'click':
      return `点击 (${input.x}, ${input.y})`;
    case 'type_text':
      return `输入 "${(input.text as string)?.slice(0, 30)}"`;
    case 'press_key':
      return `按键 ${input.key}`;
    case 'scroll':
      return `${input.direction === 'up' ? '向上' : '向下'}滚动`;
    case 'navigate':
      return `打开 ${input.url}`;
    case 'go_back':
      return '返回上一页';
    case 'wait':
      return `等待 ${input.seconds || 2}秒`;
    case 'screenshot':
      return '截图观察';
    case 'save_memory':
      return `记忆: ${input.key}`;
    default:
      return toolName;
  }
}

function buildSystemPrompt(url: string, memoryContext: string): string {
  return `你是一个电视遥控器AI助手。用户通过语音给你下达指令，你需要用工具操控浏览器来执行。

你可以看到浏览器的截图（${config.viewportWidth}x${config.viewportHeight}像素），根据截图内容精确操作。

操作规则:
- 仔细观察截图，理解当前页面状态后再行动
- 一步一步执行，每步操作后会自动获得新截图
- 需要打开新网站时，用 navigate 工具
- 搜索时：先用 click 点击搜索框 → 再用 type_text 输入文字 → 最后用 press_key 按 Enter
- 如果输入框有旧文字，先用 click 点击它，再用 press_key("Control+a") 全选，然后 type_text 输入新内容
- 点击坐标要精确，仔细看截图中元素的位置
- 页面跳转后用 wait 等待加载，再 screenshot 观察
- 遇到弹窗/广告时尝试找关闭按钮点击，找不到就忽略继续操作
- 某些网站的搜索会在新标签页中打开（如bilibili），按Enter或点击搜索后，用 wait 等待2-3秒让新页面加载，然后 screenshot 查看新页面
- 操作完成后简短总结你做了什么

当前URL: ${url}
${memoryContext ? `\n网站记忆:\n${memoryContext}` : ''}`;
}

// ================================================================
// NavigationAgent
// ================================================================

export class NavigationAgent {
  private client: Anthropic;
  private browserManager: BrowserManager;
  private memoryStore: MemoryStore;
  private isProcessing = false;

  constructor(browserManager: BrowserManager, memoryStore: MemoryStore) {
    this.browserManager = browserManager;
    this.memoryStore = memoryStore;

    const opts: { apiKey: string; baseURL?: string } = {
      apiKey: config.anthropicApiKey,
    };
    if (config.anthropicBaseUrl) {
      opts.baseURL = config.anthropicBaseUrl;
    }
    this.client = new Anthropic(opts);
  }

  // ================================================================
  // Voice command — Claude tool-use agentic loop
  // ================================================================

  async handleVoiceCommand(
    text: string,
    sendStatus: (text: string, level: string) => void,
    sendFrame: (buffer: Buffer) => void,
  ): Promise<Buffer> {
    if (this.isProcessing) {
      sendStatus('正在处理上一个指令，请稍等...', 'info');
      return this.browserManager.screenshot();
    }

    this.isProcessing = true;

    try {
      sendStatus(`AI 正在理解: "${text}"`, 'thinking');

      const screenshotBuf = await this.browserManager.screenshot();
      const { url } = await this.browserManager.getCurrentInfo();
      const domain = this.extractDomain(url);
      const memoryContext = this.memoryStore.getFormattedMemory(domain);

      const systemPrompt = buildSystemPrompt(url, memoryContext);

      const messages: MessageParam[] = [
        {
          role: 'user',
          content: [
            {
              type: 'image',
              source: {
                type: 'base64',
                media_type: 'image/jpeg',
                data: screenshotBuf.toString('base64'),
              },
            } as ImageBlockParam,
            {
              type: 'text',
              text: `用户语音指令: "${text}"`,
            } as TextBlockParam,
          ],
        },
      ];

      let iteration = 0;

      while (iteration < MAX_ITERATIONS) {
        iteration++;
        sendStatus(`AI 思考中... (第${iteration}步)`, 'thinking');

        // Compact old screenshots to prevent 413 payload too large
        const compacted = compactMessages(messages, 2);

        const response = await this.client.messages.create({
          model: config.claudeModel,
          max_tokens: 4096,
          system: systemPrompt,
          messages: compacted,
          tools: browserTools,
        });

        // Append assistant turn
        messages.push({ role: 'assistant', content: response.content });

        // Show Claude's text responses
        for (const block of response.content) {
          if (block.type === 'text' && block.text.trim()) {
            sendStatus(`AI: ${block.text.slice(0, 100)}`, 'thinking');
            console.log(`[AI text] ${block.text.slice(0, 120)}`);
          }
        }

        // Check if done
        if (response.stop_reason === 'end_turn') {
          const textBlocks = response.content.filter(
            (b): b is Anthropic.TextBlock => b.type === 'text',
          );
          if (textBlocks.length > 0) {
            sendStatus(textBlocks[textBlocks.length - 1].text.slice(0, 100), 'done');
          } else {
            sendStatus('完成', 'done');
          }
          break;
        }

        // Collect tool_use blocks
        const toolUseBlocks = response.content.filter(
          (b): b is Anthropic.ToolUseBlock => b.type === 'tool_use',
        );
        if (toolUseBlocks.length === 0) {
          sendStatus('完成', 'done');
          break;
        }

        // Execute each tool call
        const toolResults: ToolResultBlockParam[] = [];

        for (const block of toolUseBlocks) {
          const input = block.input as Record<string, unknown>;
          const desc = describeAction(block.name, input);
          sendStatus(`AI 执行: ${desc}`, 'thinking');
          console.log(`[AI tool] ${desc}`);

          // Execute the tool
          const textResult = await this.executeTool(block.name, input, domain);

          // Wait for page to settle — longer for actions that may trigger navigation
          const isNavAction =
            block.name === 'navigate' ||
            block.name === 'go_back' ||
            (block.name === 'press_key' && (input.key as string)?.toLowerCase() === 'enter') ||
            block.name === 'click';
          await delay(isNavAction ? 1500 : 500);

          // Take screenshot after action
          const postScreenshot = await this.browserManager.screenshot();

          // Send intermediate frame to all clients
          sendFrame(postScreenshot);

          // Build tool result — always include screenshot so Claude can see the result
          const resultContent: (TextBlockParam | ImageBlockParam)[] = [
            {
              type: 'image',
              source: {
                type: 'base64',
                media_type: 'image/jpeg',
                data: postScreenshot.toString('base64'),
              },
            } as ImageBlockParam,
          ];

          // Include text result if any
          if (textResult) {
            resultContent.unshift({
              type: 'text',
              text: textResult,
            } as TextBlockParam);
          }

          toolResults.push({
            type: 'tool_result',
            tool_use_id: block.id,
            content: resultContent,
          });
        }

        messages.push({ role: 'user', content: toolResults });
      }

      if (iteration >= MAX_ITERATIONS) {
        sendStatus('已达到最大步骤数', 'done');
      }

      // Update URL info
      const finalInfo = await this.browserManager.getCurrentInfo();
      const finalDomain = this.extractDomain(finalInfo.url);
      if (finalDomain !== domain) {
        // Save that we navigated to a new domain
        this.memoryStore.saveEntry(
          finalDomain,
          'visit',
          `用户说"${text}"后到达此站`,
        );
      }

      return this.browserManager.screenshot();
    } catch (err) {
      const errMsg = (err as Error).message;
      console.error('[NavigationAgent] handleVoiceCommand error:', errMsg);
      sendStatus(`AI 错误: ${errMsg.slice(0, 80)}`, 'error');
      return this.browserManager.screenshot();
    } finally {
      this.isProcessing = false;
    }
  }

  // ================================================================
  // Tool executor
  // ================================================================

  private async executeTool(
    name: string,
    input: Record<string, unknown>,
    domain: string,
  ): Promise<string | undefined> {
    const page = this.browserManager.getPage();

    try {
      switch (name) {
        case 'click': {
          const x = input.x as number;
          const y = input.y as number;
          await page.mouse.click(x, y);
          return `Clicked at (${x}, ${y})`;
        }

        case 'type_text': {
          const text = input.text as string;
          await page.keyboard.type(text, { delay: 30 });
          return `Typed "${text}"`;
        }

        case 'press_key': {
          const keyStr = input.key as string;
          // Handle combos like Control+a
          const parts = keyStr.split('+').map((k) => k.trim());
          if (parts.length > 1) {
            for (let i = 0; i < parts.length - 1; i++) {
              await page.keyboard.down(parts[i]);
            }
            await page.keyboard.press(parts[parts.length - 1]);
            for (let i = parts.length - 2; i >= 0; i--) {
              await page.keyboard.up(parts[i]);
            }
          } else {
            await page.keyboard.press(keyStr);
          }
          return `Pressed key: ${keyStr}`;
        }

        case 'scroll': {
          const direction = input.direction as string;
          const amount = (input.amount as number) ?? 400;
          if (direction === 'down') {
            await page.mouse.wheel(0, amount);
          } else {
            await page.mouse.wheel(0, -amount);
          }
          return `Scrolled ${direction} by ${amount}px`;
        }

        case 'navigate': {
          const url = input.url as string;
          await this.browserManager.navigate(url);
          return `Navigated to ${url}`;
        }

        case 'go_back': {
          await this.browserManager.goBack();
          return 'Went back';
        }

        case 'wait': {
          const seconds = Math.min((input.seconds as number) || 2, 10);
          await delay(seconds * 1000);
          return `Waited ${seconds}s`;
        }

        case 'screenshot': {
          // No-op; screenshot is captured and returned as tool_result anyway
          return 'Screenshot taken';
        }

        case 'save_memory': {
          this.memoryStore.saveEntry(
            domain,
            input.key as string,
            input.content as string,
          );
          return `Memory saved: ${input.key}`;
        }

        default:
          return `Unknown tool: ${name}`;
      }
    } catch (err) {
      const msg = (err as Error).message;
      console.error(`[executeTool] ${name} error:`, msg);
      return `Error: ${msg}`;
    }
  }

  // ================================================================
  // D-pad — simple keyboard arrows, no AI needed
  // ================================================================

  async handleDpad(
    direction: string,
    sendStatus: (text: string, level: string) => void,
  ): Promise<{ screenshot: Buffer; focusRect: FocusRect | null }> {
    const page = this.browserManager.getPage();

    if (direction === 'center') {
      await page.keyboard.press('Enter');
    } else {
      const keyMap: Record<string, string> = {
        up: 'ArrowUp',
        down: 'ArrowDown',
        left: 'ArrowLeft',
        right: 'ArrowRight',
      };
      await page.keyboard.press(keyMap[direction] || 'ArrowDown');
    }

    await delay(200);
    const screenshot = await this.browserManager.screenshot();
    return { screenshot, focusRect: null };
  }

  // ================================================================
  // Back / Cursor / Utilities
  // ================================================================

  async handleBack(): Promise<{ wentBack: boolean; screenshot: Buffer }> {
    const wentBack = await this.browserManager.goBack();
    const screenshot = await this.browserManager.screenshot();
    return { wentBack, screenshot };
  }

  async handleCursor(
    action: string,
    x: number,
    y: number,
  ): Promise<Buffer> {
    if (action === 'click') {
      const page = this.browserManager.getPage();
      await page.mouse.click(x, y);
      await delay(300);
    }
    return this.browserManager.screenshot();
  }

  clearNavMap(): void {
    // Kept for interface compatibility
  }

  // ================================================================
  // Helpers
  // ================================================================

  private extractDomain(url: string): string {
    try {
      return new URL(url).hostname;
    } catch {
      return 'unknown';
    }
  }
}

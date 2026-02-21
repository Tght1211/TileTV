import Anthropic from '@anthropic-ai/sdk';
import type { MessageParam, ContentBlockParam, ToolResultBlockParam, ImageBlockParam, TextBlockParam } from '@anthropic-ai/sdk/resources/messages.js';
import { config } from '../config.js';
import type { BrowserManager } from '../browser/manager.js';
import type { MemoryStore } from '../memory/store.js';
import { navigationTools, executeTool, type ToolExecContext } from './tools.js';
import type { NavigationMap, DirectionMap, FocusRect } from '../types.js';

const MAX_TOOL_ROUNDS = 10;

export class NavigationAgent {
  private client: Anthropic;
  private browserManager: BrowserManager;
  private memoryStore: MemoryStore;
  private currentNavMap: NavigationMap | null = null;
  private currentFocusIndex = -1;
  private isProcessing = false;

  constructor(browserManager: BrowserManager, memoryStore: MemoryStore) {
    this.browserManager = browserManager;
    this.memoryStore = memoryStore;
    const clientOptions: { apiKey: string; baseURL?: string } = {
      apiKey: config.anthropicApiKey,
    };
    if (config.anthropicBaseUrl) {
      clientOptions.baseURL = config.anthropicBaseUrl;
    }
    this.client = new Anthropic(clientOptions);
  }

  // ================================================================
  // Page analysis
  // ================================================================

  async analyzePage(
    sendStatus: (text: string, level: string) => void,
  ): Promise<NavigationMap> {
    sendStatus('正在分析页面...', 'thinking');

    const [screenshotBuf, elements] = await Promise.all([
      this.browserManager.screenshot(),
      this.browserManager.getInteractiveElements(),
    ]);

    if (elements.length === 0) {
      const emptyMap: NavigationMap = {
        elements: [],
        navigation: {},
        currentFocus: -1,
        pageDescription: 'No interactive elements found',
      };
      this.currentNavMap = emptyMap;
      sendStatus('页面没有找到可交互元素', 'done');
      return emptyMap;
    }

    const { url } = await this.browserManager.getCurrentInfo();
    const domain = this.extractDomain(url);
    const memoryContext = this.memoryStore.getFormattedMemory(domain);

    const elementsSummary = elements
      .map(
        (el) =>
          `[${el.index}] <${el.tag}> "${el.text}" at (${el.bounds.x},${el.bounds.y}) ${el.bounds.width}x${el.bounds.height} selector="${el.selector}"`,
      )
      .join('\n');

    const systemPrompt = `You are a TV remote navigation AI. Analyze the webpage screenshot and interactive elements to create a spatial navigation map.

For each element, determine which element is closest in each direction (up/down/left/right) based on visual position.

Rules:
- "right" means the nearest element to the RIGHT of current element
- "left" means the nearest element to the LEFT
- "up" means the nearest element ABOVE
- "down" means the nearest element BELOW
- Use element indices from the provided list
- Consider visual layout, not DOM order
- Recommend the best initial focus element (usually the main content area)
- Only include elements that are visible on screen

${memoryContext ? `Site memory context:\n${memoryContext}` : ''}

Return ONLY valid JSON in this format:
{
  "navigation": {
    "0": { "up": null, "down": 5, "left": null, "right": 1 },
    "1": { "up": null, "down": 6, "left": 0, "right": 2 }
  },
  "recommendedFocus": 0,
  "pageDescription": "Brief description of the page layout"
}`;

    const userContent: ContentBlockParam[] = [
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
        text: `Interactive elements on page:\n${elementsSummary}`,
      } as TextBlockParam,
    ];

    try {
      const response = await this.client.messages.create({
        model: config.claudeModel,
        max_tokens: 4096,
        system: systemPrompt,
        messages: [{ role: 'user', content: userContent }],
      });

      const textBlock = response.content.find((b) => b.type === 'text');
      const rawJson = textBlock && textBlock.type === 'text' ? textBlock.text : '{}';

      // Extract JSON from possible markdown code fences
      const jsonMatch = rawJson.match(/```(?:json)?\s*([\s\S]*?)```/) || [null, rawJson];
      const cleanJson = (jsonMatch[1] ?? rawJson).trim();

      const parsed = JSON.parse(cleanJson) as {
        navigation: Record<string, DirectionMap>;
        recommendedFocus: number;
        pageDescription: string;
      };

      const navMap: NavigationMap = {
        elements,
        navigation: parsed.navigation ?? {},
        currentFocus: parsed.recommendedFocus ?? 0,
        pageDescription: parsed.pageDescription ?? '',
      };

      this.currentNavMap = navMap;
      this.currentFocusIndex = navMap.currentFocus;

      // Highlight the recommended initial focus
      if (this.currentFocusIndex >= 0 && this.currentFocusIndex < elements.length) {
        await this.browserManager.highlightElement(
          elements[this.currentFocusIndex].selector,
        );
      }

      // Save a memory entry for new sites
      if (!memoryContext) {
        this.memoryStore.saveEntry(
          domain,
          'page_layout',
          navMap.pageDescription,
        );
      }

      sendStatus(
        `页面分析完成，找到 ${elements.length} 个可交互元素`,
        'done',
      );
      return navMap;
    } catch (err) {
      console.error('[NavigationAgent] analyzePage error:', (err as Error).message);
      // Fallback: build a simple linear navigation map
      const fallbackNav: Record<string, DirectionMap> = {};
      for (let i = 0; i < elements.length; i++) {
        fallbackNav[String(i)] = {
          up: i > 0 ? i - 1 : null,
          down: i < elements.length - 1 ? i + 1 : null,
          left: null,
          right: null,
        };
      }

      const navMap: NavigationMap = {
        elements,
        navigation: fallbackNav,
        currentFocus: 0,
        pageDescription: 'Fallback linear navigation (AI analysis failed)',
      };
      this.currentNavMap = navMap;
      this.currentFocusIndex = 0;

      if (elements.length > 0) {
        await this.browserManager.highlightElement(elements[0].selector);
      }

      sendStatus(
        `分析完成 (降级模式)，找到 ${elements.length} 个元素`,
        'done',
      );
      return navMap;
    }
  }

  // ================================================================
  // D-pad handling
  // ================================================================

  async handleDpad(
    direction: string,
    sendStatus: (text: string, level: string) => void,
  ): Promise<{ screenshot: Buffer; focusRect: FocusRect | null }> {
    // Ensure we have a navigation map
    if (!this.currentNavMap || this.currentNavMap.elements.length === 0) {
      await this.analyzePage(sendStatus);
    }

    if (
      !this.currentNavMap ||
      this.currentNavMap.elements.length === 0
    ) {
      return { screenshot: await this.browserManager.screenshot(), focusRect: null };
    }

    // Center = click current element
    if (direction === 'center') {
      if (
        this.currentFocusIndex >= 0 &&
        this.currentFocusIndex < this.currentNavMap.elements.length
      ) {
        const el = this.currentNavMap.elements[this.currentFocusIndex];
        sendStatus(`点击: ${el.text || el.tag}`, 'info');
        await this.browserManager.clickElement(el.selector);
        await delay(500);
        // Page may have changed, invalidate nav map
        this.currentNavMap = null;
      }
      return { screenshot: await this.browserManager.screenshot(), focusRect: null };
    }

    // Direction navigation
    const dirKey = direction as keyof DirectionMap;
    const currentNav = this.currentNavMap.navigation[String(this.currentFocusIndex)];
    const nextIndex = currentNav?.[dirKey] ?? null;

    if (nextIndex !== null && nextIndex < this.currentNavMap.elements.length) {
      this.currentFocusIndex = nextIndex;
      const el = this.currentNavMap.elements[nextIndex];
      const rect = await this.browserManager.highlightElement(el.selector);
      const screenshot = await this.browserManager.screenshot();

      const focusRect = rect
        ? { x: rect.x, y: rect.y, w: rect.w, h: rect.h }
        : null;
      return { screenshot, focusRect };
    }

    // No neighbor in that direction: try scrolling & re-analyze
    sendStatus(`边界到达，正在滚动${direction}...`, 'info');
    await this.browserManager.scrollPage(direction, 300);
    await delay(300);
    this.currentNavMap = null;
    const newNavMap = await this.analyzePage(sendStatus);

    const screenshot = await this.browserManager.screenshot();
    let focusRect: FocusRect | null = null;
    if (
      newNavMap &&
      this.currentFocusIndex >= 0 &&
      this.currentFocusIndex < newNavMap.elements.length
    ) {
      const el = newNavMap.elements[this.currentFocusIndex];
      const rect = await this.browserManager.highlightElement(el.selector);
      if (rect) focusRect = { x: rect.x, y: rect.y, w: rect.w, h: rect.h };
    }

    return { screenshot: await this.browserManager.screenshot(), focusRect };
  }

  // ================================================================
  // Voice command handling (agentic tool-use loop)
  // ================================================================

  async handleVoiceCommand(
    text: string,
    sendStatus: (text: string, level: string) => void,
  ): Promise<Buffer> {
    if (this.isProcessing) {
      sendStatus('正在处理上一个指令，请稍等...', 'info');
      return this.browserManager.screenshot();
    }

    this.isProcessing = true;

    try {
      sendStatus(`正在理解: "${text}"`, 'thinking');

      const screenshotBuf = await this.browserManager.screenshot();
      const { url } = await this.browserManager.getCurrentInfo();
      const domain = this.extractDomain(url);
      const memoryContext = this.memoryStore.getFormattedMemory(domain);

      const toolCtx: ToolExecContext = {
        browserManager: this.browserManager,
        saveSiteMemory: (key: string, content: string) => {
          this.memoryStore.saveEntry(domain, key, content);
        },
      };

      const systemPrompt = `You are a TV remote AI assistant. The user gives voice commands and you execute them on the current webpage using the available tools.

Rules:
- Analyze the screenshot to understand the page state
- Execute the user's intent step by step using tools
- After each action, check the result before proceeding
- Be efficient: use the fewest tool calls possible
- If the user asks to search, find the search box, type, and submit
- If the user asks to play a video, find and click the video
- Always save useful navigation patterns using save_memory

${memoryContext ? `Site memory:\n${memoryContext}` : 'No prior memory for this site.'}

Current URL: ${url}`;

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
              text: `Voice command: "${text}"`,
            } as TextBlockParam,
          ],
        },
      ];

      let round = 0;

      while (round < MAX_TOOL_ROUNDS) {
        round++;

        const response = await this.client.messages.create({
          model: config.claudeModel,
          max_tokens: 4096,
          system: systemPrompt,
          messages,
          tools: navigationTools,
        });

        // Collect assistant content blocks
        messages.push({ role: 'assistant', content: response.content });

        // Find tool-use blocks
        const toolUseBlocks = response.content.filter(
          (b) => b.type === 'tool_use',
        );

        // No tool use -> done
        if (toolUseBlocks.length === 0 || response.stop_reason === 'end_turn') {
          break;
        }

        // Execute each tool and build tool_result blocks
        const toolResults: ToolResultBlockParam[] = [];

        for (const block of toolUseBlocks) {
          if (block.type !== 'tool_use') continue;

          sendStatus(`执行: ${block.name}`, 'thinking');
          const result = await executeTool(
            block.name,
            block.input as Record<string, unknown>,
            toolCtx,
          );
          await delay(300);

          // Capture screenshot after tool execution for context
          const postScreenshot = await this.browserManager.screenshot();

          toolResults.push({
            type: 'tool_result',
            tool_use_id: block.id,
            content: [
              { type: 'text', text: result } as TextBlockParam,
              {
                type: 'image',
                source: {
                  type: 'base64',
                  media_type: 'image/jpeg',
                  data: postScreenshot.toString('base64'),
                },
              } as ImageBlockParam,
            ],
          });
        }

        messages.push({ role: 'user', content: toolResults });
      }

      // Invalidate nav map since page likely changed
      this.currentNavMap = null;

      sendStatus('完成', 'done');
      return this.browserManager.screenshot();
    } catch (err) {
      console.error('[NavigationAgent] handleVoiceCommand error:', (err as Error).message);
      sendStatus(`错误: ${(err as Error).message}`, 'error');
      return this.browserManager.screenshot();
    } finally {
      this.isProcessing = false;
    }
  }

  // ================================================================
  // Back / Cursor / Utilities
  // ================================================================

  async handleBack(): Promise<{ wentBack: boolean; screenshot: Buffer }> {
    const wentBack = await this.browserManager.goBack();
    this.currentNavMap = null;
    const screenshot = await this.browserManager.screenshot();
    return { wentBack, screenshot };
  }

  async handleCursor(
    action: string,
    x: number,
    y: number,
  ): Promise<Buffer> {
    if (action === 'click') {
      await this.browserManager.clickAt(x, y);
      this.currentNavMap = null;
      await delay(300);
    }
    // For move: could highlight nearest element, but just return screenshot for now
    return this.browserManager.screenshot();
  }

  clearNavMap(): void {
    this.currentNavMap = null;
    this.currentFocusIndex = -1;
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

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

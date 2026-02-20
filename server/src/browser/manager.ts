import { chromium, type Browser, type BrowserContext, type Page } from 'playwright';
import { config } from '../config.js';
import type { ElementInfo } from '../types.js';

const FOCUS_HIGHLIGHT_STYLE = `
.tiletv-focus-highlight {
  outline: 3px solid #FF6B35 !important;
  outline-offset: 2px !important;
  box-shadow: 0 0 12px rgba(255,107,53,0.5) !important;
  transition: outline 0.15s ease, box-shadow 0.15s ease !important;
}
`;

const DESKTOP_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36';

export class BrowserManager {
  private browser: Browser | null = null;
  private context: BrowserContext | null = null;
  private page: Page | null = null;
  private screenshotInterval: ReturnType<typeof setInterval> | null = null;
  private onFrameCallback: ((frame: Buffer) => void) | null = null;

  // ------------------------------------------------------------------
  // Lifecycle
  // ------------------------------------------------------------------

  async init(): Promise<void> {
    this.browser = await chromium.launch({
      headless: config.browserHeadless,
      args: [
        '--autoplay-policy=no-user-gesture-required',
        '--disable-features=PreloadMediaEngagementData,MediaEngagementBypassAutoplayPolicies',
      ],
    });

    this.context = await this.browser.newContext({
      viewport: { width: config.viewportWidth, height: config.viewportHeight },
      userAgent: DESKTOP_USER_AGENT,
      javaScriptEnabled: true,
      ignoreHTTPSErrors: true,
    });

    this.page = await this.context.newPage();
    console.log(
      `[BrowserManager] Chromium launched ${config.viewportWidth}x${config.viewportHeight} headless=${config.browserHeadless}`,
    );
  }

  async close(): Promise<void> {
    this.stopScreenshotStream();
    try {
      await this.page?.close();
    } catch { /* ignore */ }
    try {
      await this.context?.close();
    } catch { /* ignore */ }
    try {
      await this.browser?.close();
    } catch { /* ignore */ }
    this.page = null;
    this.context = null;
    this.browser = null;
    console.log('[BrowserManager] Closed');
  }

  // ------------------------------------------------------------------
  // Navigation
  // ------------------------------------------------------------------

  async navigate(url: string): Promise<void> {
    this.ensurePage();
    try {
      await this.page!.goto(url, { waitUntil: 'domcontentloaded', timeout: 30_000 });
    } catch (err) {
      console.warn(`[BrowserManager] navigate warning: ${(err as Error).message}`);
    }
    await this.injectStyles();
  }

  async goBack(): Promise<boolean> {
    this.ensurePage();
    try {
      const response = await this.page!.goBack({ waitUntil: 'domcontentloaded', timeout: 10_000 });
      if (response) {
        await this.injectStyles();
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }

  async getCurrentInfo(): Promise<{ url: string; title: string }> {
    this.ensurePage();
    return {
      url: this.page!.url(),
      title: await this.page!.title(),
    };
  }

  // ------------------------------------------------------------------
  // Screenshot
  // ------------------------------------------------------------------

  async screenshot(): Promise<Buffer> {
    this.ensurePage();
    const buf = await this.page!.screenshot({
      type: 'jpeg',
      quality: config.screenshotQuality,
    });
    return Buffer.from(buf);
  }

  startScreenshotStream(callback: (frame: Buffer) => void, fps = 5): void {
    this.stopScreenshotStream();
    this.onFrameCallback = callback;
    const interval = Math.max(Math.round(1000 / fps), 100);
    this.screenshotInterval = setInterval(async () => {
      try {
        if (!this.page || !this.onFrameCallback) return;
        const frame = await this.screenshot();
        this.onFrameCallback(frame);
      } catch (err) {
        console.warn(`[BrowserManager] screenshot stream error: ${(err as Error).message}`);
      }
    }, interval);
  }

  stopScreenshotStream(): void {
    if (this.screenshotInterval) {
      clearInterval(this.screenshotInterval);
      this.screenshotInterval = null;
    }
    this.onFrameCallback = null;
  }

  async captureAndSend(): Promise<void> {
    if (!this.onFrameCallback) return;
    const frame = await this.screenshot();
    this.onFrameCallback(frame);
  }

  // ------------------------------------------------------------------
  // Interactive elements
  // ------------------------------------------------------------------

  async getInteractiveElements(): Promise<ElementInfo[]> {
    this.ensurePage();

    const raw: ElementInfo[] = await this.page!.evaluate(() => {
      const selectors = [
        'a',
        'button',
        'input',
        'textarea',
        'select',
        '[role="button"]',
        '[role="link"]',
        '[role="tab"]',
        '[tabindex]',
        'video',
        '[onclick]',
      ];

      const seen = new Set<Element>();
      const results: ElementInfo[] = [];

      function generateSelector(el: Element): string {
        if (el.id) return `#${CSS.escape(el.id)}`;

        const tag = el.tagName.toLowerCase();
        const parent = el.parentElement;
        if (!parent) return tag;

        const siblings = Array.from(parent.children).filter((c) => c.tagName === el.tagName);
        if (siblings.length === 1) {
          const parentSel = generateSelector(parent);
          return `${parentSel} > ${tag}`;
        }
        const idx = siblings.indexOf(el) + 1;
        const parentSel = generateSelector(parent);
        return `${parentSel} > ${tag}:nth-of-type(${idx})`;
      }

      for (const sel of selectors) {
        const nodes = document.querySelectorAll(sel);
        for (const el of nodes) {
          if (seen.has(el)) continue;
          seen.add(el);

          const rect = el.getBoundingClientRect();
          const style = window.getComputedStyle(el);
          const isVisible =
            style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            parseFloat(style.opacity) > 0 &&
            rect.width > 0 &&
            rect.height > 0 &&
            rect.bottom > 0 &&
            rect.right > 0 &&
            rect.top < window.innerHeight &&
            rect.left < window.innerWidth;

          if (!isVisible) continue;

          const text = (
            (el as HTMLElement).innerText ||
            el.getAttribute('aria-label') ||
            el.getAttribute('title') ||
            el.getAttribute('placeholder') ||
            el.getAttribute('alt') ||
            ''
          )
            .trim()
            .slice(0, 50);

          results.push({
            index: results.length,
            selector: generateSelector(el),
            tag: el.tagName.toLowerCase(),
            text,
            type:
              el.getAttribute('type') ||
              el.getAttribute('role') ||
              el.tagName.toLowerCase(),
            bounds: {
              x: Math.round(rect.x),
              y: Math.round(rect.y),
              width: Math.round(rect.width),
              height: Math.round(rect.height),
            },
            isVisible: true,
          });
        }
      }

      return results;
    });

    return raw;
  }

  // ------------------------------------------------------------------
  // Interactions
  // ------------------------------------------------------------------

  async clickElement(selector: string): Promise<boolean> {
    this.ensurePage();
    try {
      await this.page!.click(selector, { timeout: 5_000 });
      return true;
    } catch (err) {
      console.warn(`[BrowserManager] clickElement failed (${selector}): ${(err as Error).message}`);
      return false;
    }
  }

  async clickAt(x: number, y: number): Promise<void> {
    this.ensurePage();
    await this.page!.mouse.click(x, y);
  }

  async typeText(selector: string, text: string): Promise<void> {
    this.ensurePage();
    try {
      await this.page!.fill(selector, text, { timeout: 3_000 });
    } catch {
      try {
        await this.page!.click(selector, { timeout: 3_000 });
        await this.page!.keyboard.type(text, { delay: 30 });
      } catch (err) {
        console.warn(`[BrowserManager] typeText failed: ${(err as Error).message}`);
      }
    }
  }

  async pressKey(key: string): Promise<void> {
    this.ensurePage();
    await this.page!.keyboard.press(key);
  }

  async scrollPage(direction: string, amount = 400): Promise<void> {
    this.ensurePage();
    switch (direction) {
      case 'up':
        await this.page!.mouse.wheel(0, -amount);
        break;
      case 'down':
        await this.page!.mouse.wheel(0, amount);
        break;
      case 'left':
        await this.page!.evaluate((px) => window.scrollBy(-px, 0), amount);
        break;
      case 'right':
        await this.page!.evaluate((px) => window.scrollBy(px, 0), amount);
        break;
    }
  }

  // ------------------------------------------------------------------
  // Highlight
  // ------------------------------------------------------------------

  async highlightElement(
    selector: string,
  ): Promise<{ x: number; y: number; w: number; h: number } | null> {
    this.ensurePage();
    try {
      const rect = await this.page!.evaluate((sel: string) => {
        // Remove previous highlights
        document
          .querySelectorAll('.tiletv-focus-highlight')
          .forEach((el) => el.classList.remove('tiletv-focus-highlight'));

        const target = document.querySelector(sel);
        if (!target) return null;

        target.classList.add('tiletv-focus-highlight');
        target.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });

        const r = target.getBoundingClientRect();
        return {
          x: Math.round(r.x),
          y: Math.round(r.y),
          w: Math.round(r.width),
          h: Math.round(r.height),
        };
      }, selector);

      return rect;
    } catch (err) {
      console.warn(`[BrowserManager] highlightElement failed: ${(err as Error).message}`);
      return null;
    }
  }

  async removeHighlight(): Promise<void> {
    this.ensurePage();
    try {
      await this.page!.evaluate(() => {
        document
          .querySelectorAll('.tiletv-focus-highlight')
          .forEach((el) => el.classList.remove('tiletv-focus-highlight'));
      });
    } catch { /* ignore */ }
  }

  // ------------------------------------------------------------------
  // Internal helpers
  // ------------------------------------------------------------------

  private ensurePage(): void {
    if (!this.page) {
      throw new Error('BrowserManager is not initialised. Call init() first.');
    }
  }

  private async injectStyles(): Promise<void> {
    if (!this.page) return;
    try {
      await this.page.evaluate((css: string) => {
        let style = document.getElementById('tiletv-styles');
        if (!style) {
          style = document.createElement('style');
          style.id = 'tiletv-styles';
          document.head.appendChild(style);
        }
        style.textContent = css;
      }, FOCUS_HIGHLIGHT_STYLE);
    } catch (err) {
      console.warn(`[BrowserManager] injectStyles warning: ${(err as Error).message}`);
    }
  }
}

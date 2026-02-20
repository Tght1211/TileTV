import type { BrowserManager } from '../browser/manager.js';
import type { Tool } from '@anthropic-ai/sdk/resources/messages.js';

// ------------------------------------------------------------------
// Tool definitions (Anthropic tool-use format)
// ------------------------------------------------------------------

export const navigationTools: Tool[] = [
  {
    name: 'click_element',
    description:
      'Click on an interactive element by its CSS selector. Use this to activate buttons, links, etc.',
    input_schema: {
      type: 'object' as const,
      properties: {
        selector: {
          type: 'string',
          description: 'CSS selector of the element to click',
        },
      },
      required: ['selector'],
    },
  },
  {
    name: 'click_coordinates',
    description:
      'Click at specific x,y coordinates on the page. Use when CSS selector is unavailable.',
    input_schema: {
      type: 'object' as const,
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
      },
      required: ['x', 'y'],
    },
  },
  {
    name: 'type_text',
    description:
      'Type text into an input field. If selector is omitted, types into the currently focused element.',
    input_schema: {
      type: 'object' as const,
      properties: {
        text: { type: 'string', description: 'Text to type' },
        selector: {
          type: 'string',
          description: 'Optional CSS selector of the input field',
        },
        submit: {
          type: 'boolean',
          description: 'Whether to press Enter after typing',
        },
      },
      required: ['text'],
    },
  },
  {
    name: 'scroll_page',
    description: 'Scroll the page in a direction',
    input_schema: {
      type: 'object' as const,
      properties: {
        direction: {
          type: 'string',
          enum: ['up', 'down', 'left', 'right'],
          description: 'Scroll direction',
        },
        amount: {
          type: 'number',
          description: 'Pixels to scroll, default 400',
        },
      },
      required: ['direction'],
    },
  },
  {
    name: 'press_key',
    description: 'Press a keyboard key (Enter, Tab, Escape, ArrowDown, etc.)',
    input_schema: {
      type: 'object' as const,
      properties: {
        key: { type: 'string', description: 'Key to press' },
      },
      required: ['key'],
    },
  },
  {
    name: 'focus_element',
    description:
      'Highlight/focus an element visually (orange border). Used for D-pad navigation to show which element is selected.',
    input_schema: {
      type: 'object' as const,
      properties: {
        selector: {
          type: 'string',
          description: 'CSS selector to highlight',
        },
      },
      required: ['selector'],
    },
  },
  {
    name: 'save_memory',
    description:
      'Save a navigation pattern or insight about this website for future reference. This helps you navigate faster next time.',
    input_schema: {
      type: 'object' as const,
      properties: {
        key: {
          type: 'string',
          description:
            'Memory key (e.g. "homepage_layout", "search_flow", "video_player_controls")',
        },
        content: {
          type: 'string',
          description:
            'What to remember (page structure, navigation tips, element selectors, etc.)',
        },
      },
      required: ['key', 'content'],
    },
  },
];

// ------------------------------------------------------------------
// Tool executor
// ------------------------------------------------------------------

export interface ToolExecContext {
  browserManager: BrowserManager;
  saveSiteMemory: (key: string, content: string) => void;
}

export async function executeTool(
  toolName: string,
  input: Record<string, unknown>,
  ctx: ToolExecContext,
): Promise<string> {
  const { browserManager, saveSiteMemory } = ctx;

  try {
    switch (toolName) {
      case 'click_element': {
        const selector = input.selector as string;
        const ok = await browserManager.clickElement(selector);
        return ok ? `Clicked element: ${selector}` : `Failed to click element: ${selector}`;
      }

      case 'click_coordinates': {
        const x = input.x as number;
        const y = input.y as number;
        await browserManager.clickAt(x, y);
        return `Clicked at coordinates (${x}, ${y})`;
      }

      case 'type_text': {
        const text = input.text as string;
        const selector = input.selector as string | undefined;
        const submit = input.submit as boolean | undefined;
        if (selector) {
          await browserManager.typeText(selector, text);
        } else {
          await browserManager.pressKey('');
          // Type into currently focused element
          for (const ch of text) {
            await browserManager.pressKey(ch);
          }
        }
        if (submit) {
          await browserManager.pressKey('Enter');
        }
        return `Typed "${text}"${selector ? ` into ${selector}` : ''}${submit ? ' and pressed Enter' : ''}`;
      }

      case 'scroll_page': {
        const direction = input.direction as string;
        const amount = (input.amount as number) ?? 400;
        await browserManager.scrollPage(direction, amount);
        return `Scrolled ${direction} by ${amount}px`;
      }

      case 'press_key': {
        const key = input.key as string;
        await browserManager.pressKey(key);
        return `Pressed key: ${key}`;
      }

      case 'focus_element': {
        const selector = input.selector as string;
        const rect = await browserManager.highlightElement(selector);
        if (rect) {
          return `Highlighted element: ${selector} at (${rect.x},${rect.y}) ${rect.w}x${rect.h}`;
        }
        return `Could not find element to highlight: ${selector}`;
      }

      case 'save_memory': {
        const key = input.key as string;
        const content = input.content as string;
        saveSiteMemory(key, content);
        return `Memory saved: ${key}`;
      }

      default:
        return `Unknown tool: ${toolName}`;
    }
  } catch (err) {
    const msg = (err as Error).message;
    console.error(`[executeTool] ${toolName} error:`, msg);
    return `Tool error (${toolName}): ${msg}`;
  }
}

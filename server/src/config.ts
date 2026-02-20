const env = process.env;

function getEnvString(key: string, defaultValue: string): string {
  return env[key] ?? defaultValue;
}

function getEnvNumber(key: string, defaultValue: number): number {
  const raw = env[key];
  if (raw === undefined) return defaultValue;
  const parsed = Number(raw);
  return Number.isNaN(parsed) ? defaultValue : parsed;
}

function getEnvBoolean(key: string, defaultValue: boolean): boolean {
  const raw = env[key];
  if (raw === undefined) return defaultValue;
  return raw === 'true' || raw === '1';
}

export const config = {
  anthropicApiKey: getEnvString('ANTHROPIC_API_KEY', ''),
  claudeModel: getEnvString('CLAUDE_MODEL', 'claude-haiku-4-5-20250315'),
  port: getEnvNumber('PORT', 9870),
  viewportWidth: getEnvNumber('VIEWPORT_WIDTH', 1280),
  viewportHeight: getEnvNumber('VIEWPORT_HEIGHT', 720),
  screenshotQuality: getEnvNumber('SCREENSHOT_QUALITY', 80),
  browserHeadless: getEnvBoolean('BROWSER_HEADLESS', true),
} as const;

export type Config = typeof config;

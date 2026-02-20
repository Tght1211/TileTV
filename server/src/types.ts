// ============================================================
// Client -> Server Messages
// ============================================================

export interface OpenMessage {
  type: 'open';
  url: string;
  name: string;
}

export interface DpadMessage {
  type: 'dpad';
  direction: 'up' | 'down' | 'left' | 'right' | 'center';
}

export interface BackMessage {
  type: 'back';
}

export interface VoiceMessage {
  type: 'voice';
  text: string;
}

export interface CursorMessage {
  type: 'cursor';
  action: 'move' | 'click';
  x: number;
  y: number;
}

export interface HomeMessage {
  type: 'home';
}

export interface PingMessage {
  type: 'ping';
}

export type ClientMessage =
  | OpenMessage
  | DpadMessage
  | BackMessage
  | VoiceMessage
  | CursorMessage
  | HomeMessage
  | PingMessage;

// ============================================================
// Server -> Client Messages
// ============================================================

export interface FrameMessage {
  type: 'frame';
  data: string; // base64 jpeg
  width: number;
  height: number;
}

export interface StatusMessage {
  type: 'status';
  text: string;
  level: 'info' | 'thinking' | 'done' | 'error';
}

export interface FocusRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface FocusMessage {
  type: 'focus';
  rect: FocusRect | null;
  label?: string;
}

export interface ToastMessage {
  type: 'toast';
  text: string;
}

export interface MemoryMessage {
  type: 'memory';
  site: string;
  summary: string;
}

export interface PongMessage {
  type: 'pong';
  url: string;
  title: string;
}

export type ServerMessage =
  | FrameMessage
  | StatusMessage
  | FocusMessage
  | ToastMessage
  | MemoryMessage
  | PongMessage;

// ============================================================
// Navigation Map
// ============================================================

export interface ElementInfo {
  index: number;
  selector: string;
  tag: string;
  text: string;
  type: string;
  bounds: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  isVisible: boolean;
}

export interface DirectionMap {
  up: number | null;
  down: number | null;
  left: number | null;
  right: number | null;
}

export interface NavigationMap {
  elements: ElementInfo[];
  navigation: Record<string, DirectionMap>;
  currentFocus: number;
  pageDescription: string;
}

// ============================================================
// Memory
// ============================================================

export interface MemoryEntry {
  key: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  usedCount: number;
}

export interface SiteMemory {
  domain: string;
  entries: MemoryEntry[];
  lastVisited: string;
}

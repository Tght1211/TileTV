# TileTV

AI 驱动的 Android TV 网页导航器。Apple TV 风格磨砂玻璃 UI，Claude AI + Playwright 智能导航，语音控制，支持老旧电视盒子。

**最低支持 Android 4.2 (API 17)** · **Claude Haiku 4.5 AI 导航** · **Playwright 无头浏览器** · **H5 语音控制**

---

## 架构概览

```
┌─────────────────┐     WebSocket      ┌────────────────────┐
│  Android TV APK  │ ◄──────────────► │   TileTV Server     │
│  (磨砂玻璃 UI)   │   截图 + 指令      │   (Node.js)         │
│                  │                   │                     │
│  · 卡片启动器     │                   │  ┌───────────────┐  │
│  · 截图显示       │                   │  │  Playwright    │  │
│  · 遥控器控制     │                   │  │  (真实浏览器)   │  │
│  · 光标兜底       │                   │  └───────────────┘  │
└─────────────────┘                   │                     │
                                      │  ┌───────────────┐  │
┌─────────────────┐     WebSocket      │  │  Claude AI     │  │
│  H5 语音控制     │ ◄──────────────► │  │  (Haiku 4.5)   │  │
│  (手机浏览器)    │   语音指令         │  └───────────────┘  │
│                  │                   │                     │
│  · 长按说话       │                   │  ┌───────────────┐  │
│  · 快捷指令       │                   │  │  记忆系统       │  │
│  · 文字输入       │                   │  │  (导航经验)     │  │
└─────────────────┘                   │  └───────────────┘  │
                                      └────────────────────┘
```

**为什么这样设计？**

- 老电视的 WebView 太旧，很多网站白屏或排版错乱
- YouTube 等有版权保护，WebView 播放黑屏
- JS 注入的焦点导航在复杂网站上效果差
- 解决方案：服务器用 Playwright 运行完整 Chrome，AI 理解页面并导航，TV 只显示截图

---

## 主要功能

- **Apple TV 磨砂玻璃 UI** — 深色主题、大圆角卡片、焦点放大动画、半透明毛玻璃效果
- **AI 智能导航** — Claude Haiku 4.5 分析页面截图，自动建立空间导航地图，方向键精准跳转
- **语音控制** — 手机打开 H5 页面，长按说话："搜索周杰伦"、"播放第一个视频"
- **导航记忆** — AI 记住每个网站的布局和操作方式，越用越智能
- **光标兜底** — 任何时候双击返回键切换到虚拟光标模式
- **离线可用** — 服务器不在线时自动降级为本地 WebView + 光标模式
- **超低门槛** — Android 4.2+ 老设备也能用

---

## 快速开始

### 1. 启动服务器

需要一台电脑（Mac/PC/Linux），和电视在同一局域网。

```bash
# 克隆项目
git clone https://github.com/Tght1211/TileTV.git
cd TileTV/server

# 安装依赖
npm install

# 安装 Chromium 浏览器
npm run setup

# 设置 API Key（从 Anthropic 获取）
export ANTHROPIC_API_KEY=sk-ant-你的key

# 启动服务器
npm run dev
```

启动后会显示：
```
TileTV Server v2.0.0
  Local:   http://localhost:9870
  Network: http://192.168.1.100:9870
  H5 Voice: http://192.168.1.100:9870/h5
```

### 2. 安装 TV 端 APK

```bash
# 方式一：自己编译
cd TileTV
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk

# 方式二：ADB 安装
adb connect 电视IP
adb install app-debug.apk
```

### 3. 配置连接

在 TV 上打开 TileTV → 按遥控器 **Menu 键** → 输入服务器 IP 地址 → 保存

### 4. 开始使用

- 选择一个网站卡片 → AI 自动打开并分析页面
- 方向键导航 → AI 在可交互元素间跳转
- OK 键确认 → AI 点击当前高亮元素
- 打开手机浏览器访问 `http://服务器IP:9870/h5` → 语音控制

---

## 遥控器操作

### 首页

| 按键 | 功能 |
|------|------|
| 方向键 | 在卡片间移动焦点 |
| OK / Enter | 打开选中的网站 |
| Menu | 打开设置（配置服务器） |

### AI 导航模式（服务器已连接）

| 按键 | 功能 |
|------|------|
| 方向键 | AI 在页面元素间智能跳转 |
| OK / Enter | 点击当前高亮元素 |
| 返回键 | 网页后退，无历史则回首页 |
| 双击返回键 | 切换到光标模式 |

### 光标模式（兜底）

| 按键 | 功能 |
|------|------|
| 方向键 | 移动屏幕上的虚拟光标 |
| OK / Enter | 在光标位置点击 |
| 返回键 | 网页后退 |
| 双击返回键 | 切回 AI 导航模式 |

---

## 语音控制 (H5)

在手机浏览器打开 `http://服务器IP:9870/h5`

- **长按麦克风按钮** → 说话 → 松开发送
- **快捷指令** → 上滑、下滑、返回、搜索、播放、首页
- **文字输入** → 输入任意指令或网址

示例语音命令：
- "搜索周杰伦"
- "播放第一个视频"
- "向下滚动"
- "点击登录按钮"
- "打开 bilibili.com"

---

## 自定义网站

编辑 `app/src/main/assets/tiles.json`：

```json
{
  "categories": [
    {
      "name": "视频平台",
      "tiles": [
        { "name": "YouTube", "url": "https://www.youtube.com", "icon": "youtube", "level": 1 },
        { "name": "哔哩哔哩", "url": "https://www.bilibili.com", "icon": "bilibili", "level": 2 }
      ]
    }
  ]
}
```

> 新架构中 `level` 字段仅在离线模式下有用。服务器模式下 AI 自动处理所有导航。

---

## 项目结构

```
TileTV/
├── app/                          # Android TV 应用
│   └── src/main/
│       ├── java/com/tiletv/app/
│       │   ├── MainActivity.java        # 首页（磨砂玻璃卡片）
│       │   ├── BrowserActivity.java     # 浏览器（截图显示/WebView）
│       │   ├── SettingsActivity.java    # 设置（服务器配置）
│       │   ├── adapter/
│       │   │   ├── CategoryAdapter.java # 分类行适配器
│       │   │   └── TileAdapter.java     # 卡片适配器（焦点动画）
│       │   ├── ws/
│       │   │   └── WebSocketManager.java # WebSocket 客户端
│       │   ├── widget/
│       │   │   ├── FocusOverlayView.java # AI 焦点指示器
│       │   │   └── VirtualCursorView.java # 虚拟光标（兜底）
│       │   ├── model/                    # 数据模型
│       │   └── util/                     # 工具类
│       ├── assets/tiles.json             # 网站配置
│       └── res/                          # 布局、颜色、样式
│
├── server/                       # Node.js 后端服务
│   ├── src/
│   │   ├── index.ts              # 入口（Express + WebSocket）
│   │   ├── config.ts             # 配置
│   │   ├── types.ts              # 类型定义
│   │   ├── browser/
│   │   │   └── manager.ts        # Playwright 浏览器管理
│   │   ├── agent/
│   │   │   ├── navigator.ts      # Claude AI 导航代理
│   │   │   └── tools.ts          # AI 工具定义
│   │   ├── memory/
│   │   │   └── store.ts          # 导航记忆系统
│   │   └── ws/
│   │       └── handler.ts        # WebSocket 消息处理
│   ├── h5/                       # H5 语音控制界面
│   │   ├── index.html
│   │   ├── style.css
│   │   └── app.js
│   ├── data/                     # AI 记忆存储（运行时生成）
│   └── package.json
│
└── README.md
```

---

## 技术栈

| 组件 | 技术 |
|------|------|
| TV 端 | Android 4.2+ · Java 8 · AndroidX · Gson · Java-WebSocket |
| 服务器 | Node.js 18+ · TypeScript · Playwright · Express · ws |
| AI 引擎 | Claude Haiku 4.5 · @anthropic-ai/sdk · Tool Use |
| 语音端 | HTML5 · Web Speech API · WebSocket · Apple Design |
| 通信 | WebSocket (JSON + Base64 JPEG) |

---

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `ANTHROPIC_API_KEY` | Anthropic API 密钥 | (必填) |
| `CLAUDE_MODEL` | Claude 模型 ID | `claude-haiku-4-5-20250315` |
| `PORT` | 服务器端口 | `9870` |
| `VIEWPORT_WIDTH` | 浏览器视口宽度 | `1280` |
| `VIEWPORT_HEIGHT` | 浏览器视口高度 | `720` |
| `SCREENSHOT_QUALITY` | JPEG 质量 (1-100) | `80` |
| `BROWSER_HEADLESS` | 无头模式 | `true` |

---

## 常见问题

### Q: 服务器需要什么配置？
任何能运行 Node.js 18+ 的电脑都行。推荐 4GB+ 内存（Chromium 需要）。Mac / Windows / Linux 均可。

### Q: AI 导航准确吗？
Claude Haiku 4.5 通过分析页面截图理解布局，准确率很高。首次访问某网站时可能需要 1-2 秒分析，之后记忆系统会加速。

### Q: 没有 Anthropic API Key 能用吗？
可以。TV 端会自动降级为离线模式（本地 WebView + 虚拟光标），只是没有 AI 导航。

### Q: 支持多台电视同时连接吗？
目前一个服务器支持一个浏览器会话。多台电视需要运行多个服务器实例（不同端口）。

### Q: 流量消耗大吗？
截图流是 JPEG 格式，每帧约 100-200KB。操作时按需推送，不是持续流，实际流量很小。

---

## 许可证

MIT License

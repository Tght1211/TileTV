# TileTV

**用遥控器看全网视频的电视应用。**

家里电视太老、��不了 B站/YouTube/Netflix 的 APP？TileTV 帮你搞定 —— 它在你的电脑上打开网站，然后把画面投到电视上，用遥控器就能操作，还有 AI 帮你自动导航。

```
你的老电视                        你的电脑(同一WiFi)
┌──────────┐     画面传输     ┌──────────────┐
│          │ ◄────────────── │  Chrome浏览器  │
│  TileTV  │                 │  (Playwright)  │
│  遥控器   │ ──────────────► │              │
│          │     按键指令     │  AI 帮你点击   │
└──────────┘                 └──────────────┘

你的手机(可选)
┌──────────┐
│ 语音控制  │ ─── "搜索周杰伦" ──►
└──────────┘
```

---

## 它能干什么？

**场景**：你窝在沙发上，拿着遥控器

1. 电视上出现 YouTube、B站、Netflix 等网站卡片
2. 遥控器选一个 → 电脑帮你打开这个网站
3. 按方向键 → AI 自动在视频之间跳转（不用鼠标！）
4. 按确认键 → 开始播放
5. 想搜东西？拿手机说一句"搜索周杰伦" → AI 自动帮你搜

**为什么不直接在电视上装APP？**

| 问题 | TileTV 的解决方案 |
|------|-----------------|
| 电视太老，装不了新版APP | 电视只需显示画面，浏览器跑在电脑上 |
| YouTube WebView 播放黑屏 | 电脑上用完整 Chrome，没有限制 |
| 网页用遥控器操作太难 | AI 看懂页面，帮你在按钮之间跳转 |

---

## 你需要准备什么

| 东西 | 要求 | 说明 |
|------|------|------|
| 一台电视/盒子 | Android 4.2 以上 | 再老的电视也行 |
| 一台电脑 | 能上网，4GB内存 | Mac / Windows / Linux 都行 |
| 同一个 WiFi | 电视和电脑在同一网络 | 连同一个路由器就行 |
| Anthropic API Key | 免费注册获取 | 没有也能用，只是没有AI导航 |

> 手机是可选的，用来语音控制。

---

## 三步开始使用

### 第一步：在电脑上启动服务

打开电脑的终端 / 命令行，依次输入：

```bash
# 1. 下载项目
git clone https://github.com/Tght1211/TileTV.git

# 2. 进入服务器目录
cd TileTV/server

# 3. 安装依赖（只需要第一次）
npm install

# 4. 安装浏览器引擎（只需要第一次）
npm run setup

# 5. 设置你的 AI 密钥
#    去 https://console.anthropic.com/ 注册并创建 API Key
#    然后替换下面的 sk-ant-xxx：
export ANTHROPIC_API_KEY=sk-ant-xxx

# （可选）如果你用的不是 Anthropic 官方地址（比如代理、中转站），设置自定义地址：
# export ANTHROPIC_BASE_URL=https://your-proxy.example.com

# 6. 启动！
npm run dev
```

> **没有 Node.js？** 去 [nodejs.org](https://nodejs.org/) 下载安装，选 LTS 版本就行。

启动成功后你会看到：

```
TileTV Server v2.0.0
  Local:   http://localhost:9870
  Network: http://192.168.1.100:9870      ← 记住这个 IP
  H5 Voice: http://192.168.1.100:9870/h5  ← 手机语音控制地址
```

### 第二步：在电视上安装 APK

**方式 A：直接安装（推荐）**

到 [Releases](https://github.com/Tght1211/TileTV/releases) 页面下载 APK，用 U 盘拷到电视上安装。

**方式 B：自己编译**

```bash
cd TileTV
./gradlew assembleDebug
# 编译好的 APK 在：app/build/outputs/apk/debug/app-debug.apk
```

**方式 C：ADB 安装（开发者）**

```bash
adb connect 电视的IP地址
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 第三步：连接电视和电脑

1. 在电视上打开 TileTV
2. 按遥控器的 **Menu 键**（菜单键）→ 进入设置
3. 输入电脑的 IP 地址（第一步结尾看到的那个）
4. 点"测试连接" → 显示绿色"已连接"就 OK 了
5. 点"保存" → 回到首页

首页左上角会显示绿色圆点，表示已连接服务器。

---

## 怎么用遥控器操作？

### 首页 —— 选网站

```
    ↑
  ← ● →    在卡片之间移动
    ↓

   [OK]    打开选中的网站

  [Menu]   打开设置
```

### 看网页 —— AI 帮你导航

```
    ↑
  ← ● →    AI 在页面按钮/链接间跳转
    ↓

   [OK]    点击当前高亮的元素

  [返回]   后退一页（多按几次回首页）

  [返回×2] 快速双击返回键 → 切换到手动光标模式
```

### 光标模式 —— 手动兜底

AI 搞不定的时候，双击返回键切到光标模式：

```
    ↑
  ← ● →    移动屏幕上的十字光标
    ↓

   [OK]    在光标位置点击

  [返回×2] 切回 AI 导航模式
```

---

## 手机语音控制（可选）

拿出手机，用浏览器打开：

```
http://你电脑的IP:9870/h5
```

比如 `http://192.168.1.100:9870/h5`

- **长按麦克风** → 说话 → 松手发送
- 或者直接打字输入指令

**能说什么？**

| 你说 | AI 会做 |
|------|--------|
| "搜索周杰伦" | 找到搜索框，输入周杰伦，按回车 |
| "播放第一个视频" | 点击第一个视频 |
| "向下翻页" | 往下滚动页面 |
| "返回" | 后退一页 |
| "打开 bilibili.com" | 导航到 B 站 |

还有快捷按钮：上滑、下滑、返回、搜索、播放、首页 —— 一键直达。

---

## 添加/修改网站

所有网站卡片配置在一个文件里：

`app/src/main/assets/tiles.json`

```json
{
  "categories": [
    {
      "name": "视频平台",
      "tiles": [
        { "name": "YouTube", "url": "https://www.youtube.com", "icon": "youtube", "level": 1 },
        { "name": "哔哩哔哩", "url": "https://m.bilibili.com", "icon": "bilibili", "level": 2 }
      ]
    }
  ]
}
```

想加微博？在 tiles 数组里加一行：
```json
{ "name": "微博", "url": "https://m.weibo.cn", "icon": "weibo", "level": 2 }
```

想加一整个分类？在 categories 数组里加：
```json
{
  "name": "社交媒体",
  "tiles": [
    { "name": "微博", "url": "https://m.weibo.cn", "icon": "weibo", "level": 2 },
    { "name": "Twitter", "url": "https://x.com", "icon": "twitter", "level": 2 }
  ]
}
```

修改后重新编译安装 APK 即可。

> `level` 字段只在离线模式（没连服务器）时有用。连了服务器后 AI 自动处理导航，不用管这个值。

---

## 常见问题

### 没有 Anthropic API Key 能用吗？

能用。没有 Key 就不启动服务器，电视端会自动进入「离线模式」—— 用电视自带的 WebView 打开网页 + 虚拟光标操作。没有 AI 导航但基本功能都有。

### 服务器需要一直开着吗？

是的。电脑关了或者服务停了，电视端会自动切到离线模式。下次电脑开机启动服务后自动重连。

### 电脑配置要求高吗？

不高。能跑 Chrome 浏览器就行。推荐 4GB 以上内存。Mac / Windows / Linux 都支持。

### 画面会卡吗？

不会。画面只在你按遥控器时才更新（不是视频流），每张图片 100-200KB，局域网内几乎无延迟。播放视频时，视频本身由服务器的 Chrome 渲染，画面通过截图传输。

### AI 导航不准怎么办？

1. 双击返回键切到光标模式，手动操作
2. AI 会自动学习 —— 记住每个网站怎么操作，下次更准
3. 复杂网站建议用语音控制，直接说你想做什么

### 支持多台电视吗？

目前一个服务器对应一台电视。多台电视可以在不同端口启动多个服务：

```bash
PORT=9871 npm run dev  # 第二台电视
PORT=9872 npm run dev  # 第三台电视
```

### 怎么更新？

```bash
cd TileTV
git pull
cd server && npm install  # 更新服务器
cd .. && ./gradlew assembleDebug  # 重新编译 APK
```

---

## 工作原理（给好奇的人）

```
你按了遥控器 →  方向键

    ↓

电视APP →  通过 WiFi 发送给电脑："用户按了右键"

    ↓

电脑上的服务器：
  1. 让 AI 看一眼当前网页截图
  2. AI 说："右边有个视频卡片，CSS选择器是 .video-card:nth(2)"
  3. Playwright 在 Chrome 里高亮那个元素
  4. 截一张图

    ↓

电脑 →  把截图发回电视

    ↓

电视APP →  显示新截图（你看到焦点跳到了右边的视频）
```

AI 还会把学到的东西记下来：
- "B站首页的视频卡片在 `.bili-video-card` 里"
- "搜索框在页面顶部 `.search-input`"
- 下次访问 B 站就不用重新分析了，直接用记忆

---

## 项目结构

```
TileTV/
├── app/                    # 📱 Android TV 应用
│   └── src/main/
│       ├── java/.../
│       │   ├── MainActivity        # 首页 - 卡片选择
│       │   ├── BrowserActivity     # 浏览 - 显示截图/WebView
│       │   ├── SettingsActivity    # 设置 - 服务器地址
│       │   ├── adapter/            # 卡片和分类行的适配器
│       │   ├── ws/                 # WebSocket 通信
│       │   └── widget/             # 焦点指示器、虚拟光标
│       ├── assets/tiles.json       # ← 改这个文件增删网站
│       └── res/                    # UI 布局和样式
│
├── server/                 # 💻 电脑端服务器
│   ├── src/
│   │   ├── browser/        # Playwright 控制 Chrome
│   │   ├── agent/          # Claude AI 导航引擎
│   │   ├── memory/         # 网站导航记忆
│   │   └── ws/             # WebSocket 消息处理
│   ├── h5/                 # 📱 手机语音控制页面
│   └── package.json
│
└── README.md               # 你正在看的这个文件
```

---

## 技术细节

| 项目 | 说明 |
|------|------|
| TV 最低版本 | Android 4.2 (API 17) |
| 服务器要求 | Node.js 18+ |
| AI 模型 | Claude Haiku 4.5 (可配置) |
| 浏览器引擎 | Playwright + Chromium |
| 通信协议 | WebSocket (JSON + Base64 JPEG) |
| 语音识别 | Web Speech API (浏览器原生) |
| UI 风格 | Apple TV 磨砂玻璃 |

### 环境变量（高级配置）

在 `server/.env` 文件中设置，或者用 `export` 命令：

| 变量 | 干什么的 | 默认值 |
|------|---------|--------|
| `ANTHROPIC_API_KEY` | AI 密钥 | 必填 |
| `ANTHROPIC_BASE_URL` | API 地址（代理/中转站用） | 留空=官方地址 |
| `CLAUDE_MODEL` | AI 模型名 | `claude-haiku-4-5-20250315` |
| `PORT` | 服务器端口 | `9870` |
| `VIEWPORT_WIDTH` | 网页宽度 | `1280` |
| `VIEWPORT_HEIGHT` | 网页高度 | `720` |
| `SCREENSHOT_QUALITY` | 截图质量 1-100 | `80` |
| `BROWSER_HEADLESS` | 后台运行浏览器 | `true` |

---

## 许可证

MIT License — 随便用，不用付钱。

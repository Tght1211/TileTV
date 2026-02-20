# TileTV

一个轻量级 Android TV 网页导航启动器，专为老旧电视盒子设计。Apple TV 风格 UI，预设常用视频网站，遥控器即可操作一切。

**最低支持 Android 4.2 (API 17)**，不依赖任何第三方客户端 APK，一个应用搞定所有网站。

---

## 它是什么？

TileTV 是一个「网页壳子」—— 它本身不提供任何视频内容，只是把常用网站以卡片形式展示在电视上，点击后用内置浏览器打开。适合家里有老电视/老盒子、装不了现代 APP 的场景。

```
┌──────────────────────────────────────────────────┐
│  TileTV                                   22:30  │
│                                                  │
│  视频平台                                         │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌───       │
│  │  YT  │ │ B站  │ │ 抖音 │ │  NF  │ │ ...     │
│  └──────┘ └──────┘ └──────┘ └──────┘ └───       │
│                                                  │
│  电视直播                                         │
│  ┌──────┐ ┌──────┐                               │
│  │ 央视 │ │ 咪咕 │                               │
│  └──────┘ └──────┘                               │
│                                                  │
│  全球直播                                         │
│  ┌──────┐ ┌──────┐ ┌──────┐                      │
│  │Pluto │ │ Plex │ │三星  │                      │
│  └──────┘ └──────┘ └──────┘                      │
└──────────────────────────────────────────────────┘
```

---

## 主要功能

- **Apple TV 风格界面** — 水平滚动分类行、大圆角卡片、焦点放大+白色光晕动画
- **三级网页导航策略** — 根据网站特性自动选择最佳操作方式
- **纯遥控器操作** — 无需鼠标/触屏，方向键 + 确认键搞定一切
- **JSON 配置** — 修改一个文件即可增删网站，无需改代码
- **超低门槛** — 支持 Android 4.2+，老旧设备也能用

---

## 三级导航策略

TileTV 根据网站的遥控器友好程度，提供三种操作模式：

| Level | 模式 | 适用场景 | 原理 |
|-------|------|----------|------|
| **1** | TV 模式 | youtube.com/tv 等 TV 专版网页 | 网页本身支持遥控器，直接用 |
| **2** | 智能导航 | 大多数普通网页 | 注入 JS 脚本，方向键在可点击元素间跳转 |
| **3** | 光标模式 | 复杂网页（兜底方案） | 虚拟鼠标光标，方向键移动、按键点击 |

> 在浏览网页时，**双击返回键**可以在 Level 2 和 Level 3 之间切换。

---

## 遥控器按键说明

### 首页（卡片选择界面）

| 按键 | 功能 |
|------|------|
| 方向键 ↑↓←→ | 在卡片之间移动焦点 |
| 确认键 (OK) | 打开选中的网站 |

### Level 1 — TV 模式

遥控器按键直接传递给网页，由网站自己处理（比如 YouTube TV 版本身就支持方向键导航）。

### Level 2 — 智能导航

| 按键 | 功能 |
|------|------|
| 方向键 ↑↓←→ | 在网页的可点击元素（链接、按钮等）之间跳转 |
| 确认键 (OK) | 点击当前高亮的元素 |
| 返回键 | 网页后退（如果有历史记录），否则回到首页 |
| 双击返回键 | 切换到 Level 3 光标模式 |

### Level 3 — 光标模式

| 按键 | 功能 |
|------|------|
| 方向键 ↑↓←→ | 移动屏幕上的虚拟光标（十字准星） |
| 确认键 (OK) | 在光标位置触发右键点击 |
| 菜单键 (Menu) | 在光标位置触发左键点击（主要确认操作） |
| 返回键 | 网页后退 |
| 双击返回键 | 切换回 Level 2 智能导航 |

> 光标移动支持加速：快速连按方向键时步长会从 20px 增加到 50px。

---

## 快速开始

### 环境准备

你需要以下工具（任选一种方式）：

**方式一：Android Studio（推荐新手）**

1. 下载安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio → `File` → `Open` → 选择 `TileTV` 文件夹
3. 等待 Gradle 同步完成（首次可能需要下载依赖）
4. 点击绿色三角 ▶ 运行按钮

**方式二：命令行编译**

需要先安装 [Android SDK](https://developer.android.com/studio#command-tools)，然后：

```bash
# 克隆项目
git clone https://github.com/Tght1211/TileTV.git
cd TileTV

# 创建 local.properties（指向你的 Android SDK 路径）
echo "sdk.dir=/path/to/your/Android/sdk" > local.properties
# macOS 默认路径通常是:
# echo "sdk.dir=/Users/你的用户名/Library/Android/sdk" > local.properties
# Windows 默认路径通常是:
# echo "sdk.dir=C:\\Users\\你的用户名\\AppData\\Local\\Android\\sdk" > local.properties

# 编译 debug 版本
./gradlew assembleDebug

# 编译好的 APK 在这里：
# app/build/outputs/apk/debug/app-debug.apk
```

### 安装到电视

```bash
# 方法一：ADB 安装（电视需要开启"开发者选项"和"USB调试"）
adb connect 电视的IP地址
adb install app/build/outputs/apk/debug/app-debug.apk

# 方法二：U盘安装
# 把 APK 文件复制到 U盘，插到电视上，用文件管理器打开安装
```

> **如何开启电视的开发者选项？**
> 通常在 `设置` → `关于` → 连续点击 `版本号` 7 次 → 返回设置会看到 `开发者选项`。

---

## 自定义网站列表

所有预设网站都在一个 JSON 文件中配置：

**文件位置**：`app/src/main/assets/tiles.json`

### 格式说明

```json
{
  "categories": [
    {
      "name": "分类名称",
      "tiles": [
        {
          "name": "网站名称（显示在卡片上）",
          "url": "https://网站地址",
          "icon": "图标标识（预留字段，暂未使用）",
          "level": 2
        }
      ]
    }
  ]
}
```

### level 怎么选？

| 值 | 什么时候用 | 示例 |
|----|-----------|------|
| `1` | 网站本身有 TV 版界面，支持遥控器操作 | youtube.com/tv |
| `2` | 普通网站，有明确的链接和按钮可以点击 | bilibili.com, baidu.com |
| `3` | 网站很复杂或大量使用自定义控件 | douyin.com |

> **不确定选几？** 先试 `2`，如果方向键导航不好用再改成 `3`。

### 添加网站示例

想添加微博？在某个分类的 `tiles` 数组里加一条：

```json
{ "name": "微博", "url": "https://m.weibo.cn", "icon": "weibo", "level": 2 }
```

想新增一个分类？在 `categories` 数组里加：

```json
{
  "name": "社交媒体",
  "tiles": [
    { "name": "微博", "url": "https://m.weibo.cn", "icon": "weibo", "level": 2 },
    { "name": "Twitter", "url": "https://mobile.twitter.com", "icon": "twitter", "level": 2 }
  ]
}
```

修改后重新编译安装即可生效。

---

## 项目结构

```
TileTV/
├── app/src/main/
│   ├── assets/
│   │   ├── tiles.json              ← 【改这个文件来增删网站】
│   │   └── js/spatial_nav.js       ← 智能导航 JS 脚本（注入网页）
│   ├── java/com/tiletv/app/
│   │   ├── MainActivity.java       ← 首页（卡片分类行列表）
│   │   ├── WebViewActivity.java    ← 网页浏览（三级导航策略）
│   │   ├── adapter/
│   │   │   ├── CategoryAdapter.java ← 分类行适配器
│   │   │   └── TileAdapter.java     ← 卡片适配器（焦点动画）
│   │   ├── model/                   ← 数据模型
│   │   ├── widget/
│   │   │   └── VirtualCursorView.java ← 虚拟光标（Level 3）
│   │   └── util/
│   │       └── AssetUtil.java       ← 文件读取工具
│   └── res/                         ← 布局、颜色、样式
├── build.gradle                     ← 项目构建配置
└── gradlew                          ← Gradle 构建脚本
```

---

## 常见问题

### Q: 网页打开后是白屏 / 排版错乱？

Android 4.2 自带的 WebView 版本太老，不支持现代网页技术。解决方案：

- **优先使用移动版网址**（`m.bilibili.com` 而不是 `www.bilibili.com`），移动版对老浏览器兼容性更好
- 如果设备支持 Android 5.0+，可以去应用商店更新 Android System WebView
- 进阶方案：集成 [Crosswalk](https://github.com/nickstenning/xwalk) 嵌入式浏览器引擎（APK 体积会增大 ~30MB）

### Q: 方向键导航（Level 2）跳转不准确？

智能导航依赖 DOM 元素扫描，某些网站可能：
- 用 canvas/WebGL 渲染内容（无 DOM 元素可识别）
- 大量使用 iframe（跨域 iframe 内的元素无法扫描）
- 动态加载内容（JS 会自动重新扫描，但可能有短暂延迟）

遇到这类网站，双击返回键切换到 Level 3 光标模式。

### Q: 视频播放没声音 / 播放不了？

- 部分网站需要手动点击播放按钮（浏览器安全策略限制自动播放）
- 老设备可能不支持某些视频编码格式（H.265/VP9）
- 确保电视系统时间正确（HTTPS 证书验证需要）

### Q: 怎么回到首页？

按遥控器的 **返回键**。如果当前网页有浏览历史会先后退，多按几次直到回到首页。

### Q: 能不能设为电视桌面（Launcher）？

可以。AndroidManifest.xml 中已经声明了 `LEANBACK_LAUNCHER` category。安装后在系统设置中将默认桌面改为 TileTV 即可。

---

## 技术细节

| 项目 | 说明 |
|------|------|
| 最低系统版本 | Android 4.2 (API 17) |
| 目标系统版本 | Android 9.0 (API 28) |
| 编译 SDK | 34 |
| 语言 | Java 8 |
| UI 框架 | AndroidX RecyclerView |
| 构建工具 | Gradle 7.5.1 + AGP 7.4.2 |
| 数据解析 | Gson 2.10.1 |
| 代码混淆 | ProGuard（release 构建启用） |

---

## 许可证

MIT License
